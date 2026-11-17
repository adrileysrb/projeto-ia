import { formatPercentPoints, formatValue } from '../chart/chart-format';
import { Metric, METRICS } from '../chart/chart-spec';
import { isFiniteNumber, isRecord, isString, toRecord } from './guards';

/** O mesmo número no período anterior. */
export interface StatComparison {
  period: string;
  /** `null` quando o período anterior não teve transações e a métrica não é uma soma. */
  value: number | null;
  /** Variação do período anterior para o atual, em pontos percentuais; `null` sem base de comparação. */
  changePercent: number | null;
}

/** Um número em destaque calculado pelo backend, para perguntas cuja resposta é um valor só. */
export interface StatSpec {
  title: string;
  subtitle: string;
  metric: Metric;
  value: number;
  comparison: StatComparison | null;
}

export function parseStatSpec(content: unknown): StatSpec | null {
  const value = toRecord(content);
  if (!value) {
    return null;
  }
  const { title, subtitle, metric, value: amount, comparison } = value;
  if (typeof metric !== 'string' || !METRICS.includes(metric) || !isFiniteNumber(amount)) {
    return null;
  }
  return {
    title: isString(title) ? title : 'Total',
    subtitle: isString(subtitle) ? subtitle : '',
    metric: metric as Metric,
    value: amount,
    comparison: parseComparison(comparison),
  };
}

function parseComparison(value: unknown): StatComparison | null {
  if (!isRecord(value) || !isString(value['period'])) {
    return null;
  }
  return {
    period: value['period'],
    value: isFiniteNumber(value['value']) ? value['value'] : null,
    changePercent: isFiniteNumber(value['changePercent']) ? value['changePercent'] : null,
  };
}

export interface ComparisonSummary {
  /** Sentido da variação; `none` quando não há base de comparação. */
  direction: 'up' | 'down' | 'same' | 'none';
  text: string;
}

/** A comparação em uma frase, como aparece abaixo do número. */
export function summarizeComparison(spec: StatSpec): ComparisonSummary | null {
  const comparison = spec.comparison;
  if (!comparison) {
    return null;
  }
  if (comparison.changePercent === null || comparison.value === null) {
    return { direction: 'none', text: `Sem transações em ${comparison.period} para comparar` };
  }
  const before = `${comparison.period} (${formatValue(comparison.value, spec.metric)})`;
  if (comparison.changePercent === 0) {
    return { direction: 'same', text: `Igual a ${before}` };
  }
  const change = formatPercentPoints(comparison.changePercent);
  return comparison.changePercent > 0
    ? { direction: 'up', text: `${change} acima de ${before}` }
    : { direction: 'down', text: `${change} abaixo de ${before}` };
}
