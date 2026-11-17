package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recorte das transacoes, comum a graficos, numeros e tabelas. Todo campo e opcional;
 * as datas sao inclusivas.
 */
public record TransactionFilter(
        TransactionType type,
        String userName,
        TransactionCategory category,
        TransactionStatus status,
        LocalDate startDate,
        LocalDate endDate) {

    public static final TransactionFilter NONE = new TransactionFilter(null, null, null, null, null, null);

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public TransactionFilter {
        userName = userName == null || userName.isBlank() ? null : userName.strip();
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ChartRequestException("startDate não pode ser depois de endDate.");
        }
    }

    /** As datas chegam do modelo como texto, no formato AAAA-MM-DD. */
    public static TransactionFilter of(TransactionType type, String userName, TransactionCategory category,
                                       TransactionStatus status, String startDate, String endDate) {
        return new TransactionFilter(type, userName, category, status,
                parseDate("startDate", startDate), parseDate("endDate", endDate));
    }

    private static LocalDate parseDate(String field, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        // Alguns modelos mandam data e hora; so a data interessa.
        String datePart = value.length() > 10 ? value.substring(0, 10) : value;
        try {
            return LocalDate.parse(datePart);
        } catch (DateTimeParseException e) {
            throw new ChartRequestException(
                    field + " inválida: '" + value + "'. Use o formato AAAA-MM-DD, por exemplo 2026-10-01.");
        }
    }

    public TransactionFilter withPeriod(LocalDate start, LocalDate end) {
        return new TransactionFilter(type, userName, category, status, start, end);
    }

    /**
     * O periodo imediatamente anterior, do mesmo tamanho, para comparacao. Meses inteiros sao
     * comparados com meses inteiros (outubro com setembro), e nao com os 31 dias anteriores.
     * Vazio quando o filtro nao tem inicio e fim.
     */
    public Optional<TransactionFilter> previousPeriod() {
        if (startDate == null || endDate == null) {
            return Optional.empty();
        }
        boolean wholeMonths = startDate.getDayOfMonth() == 1
                && endDate.equals(endDate.with(TemporalAdjusters.lastDayOfMonth()));
        LocalDate previousEnd = startDate.minusDays(1);
        LocalDate previousStart;
        if (wholeMonths) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(endDate)) + 1;
            previousStart = startDate.minusMonths(months);
        } else {
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            previousStart = startDate.minusDays(days);
        }
        return Optional.of(withPeriod(previousStart, previousEnd));
    }

    /** O periodo por extenso, ou vazio quando o filtro nao tem datas. */
    public Optional<String> periodLabel() {
        if (startDate != null && endDate != null) {
            return Optional.of(startDate.format(DATE_LABEL) + " a " + endDate.format(DATE_LABEL));
        }
        if (startDate != null) {
            return Optional.of("a partir de " + startDate.format(DATE_LABEL));
        }
        if (endDate != null) {
            return Optional.of("até " + endDate.format(DATE_LABEL));
        }
        return Optional.empty();
    }

    /** Os filtros aplicados, em texto, para o subtitulo mostrar de que recorte os numeros sairam. */
    public List<String> describe() {
        List<String> parts = new ArrayList<>();
        if (type != null) {
            parts.add(type.label());
        }
        if (userName != null) {
            parts.add("Usuário: " + userName);
        }
        if (category != null) {
            parts.add(category.label());
        }
        if (status != null) {
            parts.add("Situação: " + status.label());
        }
        periodLabel().ifPresent(parts::add);
        return parts;
    }
}
