package br.com.projetoia.ai;

import br.com.projetoia.chart.ChartDataService;
import br.com.projetoia.chart.ChartRequest;
import br.com.projetoia.chart.ChartRequestException;
import br.com.projetoia.chart.ChartSpec;
import br.com.projetoia.chart.ChartType;
import br.com.projetoia.chart.GroupBy;
import br.com.projetoia.chart.Metric;
import br.com.projetoia.chart.SplitBy;
import br.com.projetoia.chart.StatRequest;
import br.com.projetoia.chart.StatSpec;
import br.com.projetoia.chart.TableOrder;
import br.com.projetoia.chart.TableRequest;
import br.com.projetoia.chart.TableSpec;
import br.com.projetoia.chart.TransactionFilter;
import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;

import java.util.function.Supplier;

/**
 * Tools que o modelo pode chamar. O modelo escolhe apenas a forma da resposta (grafico,
 * numero ou tabela) e o recorte; os valores sao calculados no servidor a partir do banco.
 */
public class ChartTools {

    public static final String RENDER_CHART = "renderChart";
    public static final String SHOW_NUMBER = "showNumber";
    public static final String SHOW_TABLE = "showTable";

    // Os filtros sao os mesmos nas tres tools; as descricoes ficam aqui para nao divergirem.
    // Os nomes comecam com "only" porque um modelo pequeno confundia o parametro "category"
    // com o pedido de agrupar por categoria, e preenchia o filtro sem querer.
    private static final String TYPE_FILTER = """
            Filtro opcional. Restringe a um único tipo de transação: PIX, BOLETO ou CARTAO. \
            Não preencha para incluir todos os tipos""";
    private static final String USER_FILTER = """
            Filtro opcional. Restringe a um único usuário, pelo nome ou parte do nome. \
            Não preencha para incluir todos os usuários""";
    private static final String CATEGORY_FILTER = """
            Filtro opcional. Restringe a uma única categoria: ALIMENTACAO, TRANSPORTE, MORADIA, LAZER, \
            SAUDE ou EDUCACAO. Não preencha para incluir todas as categorias""";
    private static final String STATUS_FILTER = """
            Filtro opcional. Restringe a uma única situação: CONCLUIDA, RECUSADA ou ESTORNADA. \
            Não preencha para incluir todas as situações""";
    private static final String START_DATE = "Opcional. Primeiro dia do período, no formato AAAA-MM-DD";
    private static final String END_DATE = "Opcional. Último dia do período (inclusive), no formato AAAA-MM-DD";

    private final ChartDataService chartData;

    public ChartTools(ChartDataService chartData) {
        this.chartData = chartData;
    }

    @Tool(name = RENDER_CHART, value = """
            Desenha um gráfico no chat a partir das transações financeiras do banco de dados. \
            Use para comparações, composições, evoluções no tempo e distribuições. Os valores \
            são calculados pelo servidor e voltam no resultado.""")
    public ChartSpec renderChart(
            @P("""
                    Tipo do gráfico. PIE: pizza, partes de um total. BAR: barras, compara grupos. \
                    STACKED_BAR: barras empilhadas, o total de cada grupo dividido por splitBy. \
                    HORIZONTAL_BAR: ranking do maior para o menor. LINE: evolução no tempo. \
                    CUMULATIVE_LINE: total acumulado no tempo. HISTOGRAM: distribuição das transações \
                    por faixa de valor. FUNNEL: transações iniciadas, aprovadas e concluídas. \
                    TREEMAP: blocos proporcionais ao valor de cada grupo""")
            ChartType chartType,
            @P("Título curto do gráfico, em português")
            String title,
            @P(value = """
                    Opcional. O que medir: TOTAL_AMOUNT (soma dos valores em reais, o padrão), COUNT \
                    (quantidade de transações), AVERAGE_AMOUNT (ticket médio), MAX_AMOUNT (maior valor) \
                    ou MIN_AMOUNT (menor valor)""", required = false)
            Metric metric,
            @P(value = """
                    Como agrupar: TYPE (tipo de transação), USER (usuário), CATEGORY (categoria de gasto), \
                    STATUS (situação), DAY (dia), WEEK (semana) ou MONTH (mês). Obrigatório, exceto em \
                    HISTOGRAM e FUNNEL. LINE e CUMULATIVE_LINE usam DAY, WEEK ou MONTH. Agrupar já \
                    compara todos os valores: "por categoria" é groupBy CATEGORY, sem nenhum filtro""", required = false)
            GroupBy groupBy,
            @P(value = """
                    Opcional. Divide o gráfico em várias séries: TYPE, USER, CATEGORY ou STATUS. \
                    Obrigatório em STACKED_BAR""", required = false)
            SplitBy splitBy,
            @P(value = "Opcional. Em HORIZONTAL_BAR, quantas barras mostrar (as maiores)", required = false)
            Integer limit,
            @P(value = TYPE_FILTER, required = false) TransactionType onlyType,
            @P(value = USER_FILTER, required = false) String onlyUser,
            @P(value = CATEGORY_FILTER, required = false) TransactionCategory onlyCategory,
            @P(value = STATUS_FILTER, required = false) TransactionStatus onlyStatus,
            @P(value = START_DATE, required = false) String startDate,
            @P(value = END_DATE, required = false) String endDate) {
        return answer(() -> chartData.buildChart(new ChartRequest(chartType, title, metric, groupBy, splitBy, limit,
                TransactionFilter.of(onlyType, onlyUser, onlyCategory, onlyStatus, startDate, endDate))));
    }

    @Tool(name = SHOW_NUMBER, value = """
            Mostra um único número em destaque no chat, calculado a partir das transações. Use quando \
            a resposta é um valor só, como o total de um mês ou o ticket médio de um usuário. Quando \
            startDate e endDate são informados, o resultado traz também a comparação com o período anterior.""")
    public StatSpec showNumber(
            @P("Título curto do número, em português")
            String title,
            @P("""
                    O que medir: TOTAL_AMOUNT (soma dos valores em reais), COUNT (quantidade de transações), \
                    AVERAGE_AMOUNT (ticket médio), MAX_AMOUNT (maior valor) ou MIN_AMOUNT (menor valor)""")
            Metric metric,
            @P(value = TYPE_FILTER, required = false) TransactionType onlyType,
            @P(value = USER_FILTER, required = false) String onlyUser,
            @P(value = CATEGORY_FILTER, required = false) TransactionCategory onlyCategory,
            @P(value = STATUS_FILTER, required = false) TransactionStatus onlyStatus,
            @P(value = START_DATE, required = false) String startDate,
            @P(value = END_DATE, required = false) String endDate) {
        return answer(() -> chartData.buildStat(new StatRequest(title, metric,
                TransactionFilter.of(onlyType, onlyUser, onlyCategory, onlyStatus, startDate, endDate))));
    }

    @Tool(name = SHOW_TABLE, value = """
            Mostra no chat uma tabela com transações individuais (data, usuário, tipo, categoria, \
            situação e valor). Use para pedidos de lista, como as maiores ou as mais recentes transações.""")
    public TableSpec showTable(
            @P("Título curto da tabela, em português")
            String title,
            @P(value = """
                    Opcional. Ordem das linhas: NEWEST (mais recentes primeiro, o padrão), OLDEST (mais \
                    antigas primeiro), HIGHEST_AMOUNT (maiores valores primeiro) ou LOWEST_AMOUNT \
                    (menores valores primeiro)""", required = false)
            TableOrder orderBy,
            @P(value = "Opcional. Quantas transações mostrar. O padrão é 10 e o máximo é 50", required = false)
            Integer limit,
            @P(value = TYPE_FILTER, required = false) TransactionType onlyType,
            @P(value = USER_FILTER, required = false) String onlyUser,
            @P(value = CATEGORY_FILTER, required = false) TransactionCategory onlyCategory,
            @P(value = STATUS_FILTER, required = false) TransactionStatus onlyStatus,
            @P(value = START_DATE, required = false) String startDate,
            @P(value = END_DATE, required = false) String endDate) {
        return answer(() -> chartData.buildTable(new TableRequest(title, orderBy, limit,
                TransactionFilter.of(onlyType, onlyUser, onlyCategory, onlyStatus, startDate, endDate))));
    }

    /**
     * Pedido que nao da para atender (combinacao invalida, filtro sem dados): a mensagem foi
     * escrita para o modelo, que pode corrigir os parametros ou explicar ao usuario. Qualquer
     * outra excecao e erro interno e nao chega ao modelo.
     */
    private static <T> T answer(Supplier<T> request) {
        try {
            return request.get();
        } catch (ChartRequestException e) {
            throw ToolErrorVisibleToLlm.from(e.getMessage());
        }
    }
}
