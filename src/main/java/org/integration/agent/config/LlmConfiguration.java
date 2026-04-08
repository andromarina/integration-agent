package org.integration.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.integration.agent.service.LangChain4jLlmService;
import org.integration.agent.service.LlmService;
import org.integration.agent.service.MockLlmService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    /**
     * OpenRouter exposes an OpenAI-compatible HTTP API at this path prefix.
     */
    static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";

    @Bean
    @ConditionalOnProperty(prefix = "integration.agent.llm", name = "mode", havingValue = "chat")
    public OpenAiChatModel openAiChatModel(LlmProperties properties) {
        LlmProperties.Chat chat = properties.getChat();
        String apiKey = chat.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "integration.agent.llm.chat.api-key is required when integration.agent.llm.mode=chat");
        }
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey.trim())
                .modelName(chat.getModelName())
                .timeout(Duration.ofSeconds(chat.getTimeoutSeconds()));
        String baseUrl = resolveBaseUrl(chat);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.trim());
        }
        return builder.build();
    }

    private static String resolveBaseUrl(LlmProperties.Chat chat) {
        String configured = chat.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if ("openrouter".equalsIgnoreCase(chat.getProvider())) {
            return DEFAULT_OPENROUTER_BASE_URL;
        }
        return null;
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.agent.llm", name = "mode", havingValue = "chat")
    public LlmService langChain4jLlmService(ChatModel chatModel, ObjectMapper objectMapper) {
        return new LangChain4jLlmService(chatModel, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.agent.llm", name = "mode", havingValue = "mock", matchIfMissing = true)
    public LlmService mockLlmService() {
        return new MockLlmService();
    }
}
