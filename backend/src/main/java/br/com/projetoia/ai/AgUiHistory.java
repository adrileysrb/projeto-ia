package br.com.projetoia.ai;

import com.agui.community.core.message.AssistantMessage;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.ToolCall;
import com.agui.community.core.message.ToolMessage;
import com.agui.community.core.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converte o historico que o cliente AG-UI envia a cada execucao em mensagens do LangChain4j.
 * O servidor nao guarda conversa: o historico completo vem sempre na requisicao.
 */
public final class AgUiHistory {

    private AgUiHistory() {
    }

    public static List<ChatMessage> toChatMessages(List<Message> messages) {
        // Uma chamada de tool sem resultado (execucao interrompida) e rejeitada pelos modelos,
        // entao so entram as chamadas que tem resposta, e as respostas que tem chamada.
        Set<String> answeredCalls = messages.stream()
                .filter(ToolMessage.class::isInstance)
                .map(message -> ((ToolMessage) message).toolCallId())
                .collect(Collectors.toSet());
        Map<String, String> toolNameByCallId = new HashMap<>();

        List<ChatMessage> result = new ArrayList<>();
        for (Message message : messages) {
            switch (message) {
                case UserMessage user when hasText(user.content()) ->
                        result.add(dev.langchain4j.data.message.UserMessage.from(user.content()));
                case AssistantMessage assistant -> {
                    List<ToolExecutionRequest> requests = toolRequests(assistant, answeredCalls);
                    requests.forEach(request -> toolNameByCallId.put(request.id(), request.name()));
                    if (!requests.isEmpty()) {
                        result.add(hasText(assistant.content())
                                ? AiMessage.from(assistant.content(), requests)
                                : AiMessage.from(requests));
                    } else if (hasText(assistant.content())) {
                        result.add(AiMessage.from(assistant.content()));
                    }
                }
                case ToolMessage tool when toolNameByCallId.containsKey(tool.toolCallId()) ->
                        result.add(ToolExecutionResultMessage.from(
                                tool.toolCallId(),
                                toolNameByCallId.get(tool.toolCallId()),
                                tool.content() == null ? "" : tool.content()));
                // Mensagens de sistema vindas do cliente sao ignoradas: quem define o prompt e o servidor.
                default -> { }
            }
        }
        return result;
    }

    private static List<ToolExecutionRequest> toolRequests(AssistantMessage assistant, Set<String> answeredCalls) {
        if (assistant.toolCalls() == null) {
            return List.of();
        }
        return assistant.toolCalls().stream()
                .filter(call -> call.function() != null && answeredCalls.contains(call.id()))
                .map(AgUiHistory::toRequest)
                .toList();
    }

    private static ToolExecutionRequest toRequest(ToolCall call) {
        return ToolExecutionRequest.builder()
                .id(call.id())
                .name(call.function().name())
                .arguments(call.function().arguments())
                .build();
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
