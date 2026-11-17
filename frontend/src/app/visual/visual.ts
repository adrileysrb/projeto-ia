import { ChartSpec, parseChartSpec } from '../chart/chart-spec';
import { toRecord } from './guards';
import { parseStatSpec, StatSpec } from './stat-spec';
import { parseTableSpec, TableSpec } from './table-spec';

/** O que uma tool devolve para a tela: gráfico, número em destaque ou tabela. */
export type Visual =
  | { kind: 'chart'; spec: ChartSpec }
  | { kind: 'stat'; spec: StatSpec }
  | { kind: 'table'; spec: TableSpec };

/**
 * Interpreta o resultado de uma tool pelo campo `kind` que o backend envia. Devolve `null`
 * quando o conteúdo não é algo para mostrar (por exemplo, a mensagem de erro que a tool
 * devolve ao modelo).
 */
export function parseVisual(content: unknown): Visual | null {
  const value = toRecord(content);
  switch (value?.['kind']) {
    case 'chart': {
      const spec = parseChartSpec(value);
      return spec && { kind: 'chart', spec };
    }
    case 'stat': {
      const spec = parseStatSpec(value);
      return spec && { kind: 'stat', spec };
    }
    case 'table': {
      const spec = parseTableSpec(value);
      return spec && { kind: 'table', spec };
    }
    default:
      return null;
  }
}
