import { parseChartSpec } from './chart-spec';

const VALID = {
  chartType: 'PIE',
  title: 'Total por tipo',
  subtitle: 'Valor total em reais',
  metric: 'TOTAL_AMOUNT',
  labels: ['Pix', 'Boleto', 'Cartão'],
  labelSlots: [0, 1, 2],
  series: [{ name: 'Valor total', slot: 0, data: [100.5, 200, 10] }],
};

describe('parseChartSpec', () => {
  it('should read the JSON the backend sends as the tool result', () => {
    expect(parseChartSpec(JSON.stringify(VALID))).toEqual(VALID);
  });

  it('should return null for the error text a failed tool sends to the model', () => {
    expect(parseChartSpec('Gráfico de pizza aceita groupBy TYPE, USER ou MONTH.')).toBeNull();
  });

  it('should reject an unknown chart type', () => {
    expect(parseChartSpec({ ...VALID, chartType: 'RADAR' })).toBeNull();
  });

  it('should accept every chart type and metric the backend can send', () => {
    for (const chartType of ['STACKED_BAR', 'HORIZONTAL_BAR', 'CUMULATIVE_LINE', 'HISTOGRAM', 'FUNNEL', 'TREEMAP']) {
      expect(parseChartSpec({ ...VALID, chartType })?.chartType).toBe(chartType);
    }
    for (const metric of ['AVERAGE_AMOUNT', 'MAX_AMOUNT', 'MIN_AMOUNT']) {
      expect(parseChartSpec({ ...VALID, metric })?.metric).toBe(metric);
    }
  });

  it('should reject a series whose data does not match the labels', () => {
    expect(parseChartSpec({ ...VALID, series: [{ name: 'x', slot: 0, data: [1, 2] }] })).toBeNull();
  });

  it('should reject values that are not numbers', () => {
    expect(parseChartSpec({ ...VALID, series: [{ name: 'x', slot: 0, data: [1, '2', 3] }] })).toBeNull();
  });

  it('should keep the gaps of a metric that has no value at some point', () => {
    const spec = parseChartSpec({ ...VALID, chartType: 'LINE', series: [{ name: 'x', slot: 0, data: [1, null, 3] }] });

    expect(spec?.series[0].data).toEqual([1, null, 3]);
  });

  it('should fall back to positional colors and an empty subtitle when they are missing', () => {
    const spec = parseChartSpec({
      ...VALID,
      subtitle: undefined,
      labelSlots: undefined,
      series: [{ name: 'x', data: [1, 2, 3] }],
    });

    expect(spec?.labelSlots).toEqual([0, 1, 2]);
    expect(spec?.series[0].slot).toBe(0);
    expect(spec?.subtitle).toBe('');
  });
});
