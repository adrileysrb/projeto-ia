import { isArrayOf, isFiniteNumber, isRecord, isString, toRecord } from '../visual/guards';

export type ChartType =
  | 'PIE'
  | 'BAR'
  | 'STACKED_BAR'
  | 'HORIZONTAL_BAR'
  | 'LINE'
  | 'CUMULATIVE_LINE'
  | 'HISTOGRAM'
  | 'FUNNEL'
  | 'TREEMAP';
export type Metric = 'TOTAL_AMOUNT' | 'COUNT' | 'AVERAGE_AMOUNT' | 'MAX_AMOUNT' | 'MIN_AMOUNT';

export interface ChartSeries {
  name: string;
  /** Posição de cor estável da série: a mesma entidade recebe sempre a mesma cor. */
  slot: number;
  /** Um valor por rótulo; `null` onde não há transações e a métrica não é uma soma. */
  data: (number | null)[];
}

/** Gráfico calculado pelo backend; chega como conteúdo do evento TOOL_CALL_RESULT. */
export interface ChartSpec {
  chartType: ChartType;
  title: string;
  /** O que os números representam e quais filtros foram aplicados. */
  subtitle: string;
  metric: Metric;
  labels: string[];
  /** Posição de cor estável de cada rótulo, usada pelas fatias da pizza e pelos blocos do treemap. */
  labelSlots: number[];
  series: ChartSeries[];
}

const CHART_TYPES: readonly string[] = [
  'PIE',
  'BAR',
  'STACKED_BAR',
  'HORIZONTAL_BAR',
  'LINE',
  'CUMULATIVE_LINE',
  'HISTOGRAM',
  'FUNNEL',
  'TREEMAP',
];
export const METRICS: readonly string[] = ['TOTAL_AMOUNT', 'COUNT', 'AVERAGE_AMOUNT', 'MAX_AMOUNT', 'MIN_AMOUNT'];

/**
 * Interpreta o resultado de uma tool como gráfico. Devolve `null` quando o conteúdo
 * não é um gráfico válido (por exemplo, a mensagem de erro que a tool devolve ao modelo).
 */
export function parseChartSpec(content: unknown): ChartSpec | null {
  const value = toRecord(content);
  if (!value) {
    return null;
  }

  const { chartType, title, subtitle, metric, labels, labelSlots, series } = value;
  if (typeof chartType !== 'string' || !CHART_TYPES.includes(chartType)) {
    return null;
  }
  if (typeof metric !== 'string' || !METRICS.includes(metric)) {
    return null;
  }
  if (!isArrayOf(labels, isString) || labels.length === 0) {
    return null;
  }
  if (!Array.isArray(series) || series.length === 0) {
    return null;
  }

  const parsedSeries: ChartSeries[] = [];
  for (const [index, item] of series.entries()) {
    if (!isRecord(item) || !isArrayOf(item['data'], isValueOrGap) || item['data'].length !== labels.length) {
      return null;
    }
    parsedSeries.push({
      name: isString(item['name']) ? item['name'] : `Série ${index + 1}`,
      slot: isSlot(item['slot']) ? item['slot'] : index,
      data: item['data'],
    });
  }

  return {
    chartType: chartType as ChartType,
    title: isString(title) ? title : 'Gráfico',
    subtitle: isString(subtitle) ? subtitle : '',
    metric: metric as Metric,
    labels,
    labelSlots:
      isArrayOf(labelSlots, isSlot) && labelSlots.length === labels.length ? labelSlots : labels.map((_, index) => index),
    series: parsedSeries,
  };
}

function isValueOrGap(value: unknown): value is number | null {
  return value === null || isFiniteNumber(value);
}

function isSlot(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 0;
}
