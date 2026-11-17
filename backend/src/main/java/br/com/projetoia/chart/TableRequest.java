package br.com.projetoia.chart;

/** Pedido de uma lista de transacoes. */
public record TableRequest(String title, TableOrder orderBy, Integer limit, TransactionFilter filter) {

    public static final int DEFAULT_LIMIT = 10;
    /** Teto para a lista caber na conversa e no contexto do modelo. */
    public static final int MAX_LIMIT = 50;

    public TableRequest {
        title = title == null || title.isBlank() ? "Transações" : title.strip();
        orderBy = orderBy == null ? TableOrder.NEWEST : orderBy;
        limit = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        filter = filter == null ? TransactionFilter.NONE : filter;
    }
}
