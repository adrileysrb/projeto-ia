package br.com.projetoia.chart;

/** Pedido de um unico numero em destaque: uma metrica sobre um recorte das transacoes. */
public record StatRequest(String title, Metric metric, TransactionFilter filter) {

    public StatRequest {
        metric = metric == null ? Metric.TOTAL_AMOUNT : metric;
        title = title == null || title.isBlank() ? metric.label() : title.strip();
        filter = filter == null ? TransactionFilter.NONE : filter;
    }
}
