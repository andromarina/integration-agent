package org.integration.agent.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LLM integration settings.
 * <ul>
 *   <li>{@code mode=mock} — rule-based planner, no API key.</li>
 *   <li>{@code mode=chat} — LangChain4j {@code OpenAiChatModel}; set {@link Chat#getProvider()} to {@code openai} or {@code openrouter}.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "integration.agent.llm")
public class LlmProperties {

    /**
     * {@code mock} | {@code chat}.
     */
    @Pattern(regexp = "mock|chat")
    private String mode = "mock";

    /**
     * Used when {@code mode=chat}.
     */
    private final Chat chat = new Chat();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Chat getChat() {
        return chat;
    }

    public static class Chat {

        /**
         * {@code openai} — OpenAI API. {@code openrouter} — OpenAI-compatible API (e.g. DeepSeek via OpenRouter).
         */
        @Pattern(regexp = "openai|openrouter")
        private String provider = "openai";

        /**
         * API key (OpenAI or OpenRouter depending on {@link #provider}).
         */
        private String apiKey;

        /**
         * Optional override. For OpenRouter, defaults to the OpenRouter OpenAI-compatible base URL when blank.
         */
        private String baseUrl = "";

        private String modelName = "gpt-4o-mini";

        private int timeoutSeconds = 60;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
