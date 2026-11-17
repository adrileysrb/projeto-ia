package br.com.projetoia.chart;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Ordena as transacoes, corta no limite e formata as linhas da tabela. */
public final class TableBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TableBuilder() {
    }

    public static TableSpec build(TableRequest request, List<TransactionRow> rows) {
        if (rows.isEmpty()) {
            throw new ChartRequestException("Nenhuma transação encontrada para esse filtro.");
        }

        List<TableSpec.Row> shown = rows.stream()
                .sorted(request.orderBy().comparator())
                .limit(request.limit())
                .map(row -> new TableSpec.Row(
                        row.createdAt().format(DATE_TIME),
                        row.userName(),
                        row.type().label(),
                        row.category().label(),
                        row.status().label(),
                        row.amount()))
                .toList();

        List<String> parts = new ArrayList<>();
        parts.add(request.orderBy().description());
        parts.addAll(request.filter().describe());

        return new TableSpec(request.title(), String.join(" · ", parts), shown, rows.size());
    }
}
