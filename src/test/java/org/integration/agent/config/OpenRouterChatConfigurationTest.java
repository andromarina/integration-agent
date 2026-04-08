package org.integration.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@code mode=chat} with {@code provider=openrouter} builds an {@link OpenAiChatModel} (OpenAI-compatible client).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
        properties = {
                "integration.agent.llm.mode=chat",
                "integration.agent.llm.chat.provider=openrouter",
                "integration.agent.llm.chat.api-key=test-openrouter-key",
                "integration.agent.llm.chat.base-url=",
                "integration.agent.llm.chat.model-name=deepseek/deepseek-chat",
                "integration.agent.llm.chat.timeout-seconds=30"
        }
)
class OpenRouterChatConfigurationTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void loadsOpenAiCompatibleModelForOpenRouter() {
        assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
    }
}
