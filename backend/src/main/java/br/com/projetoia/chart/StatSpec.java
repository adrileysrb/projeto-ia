package br.com.projetoia.chart;

import java.math.BigDecimal;

/**
 * Um numero em destaque, para perguntas cuja resposta e um valor so.
 *
 * @param kind       sempre {@link #KIND}; diz a tela que este resultado e um numero
 * @param subtitle   o que o numero representa e quais filtros foram aplicados
 * @param comparison o mesmo numero no periodo anterior; nulo quando o pedido nao tem inicio e fim
 */
public record StatSpec(
        String kind,
        String title,
        String subtitle,
        Metric metric,
        BigDecimal value,
        Comparison comparison) {

    public static final String KIND = "stat";

    public StatSpec(String title, String subtitle, Metric metric, BigDecimal value, Comparison comparison) {
        this(KIND, title, subtitle, metric, value, comparison);
    }

    /**
     * @param period        o periodo anterior, por extenso
     * @param value         nulo quando o periodo anterior nao teve transacoes e a metrica nao e uma soma
     * @param changePercent variacao do periodo anterior para o atual; nulo quando nao ha base de comparacao
     */
    public record Comparison(String period, BigDecimal value, BigDecimal changePercent) {
    }
}
