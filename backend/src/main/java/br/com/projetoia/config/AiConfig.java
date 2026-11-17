package br.com.projetoia.config;

import br.com.projetoia.ai.ChartAgent;
import br.com.projetoia.ai.ChartTools;
import br.com.projetoia.chart.ChartDataService;
import com.agui.community.core.agent.Agent;
import com.agui.community.core.serialization.Serializer;
import com.agui.community.spring.server.core.JacksonSerializer;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * Unico ponto em que o Quarkus monta as pecas de IA. O restante do pacote {@code ai}
 * e Java puro; em Spring esta classe viraria uma {@code @Configuration} com {@code @Bean}.
 */
@ApplicationScoped
public class AiConfig {

    @Produces
    @Singleton
    StreamingChatModel streamingChatModel(
            @ConfigProperty(name = "app.llm.provider") String provider,
            @ConfigProperty(name = "app.llm.lmstudio.base-url") String lmStudioBaseUrl,
            @ConfigProperty(name = "app.llm.lmstudio.model") String lmStudioModel,
            @ConfigProperty(name = "app.llm.deepseek.base-url") String deepSeekBaseUrl,
            @ConfigProperty(name = "app.llm.deepseek.model") String deepSeekModel,
            @ConfigProperty(name = "app.llm.deepseek.api-key") Optional<String> deepSeekApiKey,
            @ConfigProperty(name = "app.llm.bedrock.region") String bedrockRegion,
            @ConfigProperty(name = "app.llm.bedrock.model-id") Optional<String> bedrockModelId) {
        return switch (provider) {
            case "lmstudio" -> OpenAiStreamingChatModel.builder()
                    .baseUrl(lmStudioBaseUrl)
                    // O LM Studio nao valida a chave, mas o cliente exige que exista alguma.
                    .apiKey("lm-studio")
                    .modelName(lmStudioModel)
                    .temperature(0.2)
                    // O cliente HTTP do JDK tenta HTTP/2 por padrao, e o LM Studio so fala HTTP/1.1.
                    .httpClientBuilder(new JdkHttpClientBuilder()
                            .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1))
                            .connectTimeout(Duration.ofSeconds(10))
                            .readTimeout(Duration.ofMinutes(5)))
                    .build();
            // A API do DeepSeek segue o formato da OpenAI, entao usa o mesmo cliente do LM Studio,
            // so com outra URL e uma chave de verdade.
            case "deepseek" -> OpenAiStreamingChatModel.builder()
                    .baseUrl(deepSeekBaseUrl)
                    .apiKey(deepSeekApiKey.orElseThrow(() -> new IllegalStateException(
                            "Defina DEEPSEEK_API_KEY (variável de ambiente ou arquivo backend/.env) "
                                    + "para usar o provedor deepseek.")))
                    .modelName(deepSeekModel)
                    .temperature(0.2)
                    .timeout(Duration.ofMinutes(2))
                    .build();
            case "bedrock" -> BedrockStreamingChatModel.builder()
                    .region(Region.of(bedrockRegion))
                    .modelId(bedrockModelId.orElseThrow(() -> new IllegalStateException(
                            "Defina app.llm.bedrock.model-id para usar o provedor bedrock.")))
                    .build();
            default -> throw new IllegalStateException(
                    "app.llm.provider inválido: '" + provider + "'. Use lmstudio, deepseek ou bedrock.");
        };
    }

    @Produces
    @Singleton
    Agent chartAgent(StreamingChatModel model, ChartDataService chartData) {
        return new ChartAgent(model, new ChartTools(chartData));
    }

    @Produces
    @Singleton
    Serializer agUiSerializer() {
        return new JacksonSerializer();
    }
}
