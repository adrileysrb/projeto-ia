package br.com.projetoia.chart;

public enum Metric {
    /** Soma dos valores das transacoes, em reais. */
    TOTAL_AMOUNT("Valor total", "Valor total em reais"),
    /** Quantidade de transacoes. */
    COUNT("Transações", "Quantidade de transações"),
    /** Valor medio por transacao (ticket medio), em reais. */
    AVERAGE_AMOUNT("Ticket médio", "Valor médio por transação, em reais"),
    /** Maior valor de uma transacao, em reais. */
    MAX_AMOUNT("Maior valor", "Maior valor de uma transação, em reais"),
    /** Menor valor de uma transacao, em reais. */
    MIN_AMOUNT("Menor valor", "Menor valor de uma transação, em reais");

    private final String label;
    private final String description;

    Metric(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Nome curto, usado como nome da serie. */
    public String label() {
        return label;
    }

    /** O que os numeros representam, usado no subtitulo. */
    public String description() {
        return description;
    }

    /**
     * Metricas aditivas podem ser somadas entre grupos: as partes formam um total. So elas fazem
     * sentido em pizza, barras empilhadas, treemap, funil e acumulado.
     */
    public boolean isAdditive() {
        return this == TOTAL_AMOUNT || this == COUNT;
    }

    /** Casas decimais do resultado: reais tem centavos, quantidade nao. */
    public int scale() {
        return this == COUNT ? 0 : 2;
    }
}
