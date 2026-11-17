package br.com.projetoia.chart;

public enum ChartType {
    /** Pizza: partes de um total. */
    PIE,
    /** Barras verticais, lado a lado quando ha varias series. */
    BAR,
    /** Barras empilhadas: o total de cada grupo dividido em partes. */
    STACKED_BAR,
    /** Barras horizontais ordenadas do maior para o menor (ranking). */
    HORIZONTAL_BAR,
    /** Linhas: evolucao no tempo. */
    LINE,
    /** Linhas com o total acumulado ao longo do tempo. */
    CUMULATIVE_LINE,
    /** Distribuicao das transacoes por faixa de valor. */
    HISTOGRAM,
    /** Transacoes iniciadas, aprovadas e concluidas. */
    FUNNEL,
    /** Blocos com area proporcional ao valor de cada grupo. */
    TREEMAP
}
