package br.com.projetoia.chart;

/**
 * O que o modelo pede: qual grafico desenhar e qual recorte dos dados usar.
 * Os numeros nunca vem do modelo; sao calculados a partir do banco.
 *
 * @param groupBy obrigatorio em todos os tipos, menos HISTOGRAM e FUNNEL
 * @param limit   quantas barras mostrar em HORIZONTAL_BAR; nulo mostra todas
 */
public record ChartRequest(
        ChartType chartType,
        String title,
        Metric metric,
        GroupBy groupBy,
        SplitBy splitBy,
        Integer limit,
        TransactionFilter filter) {

    public ChartRequest {
        if (chartType == null) {
            throw new ChartRequestException("chartType é obrigatório: PIE, BAR, STACKED_BAR, HORIZONTAL_BAR, "
                    + "LINE, CUMULATIVE_LINE, HISTOGRAM, FUNNEL ou TREEMAP.");
        }
        if (chartType == ChartType.HISTOGRAM) {
            // O histograma sempre conta transacoes por faixa de valor.
            metric = Metric.COUNT;
        } else if (metric == null) {
            metric = chartType == ChartType.FUNNEL ? Metric.COUNT : Metric.TOTAL_AMOUNT;
        }
        title = title == null || title.isBlank() ? "Gráfico" : title.strip();
        limit = limit == null || limit < 1 ? null : limit;
        filter = filter == null ? TransactionFilter.NONE : filter;
    }

    /** Pedido sem filtro, divisao em series nem limite. */
    public ChartRequest(ChartType chartType, String title, Metric metric, GroupBy groupBy) {
        this(chartType, title, metric, groupBy, null, null, TransactionFilter.NONE);
    }
}
