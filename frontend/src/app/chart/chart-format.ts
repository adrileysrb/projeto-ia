import { Metric } from './chart-spec';

const CURRENCY = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const INTEGER = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });
const COMPACT = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });
const PERCENT = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 });

/** Só a quantidade de transações não é dinheiro; total, média, maior e menor são valores em reais. */
function isMoney(metric: Metric): boolean {
  return metric !== 'COUNT';
}

/** Valor completo, como aparece no tooltip e na tabela. Um ponto sem valor vira um travessão. */
export function formatValue(value: number | null, metric: Metric): string {
  if (value === null) {
    return '—';
  }
  return isMoney(metric) ? CURRENCY.format(value) : INTEGER.format(value);
}

/** Valor curto para os ticks do eixo: por extenso até 10 mil, compacto acima disso. */
export function formatAxisTick(value: number, metric: Metric, axisMax: number): string {
  const text = axisMax >= 10_000 ? COMPACT.format(value) : INTEGER.format(value);
  return isMoney(metric) ? `R$ ${text}` : text;
}

export function formatShare(value: number, total: number): string {
  return PERCENT.format(total === 0 ? 0 : value / total);
}

/** Percentual que já vem calculado (12,5 significa 12,5%), sem sinal. */
export function formatPercentPoints(percent: number): string {
  return PERCENT.format(Math.abs(percent) / 100);
}
