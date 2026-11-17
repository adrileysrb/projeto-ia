package br.com.projetoia.chart;

import java.util.Comparator;

/** Ordem das linhas de uma tabela de transacoes. */
public enum TableOrder {
    NEWEST("Mais recentes primeiro", Comparator.comparing(TransactionRow::createdAt).reversed()),
    OLDEST("Mais antigas primeiro", Comparator.comparing(TransactionRow::createdAt)),
    HIGHEST_AMOUNT("Maiores valores primeiro", Comparator.comparing(TransactionRow::amount).reversed()),
    LOWEST_AMOUNT("Menores valores primeiro", Comparator.comparing(TransactionRow::amount));

    private final String description;
    private final Comparator<TransactionRow> comparator;

    TableOrder(String description, Comparator<TransactionRow> comparator) {
        this.description = description;
        this.comparator = comparator;
    }

    public String description() {
        return description;
    }

    public Comparator<TransactionRow> comparator() {
        return comparator;
    }
}
