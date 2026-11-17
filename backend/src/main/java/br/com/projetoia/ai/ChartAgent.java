package br.com.projetoia.ai;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.Role;
import com.agui.community.core.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Agente AG-UI: recebe uma execucao (historico da conversa) e devolve o fluxo de eventos
 * do protocolo, traduzindo os callbacks de streaming do LangChain4j.
 *
 * <p>Depende so do JDK, do LangChain4j e dos tipos do AG-UI, sem nada de Quarkus ou Spring.
 */
public class ChartAgent implements Agent {

    /** Nome do evento CUSTOM do AG-UI que leva as proximas perguntas sugeridas (uma lista de textos). */
    public static final String SUGGESTIONS_EVENT = "suggestions";

    private static final Logger LOG = System.getLogger(ChartAgent.class.getName());
    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_TOOL_ROUNDS = 5;

    private final StreamingChatModel model;
    private final ChartTools tools;

    public ChartAgent(StreamingChatModel model, ChartTools tools) {
        this.model = model;
        this.tools = tools;
    }

    @Override
    public Flow.Publisher<Event> run(RunAgentInput input) {
        // O trabalho so comeca quando alguem assina: o SubmissionPublisher descarta
        // o que for publicado antes de haver assinante.
        return subscriber -> {
            SubmissionPublisher<Event> events = new SubmissionPublisher<>();
            events.subscribe(subscriber);
            // Thread propria para nao depender do tipo de thread de quem assinou
            // (por exemplo, uma thread de I/O que nao pode bloquear).
            Thread.startVirtualThread(() -> new Run(input, events).start());
        };
    }

    /** Estado de uma unica execucao. */
    private final class Run {

        private final RunAgentInput input;
        private final SubmissionPublisher<Event> events;
        private final String threadId;
        private final String runId;
        private final Map<ToolExecutionRequest, String> toolCallIds = new HashMap<>();
        private final SuggestionSplitter suggestions = new SuggestionSplitter();
        private String openTextMessageId;

        Run(RunAgentInput input, SubmissionPublisher<Event> events) {
            this.input = input;
            this.events = events;
            this.threadId = input.threadId() != null ? input.threadId() : newId();
            this.runId = input.runId() != null ? input.runId() : newId();
        }

        void start() {
            events.submit(new RunStartedEvent(threadId, runId));
            try {
                List<Message> messages = input.messages() == null ? List.of() : input.messages();
                if (messages.isEmpty() || !(messages.getLast() instanceof UserMessage question)) {
                    endWithError("A última mensagem da conversa precisa ser do usuário.");
                    return;
                }

                // Historico anterior vai para a memoria; a pergunta atual e enviada pelo AI Service.
                ChatMemory memory = MessageWindowChatMemory.builder()
                        .maxMessages(MAX_HISTORY_MESSAGES)
                        // O prompt de sistema e adicionado depois do historico; sem isto ele iria para o meio.
                        .alwaysKeepSystemMessageFirst(true)
                        .build();
                AgUiHistory.toChatMessages(messages.subList(0, messages.size() - 1)).forEach(memory::add);

                ChartAssistant assistant = AiServices.builder(ChartAssistant.class)
                        .streamingChatModel(model)
                        .chatMemory(memory)
                        .tools(tools)
                        // Parametros invalidos voltam para o modelo como texto, para ele se corrigir.
                        .toolArgumentsErrorHandler(Run::explainInvalidArguments)
                        // Erros da tool so chegam ao modelo quando foram escritos para ele
                        // (ToolErrorVisibleToLlm); qualquer outro encerra a execucao com RUN_ERROR.
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                        .maxSequentialToolsInvocations(MAX_TOOL_ROUNDS)
                        .build();

                assistant.chat(question.content())
                        .onPartialResponse(this::onText)
                        .beforeToolExecution(before -> onToolCall(before.request()))
                        .onToolExecuted(this::onToolResult)
                        .onCompleteResponse(response -> finish())
                        .onError(this::fail)
                        .start();
            } catch (RuntimeException e) {
                fail(e);
            }
        }

        /** Diz ao modelo o que estava errado e que ele deve chamar de novo; sem isso, modelos pequenos desistem. */
        private static ToolErrorHandlerResult explainInvalidArguments(Throwable error, ToolErrorContext context) {
            return ToolErrorHandlerResult.text("Parâmetros inválidos para " + context.toolExecutionRequest().name()
                    + ": " + error.getMessage() + ". Corrija os parâmetros e chame a ferramenta de novo.");
        }

        private synchronized void onText(String delta) {
            if (delta != null) {
                // O bloco de sugestoes que o modelo escreve no fim nao vai para a tela como texto.
                emitText(suggestions.accept(delta));
            }
        }

        private void emitText(String text) {
            if (text.isEmpty()) {
                return;
            }
            if (openTextMessageId == null) {
                openTextMessageId = newId();
                events.submit(new TextMessageStartEvent(openTextMessageId, Role.ASSISTANT));
            }
            events.submit(new TextMessageContentEvent(openTextMessageId, text));
        }

        private synchronized void onToolCall(ToolExecutionRequest request) {
            closeTextMessage();
            String toolCallId = toolCallIds.computeIfAbsent(request, r -> r.id() != null ? r.id() : newId());
            events.submit(new ToolCallStartEvent(toolCallId, request.name()));
            if (request.arguments() != null && !request.arguments().isEmpty()) {
                events.submit(new ToolCallArgsEvent(toolCallId, request.arguments()));
            }
            events.submit(new ToolCallEndEvent(toolCallId));
        }

        private synchronized void onToolResult(ToolExecution execution) {
            String toolCallId = toolCallIds.getOrDefault(execution.request(), execution.request().id());
            events.submit(new ToolCallResultEvent(newId(), toolCallId, execution.result()));
        }

        private synchronized void finish() {
            closeTextMessage();
            // As proximas perguntas sugeridas vao num evento proprio, para a tela mostra-las como botoes.
            List<String> followUps = suggestions.suggestions();
            if (!followUps.isEmpty()) {
                events.submit(new CustomEvent(SUGGESTIONS_EVENT, followUps));
            }
            events.submit(new RunFinishedEvent(threadId, runId));
            events.close();
        }

        /** O detalhe do erro fica no log; para a tela vai só uma mensagem que o usuário consiga usar. */
        private void fail(Throwable error) {
            LOG.log(Level.ERROR, "Execução do agente falhou", error);
            endWithError(isConnectionFailure(error)
                    ? "Não foi possível conectar ao modelo de linguagem. Verifique se o servidor do modelo está no ar."
                    : "Ocorreu um erro ao gerar a resposta. O detalhe está no log do servidor.");
        }

        private synchronized void endWithError(String message) {
            closeTextMessage();
            events.submit(new RunErrorEvent(message));
            events.close();
        }

        private void closeTextMessage() {
            // Libera o trecho que estava em espera para saber se era o marcador de sugestoes.
            emitText(suggestions.flush());
            if (openTextMessageId != null) {
                events.submit(new TextMessageEndEvent(openTextMessageId));
                openTextMessageId = null;
            }
        }
    }

    private static boolean isConnectionFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
