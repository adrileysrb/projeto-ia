package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatAndTableBuilderTest {

    private static final List<TransactionRow> ROWS = List.of(
            row("Ana", "100.00", "2026-10-05T09:30:00"),
            row("Bruno", "300.00", "2026-10-20T18:45:00"),
            row("Carla", "50.00", "2026-10-11T12:00:00"));

    @Test
    void statWithoutPeriodHasNoComparison() {
        StatSpec spec = StatBuilder.build(new StatRequest("Total", Metric.TOTAL_AMOUNT, null), ROWS, null);

        assertEquals("stat", spec.kind());
        assertEquals(new BigDecimal("450.00"), spec.value());
        assertEquals("Valor total em reais", spec.subtitle());
        assertNull(spec.comparison());
    }

    @Test
    void statComparesWithThePreviousPeriod() {
        StatBuilder.PreviousPeriod september = new StatBuilder.PreviousPeriod(
                "01/09/2026 a 30/09/2026", List.of(row("Ana", "400.00", "2026-09-10T10:00:00")));

        StatSpec spec = StatBuilder.build(new StatRequest("Total", Metric.TOTAL_AMOUNT, null), ROWS, september);

        assertEquals("01/09/2026 a 30/09/2026", spec.comparison().period());
        assertEquals(new BigDecimal("400.00"), spec.comparison().value());
        assertEquals(new BigDecimal("12.5"), spec.comparison().changePercent());
    }

    /** Sem transacoes no periodo anterior nao ha base para calcular uma variacao. */
    @Test
    void statHasNoChangeWhenThePreviousPeriodIsEmpty() {
        StatBuilder.PreviousPeriod empty = new StatBuilder.PreviousPeriod("01/09/2026 a 30/09/2026", List.of());

        StatSpec total = StatBuilder.build(new StatRequest("t", Metric.TOTAL_AMOUNT, null), ROWS, empty);
        StatSpec average = StatBuilder.build(new StatRequest("t", Metric.AVERAGE_AMOUNT, null), ROWS, empty);

        assertEquals(new BigDecimal("0.00"), total.comparison().value());
        assertNull(total.comparison().changePercent());
        assertEquals(new BigDecimal("150.00"), average.value());
        assertNull(average.comparison().value());
        assertNull(average.comparison().changePercent());
    }

    @Test
    void statOfNothingIsZeroForTotalsAndRejectedForAverages() {
        assertEquals(new BigDecimal("0"),
                StatBuilder.build(new StatRequest("t", Metric.COUNT, null), List.of(), null).value());
        assertThrows(ChartRequestException.class,
                () -> StatBuilder.build(new StatRequest("t", Metric.AVERAGE_AMOUNT, null), List.of(), null));
    }

    @Test
    void tableShowsTheLargestAmountsFirstAndSaysHowManyMatch() {
        TableSpec spec = TableBuilder.build(new TableRequest("Maiores", TableOrder.HIGHEST_AMOUNT, 2, null), ROWS);

        assertEquals("table", spec.kind());
        assertEquals(3, spec.totalRows());
        assertEquals(List.of("Bruno", "Ana"), spec.rows().stream().map(TableSpec.Row::user).toList());
        assertEquals(new TableSpec.Row("20/10/2026 18:45", "Bruno", "Pix", "Saúde", "Concluída", new BigDecimal("300.00")),
                spec.rows().getFirst());
        assertEquals("Maiores valores primeiro", spec.subtitle());
    }

    @Test
    void tableDefaultsToTheTenMostRecentAndCapsTheLimit() {
        TableSpec spec = TableBuilder.build(new TableRequest(null, null, null, null), ROWS);

        assertEquals("Transações", spec.title());
        assertEquals(List.of("Bruno", "Carla", "Ana"), spec.rows().stream().map(TableSpec.Row::user).toList());
        assertEquals(TableRequest.DEFAULT_LIMIT, new TableRequest(null, null, 0, null).limit());
        assertEquals(TableRequest.MAX_LIMIT, new TableRequest(null, null, 500, null).limit());
    }

    @Test
    void emptyTableIsRejected() {
        assertThrows(ChartRequestException.class,
                () -> TableBuilder.build(new TableRequest(null, null, null, null), List.of()));
    }

    private static TransactionRow row(String user, String amount, String createdAt) {
        return new TransactionRow(TransactionType.PIX, user, TransactionCategory.SAUDE, TransactionStatus.CONCLUIDA,
                new BigDecimal(amount), LocalDateTime.parse(createdAt));
    }
}
