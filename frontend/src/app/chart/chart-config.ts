import { Chart, ChartConfiguration, ChartDataset, LegendItem, Plugin, TooltipItem } from 'chart.js';
import { formatAxisTick, formatShare, formatValue } from './chart-format';
import { ChartSpec } from './chart-spec';

/** Cores lidas dos tokens CSS, para o gráfico acompanhar o tema claro ou escuro. */
export interface ChartTheme {
  surface: string;
  ink: string;
  inkSecondary: string;
  muted: string;
  grid: string;
  axis: string;
  /** Paleta categórica, em ordem fixa. */
  series: string[];
  reducedMotion: boolean;
}

const FONT_FAMILY = 'system-ui, -apple-system, "Segoe UI", sans-serif';
/** Acima disso os marcadores de cada ponto viram ruído; ficam só no hover. */
const MAX_POINTS_WITH_MARKERS = 24;
/** Texto sobre um bloco colorido: o mais legível entre claro e escuro. */
const TEXT_ON_DARK = '#ffffff';
const TEXT_ON_LIGHT = '#0b0b0b';

export function readChartTheme(element: Element): ChartTheme {
  const style = getComputedStyle(element);
  const token = (name: string) => style.getPropertyValue(name).trim();
  return {
    surface: token('--surface'),
    ink: token('--ink'),
    inkSecondary: token('--ink-2'),
    muted: token('--muted'),
    grid: token('--grid'),
    axis: token('--axis'),
    series: [1, 2, 3, 4, 5, 6, 7, 8].map((slot) => token(`--series-${slot}`)),
    reducedMotion: matchMedia('(prefers-reduced-motion: reduce)').matches,
  };
}

export function buildChartConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  switch (spec.chartType) {
    case 'PIE':
      return pieConfig(spec, theme);
    case 'BAR':
      return barConfig(spec, theme);
    case 'STACKED_BAR':
      return stackedBarConfig(spec, theme);
    case 'HORIZONTAL_BAR':
      return horizontalBarConfig(spec, theme);
    case 'FUNNEL':
      return funnelConfig(spec, theme);
    case 'HISTOGRAM':
      return histogramConfig(spec, theme);
    case 'LINE':
    case 'CUMULATIVE_LINE':
      return lineConfig(spec, theme);
    case 'TREEMAP':
      return treemapConfig(spec, theme);
  }
}

/** A paleta tem 8 cores validadas; não se gera uma nona, então o excedente repete a última. */
function colorOf(theme: ChartTheme, slot: number): string {
  return theme.series[Math.min(slot, theme.series.length - 1)];
}

/** Valores de uma série em que as partes somam um total: um ponto sem valor conta como zero. */
function amounts(spec: ChartSpec): number[] {
  return spec.series[0].data.map((value) => value ?? 0);
}

function pieConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const [series] = spec.series;
  const data = amounts(spec);
  const total = data.reduce((sum, value) => sum + value, 0);

  return {
    type: 'pie',
    data: {
      labels: spec.labels,
      datasets: [
        {
          label: series.name,
          data,
          backgroundColor: spec.labelSlots.map((slot) => colorOf(theme, slot)),
          // Um vão de 2px na cor da superfície separa as fatias, em vez de uma borda.
          borderColor: theme.surface,
          borderWidth: 2,
          hoverOffset: 6,
        },
      ],
    },
    options: {
      ...baseOptions(theme),
      plugins: {
        legend: {
          ...legend(theme, 'rect'),
          labels: {
            ...legend(theme, 'rect').labels,
            // A legenda carrega a participação de cada fatia, para o valor não depender do hover.
            generateLabels: (chart) =>
              Chart.overrides.pie.plugins.legend.labels.generateLabels(chart).map((item: LegendItem) => ({
                ...item,
                text: `${item.text} · ${formatShare(data[item.index ?? 0], total)}`,
                fontColor: theme.inkSecondary,
                strokeStyle: 'transparent',
                lineWidth: 0,
              })),
          },
        },
        tooltip: {
          ...tooltip(theme),
          callbacks: {
            label: (item: TooltipItem<'pie'>) =>
              ` ${formatValue(item.parsed, spec.metric)} (${formatShare(item.parsed, total)})`,
          },
        },
      },
    },
  } as ChartConfiguration;
}

function barConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const grouped = spec.series.length > 1;
  const datasets: ChartDataset<'bar'>[] = spec.series.map((series) => ({
    label: series.name,
    data: series.data,
    // Uma série só: uma cor para todas as barras. Várias séries: a cor de cada entidade.
    backgroundColor: colorOf(theme, grouped ? series.slot : 0),
    maxBarThickness: 24,
    // Ponta arredondada no lado do dado, reta na base.
    borderRadius: { topLeft: 4, topRight: 4 },
    borderSkipped: 'bottom',
    // Barras vizinhas se separam por um vão de 2px na cor da superfície.
    borderColor: theme.surface,
    borderWidth: grouped ? { left: 1, right: 1, top: 0, bottom: 0 } : 0,
    categoryPercentage: 0.7,
    barPercentage: grouped ? 1 : 0.9,
  }));

  return {
    type: 'bar',
    data: { labels: spec.labels, datasets },
    options: {
      ...baseOptions(theme),
      scales: cartesianScales(spec, theme),
      plugins: {
        legend: { ...legend(theme, 'rectRounded'), display: grouped },
        tooltip: { ...tooltip(theme), callbacks: { label: valueLabel(spec) } },
      },
    },
  } as ChartConfiguration;
}

function stackedBarConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const columnTotals = spec.labels.map((_, index) =>
    spec.series.reduce((sum, series) => sum + (series.data[index] ?? 0), 0),
  );
  /** O segmento do topo é o último, de baixo para cima, que tem valor naquela coluna. */
  const isTopOfColumn = (seriesIndex: number, dataIndex: number) =>
    (spec.series[seriesIndex].data[dataIndex] ?? 0) > 0 &&
    spec.series.slice(seriesIndex + 1).every((series) => !series.data[dataIndex]);

  const datasets = spec.series.map((series, seriesIndex) => ({
    label: series.name,
    data: series.data,
    backgroundColor: colorOf(theme, series.slot),
    maxBarThickness: 24,
    categoryPercentage: 0.7,
    barPercentage: 0.9,
    // Só o topo da coluna é arredondado; os segmentos de dentro ficam retos.
    borderRadius: (context: { dataIndex: number }) =>
      isTopOfColumn(seriesIndex, context.dataIndex) ? { topLeft: 4, topRight: 4 } : 0,
    // Segmentos empilhados se separam por um vão de 2px na cor da superfície.
    borderColor: theme.surface,
    borderWidth: (context: { dataIndex: number }) =>
      isTopOfColumn(seriesIndex, context.dataIndex) ? 0 : { top: 2, right: 0, bottom: 0, left: 0 },
  }));

  return {
    type: 'bar',
    data: { labels: spec.labels, datasets },
    options: {
      ...baseOptions(theme),
      // O tooltip lista todas as partes da coluna e o total: não é preciso acertar um segmento.
      interaction: { mode: 'index', intersect: false },
      scales: cartesianScales(spec, theme, { stacked: true, valueMax: Math.max(...columnTotals) }),
      plugins: {
        legend: legend(theme, 'rectRounded'),
        tooltip: {
          ...tooltip(theme),
          filter: (item: TooltipItem<'bar'>) => Number(item.parsed.y) !== 0,
          callbacks: {
            label: valueLabel(spec),
            footer: (items: TooltipItem<'bar'>[]) =>
              items.length > 0 ? `Total: ${formatValue(columnTotals[items[0].dataIndex], spec.metric)}` : '',
          },
        },
      },
    },
  } as ChartConfiguration;
}

/** Ranking: o backend já manda os rótulos do maior para o menor. */
function horizontalBarConfig(spec: ChartSpec, theme: ChartTheme, labels = spec.labels): ChartConfiguration {
  const [series] = spec.series;
  const dataset: ChartDataset<'bar'> = {
    label: series.name,
    data: series.data,
    backgroundColor: colorOf(theme, 0),
    maxBarThickness: 24,
    // Ponta arredondada no lado do dado (direita), reta na base.
    borderRadius: { topRight: 4, bottomRight: 4 },
    borderSkipped: 'left',
    categoryPercentage: 0.8,
    barPercentage: 0.9,
  };

  return {
    type: 'bar',
    data: { labels, datasets: [dataset] },
    options: {
      ...baseOptions(theme),
      indexAxis: 'y',
      scales: cartesianScales(spec, theme, { horizontal: true }),
      plugins: {
        legend: { display: false },
        tooltip: { ...tooltip(theme), callbacks: { label: valueLabel(spec, 'x') } },
      },
    },
  } as ChartConfiguration;
}

/**
 * Funil como barras horizontais alinhadas à esquerda: comparar comprimentos a partir de uma
 * base comum é mais preciso do que comparar as larguras de um funil centralizado.
 */
function funnelConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const data = amounts(spec);
  const labels = spec.labels.map((label, index) => `${label} · ${formatShare(data[index], data[0])}`);
  const config = horizontalBarConfig(spec, theme, labels);
  const callbacks = config.options!.plugins!.tooltip!.callbacks as { title?: unknown; afterLabel?: unknown };

  callbacks.title = (items: TooltipItem<'bar'>[]) => (items.length > 0 ? spec.labels[items[0].dataIndex] : '');
  callbacks.afterLabel = (item: TooltipItem<'bar'>) =>
    item.dataIndex === 0
      ? ''
      : ` ${formatShare(data[item.dataIndex], data[item.dataIndex - 1])} da etapa anterior`;
  return config;
}

function histogramConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const [series] = spec.series;
  const dataset: ChartDataset<'bar'> = {
    label: series.name,
    data: series.data,
    backgroundColor: colorOf(theme, 0),
    // Faixas contínuas: as barras encostam, separadas só pelo vão de 2px na cor da superfície.
    categoryPercentage: 1,
    barPercentage: 1,
    borderColor: theme.surface,
    borderWidth: { left: 1, right: 1, top: 0, bottom: 0 },
    borderRadius: { topLeft: 2, topRight: 2 },
    borderSkipped: 'bottom',
  };
  const scales = cartesianScales(spec, theme);

  return {
    type: 'bar',
    data: { labels: spec.labels, datasets: [dataset] },
    options: {
      ...baseOptions(theme),
      scales: {
        ...scales,
        x: {
          ...scales['x'],
          title: { display: true, text: 'Faixa de valor (R$)', color: theme.muted, font: { family: FONT_FAMILY, size: 12 } },
        },
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltip(theme),
          callbacks: {
            title: (items: TooltipItem<'bar'>[]) => (items.length > 0 ? `R$ ${items[0].label}` : ''),
            label: (item: TooltipItem<'bar'>) => {
              const count = Number(item.parsed.y);
              return ` ${formatValue(count, 'COUNT')} ${count === 1 ? 'transação' : 'transações'}`;
            },
          },
        },
      },
    },
  } as ChartConfiguration;
}

function lineConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const many = spec.series.length > 1;
  const showMarkers = spec.labels.length <= MAX_POINTS_WITH_MARKERS;
  const datasets: ChartDataset<'line'>[] = spec.series.map((series) => {
    const color = colorOf(theme, many ? series.slot : 0);
    return {
      label: series.name,
      data: series.data,
      borderColor: color,
      borderWidth: 2,
      borderJoinStyle: 'round',
      borderCapStyle: 'round',
      fill: false,
      pointBackgroundColor: color,
      // Anel de 2px na cor da superfície, para o ponto continuar legível sobre as linhas.
      pointBorderColor: theme.surface,
      pointBorderWidth: 2,
      pointRadius: showMarkers ? 4 : 0,
      pointHoverRadius: 5,
      pointHoverBorderWidth: 2,
      pointHitRadius: 12,
    };
  });

  return {
    type: 'line',
    data: { labels: spec.labels, datasets },
    options: {
      ...baseOptions(theme),
      // O tooltip segue o X mais próximo e lista todas as séries: não é preciso acertar a linha.
      interaction: { mode: 'index', intersect: false },
      scales: cartesianScales(spec, theme),
      plugins: {
        legend: {
          ...legend(theme, 'line'),
          display: many,
          labels: {
            ...legend(theme, 'line').labels,
            // Por padrão a marca da legenda copia a borda do ponto, que aqui é o anel na cor
            // da superfície e ficaria invisível. A marca usa a cor e a espessura da linha.
            generateLabels: (chart) =>
              Chart.defaults.plugins.legend.labels.generateLabels(chart).map((item: LegendItem) => ({
                ...item,
                strokeStyle: datasets[item.datasetIndex ?? 0].borderColor as string,
                lineWidth: 2,
              })),
          },
        },
        tooltip: { ...tooltip(theme), callbacks: { label: valueLabel(spec) } },
        crosshair: { color: theme.axis },
      },
    },
    plugins: [crosshairPlugin],
  } as ChartConfiguration;
}

/**
 * Um bloco do treemap, como o plugin o devolve nos callbacks: `v` é o valor, `w` a largura
 * em pixels e `_data` o item original.
 */
interface TreemapTile {
  raw?: { v?: number; w?: number; _data?: { label?: string; color?: string } };
}

/** Abaixo desta largura a linha do valor não cabe no bloco, que fica só com o nome. */
const MIN_TILE_WIDTH_FOR_VALUE = 150;

function treemapConfig(spec: ChartSpec, theme: ChartTheme): ChartConfiguration {
  const [series] = spec.series;
  const data = amounts(spec);
  const total = data.reduce((sum, value) => sum + value, 0);
  const tree = spec.labels.map((label, index) => ({
    label,
    value: data[index],
    color: colorOf(theme, spec.labelSlots[index]),
  }));
  const colorOfTile = (tile: TreemapTile) => tile.raw?._data?.color ?? colorOf(theme, 0);
  const valueOfTile = (tile: TreemapTile) => tile.raw?.v ?? 0;
  const describe = (tile: TreemapTile) =>
    `${formatValue(valueOfTile(tile), spec.metric)} · ${formatShare(valueOfTile(tile), total)}`;

  return {
    type: 'treemap',
    data: {
      datasets: [
        {
          label: series.name,
          data: [],
          tree,
          key: 'value',
          backgroundColor: colorOfTile,
          // Blocos vizinhos se separam por um vão de 2px, sem borda.
          spacing: 2,
          borderWidth: 0,
          borderRadius: 4,
          labels: {
            display: true,
            align: 'left',
            position: 'top',
            // Bloco pequeno demais até para o nome fica sem rótulo; o valor está no tooltip e na tabela.
            overflow: 'hidden',
            padding: 6,
            color: (tile: TreemapTile) => readableTextOn(colorOfTile(tile)),
            font: [
              { family: FONT_FAMILY, size: 13, weight: 'bold' },
              { family: FONT_FAMILY, size: 12 },
            ],
            formatter: (tile: TreemapTile) => {
              const name = tile.raw?._data?.label ?? '';
              return (tile.raw?.w ?? 0) < MIN_TILE_WIDTH_FOR_VALUE ? [name] : [name, describe(tile)];
            },
          },
        },
      ],
    },
    options: {
      ...baseOptions(theme),
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltip(theme),
          callbacks: {
            title: (items: TreemapTile[]) => items[0]?.raw?._data?.label ?? '',
            label: (item: TreemapTile) => ` ${describe(item)}`,
          },
        },
      },
    },
  } as unknown as ChartConfiguration;
}

/** Branco ou quase preto, o que tiver mais contraste com a cor de fundo (fórmula de contraste da WCAG). */
export function readableTextOn(background: string): string {
  const channels = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(background);
  if (!channels) {
    return TEXT_ON_DARK;
  }
  const [red, green, blue] = channels.slice(1).map((hex) => {
    const channel = parseInt(hex, 16) / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  const contrastWithWhite = 1.05 / (luminance + 0.05);
  // Luminância do quase preto #0b0b0b.
  const contrastWithDark = (luminance + 0.05) / (0.0033 + 0.05);
  return contrastWithWhite >= contrastWithDark ? TEXT_ON_DARK : TEXT_ON_LIGHT;
}

function baseOptions(theme: ChartTheme) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: theme.reducedMotion ? 0 : 300 },
    font: { family: FONT_FAMILY },
    layout: { padding: { top: 8 } },
  };
}

interface ScaleLayout {
  /** Barras horizontais: as categorias vão no eixo Y e os valores no X. */
  horizontal?: boolean;
  stacked?: boolean;
  /** Maior valor do eixo, quando não é o maior valor de uma série (colunas empilhadas). */
  valueMax?: number;
}

function cartesianScales(spec: ChartSpec, theme: ChartTheme, layout: ScaleLayout = {}) {
  const axisMax = layout.valueMax ?? Math.max(...spec.series.flatMap((series) => series.data.map((value) => value ?? 0)));
  const tickFont = { family: FONT_FAMILY, size: 12 };
  const category = {
    stacked: layout.stacked,
    grid: { display: false },
    border: { color: theme.axis },
    ticks: layout.horizontal
      ? // No ranking o rótulo é o nome do grupo e precisa aparecer inteiro, em cor de texto.
        { color: theme.inkSecondary, font: tickFont, autoSkip: false }
      : { color: theme.muted, font: tickFont, maxRotation: 0, autoSkip: true, autoSkipPadding: 12 },
  };
  const value = {
    stacked: layout.stacked,
    beginAtZero: true,
    border: { display: false },
    // Grade discreta: linhas finas e contínuas, um tom fora da superfície.
    grid: { color: theme.grid, lineWidth: 1 },
    ticks: {
      color: theme.muted,
      font: tickFont,
      maxTicksLimit: 6,
      precision: spec.metric === 'COUNT' ? 0 : undefined,
      callback: (tick: number | string) => formatAxisTick(Number(tick), spec.metric, axisMax),
    },
  };
  return layout.horizontal ? { x: value, y: category } : { x: category, y: value };
}

function legend(theme: ChartTheme, pointStyle: 'rect' | 'rectRounded' | 'line') {
  return {
    display: true,
    position: 'bottom' as const,
    labels: {
      // O texto da legenda usa a cor de texto; quem carrega a identidade é a marca ao lado.
      color: theme.inkSecondary,
      font: { family: FONT_FAMILY, size: 12 },
      usePointStyle: true,
      pointStyle,
      // A marca da legenda imita a do gráfico: um traço para linhas, um bloco para barras e fatias.
      pointStyleWidth: pointStyle === 'line' ? 22 : 10,
      boxHeight: 8,
      padding: 16,
    },
  };
}

function tooltip(theme: ChartTheme) {
  return {
    backgroundColor: theme.ink,
    titleColor: theme.surface,
    bodyColor: theme.surface,
    footerColor: theme.surface,
    titleFont: { family: FONT_FAMILY, size: 12, weight: 'normal' as const },
    bodyFont: { family: FONT_FAMILY, size: 13, weight: 'bold' as const },
    footerFont: { family: FONT_FAMILY, size: 12, weight: 'normal' as const },
    padding: 10,
    cornerRadius: 6,
    boxWidth: 8,
    boxHeight: 8,
    boxPadding: 4,
    usePointStyle: true,
  };
}

/** @param valueAxis eixo em que está o valor: Y nas barras verticais e linhas, X nas horizontais */
function valueLabel(spec: ChartSpec, valueAxis: 'x' | 'y' = 'y') {
  return (item: TooltipItem<'bar' | 'line'>) => {
    const parsed = item.parsed[valueAxis];
    const value = formatValue(parsed === null || parsed === undefined ? null : Number(parsed), spec.metric);
    return spec.series.length > 1 ? ` ${value} · ${item.dataset.label}` : ` ${value}`;
  };
}

/** Linha vertical fina na posição do tooltip, para o leitor mirar numa data e não numa linha de 2px. */
const crosshairPlugin: Plugin<'line'> = {
  id: 'crosshair',
  beforeDatasetsDraw(chart, _args, options: { color?: string }) {
    const active = chart.tooltip?.getActiveElements() ?? [];
    if (active.length === 0) {
      return;
    }
    const { ctx, chartArea } = chart;
    const x = active[0].element.x;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(x, chartArea.top);
    ctx.lineTo(x, chartArea.bottom);
    ctx.lineWidth = 1;
    ctx.strokeStyle = options.color ?? '#888';
    ctx.stroke();
    ctx.restore();
  },
};
