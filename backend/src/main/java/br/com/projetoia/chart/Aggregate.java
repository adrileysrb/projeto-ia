package br.com.projetoia.chart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/** Acumula valores de transacoes e responde qualquer {@link Metric} sobre eles. */
final class Aggregate {

    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal max;
    private BigDecimal min;
    private long count;

    static Aggregate of(Collection<TransactionRow> rows) {
        Aggregate aggregate = new Aggregate();
        rows.forEach(row -> aggregate.add(row.amount()));
        return aggregate;
    }

    void add(BigDecimal amount) {
        sum = sum.add(amount);
        max = max == null ? amount : max.max(amount);
        min = min == null ? amount : min.min(amount);
        count++;
    }

    /**
     * Sem nenhuma transacao, soma e quantidade valem zero; media, maior e menor nao existem
     * e voltam nulos.
     */
    BigDecimal value(Metric metric) {
        BigDecimal value = switch (metric) {
            case TOTAL_AMOUNT -> sum;
            case COUNT -> BigDecimal.valueOf(count);
            case AVERAGE_AMOUNT -> count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            case MAX_AMOUNT -> max;
            case MIN_AMOUNT -> min;
        };
        return value == null ? null : value.setScale(metric.scale(), RoundingMode.HALF_UP);
    }
}
