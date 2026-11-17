import { parseStatSpec, StatSpec, summarizeComparison } from './stat-spec';
import { parseTableSpec } from './table-spec';
import { parseVisual } from './visual';

const STAT = {
  kind: 'stat',
  title: 'Pix em outubro',
  subtitle: 'Valor total em reais · Pix · 01/10/2026 a 31/10/2026',
  metric: 'TOTAL_AMOUNT',
  value: 10087.47,
  comparison: { period: '01/09/2026 a 30/09/2026', value: 6240.43, changePercent: 61.6 },
};

const TABLE = {
  kind: 'table',
  title: 'Maiores transações',
  subtitle: 'Maiores valores primeiro',
  rows: [{ date: '15/10/2026 20:34', user: 'Ana Souza', type: 'Cartão', category: 'Saúde', status: 'Concluída', amount: 4925.14 }],
  totalRows: 45,
};

function stat(comparison: StatSpec['comparison']): StatSpec {
  return { title: 't', subtitle: '', metric: 'TOTAL_AMOUNT', value: 100, comparison };
}

describe('parseVisual', () => {
  it('should read each kind of tool result', () => {
    expect(parseVisual(JSON.stringify(STAT))).toMatchObject({ kind: 'stat', spec: { value: 10087.47 } });
    expect(parseVisual(JSON.stringify(TABLE))).toMatchObject({ kind: 'table', spec: { totalRows: 45 } });
  });

  it('should return null for text, for unknown kinds and for a kind with invalid content', () => {
    expect(parseVisual('Nenhuma transação encontrada para esse filtro.')).toBeNull();
    expect(parseVisual({ kind: 'mapa' })).toBeNull();
    expect(parseVisual({ kind: 'chart', chartType: 'RADAR' })).toBeNull();
    expect(parseVisual({ kind: 'stat', metric: 'COUNT', value: 'muitas' })).toBeNull();
    expect(parseVisual({ kind: 'table', rows: [] })).toBeNull();
  });
});

describe('parseStatSpec', () => {
  it('should read the number and its comparison', () => {
    expect(parseStatSpec(STAT)).toEqual({
      title: 'Pix em outubro',
      subtitle: 'Valor total em reais · Pix · 01/10/2026 a 31/10/2026',
      metric: 'TOTAL_AMOUNT',
      value: 10087.47,
      comparison: { period: '01/09/2026 a 30/09/2026', value: 6240.43, changePercent: 61.6 },
    });
  });

  it('should accept a number without comparison', () => {
    expect(parseStatSpec({ ...STAT, comparison: null })?.comparison).toBeNull();
  });
});

describe('summarizeComparison', () => {
  it('should say how far above or below the previous period the number is', () => {
    expect(summarizeComparison(stat({ period: 'setembro', value: 80, changePercent: 25 }))).toEqual({
      direction: 'up',
      text: '25% acima de setembro (R$ 80,00)',
    });
    expect(summarizeComparison(stat({ period: 'setembro', value: 125, changePercent: -20 }))).toEqual({
      direction: 'down',
      text: '20% abaixo de setembro (R$ 125,00)',
    });
    expect(summarizeComparison(stat({ period: 'setembro', value: 100, changePercent: 0 }))?.direction).toBe('same');
  });

  it('should say there is nothing to compare with when the previous period is empty', () => {
    expect(summarizeComparison(stat({ period: 'setembro', value: 0, changePercent: null }))).toEqual({
      direction: 'none',
      text: 'Sem transações em setembro para comparar',
    });
  });

  it('should return null when the number has no comparison', () => {
    expect(summarizeComparison(stat(null))).toBeNull();
  });
});

describe('parseTableSpec', () => {
  it('should read the rows and how many transactions match', () => {
    const spec = parseTableSpec(TABLE);

    expect(spec?.rows).toEqual(TABLE.rows);
    expect(spec?.totalRows).toBe(45);
  });

  it('should reject a row without a numeric amount', () => {
    expect(parseTableSpec({ ...TABLE, rows: [{ ...TABLE.rows[0], amount: 'R$ 10' }] })).toBeNull();
  });

  it('should never report fewer matching transactions than the rows it shows', () => {
    expect(parseTableSpec({ ...TABLE, totalRows: 0 })?.totalRows).toBe(1);
  });
});
