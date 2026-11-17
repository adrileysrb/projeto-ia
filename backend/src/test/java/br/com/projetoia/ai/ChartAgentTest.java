package br.com.projetoia.ai;

import br.com.projetoia.chart.ChartDataService;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Erros do modelo: o que chega ao cliente AG-UI no RUN_ERROR. */
class ChartAgentTest {

    @Test
    void unreachableModelIsReportedInWordsTheUserCanActOn() throws InterruptedException {
        RunErrorEvent error = runFailingWith(new RuntimeException(new ConnectException()));

        assertTrue(error.message().contains("conectar ao modelo"), error.message());
    }

    @Test
    void internalDetailsAreNotSentToTheClient() throws InterruptedException {
        RunErrorEvent error = runFailingWith(new IllegalStateException("jdbc:h2:mem:projetoia senha=segredo"));

        assertFalse(error.message().contains("segredo"), error.message());
        assertTrue(error.message().contains("erro"), error.message());
    }

    private static RunErrorEvent runFailingWith(Throwable failure) throws InterruptedException {
        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onError(failure);
            }
        };
        ChartAgent agent = new ChartAgent(model, new ChartTools(new ChartDataService()));
        RunAgentInput input = new RunAgentInput("t1", "r1", List.of(new UserMessage("m1", "oi")), List.of());

        List<Event> events = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        agent.run(input).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Event event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                finished.countDown();
            }

            @Override
            public void onComplete() {
                finished.countDown();
            }
        });

        assertTrue(finished.await(10, TimeUnit.SECONDS), "a execução não terminou");
        return assertInstanceOf(RunErrorEvent.class, events.getLast());
    }
}
