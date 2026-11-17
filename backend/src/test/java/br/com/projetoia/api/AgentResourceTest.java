package br.com.projetoia.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercita o endpoint AG-UI de ponta a ponta, trocando apenas o modelo por um roteiro fixo. */
@QuarkusTest
class AgentResourceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    FakeStreamingChatModel model;

    @Test
    void chartRequestStreamsToolCallThenResultThenComment() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t1","runId":"r1","messages":[
                  {"id":"m1","role":"user","content":"faz um grafico de pizza por tipo"}
                ],"tools":[],"context":[],"state":{},"forwardedProps":{}}""");

        assertEquals(List.of(
                "RUN_STARTED",
                "TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END", "TOOL_CALL_RESULT",
                "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END",
                "RUN_FINISHED"), types(events));

        assertEquals("t1", events.getFirst().get("threadId").asText());
        assertEquals("r1", events.getFirst().get("runId").asText());

        JsonNode toolStart = events.get(1);
        assertEquals("renderChart", toolStart.get("toolCallName").asText());
        assertEquals("call-1", toolStart.get("toolCallId").asText());

        JsonNode toolResult = events.get(4);
        assertEquals("call-1", toolResult.get("toolCallId").asText());
        JsonNode chart = JSON.readTree(toolResult.get("content").asText());
        assertEquals("chart", chart.get("kind").asText());
        assertEquals("PIE", chart.get("chartType").asText());
        assertEquals("Valor total em reais", chart.get("subtitle").asText());
        assertEquals("Total por tipo", chart.get("title").asText());
        assertEquals("TOTAL_AMOUNT", chart.get("metric").asText());
        assertEquals(List.of("Pix", "Boleto", "Cartão"), texts(chart.get("labels")));
        assertEquals(3, chart.get("series").get(0).get("data").size());
        assertTrue(chart.get("series").get(0).get("data").get(0).isNumber());
        assertTrue(chart.get("series").get(0).get("data").get(0).asDouble() > 0);

        String messageId = events.get(5).get("messageId").asText();
        assertEquals("assistant", events.get(5).get("role").asText());
        assertEquals(messageId, events.get(6).get("messageId").asText());
        assertEquals("Aqui está ", events.get(6).get("delta").asText());
        assertEquals("o gráfico.", events.get(7).get("delta").asText());
    }

    @Test
    void requestTheToolRejectsGoesBackToTheModelAsText() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t5","runId":"r5","messages":[
                  {"id":"m1","role":"user","content":"pizza por semana"}
                ]}""");

        assertEquals(List.of(
                "RUN_STARTED",
                "TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END", "TOOL_CALL_RESULT",
                "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END",
                "RUN_FINISHED"), types(events));
        assertTrue(events.get(4).get("content").asText().contains("Para DAY ou WEEK use LINE ou BAR"));
        // O modelo recebeu a mesma explicacao como resultado da tool.
        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) model.lastMessages.getLast();
        assertTrue(toolResult.text().contains("Para DAY ou WEEK use LINE ou BAR"));
    }

    @Test
    void singleValueQuestionIsAnsweredWithANumberAndItsComparison() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t6","runId":"r6","messages":[
                  {"id":"m1","role":"user","content":"quanto entrou de pix em outubro?"}
                ]}""");

        assertEquals("showNumber", events.get(1).get("toolCallName").asText());
        JsonNode stat = JSON.readTree(events.get(4).get("content").asText());
        assertEquals("stat", stat.get("kind").asText());
        assertEquals("Pix em outubro", stat.get("title").asText());
        assertEquals("Valor total em reais · Pix · 01/10/2026 a 31/10/2026", stat.get("subtitle").asText());
        // Pix de outubro no import.sql: 4827,52 + 4168,13 + 663,64 + 428,18.
        assertEquals(10087.47, stat.get("value").asDouble(), 0.001);
        // Pix de setembro: 4373,74 + 61,30 + 1488,45 + 27,76 + 289,18.
        assertEquals("01/09/2026 a 30/09/2026", stat.get("comparison").get("period").asText());
        assertEquals(6240.43, stat.get("comparison").get("value").asDouble(), 0.001);
        assertEquals(61.6, stat.get("comparison").get("changePercent").asDouble(), 0.001);
    }

    @Test
    void listQuestionIsAnsweredWithATable() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t7","runId":"r7","messages":[
                  {"id":"m1","role":"user","content":"quais as 3 maiores transações?"}
                ]}""");

        assertEquals("showTable", events.get(1).get("toolCallName").asText());
        JsonNode table = JSON.readTree(events.get(4).get("content").asText());
        assertEquals("table", table.get("kind").asText());
        assertEquals(45, table.get("totalRows").asInt());
        assertEquals(3, table.get("rows").size());
        JsonNode first = table.get("rows").get(0);
        assertEquals("15/10/2026 20:34", first.get("date").asText());
        assertEquals("Ana Souza", first.get("user").asText());
        assertEquals("Cartão", first.get("type").asText());
        assertEquals(4925.14, first.get("amount").asDouble(), 0.001);
    }

    /** As proximas perguntas que o modelo sugere saem do texto e viram um evento proprio. */
    @Test
    void followUpSuggestionsAreSentAsACustomEventAndNotAsText() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t9","runId":"r9","messages":[{"id":"m1","role":"user","content":"me dê ideias"}]}""");

        assertEquals(List.of("RUN_STARTED", "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END",
                "CUSTOM", "RUN_FINISHED"), types(events));
        assertEquals("Posso ajudar.", events.get(2).get("delta").asText());
        assertEquals("suggestions", events.get(4).get("name").asText());
        assertEquals(List.of("Qual o total por tipo?", "Como foi outubro?"), texts(events.get(4).get("value")));
    }

    /** O modelo precisa da data de hoje para converter "outubro" em datas, e das tres tools. */
    @Test
    void modelReceivesTodaysDateAndTheThreeTools() throws Exception {
        run("""
                {"threadId":"t8","runId":"r8","messages":[{"id":"m1","role":"user","content":"oi"}]}""");

        SystemMessage system = (SystemMessage) model.lastMessages.getFirst();
        assertTrue(system.text().contains("Hoje é " + LocalDate.now()), system.text());
        assertEquals(Set.of("renderChart", "showNumber", "showTable"),
                model.lastTools.stream().map(ToolSpecification::name).collect(Collectors.toSet()));
    }

    @Test
    void plainQuestionStreamsOnlyText() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t2","runId":"r2","messages":[{"id":"m1","role":"user","content":"oi"}]}""");

        assertEquals(List.of("RUN_STARTED", "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_CONTENT",
                "TEXT_MESSAGE_END", "RUN_FINISHED"), types(events));
    }

    @Test
    void previousTurnsIncludingToolCallsReachTheModel() throws Exception {
        run("""
                {"threadId":"t3","runId":"r3","messages":[
                  {"id":"m1","role":"user","content":"faz um grafico de pizza"},
                  {"id":"m2","role":"assistant","toolCalls":[
                    {"id":"call-9","type":"function","function":{"name":"renderChart","arguments":"{}"}},
                    {"id":"call-sem-resposta","type":"function","function":{"name":"renderChart","arguments":"{}"}}
                  ]},
                  {"id":"m3","role":"tool","toolCallId":"call-9","content":"{\\"chartType\\":\\"PIE\\"}"},
                  {"id":"m4","role":"assistant","content":"Aqui está o gráfico."},
                  {"id":"m5","role":"user","content":"obrigado"}
                ]}""");

        // sistema, usuario, assistente (tool call), resultado da tool, assistente, usuario
        assertEquals(6, model.lastMessages.size());
        AiMessage toolCall = (AiMessage) model.lastMessages.get(2);
        assertEquals(1, toolCall.toolExecutionRequests().size(), "a chamada sem resposta deve ser descartada");
        assertEquals("call-9", toolCall.toolExecutionRequests().getFirst().id());
        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) model.lastMessages.get(3);
        assertEquals("renderChart", toolResult.toolName());
    }

    @Test
    void conversationThatDoesNotEndWithTheUserFailsTheRun() throws Exception {
        List<JsonNode> events = run("""
                {"threadId":"t4","runId":"r4","messages":[]}""");

        assertEquals(List.of("RUN_STARTED", "RUN_ERROR"), types(events));
        assertTrue(events.getLast().get("message").asText().contains("usuário"));
    }

    @Test
    void invalidBodyIsABadRequest() {
        given().contentType(ContentType.JSON).body("isto nao e json")
                .when().post("/api/agent")
                .then().statusCode(400);
    }

    private static List<JsonNode> run(String body) throws Exception {
        String stream = given().contentType(ContentType.JSON).body(body)
                .when().post("/api/agent")
                .then().statusCode(200)
                .extract().asString();

        List<JsonNode> events = new ArrayList<>();
        for (String line : stream.split("\\R")) {
            if (line.startsWith("data:")) {
                events.add(JSON.readTree(line.substring("data:".length()).strip()));
            }
        }
        return events;
    }

    private static List<String> types(List<JsonNode> events) {
        return events.stream().map(event -> event.get("type").asText()).toList();
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
