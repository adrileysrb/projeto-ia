package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionFilterTest {

    @Test
    void readsDatesTheWayModelsSendThem() {
        TransactionFilter filter = TransactionFilter.of(null, "  ", null, null, "2026-10-01", "2026-10-31T23:59:59");

        assertEquals(LocalDate.of(2026, 10, 1), filter.startDate());
        assertEquals(LocalDate.of(2026, 10, 31), filter.endDate());
        assertNull(filter.userName());
    }

    @Test
    void blankDatesMeanNoPeriod() {
        TransactionFilter filter = TransactionFilter.of(null, null, null, null, "", null);

        assertNull(filter.startDate());
        assertTrue(filter.previousPeriod().isEmpty());
        assertTrue(filter.describe().isEmpty());
    }

    @Test
    void invalidDateIsRejectedWithTheExpectedFormat() {
        ChartRequestException error = assertThrows(ChartRequestException.class,
                () -> TransactionFilter.of(null, null, null, null, "outubro", null));

        assertTrue(error.getMessage().contains("AAAA-MM-DD"), error.getMessage());
    }

    @Test
    void startAfterEndIsRejected() {
        assertThrows(ChartRequestException.class,
                () -> TransactionFilter.of(null, null, null, null, "2026-11-01", "2026-10-01"));
    }

    @Test
    void aWholeMonthIsComparedWithTheWholePreviousMonth() {
        TransactionFilter october = period("2026-10-01", "2026-10-31");

        TransactionFilter previous = october.previousPeriod().orElseThrow();

        assertEquals(LocalDate.of(2026, 9, 1), previous.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), previous.endDate());
    }

    @Test
    void severalWholeMonthsAreComparedWithTheSameNumberOfMonthsBefore() {
        TransactionFilter previous = period("2026-09-01", "2026-10-31").previousPeriod().orElseThrow();

        assertEquals(LocalDate.of(2026, 7, 1), previous.startDate());
        assertEquals(LocalDate.of(2026, 8, 31), previous.endDate());
    }

    @Test
    void anyOtherPeriodIsComparedWithTheSameNumberOfDaysBefore() {
        TransactionFilter previous = period("2026-11-10", "2026-11-16").previousPeriod().orElseThrow();

        assertEquals(LocalDate.of(2026, 11, 3), previous.startDate());
        assertEquals(LocalDate.of(2026, 11, 9), previous.endDate());
    }

    @Test
    void previousPeriodKeepsTheOtherFilters() {
        TransactionFilter filter = TransactionFilter.of(TransactionType.PIX, "ana", null, null, "2026-10-01", "2026-10-31");

        TransactionFilter previous = filter.previousPeriod().orElseThrow();

        assertEquals(TransactionType.PIX, previous.type());
        assertEquals("ana", previous.userName());
    }

    @Test
    void describesEveryFilterThatApplies() {
        TransactionFilter filter = TransactionFilter.of(TransactionType.CARTAO, "bruno",
                TransactionCategory.SAUDE, TransactionStatus.CONCLUIDA, "2026-10-01", null);

        assertEquals(List.of("Cartão", "Usuário: bruno", "Saúde", "Situação: Concluída", "a partir de 01/10/2026"),
                filter.describe());
    }

    private static TransactionFilter period(String start, String end) {
        return TransactionFilter.of(null, null, null, null, start, end);
    }
}
