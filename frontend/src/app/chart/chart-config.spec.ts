import { buildChartConfig, ChartTheme, readableTextOn } from './chart-config';
import { ChartSpec } from './chart-spec';

const THEME: ChartTheme = {
  surface: '#surface',
  ink: '#ink',
  inkSecondary: '#ink2',
  muted: '#muted',
  grid: '#grid',
  axis: '#axis',
  series: ['#s1', '#s2', '#s3', '#s4', '#s5', '#s6', '#s7', '#s8'],
  reducedMotion: false,
};

function spec(overrides: Partial<ChartSpec>): ChartSpec {
  return {
    chartType: 'BAR',
    title: 'Teste',
    subtitle: 'Valor total em reais',
    metric: 'TOTAL_AMOUNT',
    labels: ['A', 'B'],
    labelSlots: [0, 1],
    series: [{ name: 'Valor total', slot: 0, data: [10, 20] }],
    ...overrides,
  };
}

// As opções do Chart.js são tipadas por tipo de gráfico; nos testes basta ler os campos.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const dataset = (config: ReturnType<typeof buildChartConfig>, index = 0) => config.data.datasets[index] as any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const options = (config: ReturnType<typeof buildChartConfig>) => config.options as any;

describe('buildChartConfig', () => {
  it('should color pie slices by the stable slot of each label', () => {
    const config = buildChartConfig(spec({ chartType: 'PIE', labelSlots: [2, 0] }), THEME);

    expect(config.type).toBe('pie');
    expect(dataset(config).backgroundColor).toEqual(['#s3', '#s1']);
    expect(dataset(config).borderColor).toBe('#surface');
    expect(options(config).plugins.legend.display).toBe(true);
  });

  it('should use one color and no legend for a single-series bar chart', () => {
    const config = buildChartConfig(spec({ chartType: 'BAR', series: [{ name: 'Valor total', slot: 2, data: [10, 20] }] }), THEME);

    expect(config.type).toBe('bar');
    expect(dataset(config).backgroundColor).toBe('#s1');
    expect(dataset(config).maxBarThickness).toBe(24);
    expect(options(config).plugins.legend.display).toBe(false);
    expect(options(config).scales.y.beginAtZero).toBe(true);
  });

  it('should color each series by its own slot and show the legend when there are several', () => {
    const config = buildChartConfig(
      spec({
        chartType: 'LINE',
        series: [
          { name: 'Boleto', slot: 1, data: [1, 2] },
          { name: 'Cartão', slot: 2, data: [3, 4] },
        ],
      }),
      THEME,
    );

    expect(config.type).toBe('line');
    expect(dataset(config, 0).borderColor).toBe('#s2');
    expect(dataset(config, 1).borderColor).toBe('#s3');
    expect(dataset(config, 0).borderWidth).toBe(2);
    expect(options(config).plugins.legend.display).toBe(true);
    expect(options(config).interaction).toEqual({ mode: 'index', intersect: false });
  });

  it('should hide point markers on long lines', () => {
    const labels = Array.from({ length: 40 }, (_, index) => `d${index}`);
    const config = buildChartConfig(
      spec({ chartType: 'LINE', labels, labelSlots: labels.map((_, i) => i), series: [{ name: 'v', slot: 0, data: labels.map(() => 1) }] }),
      THEME,
    );

    expect(dataset(config).pointRadius).toBe(0);
  });

  it('should format axis ticks as currency, or as whole numbers for counts', () => {
    const amount = buildChartConfig(spec({ metric: 'TOTAL_AMOUNT', series: [{ name: 'v', slot: 0, data: [1500, 4000] }] }), THEME);
    const count = buildChartConfig(spec({ metric: 'COUNT' }), THEME);

    expect(options(amount).scales.y.ticks.callback(2000)).toBe('R$ 2.000');
    expect(options(count).scales.y.ticks.callback(15)).toBe('15');
  });

  it('should stack the series, round only the top segment and total the column in the tooltip', () => {
    const config = buildChartConfig(
      spec({
        chartType: 'STACKED_BAR',
        series: [
          { name: 'Pix', slot: 0, data: [10, 20] },
          { name: 'Boleto', slot: 1, data: [5, 0] },
        ],
      }),
      THEME,
    );

    expect(config.type).toBe('bar');
    expect(options(config).scales.x.stacked).toBe(true);
    expect(options(config).scales.y.stacked).toBe(true);
    expect(dataset(config, 1).backgroundColor).toBe('#s2');
    // Na primeira coluna o topo é o Boleto; na segunda, sem Boleto, é o Pix.
    expect(dataset(config, 0).borderRadius({ dataIndex: 0 })).toBe(0);
    expect(dataset(config, 1).borderRadius({ dataIndex: 0 })).toEqual({ topLeft: 4, topRight: 4 });
    expect(dataset(config, 0).borderRadius({ dataIndex: 1 })).toEqual({ topLeft: 4, topRight: 4 });
    expect(options(config).plugins.legend.display).toBe(true);
    expect(options(config).plugins.tooltip.callbacks.footer([{ dataIndex: 0 }])).toBe('Total: R$ 15,00');
  });

  it('should draw a ranking as horizontal bars with the values on the X axis', () => {
    const config = buildChartConfig(spec({ chartType: 'HORIZONTAL_BAR' }), THEME);

    expect(config.type).toBe('bar');
    expect(options(config).indexAxis).toBe('y');
    expect(options(config).scales.x.beginAtZero).toBe(true);
    expect(options(config).scales.y.ticks.autoSkip).toBe(false);
    expect(options(config).plugins.legend.display).toBe(false);
    expect(options(config).plugins.tooltip.callbacks.label({ parsed: { x: 20, y: 1 }, dataset: {} })).toBe(' R$ 20,00');
  });

  it('should label each funnel stage with its share of the first one', () => {
    const config = buildChartConfig(
      spec({
        chartType: 'FUNNEL',
        metric: 'COUNT',
        labels: ['Iniciadas', 'Aprovadas', 'Concluídas'],
        labelSlots: [0, 1, 2],
        series: [{ name: 'Transações', slot: 0, data: [40, 30, 15] }],
      }),
      THEME,
    );

    expect(config.data.labels).toEqual(['Iniciadas · 100%', 'Aprovadas · 75%', 'Concluídas · 37,5%']);
    expect(options(config).indexAxis).toBe('y');
    expect(options(config).plugins.tooltip.callbacks.title([{ dataIndex: 1 }])).toBe('Aprovadas');
    expect(options(config).plugins.tooltip.callbacks.afterLabel({ dataIndex: 2 })).toBe(' 50% da etapa anterior');
    expect(options(config).plugins.tooltip.callbacks.afterLabel({ dataIndex: 0 })).toBe('');
  });

  it('should draw histogram bars touching each other and name the value axis', () => {
    const config = buildChartConfig(
      spec({
        chartType: 'HISTOGRAM',
        metric: 'COUNT',
        labels: ['0–500', '500–1.000'],
        series: [{ name: 'Transações', slot: 0, data: [3, 1] }],
      }),
      THEME,
    );

    expect(dataset(config).categoryPercentage).toBe(1);
    expect(dataset(config).barPercentage).toBe(1);
    expect(options(config).scales.x.title.text).toBe('Faixa de valor (R$)');
    expect(options(config).plugins.tooltip.callbacks.label({ parsed: { y: 1 } })).toBe(' 1 transação');
    expect(options(config).plugins.tooltip.callbacks.label({ parsed: { y: 3 } })).toBe(' 3 transações');
  });

  it('should draw the cumulative line as a line chart', () => {
    expect(buildChartConfig(spec({ chartType: 'CUMULATIVE_LINE' }), THEME).type).toBe('line');
  });

  it('should leave a gap in the line where a metric has no value', () => {
    const config = buildChartConfig(
      spec({
        chartType: 'LINE',
        metric: 'AVERAGE_AMOUNT',
        labels: ['a', 'b', 'c'],
        labelSlots: [0, 1, 2],
        series: [{ name: 'Ticket médio', slot: 0, data: [10, null, 30] }],
      }),
      THEME,
    );

    expect(dataset(config).data).toEqual([10, null, 30]);
    expect(options(config).plugins.tooltip.callbacks.label({ parsed: { y: null }, dataset: {} })).toBe(' —');
  });

  it('should build treemap tiles colored by the stable slot of each label', () => {
    const config = buildChartConfig(spec({ chartType: 'TREEMAP', labelSlots: [3, 1] }), THEME);
    const tiles = dataset(config);

    expect(config.type).toBe('treemap');
    expect(tiles.tree).toEqual([
      { label: 'A', value: 10, color: '#s4' },
      { label: 'B', value: 20, color: '#s2' },
    ]);
    expect(tiles.key).toBe('value');
    expect(tiles.backgroundColor({ raw: { v: 20, _data: tiles.tree[1] } })).toBe('#s2');
    expect(tiles.labels.formatter({ raw: { v: 20, w: 300, _data: tiles.tree[1] } })).toEqual(['B', 'R$ 20,00 · 66,7%']);
    // Num bloco estreito a linha do valor não cabe: fica só o nome.
    expect(tiles.labels.formatter({ raw: { v: 20, w: 110, _data: tiles.tree[1] } })).toEqual(['B']);
    expect(options(config).plugins.legend.display).toBe(false);
  });

  it('should turn animation off when the user prefers reduced motion', () => {
    const config = buildChartConfig(spec({}), { ...THEME, reducedMotion: true });

    expect(options(config).animation.duration).toBe(0);
  });
});

describe('readableTextOn', () => {
  it('should pick white text on dark colors and dark text on light ones', () => {
    expect(readableTextOn('#6250d6')).toBe('#ffffff');
    expect(readableTextOn('#008300')).toBe('#ffffff');
    expect(readableTextOn('#eda100')).toBe('#0b0b0b');
    expect(readableTextOn('#e87ba4')).toBe('#0b0b0b');
  });
});
