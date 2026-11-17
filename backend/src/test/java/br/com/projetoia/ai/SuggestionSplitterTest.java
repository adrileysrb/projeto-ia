package br.com.projetoia.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionSplitterTest {

    private static final String ANSWER = """
            Saúde lidera com folga.

            SUGESTÕES:
            Quais são as maiores transações de Saúde?
            Como Saúde evoluiu por mês?
            Qual o ticket médio de Saúde?
            """;

    private static final List<String> QUESTIONS = List.of(
            "Quais são as maiores transações de Saúde?",
            "Como Saúde evoluiu por mês?",
            "Qual o ticket médio de Saúde?");

    @Test
    void separatesTheCommentFromTheSuggestions() {
        Result result = feed(ANSWER);

        assertEquals("Saúde lidera com folga.", result.text());
        assertEquals(QUESTIONS, result.suggestions());
    }

    /** O streaming corta o texto em qualquer ponto, inclusive no meio do marcador. */
    @Test
    void givesTheSameResultHoweverTheTextIsCutIntoPieces() {
        for (int size = 1; size <= ANSWER.length(); size++) {
            Result result = feedInPieces(ANSWER, size);

            assertEquals("Saúde lidera com folga.", result.text(), "pedaços de " + size);
            assertEquals(QUESTIONS, result.suggestions(), "pedaços de " + size);
        }
    }

    @Test
    void releasesTextAsSoonAsItCannotBeTheMarker() {
        SuggestionSplitter splitter = new SuggestionSplitter();

        assertEquals("Saúde ", splitter.accept("Saúde "));
        assertEquals("lidera.", splitter.accept("lidera.\n\n"));
        // "SUG" pode ser o comeco do marcador: espera.
        assertEquals("", splitter.accept("SUG"));
        // Nao era: a linha em espera sai inteira, com as quebras que vinham antes dela.
        assertEquals("\n\nSUGERIDO pelo time.", splitter.accept("ERIDO pelo time."));
    }

    @Test
    void textWithoutMarkerIsReleasedInFullAndHasNoSuggestions() {
        Result result = feedInPieces("Olá! Posso mostrar gráficos.\nÉ só pedir.\n", 3);

        assertEquals("Olá! Posso mostrar gráficos.\nÉ só pedir.", result.text());
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void acceptsTheMarkerAndTheListTheWayModelsDecorateThem() {
        Result result = feed("""
                Comentário.
                **Sugestões:**
                - "Qual o total por tipo?"
                2. Quais as maiores transações?
                • Como foi outubro?
                Quarta pergunta que passa do limite?
                """);

        assertEquals("Comentário.", result.text());
        assertEquals(List.of("Qual o total por tipo?", "Quais as maiores transações?", "Como foi outubro?"),
                result.suggestions());
    }

    /** A palavra no meio de uma frase nao e o marcador, que precisa comecar a linha. */
    @Test
    void markerInTheMiddleOfALineIsOrdinaryText() {
        Result result = feed("Tenho duas sugestões: ver por tipo ou por mês.");

        assertEquals("Tenho duas sugestões: ver por tipo ou por mês.", result.text());
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void answerWithOnlySuggestionsHasNoText() {
        Result result = feed("SUGESTÕES:\nQual o total por tipo?");

        assertEquals("", result.text());
        assertEquals(List.of("Qual o total por tipo?"), result.suggestions());
    }

    /** Depois de uma mensagem (antes de uma tool, por exemplo), a seguinte comeca em linha nova. */
    @Test
    void aNewMessageStartsAtTheBeginningOfALine() {
        SuggestionSplitter splitter = new SuggestionSplitter();

        assertEquals("Vou consultar", splitter.accept("Vou consultar") + splitter.flush());
        assertEquals("", splitter.accept("Sugestões:\nQual o total?") + splitter.flush());
        assertEquals(List.of("Qual o total?"), splitter.suggestions());
    }

    private static Result feed(String text) {
        return feedInPieces(text, text.length());
    }

    private static Result feedInPieces(String text, int size) {
        SuggestionSplitter splitter = new SuggestionSplitter();
        StringBuilder released = new StringBuilder();
        for (int start = 0; start < text.length(); start += size) {
            released.append(splitter.accept(text.substring(start, Math.min(text.length(), start + size))));
        }
        released.append(splitter.flush());
        return new Result(released.toString(), splitter.suggestions());
    }

    private record Result(String text, List<String> suggestions) {
    }
}
