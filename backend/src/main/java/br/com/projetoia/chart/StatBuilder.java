package br.com.projetoia.chart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Calcula o numero em destaque e, quando ha periodo, a variacao contra o periodo anterior. */
public final class StatBuilder {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private StatBuilder() {
    }

    /**
     * @param previous transacoes e rotulo do periodo anterior; nulo quando o pedido nao tem inicio e fim
     */
    public static StatSpec build(StatRequest request, List<TransactionRow> rows, PreviousPeriod previous) {
        Metric metric = request.metric();
        BigDecimal value = Aggregate.of(rows).value(metric);
        if (value == null) {
            // Media, maior e menor nao existem sem transacoes; soma e quantidade valem zero.
            throw new ChartRequestException("Nenhuma transação encontrada para esse filtro.");
        }

        List<String> parts = new ArrayList<>();
        parts.add(metric.description());
        parts.addAll(request.filter().describe());

        return new StatSpec(request.title(), String.join(" · ", parts), metric, value, comparison(metric, value, previous));
    }

    private static StatSpec.Comparison comparison(Metric metric, BigDecimal value, PreviousPeriod previous) {
        if (previous == null) {
            return null;
        }
        BigDecimal before = Aggregate.of(previous.rows()).value(metric);
        BigDecimal change = before == null || before.signum() == 0
                ? null
                : value.subtract(before).multiply(HUNDRED).divide(before, 1, RoundingMode.HALF_UP);
        return new StatSpec.Comparison(previous.label(), before, change);
    }

    public record PreviousPeriod(String label, List<TransactionRow> rows) {
    }
}
