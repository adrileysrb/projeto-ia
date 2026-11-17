package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Usa os dados do import.sql: 3 usuarios e 15 transacoes de cada um dos 3 tipos, de agosto a
 * novembro de 2026, das quais 5 recusadas e 3 estornadas.
 */
@QuarkusTest
class ChartDataServiceTest {

    private static final TransactionFilter OCTOBER = TransactionFilter.of(null, null, null, null, "2026-10-01", "2026-10-31");

    @Inject
    ChartDataService service;

    @Test
    void countsFifteenTransactionsPerType() {
        ChartSpec spec = service.buildChart(new ChartRequest(ChartType.PIE, "Teste", Metric.COUNT, GroupBy.TYPE));

        assertEquals(List.of("Pix", "Boleto", "Cartão"), spec.labels());
        assertEquals(List.of(new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("15")),
                spec.series().getFirst().data());
    }

    @Test
    void totalsByUserAddUpToTheTotalsByTypeAndByCategory() {
        ChartSpec byUser = service.buildChart(new ChartRequest(ChartType.BAR, "Teste", Metric.TOTAL_AMOUNT, GroupBy.USER));
        ChartSpec byType = service.buildChart(new ChartRequest(ChartType.BAR, "Teste", Metric.TOTAL_AMOUNT, GroupBy.TYPE));
        ChartSpec byCategory = service.buildChart(new ChartRequest(ChartType.TREEMAP, "Teste", Metric.TOTAL_AMOUNT, GroupBy.CATEGORY));

        assertEquals(List.of("Ana Souza", "Bruno Lima", "Carla Mendes"), byUser.labels());
        assertEquals(sum(byType), sum(byUser));
        assertEquals(sum(byType), sum(byCategory));
        assertEquals(6, byCategory.labels().size());
        assertTrue(sum(byUser).signum() > 0);
    }

    @Test
    void filtersByTypeAndByPartOfTheUserName() {
        TransactionFilter filter = new TransactionFilter(TransactionType.PIX, "bruno", null, null, null, null);
        ChartSpec spec = service.buildChart(
                new ChartRequest(ChartType.BAR, "Teste", Metric.COUNT, GroupBy.MONTH, SplitBy.USER, null, filter));

        assertEquals(List.of("Bruno Lima"), spec.series().stream().map(ChartSpec.Series::name).toList());
        assertEquals(new BigDecimal("5"), sum(spec));
        // Bruno e o segundo usuario em ordem alfabetica, e continua com a segunda cor mesmo sozinho.
        assertEquals(1, spec.series().getFirst().slot());
    }

    @Test
    void weeklyLineSplitByTypeHasOneValuePerWeekInEverySeries() {
        ChartSpec spec = service.buildChart(
                new ChartRequest(ChartType.LINE, "Teste", Metric.TOTAL_AMOUNT, GroupBy.WEEK, SplitBy.TYPE, null, null));

        assertEquals(3, spec.series().size());
        assertTrue(spec.labels().size() >= 12, "semanas: " + spec.labels().size());
        spec.series().forEach(series -> assertEquals(spec.labels().size(), series.data().size()));
    }

    /** O ultimo dia do periodo entra inteiro: ha uma transacao em 29/10 as 13h e nenhuma em 30 ou 31. */
    @Test
    void periodFilterIncludesTheWholeLastDay() {
        TransactionFilter untilThe29th = TransactionFilter.of(null, null, null, null, "2026-10-01", "2026-10-29");
        TransactionFilter untilThe28th = TransactionFilter.of(null, null, null, null, "2026-10-01", "2026-10-28");

        assertEquals(count(OCTOBER), count(untilThe29th));
        assertEquals(count(OCTOBER).subtract(BigDecimal.ONE), count(untilThe28th));
    }

    @Test
    void statOfAMonthMatchesTheChartOfTheSameMonthAndComparesWithTheMonthBefore() {
        StatSpec october = service.buildStat(new StatRequest("Outubro", Metric.TOTAL_AMOUNT, OCTOBER));
        ChartSpec byMonth = service.buildChart(new ChartRequest(ChartType.BAR, "Teste", Metric.TOTAL_AMOUNT, GroupBy.MONTH));

        // Meses do grafico: 08, 09, 10 e 11 de 2026.
        assertEquals(List.of("08/2026", "09/2026", "10/2026", "11/2026"), byMonth.labels());
        assertEquals(byMonth.series().getFirst().data().get(2), october.value());
        assertEquals("01/09/2026 a 30/09/2026", october.comparison().period());
        assertEquals(byMonth.series().getFirst().data().get(1), october.comparison().value());
        assertNotNull(october.comparison().changePercent());
    }

    @Test
    void funnelCountsStartedApprovedAndCompleted() {
        ChartSpec spec = service.buildChart(new ChartRequest(ChartType.FUNNEL, "Funil", null, null));

        assertEquals(List.of(new BigDecimal("45"), new BigDecimal("40"), new BigDecimal("37")),
                spec.series().getFirst().data());
    }

    @Test
    void tableListsTheLargestTransactions() {
        TableSpec spec = service.buildTable(new TableRequest("Maiores", TableOrder.HIGHEST_AMOUNT, 3, null));

        assertEquals(45, spec.totalRows());
        assertEquals(List.of(new BigDecimal("4925.14"), new BigDecimal("4827.52"), new BigDecimal("4816.24")),
                spec.rows().stream().map(TableSpec.Row::amount).toList());
        assertEquals("Ana Souza", spec.rows().getFirst().user());
        assertEquals("15/10/2026 20:34", spec.rows().getFirst().date());
    }

    @Test
    void filtersByStatus() {
        TransactionFilter refused = new TransactionFilter(null, null, null, TransactionStatus.RECUSADA, null, null);

        assertEquals(new BigDecimal("5"), count(refused));
    }

    private BigDecimal count(TransactionFilter filter) {
        return service.buildStat(new StatRequest("Teste", Metric.COUNT, filter)).value();
    }

    private static BigDecimal sum(ChartSpec spec) {
        return spec.series().getFirst().data().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
