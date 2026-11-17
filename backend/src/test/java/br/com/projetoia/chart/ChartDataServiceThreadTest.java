package br.com.projetoia.chart;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class ChartDataServiceThreadTest {

    @Inject
    ChartDataService service;

    /**
     * Em producao a tool roda numa thread do cliente HTTP do modelo, que nao carrega o
     * class loader da aplicacao. A consulta nao pode depender dele para achar classes.
     */
    @Test
    void worksOnAThreadThatDoesNotHaveTheApplicationClassLoader() throws InterruptedException {
        ChartRequest request = new ChartRequest(ChartType.BAR, "Teste", Metric.COUNT, GroupBy.TYPE);
        AtomicReference<ChartSpec> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread foreign = new Thread(() -> {
            try {
                result.set(service.buildChart(request));
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        foreign.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        foreign.start();
        foreign.join();

        assertNull(failure.get());
        assertEquals(3, result.get().labels().size());
    }
}
