package br.com.projetoia.ai;

import java.util.List;
import java.util.Locale;

/**
 * Separa do texto do modelo o bloco final de sugestoes de proximas perguntas.
 *
 * <p>O prompt pede que a resposta termine com uma linha {@value #MARKER} seguida de uma
 * pergunta por linha. Como o texto chega em pedacos (streaming), o separador segura o
 * comeco de cada linha ate saber se ela e o marcador: o que vem antes do marcador e
 * liberado para a tela; o que vem depois fica guardado e vira a lista de sugestoes.
 * Sem marcador, todo o texto e liberado e a lista fica vazia.
 */
final class SuggestionSplitter {

    static final String MARKER = "SUGESTÕES:";

    /** O marcador em minusculas e sem acento, para aceitar "Sugestões:" e "SUGESTOES:". */
    private static final String NORMALIZED_MARKER = "sugestoes:";
    private static final int MAX_SUGGESTIONS = 3;
    /** Uma "sugestao" maior que isto e um paragrafo que o modelo escreveu no lugar errado. */
    private static final int MAX_SUGGESTION_LENGTH = 120;

    /** Texto recebido e ainda nao liberado. */
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder suggestionBlock = new StringBuilder();
    private boolean inSuggestions;
    /** O texto ja liberado terminou no meio de uma linha? Entao o marcador so vale depois da proxima quebra. */
    private boolean midLine;

    /** Recebe um pedaco do texto do modelo e devolve o que ja pode ir para a tela (pode ser vazio). */
    String accept(String delta) {
        if (inSuggestions) {
            suggestionBlock.append(delta);
            return "";
        }
        pending.append(delta);

        int lineStart = midLine ? afterNextNewline(0) : 0;
        while (lineStart >= 0) {
            int contentStart = skipDecoration(lineStart);
            String rest = normalize(pending.substring(contentStart));
            if (rest.startsWith(NORMALIZED_MARKER)) {
                suggestionBlock.append(pending, contentStart + NORMALIZED_MARKER.length(), pending.length());
                inSuggestions = true;
                String before = release(lineStart).stripTrailing();
                pending.setLength(0);
                return before;
            }
            int newline = pending.indexOf("\n", contentStart);
            if (newline < 0) {
                // Ultima linha, ainda incompleta: se pode virar o marcador, espera o proximo pedaco.
                return NORMALIZED_MARKER.startsWith(rest) ? release(startOfTrailingBlank(lineStart)) : release(pending.length());
            }
            lineStart = newline + 1;
        }
        return release(pending.length());
    }

    /** Fim de uma mensagem de texto: libera o que estava em espera, sem o espaco em branco do final. */
    String flush() {
        String rest = inSuggestions ? "" : pending.toString().stripTrailing();
        pending.setLength(0);
        midLine = false;
        return rest;
    }

    /** As sugestoes encontradas, sem marcadores de lista; vazio se o modelo nao as escreveu. */
    List<String> suggestions() {
        return suggestionBlock.toString().lines()
                .map(SuggestionSplitter::cleanSuggestion)
                .filter(line -> !line.isEmpty() && line.length() <= MAX_SUGGESTION_LENGTH)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** Libera o inicio do texto em espera, ate a posicao dada. */
    private String release(int end) {
        String text = pending.substring(0, end);
        pending.delete(0, end);
        if (!text.isEmpty()) {
            midLine = !text.endsWith("\n");
        }
        return text;
    }

    private int afterNextNewline(int from) {
        int newline = pending.indexOf("\n", from);
        return newline < 0 ? -1 : newline + 1;
    }

    /** Pula recuo e enfeites de Markdown que alguns modelos poem antes do marcador ("**Sugestões:**"). */
    private int skipDecoration(int from) {
        int index = from;
        while (index < pending.length() && " \t*#_".indexOf(pending.charAt(index)) >= 0) {
            index++;
        }
        return index;
    }

    /** As linhas em branco antes do possivel marcador tambem esperam, para nao sobrarem no fim do texto. */
    private int startOfTrailingBlank(int lineStart) {
        int index = lineStart;
        while (index > 0 && Character.isWhitespace(pending.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('õ', 'o');
    }

    private static String cleanSuggestion(String line) {
        return line.strip()
                // marcador de lista ou numeracao: "- ", "• ", "1. ", "2) "
                .replaceFirst("^([-*•–]|\\d+[.)])\\s*", "")
                // aspas ou negrito em volta da pergunta
                .replaceAll("^[\"“*_]+|[\"”*_]+$", "")
                .strip();
    }
}
