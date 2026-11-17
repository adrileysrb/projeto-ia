package br.com.projetoia.chart;

import java.math.BigDecimal;
import java.util.List;

/**
 * Grafico pronto para desenhar. E o resultado da tool: vai para o modelo (para ele
 * comentar os numeros) e para a tela, dentro do evento TOOL_CALL_RESULT do AG-UI.
 *
 * @param kind       sempre {@link #KIND}; diz a tela que este resultado e um grafico
 * @param subtitle   o que os numeros representam e quais filtros foram aplicados
 * @param labels     rotulos do eixo de categorias (ou das fatias, na pizza)
 * @param labelSlots posicao de cor estavel de cada rotulo, usada por pizza e treemap
 * @param series     uma ou mais series, cada uma com um valor por rotulo
 */
public record ChartSpec(
        String kind,
        ChartType chartType,
        String title,
        String subtitle,
        Metric metric,
        List<String> labels,
        List<Integer> labelSlots,
        List<Series> series) {

    public static final String KIND = "chart";

    public ChartSpec(ChartType chartType, String title, String subtitle, Metric metric,
                     List<String> labels, List<Integer> labelSlots, List<Series> series) {
        this(KIND, chartType, title, subtitle, metric, labels, labelSlots, series);
    }

    /**
     * @param slot posicao de cor estavel da serie: a mesma entidade recebe sempre a mesma cor,
     *             mesmo quando um filtro muda quantas series aparecem
     * @param data um valor por rotulo; nulo quando nao ha transacoes naquele ponto e a metrica
     *             nao e uma soma (nao existe "media de nada")
     */
    public record Series(String name, int slot, List<BigDecimal> data) {
    }
}
