package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static br.com.projetoia.domain.TransactionCategory.ALIMENTACAO;
import static br.com.projetoia.domain.TransactionCategory.LAZER;
import static br.com.projetoia.domain.TransactionCategory.MORADIA;
import static br.com.projetoia.domain.TransactionStatus.CONCLUIDA;
import static br.com.projetoia.domain.TransactionStatus.ESTORNADA;
import static br.com.projetoia.domain.TransactionStatus.RECUSADA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartBuilderTest {

    private static final List<String> USERS = List.of("Ana", "Bruno", "Carla");

    private static final List<TransactionRow> ROWS = List.of(
            row(TransactionType.PIX, "Ana", ALIMENTACAO, CONCLUIDA, "100.00", "2026-09-07T10:00:00"),    // segunda
            row(TransactionType.PIX, "Bruno", LAZER, RECUSADA, "50.50", "2026-09-09T10:00:00"),          // mesma semana
            row(TransactionType.BOLETO, "Ana", MORADIA, CONCLUIDA, "200.00", "2026-09-28T10:00:00"),     // tres semanas depois
            row(TransactionType.CARTAO, "Carla", ALIMENTACAO, ESTORNADA, "10.00", "2026-11-02T10:00:00"));

    @Test
    void pieByTypeSumsAmountsInEnumOrder() {
        ChartSpec spec = build(ChartType.PIE, Metric.TOTAL_AMOUNT, GroupBy.TYPE, null);

        assertEquals("chart", spec.kind());
        assertEquals(List.of("Pix", "Boleto", "Cartão"), spec.labels());
        assertEquals(List.of(0, 1, 2), spec.labelSlots());
        assertEquals(1, spec.series().size());
        assertEquals(decimals("150.50", "200.00", "10.00"), spec.series().getFirst().data());
    }

    @Test
    void barByUserCountsTransactions() {
        ChartSpec spec = build(ChartType.BAR, Metric.COUNT, GroupBy.USER, null);

        assertEquals(List.of("Ana", "Bruno", "Carla"), spec.labels());
        assertEquals(decimals("2", "1", "1"), spec.series().getFirst().data());
        assertEquals("Transações", spec.series().getFirst().name());
    }

    @Test
    void lineByWeekFillsWeeksWithoutTransactions() {
        ChartSpec spec = build(ChartType.LINE, Metric.TOTAL_AMOUNT, GroupBy.WEEK, null);

        // 07/09 ate a semana de 02/11: nove semanas seguidas, inclusive as vazias.
        assertEquals(9, spec.labels().size());
        assertEquals("07/09", spec.labels().getFirst());
        assertEquals("02/11", spec.labels().getLast());
        assertEquals(new BigDecimal("150.50"), spec.series().getFirst().data().get(0));
        assertEquals(new BigDecimal("0.00"), spec.series().getFirst().data().get(1));
        assertEquals(new BigDecimal("200.00"), spec.series().getFirst().data().get(3));
    }

    @Test
    void lineByMonthSplitByTypeCreatesOneSeriesPerType() {
        ChartSpec spec = build(ChartType.LINE, Metric.TOTAL_AMOUNT, GroupBy.MONTH, SplitBy.TYPE);

        assertEquals(List.of("09/2026", "10/2026", "11/2026"), spec.labels());
        assertEquals(List.of("Pix", "Boleto", "Cartão"), spec.series().stream().map(ChartSpec.Series::name).toList());
        assertEquals(decimals("150.50", "0.00", "0.00"), spec.series().get(0).data());
        assertEquals(decimals("0.00", "0.00", "10.00"), spec.series().get(2).data());
    }

    @Test
    void seriesKeepTheirColorSlotWhenAFilterRemovesOthers() {
        List<TransactionRow> onlyCarla = ROWS.stream().filter(row -> row.userName().equals("Carla")).toList();
        ChartRequest request = new ChartRequest(ChartType.BAR, "t", Metric.COUNT, GroupBy.MONTH, SplitBy.USER, null, null);

        ChartSpec spec = ChartBuilder.build(request, onlyCarla, USERS);

        assertEquals("Carla", spec.series().getFirst().name());
        assertEquals(2, spec.series().getFirst().slot());
    }

    @Test
    void pieIgnoresSplit() {
        ChartSpec spec = build(ChartType.PIE, Metric.COUNT, GroupBy.USER, SplitBy.TYPE);

        assertEquals(1, spec.series().size());
    }

    @Test
    void pieByWeekIsRejectedWithAMessageTheModelCanActOn() {
        ChartRequestException error = assertThrows(ChartRequestException.class,
                () -> build(ChartType.PIE, Metric.COUNT, GroupBy.WEEK, null));

        assertTrue(error.getMessage().contains("Para DAY ou WEEK use LINE ou BAR"));
    }

    @Test
    void emptyResultIsRejected() {
        ChartRequest request = new ChartRequest(ChartType.BAR, "t", Metric.COUNT, GroupBy.TYPE);

        assertThrows(ChartRequestException.class, () -> ChartBuilder.build(request, List.of(), USERS));
    }

    @Test
    void requestWithoutChartTypeIsRejected() {
        assertThrows(ChartRequestException.class, () -> new ChartRequest(null, "t", Metric.COUNT, GroupBy.TYPE));
    }

    @Test
    void groupByIsRequiredExceptForHistogramAndFunnel() {
        ChartRequestException error = assertThrows(ChartRequestException.class,
                () -> build(ChartType.BAR, Metric.COUNT, null, null));

        assertTrue(error.getMessage().contains("groupBy é obrigatório"));
    }

    @Test
    void groupsByCategoryAndByStatus() {
        ChartSpec byCategory = build(ChartType.BAR, Metric.TOTAL_AMOUNT, GroupBy.CATEGORY, null);
        ChartSpec byStatus = build(ChartType.PIE, Metric.COUNT, GroupBy.STATUS, null);

        assertEquals(List.of("Alimentação", "Moradia", "Lazer"), byCategory.labels());
        assertEquals(List.of(0, 2, 3), byCategory.labelSlots());
        assertEquals(decimals("110.00", "200.00", "50.50"), byCategory.series().getFirst().data());
        assertEquals(List.of("Concluída", "Recusada", "Estornada"), byStatus.labels());
        assertEquals(decimals("2", "1", "1"), byStatus.series().getFirst().data());
    }

    @Test
    void averageMaxAndMinAreCalculatedPerGroup() {
        assertEquals(decimals("75.25", "200.00", "10.00"),
                build(ChartType.BAR, Metric.AVERAGE_AMOUNT, GroupBy.TYPE, null).series().getFirst().data());
        assertEquals(decimals("100.00", "200.00", "10.00"),
                build(ChartType.BAR, Metric.MAX_AMOUNT, GroupBy.TYPE, null).series().getFirst().data());
        assertEquals(decimals("50.50", "200.00", "10.00"),
                build(ChartType.BAR, Metric.MIN_AMOUNT, GroupBy.TYPE, null).series().getFirst().data());
    }

    /** Num mes sem transacoes a soma e zero, mas a media nao existe: o ponto fica vazio. */
    @Test
    void averageHasNoValueWhereThereAreNoTransactions() {
        ChartSpec spec = build(ChartType.LINE, Metric.AVERAGE_AMOUNT, GroupBy.MONTH, null);

        assertEquals(Arrays.asList(new BigDecimal("116.83"), null, new BigDecimal("10.00")),
                spec.series().getFirst().data());
    }

    @Test
    void partsOfATotalRejectMetricsThatDoNotAddUp() {
        for (ChartType type : List.of(ChartType.PIE, ChartType.TREEMAP, ChartType.FUNNEL)) {
            ChartRequestException error = assertThrows(ChartRequestException.class,
                    () -> build(type, Metric.AVERAGE_AMOUNT, GroupBy.TYPE, null), type.name());
            assertTrue(error.getMessage().contains("TOTAL_AMOUNT ou COUNT"), error.getMessage());
        }
    }

    @Test
    void stackedBarHasOneSeriesPerPartAndNeedsASplit() {
        ChartSpec spec = build(ChartType.STACKED_BAR, Metric.TOTAL_AMOUNT, GroupBy.MONTH, SplitBy.TYPE);

        assertEquals(3, spec.series().size());
        assertEquals(decimals("150.50", "0.00", "0.00"), spec.series().get(0).data());

        ChartRequestException error = assertThrows(ChartRequestException.class,
                () -> build(ChartType.STACKED_BAR, Metric.TOTAL_AMOUNT, GroupBy.TYPE, SplitBy.TYPE));
        assertTrue(error.getMessage().contains("splitBy"));
    }

    @Test
    void horizontalBarRanksFromLargestToSmallestAndKeepsColorSlots() {
        ChartSpec spec = build(ChartType.HORIZONTAL_BAR, Metric.TOTAL_AMOUNT, GroupBy.USER, null);

        assertEquals(List.of("Ana", "Bruno", "Carla"), spec.labels());
        assertEquals(decimals("300.00", "50.50", "10.00"), spec.series().getFirst().data());

        ChartSpec byType = build(ChartType.HORIZONTAL_BAR, Metric.TOTAL_AMOUNT, GroupBy.TYPE, null);
        assertEquals(List.of("Boleto", "Pix", "Cartão"), byType.labels());
        assertEquals(List.of(1, 0, 2), byType.labelSlots());
    }

    @Test
    void horizontalBarKeepsOnlyTheLargestWhenLimited() {
        ChartRequest request = new ChartRequest(
                ChartType.HORIZONTAL_BAR, "t", Metric.TOTAL_AMOUNT, GroupBy.TYPE, null, 2, null);

        ChartSpec spec = ChartBuilder.build(request, ROWS, USERS);

        assertEquals(List.of("Boleto", "Pix"), spec.labels());
        assertEquals(decimals("200.00", "150.50"), spec.series().getFirst().data());
        assertTrue(spec.subtitle().contains("2 maiores de 3"), spec.subtitle());
    }

    @Test
    void horizontalBarAndTreemapRejectTimeGroups() {
        assertThrows(ChartRequestException.class, () -> build(ChartType.HORIZONTAL_BAR, Metric.COUNT, GroupBy.MONTH, null));
        assertThrows(ChartRequestException.class, () -> build(ChartType.TREEMAP, Metric.COUNT, GroupBy.WEEK, null));
    }

    @Test
    void cumulativeLineAddsUpOverTimeAndNeedsATimeGroup() {
        ChartSpec spec = build(ChartType.CUMULATIVE_LINE, Metric.TOTAL_AMOUNT, GroupBy.MONTH, null);

        assertEquals(decimals("350.50", "350.50", "360.50"), spec.series().getFirst().data());
        assertTrue(spec.subtitle().contains("acumulado"), spec.subtitle());
        assertThrows(ChartRequestException.class, () -> build(ChartType.CUMULATIVE_LINE, Metric.COUNT, GroupBy.TYPE, null));
    }

    @Test
    void histogramCountsTransactionsInRoundValueRanges() {
        ChartSpec spec = build(ChartType.HISTOGRAM, Metric.TOTAL_AMOUNT, null, null);

        // De 10,00 a 200,00: faixas de 20 dariam onze, entao a largura sobe para 25.
        assertEquals(Metric.COUNT, spec.metric());
        assertEquals("0–25", spec.labels().getFirst());
        assertEquals("200–225", spec.labels().getLast());
        assertEquals(decimals("1", "0", "1", "0", "1", "0", "0", "0", "1"), spec.series().getFirst().data());
    }

    @Test
    void histogramUsesThousandsSeparatorAndNeverMoreThanTenRanges() {
        List<TransactionRow> rows = List.of(
                row(TransactionType.PIX, "Ana", ALIMENTACAO, CONCLUIDA, "27.76", "2026-09-07T10:00:00"),
                row(TransactionType.PIX, "Ana", ALIMENTACAO, CONCLUIDA, "1200.00", "2026-09-07T10:00:00"),
                row(TransactionType.PIX, "Ana", ALIMENTACAO, CONCLUIDA, "4925.14", "2026-09-07T10:00:00"));

        ChartSpec spec = ChartBuilder.build(new ChartRequest(ChartType.HISTOGRAM, "t", null, null), rows, USERS);

        assertEquals(10, spec.labels().size());
        assertEquals("0–500", spec.labels().getFirst());
        assertEquals("1.000–1.500", spec.labels().get(2));
        assertEquals("4.500–5.000", spec.labels().getLast());
        assertEquals(decimals("1", "0", "1", "0", "0", "0", "0", "0", "0", "1"), spec.series().getFirst().data());
    }

    @Test
    void funnelNarrowsFromStartedToApprovedToCompleted() {
        ChartSpec count = ChartBuilder.build(new ChartRequest(ChartType.FUNNEL, "t", null, null), ROWS, USERS);
        ChartSpec amount = build(ChartType.FUNNEL, Metric.TOTAL_AMOUNT, null, null);

        assertEquals(List.of("Iniciadas", "Aprovadas", "Concluídas"), count.labels());
        assertEquals(Metric.COUNT, count.metric());
        assertEquals(decimals("4", "3", "2"), count.series().getFirst().data());
        assertEquals(decimals("360.50", "310.00", "300.00"), amount.series().getFirst().data());
    }

    @Test
    void funnelRejectsAStatusFilter() {
        TransactionFilter onlyRefused = new TransactionFilter(null, null, null, RECUSADA, null, null);
        ChartRequest request = new ChartRequest(ChartType.FUNNEL, "t", Metric.COUNT, null, null, null, onlyRefused);

        assertThrows(ChartRequestException.class, () -> ChartBuilder.build(request, ROWS, USERS));
    }

    /** Engano tipico de modelo pequeno: "pizza por categoria" com o filtro de categoria preenchido. */
    @Test
    void groupingByADimensionThatIsFilteredToOneValueIsRejected() {
        TransactionFilter onlyFood = new TransactionFilter(null, null, ALIMENTACAO, null, null, null);
        List<TransactionRow> food = ROWS.stream().filter(row -> row.category() == ALIMENTACAO).toList();
        ChartRequest request = new ChartRequest(ChartType.PIE, "t", Metric.TOTAL_AMOUNT, GroupBy.CATEGORY, null, null, onlyFood);

        ChartRequestException error = assertThrows(ChartRequestException.class,
                () -> ChartBuilder.build(request, food, USERS));

        assertTrue(error.getMessage().contains("sem o filtro de categoria"), error.getMessage());
    }

    /** O filtro de usuario e por parte do nome: quando pega varios usuarios, o grafico continua valido. */
    @Test
    void groupingByUserWithANameFilterThatMatchesSeveralUsersIsAccepted() {
        TransactionFilter nameWithA = new TransactionFilter(null, "a", null, null, null, null);
        ChartRequest request = new ChartRequest(ChartType.BAR, "t", Metric.COUNT, GroupBy.USER, null, null, nameWithA);

        assertEquals(3, ChartBuilder.build(request, ROWS, USERS).labels().size());
    }

    @Test
    void subtitleSaysWhatIsMeasuredAndWhichFiltersApply() {
        TransactionFilter filter = TransactionFilter.of(TransactionType.PIX, null, null, null, "2026-10-01", "2026-10-31");
        ChartRequest request = new ChartRequest(ChartType.BAR, "t", Metric.TOTAL_AMOUNT, GroupBy.USER, null, null, filter);

        ChartSpec spec = ChartBuilder.build(request, ROWS, USERS);

        assertEquals("Valor total em reais · Pix · 01/10/2026 a 31/10/2026", spec.subtitle());
    }

    private static ChartSpec build(ChartType chartType, Metric metric, GroupBy groupBy, SplitBy splitBy) {
        return ChartBuilder.build(new ChartRequest(chartType, "Teste", metric, groupBy, splitBy, null, null), ROWS, USERS);
    }

    private static TransactionRow row(TransactionType type, String user, TransactionCategory category,
                                      TransactionStatus status, String amount, String createdAt) {
        return new TransactionRow(type, user, category, status, new BigDecimal(amount), LocalDateTime.parse(createdAt));
    }

    private static List<BigDecimal> decimals(String... values) {
        return List.of(values).stream().map(BigDecimal::new).toList();
    }
}
