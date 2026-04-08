package org.integration.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.integration.agent.model.ToolAction;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.integration.agent.tools.OpenApiParserTool;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps LangChain4j {@link AiMessage} (tool calls or text) to {@link ToolAction} for {@link org.integration.agent.service.AgentService}.
 */
public final class LlmPlannerMappings {

    private static final Set<String> KNOWN_TOOLS = new LinkedHashSet<>();

    static {
        KNOWN_TOOLS.add(OpenApiParserTool.NAME);
        KNOWN_TOOLS.add(HttpCallTool.NAME);
        KNOWN_TOOLS.add(CodeGenTool.NAME);
        KNOWN_TOOLS.add(ToolAction.DONE);
    }

    private LlmPlannerMappings() {
    }

    public static ToolAction mapResponse(AiMessage aiMessage, ObjectMapper objectMapper) {
        if (aiMessage == null) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "Empty AI message"));
        }
        if (aiMessage.hasToolExecutionRequests()) {
            Object first = aiMessage.toolExecutionRequests().get(0);
            if (!(first instanceof ToolExecutionRequest request)) {
                return new ToolAction(ToolAction.DONE, Map.of("reason", "Invalid tool request entry"));
            }
            return mapToolExecutionRequest(request, objectMapper);
        }
        String text = aiMessage.text();
        if (text != null && !text.isBlank()) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", text.trim()));
        }
        return new ToolAction(ToolAction.DONE, Map.of("reason", "Model returned no tool call"));
    }

    private static ToolAction mapToolExecutionRequest(ToolExecutionRequest request, ObjectMapper objectMapper) {
        String name = request.name();
        if (name == null || name.isBlank()) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "Tool name missing"));
        }
        if (!KNOWN_TOOLS.contains(name)) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "Unknown tool: " + name));
        }
        Map<String, Object> args = parseArguments(request.arguments(), objectMapper, name);
        if (args == null) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "Invalid tool arguments JSON"));
        }
        if (ToolAction.DONE.equals(name)) {
            return new ToolAction(ToolAction.DONE, args);
        }
        return new ToolAction(name, args);
    }

    /**
     * Models sometimes emit a JSON string (e.g. a spec URL) instead of an object; that cannot deserialize to {@code Map}.
     */
    private static Map<String, Object> parseArguments(String json, ObjectMapper objectMapper, String toolName) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        String trimmed = json.trim();
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.isNull() || root.isMissingNode()) {
                return new LinkedHashMap<>();
            }
            if (root.isTextual()) {
                return mapTextualToolArgument(toolName, root.asText());
            }
            if (root.isObject()) {
                Map<String, Object> map = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {
                });
                if (map == null) {
                    return new LinkedHashMap<>();
                }
                return new LinkedHashMap<>(map);
            }
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Map<String, Object> mapTextualToolArgument(String toolName, String text) {
        if (OpenApiParserTool.NAME.equals(toolName)) {
            return Map.of("openApiSpec", text);
        }
        if (ToolAction.DONE.equals(toolName)) {
            return Map.of("reason", text);
        }
        return null;
    }
}
