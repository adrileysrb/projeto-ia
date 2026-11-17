package br.com.projetoia.chart;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lista de transacoes pronta para mostrar.
 *
 * @param kind      sempre {@link #KIND}; diz a tela que este resultado e uma tabela
 * @param subtitle  a ordem das linhas e quais filtros foram aplicados
 * @param totalRows quantas transacoes atendem ao filtro, que pode ser mais do que as linhas mostradas
 */
public record TableSpec(String kind, String title, String subtitle, List<Row> rows, int totalRows) {

    public static final String KIND = "table";

    public TableSpec(String title, String subtitle, List<Row> rows, int totalRows) {
        this(KIND, title, subtitle, rows, totalRows);
    }

    /** Uma transacao, com os textos ja no formato de exibicao. */
    public record Row(String date, String user, String type, String category, String status, BigDecimal amount) {
    }
}
