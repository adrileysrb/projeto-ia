package br.com.projetoia.api;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Substitui o modelo real nos testes, com um roteiro fixo: conforme a palavra que aparece na
 * pergunta ("pizza", "quanto", "maiores"), pede uma das tools e, quando recebe o resultado,
 * comenta em texto. Qualquer outra pergunta recebe so texto.
 */
@Alternative
@Priority(1)
@Singleton
public class FakeStreamingChatModel implements StreamingChatModel {

    static final String PIE_ARGUMENTS = """
            {"chartType":"PIE","title":"Total por tipo","metric":"TOTAL_AMOUNT","groupBy":"TYPE"}""";

    /** Combinacao que a tool recusa: pizza nao aceita agrupamento por semana. */
    static final String PIE_BY_WEEK_ARGUMENTS = """
            {"chartType":"PIE","title":"Por semana","metric":"COUNT","groupBy":"WEEK"}""";

    static final String NUMBER_ARGUMENTS = """
            {"title":"Pix em outubro","metric":"TOTAL_AMOUNT","onlyType":"PIX",\
            "startDate":"2026-10-01","endDate":"2026-10-31"}""";

    static final String TABLE_ARGUMENTS = """
            {"title":"Maiores transações","orderBy":"HIGHEST_AMOUNT","limit":3}""";

    volatile List<ChatMessage> lastMessages = List.of();
    volatile List<ToolSpecification> lastTools = List.of();

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        lastMessages = List.copyOf(request.messages());
        lastTools = request.toolSpecifications() == null ? List.of() : List.copyOf(request.toolSpecifications());
        ChatMessage last = request.messages().getLast();
        String question = last instanceof UserMessage user ? user.singleText() : "";

        if (last instanceof ToolExecutionResultMessage) {
            reply(handler, "Aqui está ", "o gráfico.");
        } else if (question.contains("pizza")) {
            call(handler, "renderChart", question.contains("semana") ? PIE_BY_WEEK_ARGUMENTS : PIE_ARGUMENTS);
        } else if (question.contains("quanto")) {
            call(handler, "showNumber", NUMBER_ARGUMENTS);
        } else if (question.contains("maiores")) {
            call(handler, "showTable", TABLE_ARGUMENTS);
        } else if (question.contains("ideias")) {
            // Resposta com o bloco de sugestoes, cortada no meio do marcador como no streaming real.
            reply(handler, "Posso ajudar.\n\nSUGES", "TÕES:\nQual o total por tipo?\n", "Como foi outubro?\n");
        } else {
            reply(handler, "Olá! ", "Posso desenhar gráficos.");
        }
    }

    private static void call(StreamingChatResponseHandler handler, String tool, String arguments) {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name(tool)
                .arguments(arguments)
                .build();
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build());
    }

    private static void reply(StreamingChatResponseHandler handler, String... parts) {
        for (String part : parts) {
            handler.onPartialResponse(part);
        }
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(String.join("", parts))).build());
    }
}
