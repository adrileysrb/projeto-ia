package br.com.projetoia.ai;

import br.com.projetoia.chart.ChartDataService;
import br.com.projetoia.chart.ChartRequest;
import br.com.projetoia.chart.ChartRequestException;
import br.com.projetoia.chart.ChartSpec;
import br.com.projetoia.chart.ChartType;
import br.com.projetoia.chart.GroupBy;
import br.com.projetoia.chart.Metric;
import br.com.projetoia.chart.StatRequest;
import br.com.projetoia.chart.StatSpec;
import br.com.projetoia.chart.TableRequest;
import br.com.projetoia.chart.TableSpec;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChartToolsTest {

    @Test
    void requestProblemsAreReportedToTheModel() {
        ChartTools tools = toolsFailingWith(new ChartRequestException("Nenhuma transação encontrada para esse filtro."));

        for (Runnable call : callsTo(tools)) {
            RuntimeException error = assertThrows(RuntimeException.class, call::run);

            ToolErrorVisibleToLlm visible = assertInstanceOf(ToolErrorVisibleToLlm.class, error);
            assertEquals("Nenhuma transação encontrada para esse filtro.", visible.messageForLlm());
        }
    }

    /** Um erro interno (SQL, por exemplo) nao pode virar texto para o modelo nem para a tela. */
    @Test
    void internalErrorsAreNotExposedToTheModel() {
        IllegalArgumentException internal = new IllegalArgumentException("org.hibernate.query.SemanticException: select ...");
        ChartTools tools = toolsFailingWith(internal);

        for (Runnable call : callsTo(tools)) {
            RuntimeException error = assertThrows(RuntimeException.class, call::run);

            assertFalse(error instanceof ToolErrorVisibleToLlm);
            assertEquals(internal, error);
        }
    }

    /** Uma data que o modelo escreveu errado volta para ele como texto, antes de chegar ao banco. */
    @Test
    void invalidDateIsReportedToTheModel() {
        ChartTools tools = new ChartTools(new ChartDataService());

        RuntimeException error = assertThrows(RuntimeException.class, () -> tools.showNumber(
                "Total", Metric.TOTAL_AMOUNT, null, null, null, null, "mês passado", null));

        assertInstanceOf(ToolErrorVisibleToLlm.class, error);
    }

    @Test
    void datesFromTheModelReachTheFilter() {
        AtomicReference<StatRequest> received = new AtomicReference<>();
        ChartTools tools = new ChartTools(new ChartDataService() {
            @Override
            public StatSpec buildStat(StatRequest request) {
                received.set(request);
                return null;
            }
        });

        tools.showNumber("Total", Metric.COUNT, null, null, null, null, "2026-10-01", "2026-10-31");

        assertEquals(LocalDate.of(2026, 10, 1), received.get().filter().startDate());
        assertEquals(LocalDate.of(2026, 10, 31), received.get().filter().endDate());
    }

    private static Iterable<Runnable> callsTo(ChartTools tools) {
        return List.of(
                () -> tools.renderChart(ChartType.BAR, "Teste", Metric.COUNT, GroupBy.TYPE,
                        null, null, null, null, null, null, null, null),
                () -> tools.showNumber("Teste", Metric.COUNT, null, null, null, null, null, null),
                () -> tools.showTable("Teste", null, null, null, null, null, null, null, null));
    }

    private static ChartTools toolsFailingWith(RuntimeException error) {
        return new ChartTools(new ChartDataService() {
            @Override
            public ChartSpec buildChart(ChartRequest request) {
                throw error;
            }

            @Override
            public StatSpec buildStat(StatRequest request) {
                throw error;
            }

            @Override
            public TableSpec buildTable(TableRequest request) {
                throw error;
            }
        });
    }
}
