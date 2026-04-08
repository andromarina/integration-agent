package org.integration.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.integration.agent.model.ToolAction;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPlannerMappingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsGenerateClientCodeToolCallToToolAction() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(CodeGenTool.NAME)
                .arguments("{\"method\":\"GET\",\"path\":\"/users\"}")
                .build();
        AiMessage message = AiMessage.from(request);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(CodeGenTool.NAME);
        assertThat(action.getInput()).containsEntry("path", "/users");
    }

    @Test
    void mapsToolCallToToolAction() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(HttpCallTool.NAME)
                .arguments("{\"url\":\"http://localhost:8080/users\",\"method\":\"GET\"}")
                .build();
        AiMessage message = AiMessage.from(request);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(HttpCallTool.NAME);
        assertThat(action.getInput()).containsEntry("url", "http://localhost:8080/users");
        assertThat(action.getInput()).containsEntry("method", "GET");
    }

    @Test
    void mapsDoneToolToDoneAction() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(ToolAction.DONE)
                .arguments("{\"reason\":\"finished\"}")
                .build();
        AiMessage message = AiMessage.from(request);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(ToolAction.DONE);
        assertThat(action.getInput()).containsEntry("reason", "finished");
    }

    @Test
    void unknownToolReturnsDoneWithReason() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("UnknownTool")
                .arguments("{}")
                .build();
        AiMessage message = AiMessage.from(request);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(ToolAction.DONE);
        assertThat(action.getInput().get("reason").toString()).contains("Unknown tool");
    }

    @Test
    void invalidArgumentsJsonReturnsDone() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(HttpCallTool.NAME)
                .arguments("{not-json")
                .build();
        AiMessage message = AiMessage.from(request);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(ToolAction.DONE);
        assertThat(action.getInput().get("reason").toString()).contains("Invalid tool arguments JSON");
    }

    @Test
    void textOnlyMessageMapsToDoneWithReasonFromText() {
        AiMessage message = AiMessage.from("Stopping here.");

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(ToolAction.DONE);
        assertThat(action.getInput()).containsEntry("reason", "Stopping here.");
    }

    @Test
    void jsonStringRootMapsToOpenApiSpecForParserTool() {
        ToolExecutionRequest parseRequest = ToolExecutionRequest.builder()
                .name(org.integration.agent.tools.OpenApiParserTool.NAME)
                .arguments("\"https://petstore3.swagger.io/api/v3/openapi.json\"")
                .build();
        AiMessage message = AiMessage.from(parseRequest);

        ToolAction action = LlmPlannerMappings.mapResponse(message, objectMapper);

        assertThat(action.getName()).isEqualTo(org.integration.agent.tools.OpenApiParserTool.NAME);
        assertThat(action.getInput()).containsEntry("openApiSpec", "https://petstore3.swagger.io/api/v3/openapi.json");
    }
}
