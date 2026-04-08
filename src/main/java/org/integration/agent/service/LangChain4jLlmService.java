package org.integration.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.integration.agent.llm.AgentToolSpecifications;
import org.integration.agent.llm.LlmPlannerMappings;
import org.integration.agent.model.AgentStep;
import org.integration.agent.model.ToolAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Calls a {@link ChatModel} with tool specifications each iteration and maps the result to {@link ToolAction}.
 */
public class LangChain4jLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jLlmService.class);

    private static final String SYSTEM_PROMPT = """
            You are a backend agent that integrates with HTTP APIs using tools.
            Use OpenApiParserTool first to parse the spec unless prior steps already show a successful parse.
            Then choose generate_client_code and/or HttpCallTool to fulfill the user's goal.
            When finished or blocked, call DONE with a short reason.
            Return exactly one tool call per turn.""";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public LangChain4jLlmService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolAction decideNextAction(String goal, List<AgentStep> steps) {
        try {
            String stepsJson = serializeSteps(steps);
            UserMessage userMessage = UserMessage.from(
                    "User goal:\n" + goal + "\n\nPrior steps (JSON):\n" + stepsJson);
            ChatRequest request = ChatRequest.builder()
                    .messages(SystemMessage.from(SYSTEM_PROMPT), userMessage)
                    .toolSpecifications(AgentToolSpecifications.all())
                    .build();
            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage != null && aiMessage.hasToolExecutionRequests() && aiMessage.toolExecutionRequests().size() > 1) {
                log.warn("Model returned {} tool calls; using the first only", aiMessage.toolExecutionRequests().size());
            }
            return LlmPlannerMappings.mapResponse(aiMessage, objectMapper);
        } catch (Exception ex) {
            log.warn("LLM planning failed: {}", ex.toString());
            return new ToolAction(ToolAction.DONE, Map.of("reason", "LLM error: " + String.valueOf(ex.getMessage())));
        }
    }

    private String serializeSteps(List<AgentStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
