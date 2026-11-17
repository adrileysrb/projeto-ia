package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Transforma transacoes em um {@link ChartSpec}: agrupa, calcula a metrica e ordena.
 * Funcao pura (sem banco nem framework), para ser facil de testar e de reaproveitar.
 */
public final class ChartBuilder {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final int MAX_HISTOGRAM_BINS = 10;

    /** Tipos em que as partes somam um total, e por isso so aceitam metricas aditivas. */
    private static final Set<ChartType> PARTS_OF_A_TOTAL = EnumSet.of(
            ChartType.PIE, ChartType.STACKED_BAR, ChartType.TREEMAP, ChartType.CUMULATIVE_LINE, ChartType.FUNNEL);
    /** Tipos com uma serie so, em que dividir por outra dimensao nao se aplica. */
    private static final Set<ChartType> SINGLE_SERIES = EnumSet.of(
            ChartType.PIE, ChartType.HORIZONTAL_BAR, ChartType.TREEMAP);

    private ChartBuilder() {
    }

    /**
     * @param allUserNames todos os usuarios do banco em ordem alfabetica; define a cor estavel de cada um
     */
    public static ChartSpec build(ChartRequest request, List<TransactionRow> rows, List<String> allUserNames) {
        validate(request);
        if (rows.isEmpty()) {
            throw new ChartRequestException("Nenhuma transação encontrada para esse filtro.");
        }
        return switch (request.chartType()) {
            case HISTOGRAM -> histogram(request, rows);
            case FUNNEL -> funnel(request, rows);
            default -> grouped(request, rows, allUserNames);
        };
    }

    /** Recusa combinacoes que nao formam um grafico legivel, com uma mensagem que o modelo consegue seguir. */
    private static void validate(ChartRequest request) {
        ChartType type = request.chartType();
        GroupBy groupBy = request.groupBy();
        if (type == ChartType.HISTOGRAM) {
            return;
        }
        if (PARTS_OF_A_TOTAL.contains(type) && !request.metric().isAdditive()) {
            throw new ChartRequestException(type + " mostra partes que somam um total e só aceita metric "
                    + "TOTAL_AMOUNT ou COUNT. Para AVERAGE_AMOUNT, MAX_AMOUNT ou MIN_AMOUNT use BAR, "
                    + "HORIZONTAL_BAR ou LINE.");
        }
        if (type == ChartType.FUNNEL) {
            if (request.filter().status() != null) {
                throw new ChartRequestException(
                        "FUNNEL já separa as transações por situação. Chame de novo sem o filtro status.");
            }
            return;
        }
        if (groupBy == null) {
            throw new ChartRequestException("groupBy é obrigatório para " + type
                    + ": TYPE, USER, CATEGORY, STATUS, DAY, WEEK ou MONTH.");
        }
        switch (type) {
            case PIE -> {
                if (groupBy == GroupBy.DAY || groupBy == GroupBy.WEEK) {
                    throw new ChartRequestException("Gráfico de pizza aceita groupBy TYPE, USER, CATEGORY, STATUS "
                            + "ou MONTH. Para DAY ou WEEK use LINE ou BAR.");
                }
            }
            case HORIZONTAL_BAR, TREEMAP -> {
                if (groupBy.isTime()) {
                    throw new ChartRequestException(type + " aceita groupBy TYPE, USER, CATEGORY ou STATUS. "
                            + "Para DAY, WEEK ou MONTH use BAR ou LINE.");
                }
            }
            case CUMULATIVE_LINE -> {
                if (!groupBy.isTime()) {
                    throw new ChartRequestException("CUMULATIVE_LINE precisa de groupBy DAY, WEEK ou MONTH.");
                }
            }
            case STACKED_BAR -> {
                if (effectiveSplit(request) == null) {
                    throw new ChartRequestException("STACKED_BAR precisa de splitBy (TYPE, USER, CATEGORY ou "
                            + "STATUS) diferente de groupBy. Sem divisão, use BAR.");
                }
            }
            default -> {
            }
        }
    }

    private static ChartSpec grouped(ChartRequest request, List<TransactionRow> rows, List<String> allUserNames) {
        GroupBy groupBy = request.groupBy();
        Metric metric = request.metric();

        List<Bucket> labels = groupBy.isTime()
                ? timeBuckets(groupBy, rows)
                : categoryBuckets(Dimension.of(groupBy), rows, allUserNames);
        if (!groupBy.isTime()) {
            rejectSingleGroup(Dimension.of(groupBy), labels, request.filter());
        }

        Dimension split = effectiveSplit(request);
        List<Bucket> seriesBuckets = split == null
                ? List.of(new Bucket("", metric.label(), 0))
                : categoryBuckets(split, rows, allUserNames);

        // (serie, rotulo) -> transacoes daquele ponto
        Map<String, Map<String, Aggregate>> cells = new HashMap<>();
        for (TransactionRow row : rows) {
            String seriesKey = split == null ? "" : split.keyOf(row);
            cells.computeIfAbsent(seriesKey, key -> new HashMap<>())
                    .computeIfAbsent(labelKeyOf(groupBy, row), key -> new Aggregate())
                    .add(row.amount());
        }

        List<ChartSpec.Series> series = new ArrayList<>();
        for (Bucket seriesBucket : seriesBuckets) {
            Map<String, Aggregate> byLabel = cells.getOrDefault(seriesBucket.key(), Map.of());
            List<BigDecimal> data = labels.stream()
                    .map(label -> byLabel.getOrDefault(label.key(), new Aggregate()).value(metric))
                    .toList();
            if (request.chartType() == ChartType.CUMULATIVE_LINE) {
                data = runningTotal(data);
            }
            series.add(new ChartSpec.Series(seriesBucket.label(), seriesBucket.slot(), data));
        }

        String detail = null;
        if (request.chartType() == ChartType.HORIZONTAL_BAR) {
            int groups = labels.size();
            List<Integer> ranking = ranking(series.getFirst().data(), request.limit());
            labels = ranking.stream().map(labels::get).toList();
            series = List.of(reorder(series.getFirst(), ranking));
            detail = ranking.size() < groups ? ranking.size() + " maiores de " + groups : null;
        }

        return new ChartSpec(
                request.chartType(),
                request.title(),
                subtitle(request, detail),
                metric,
                labels.stream().map(Bucket::label).toList(),
                labels.stream().map(Bucket::slot).toList(),
                series);
    }

    /**
     * Agrupar por uma dimensao e filtrar por um unico valor dela deixa o grafico com um grupo so.
     * Modelos pequenos fazem isso quando leem "por categoria" e preenchem o filtro alem do
     * agrupamento; a recusa explica o engano para o modelo chamar de novo sem o filtro.
     */
    private static void rejectSingleGroup(Dimension dimension, List<Bucket> groups, TransactionFilter filter) {
        if (groups.size() == 1 && dimension.isFilteredBy(filter)) {
            throw new ChartRequestException("O pedido agrupa por " + dimension.name() + " e também filtra por "
                    + dimension.word + ", então o gráfico teria um grupo só (" + groups.getFirst().label()
                    + "). Para comparar todos os grupos, chame de novo sem o filtro de " + dimension.word + ".");
        }
    }

    /** Quantas transacoes caem em cada faixa de valor. As faixas tem largura "redonda" (100, 250, 500...). */
    private static ChartSpec histogram(ChartRequest request, List<TransactionRow> rows) {
        List<BigDecimal> amounts = rows.stream().map(TransactionRow::amount).toList();
        BigDecimal min = amounts.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = amounts.stream().max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal step = binStep(min, max);
        BigDecimal low = floorTo(min, step);

        long[] counts = new long[binIndex(max, low, step) + 1];
        amounts.forEach(amount -> counts[binIndex(amount, low, step)]++);

        DecimalFormat number = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(PT_BR));
        List<String> labels = IntStream.range(0, counts.length)
                .mapToObj(bin -> {
                    BigDecimal from = low.add(step.multiply(BigDecimal.valueOf(bin)));
                    return number.format(from) + "–" + number.format(from.add(step));
                })
                .toList();
        List<BigDecimal> data = IntStream.range(0, counts.length)
                .mapToObj(bin -> BigDecimal.valueOf(counts[bin]))
                .toList();

        return new ChartSpec(
                ChartType.HISTOGRAM,
                request.title(),
                subtitle(request, null),
                Metric.COUNT,
                labels,
                IntStream.range(0, counts.length).boxed().toList(),
                List.of(new ChartSpec.Series(Metric.COUNT.label(), 0, data)));
    }

    /**
     * Tres etapas, cada uma contida na anterior: toda transacao e iniciada; as que nao foram
     * recusadas foram aprovadas; das aprovadas, as que nao foram estornadas estao concluidas.
     */
    private static ChartSpec funnel(ChartRequest request, List<TransactionRow> rows) {
        List<TransactionRow> approved = rows.stream()
                .filter(row -> row.status() != TransactionStatus.RECUSADA)
                .toList();
        List<TransactionRow> completed = rows.stream()
                .filter(row -> row.status() == TransactionStatus.CONCLUIDA)
                .toList();
        List<BigDecimal> data = Stream.of(rows, approved, completed)
                .map(stage -> Aggregate.of(stage).value(request.metric()))
                .toList();

        return new ChartSpec(
                ChartType.FUNNEL,
                request.title(),
                subtitle(request, null),
                request.metric(),
                List.of("Iniciadas", "Aprovadas", "Concluídas"),
                List.of(0, 1, 2),
                List.of(new ChartSpec.Series(request.metric().label(), 0, data)));
    }

    /** O que os numeros representam, seguido dos filtros aplicados. */
    private static String subtitle(ChartRequest request, String detail) {
        List<String> parts = new ArrayList<>();
        parts.add(switch (request.chartType()) {
            case HISTOGRAM -> "Quantidade de transações por faixa de valor, em reais";
            case CUMULATIVE_LINE -> request.metric().description() + ", acumulado";
            case FUNNEL -> request.metric().description() + " em cada etapa";
            default -> request.metric().description();
        });
        if (detail != null) {
            parts.add(detail);
        }
        parts.addAll(request.filter().describe());
        return String.join(" · ", parts);
    }

    /** Dividir pela mesma dimensao do agrupamento nao acrescenta nada. */
    private static Dimension effectiveSplit(ChartRequest request) {
        if (request.splitBy() == null || SINGLE_SERIES.contains(request.chartType())) {
            return null;
        }
        Dimension split = Dimension.of(request.splitBy());
        boolean sameAsGroup = !request.groupBy().isTime() && Dimension.of(request.groupBy()) == split;
        return sameAsGroup ? null : split;
    }

    private static List<BigDecimal> runningTotal(List<BigDecimal> data) {
        List<BigDecimal> totals = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : data) {
            total = total.add(value);
            totals.add(total);
        }
        return totals;
    }

    /** Posicoes dos rotulos do maior valor para o menor, cortadas no limite pedido. */
    private static List<Integer> ranking(List<BigDecimal> values, Integer limit) {
        return IntStream.range(0, values.size()).boxed()
                .sorted(Comparator.comparing((Integer index) -> values.get(index)).reversed())
                .limit(limit == null ? values.size() : limit)
                .toList();
    }

    private static ChartSpec.Series reorder(ChartSpec.Series series, List<Integer> order) {
        return new ChartSpec.Series(series.name(), series.slot(), order.stream().map(series.data()::get).toList());
    }

    private static List<Bucket> categoryBuckets(Dimension dimension, List<TransactionRow> rows, List<String> allUserNames) {
        return rows.stream()
                .map(dimension::keyOf)
                .distinct()
                .map(key -> dimension.bucket(key, allUserNames))
                .sorted(Comparator.comparingInt(Bucket::slot).thenComparing(Bucket::label))
                .toList();
    }

    /** Periodos continuos do primeiro ao ultimo, incluindo os que nao tiveram transacao. */
    private static List<Bucket> timeBuckets(GroupBy groupBy, List<TransactionRow> rows) {
        TreeMap<LocalDate, Boolean> present = new TreeMap<>();
        rows.forEach(row -> present.put(bucketStart(groupBy, row), Boolean.TRUE));

        List<Bucket> buckets = new ArrayList<>();
        LocalDate last = present.lastKey();
        for (LocalDate start = present.firstKey(); !start.isAfter(last); start = next(groupBy, start)) {
            String label = groupBy == GroupBy.MONTH ? start.format(MONTH_LABEL) : start.format(DAY_LABEL);
            buckets.add(new Bucket(start.toString(), label, buckets.size()));
        }
        return buckets;
    }

    private static String labelKeyOf(GroupBy groupBy, TransactionRow row) {
        return groupBy.isTime() ? bucketStart(groupBy, row).toString() : Dimension.of(groupBy).keyOf(row);
    }

    private static LocalDate bucketStart(GroupBy groupBy, TransactionRow row) {
        LocalDate date = row.createdAt().toLocalDate();
        return switch (groupBy) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private static LocalDate next(GroupBy groupBy, LocalDate start) {
        return switch (groupBy) {
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
    }

    /** A menor largura "redonda" (1, 2, 2,5 ou 5 vezes uma potencia de dez) que cabe no limite de faixas. */
    private static BigDecimal binStep(BigDecimal min, BigDecimal max) {
        for (int exponent = -2; exponent <= 12; exponent++) {
            for (String multiple : List.of("1", "2", "2.5", "5")) {
                BigDecimal step = new BigDecimal(multiple).scaleByPowerOfTen(exponent);
                if (binIndex(max, floorTo(min, step), step) < MAX_HISTOGRAM_BINS) {
                    return step;
                }
            }
        }
        throw new IllegalStateException("Valores fora da faixa suportada pelo histograma: " + min + " a " + max);
    }

    private static BigDecimal floorTo(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.FLOOR).multiply(step);
    }

    private static int binIndex(BigDecimal amount, BigDecimal low, BigDecimal step) {
        return amount.subtract(low).divide(step, 0, RoundingMode.FLOOR).intValueExact();
    }

    /** Um rotulo ou uma serie: chave de agrupamento, texto exibido e posicao de cor estavel. */
    private record Bucket(String key, String label, int slot) {
    }

    private enum Dimension {
        TYPE("tipo") {
            @Override
            boolean isFilteredBy(TransactionFilter filter) {
                return filter.type() != null;
            }

            @Override
            String keyOf(TransactionRow row) {
                return row.type().name();
            }

            @Override
            Bucket bucket(String key, List<String> allUserNames) {
                TransactionType type = TransactionType.valueOf(key);
                return new Bucket(key, type.label(), type.ordinal());
            }
        },
        USER("usuário") {
            @Override
            boolean isFilteredBy(TransactionFilter filter) {
                return filter.userName() != null;
            }

            @Override
            String keyOf(TransactionRow row) {
                return row.userName();
            }

            @Override
            Bucket bucket(String key, List<String> allUserNames) {
                int index = allUserNames.indexOf(key);
                return new Bucket(key, key, index < 0 ? allUserNames.size() : index);
            }
        },
        CATEGORY("categoria") {
            @Override
            boolean isFilteredBy(TransactionFilter filter) {
                return filter.category() != null;
            }

            @Override
            String keyOf(TransactionRow row) {
                return row.category().name();
            }

            @Override
            Bucket bucket(String key, List<String> allUserNames) {
                TransactionCategory category = TransactionCategory.valueOf(key);
                return new Bucket(key, category.label(), category.ordinal());
            }
        },
        STATUS("situação") {
            @Override
            boolean isFilteredBy(TransactionFilter filter) {
                return filter.status() != null;
            }

            @Override
            String keyOf(TransactionRow row) {
                return row.status().name();
            }

            @Override
            Bucket bucket(String key, List<String> allUserNames) {
                TransactionStatus status = TransactionStatus.valueOf(key);
                return new Bucket(key, status.label(), status.ordinal());
            }
        };

        /** Como a dimensao e chamada nas mensagens para o modelo. */
        private final String word;

        Dimension(String word) {
            this.word = word;
        }

        /** O filtro restringe esta dimensao a um valor? */
        abstract boolean isFilteredBy(TransactionFilter filter);

        abstract String keyOf(TransactionRow row);

        abstract Bucket bucket(String key, List<String> allUserNames);

        static Dimension of(GroupBy groupBy) {
            return valueOf(groupBy.name());
        }

        static Dimension of(SplitBy splitBy) {
            return valueOf(splitBy.name());
        }
    }
}
