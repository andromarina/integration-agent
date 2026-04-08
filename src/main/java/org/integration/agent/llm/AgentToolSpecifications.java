package org.integration.agent.llm;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.integration.agent.model.ToolAction;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.integration.agent.tools.OpenApiParserTool;

import java.util.List;

/**
 * Tool schemas exposed to the chat model; names match {@link org.integration.agent.tools.Tool#getName()}.
 */
public final class AgentToolSpecifications {

    private static final List<ToolSpecification> ALL = List.of(
            openApiParser(),
            httpCall(),
            codeGen(),
            done()
    );

    private AgentToolSpecifications() {
    }

    public static List<ToolSpecification> all() {
        return ALL;
    }

    private static ToolSpecification openApiParser() {
        return ToolSpecification.builder()
                .name(OpenApiParserTool.NAME)
                .description("Parse a raw OpenAPI 3 spec string and list operations with a base URL.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("openApiSpec",
                                "Raw OpenAPI JSON or YAML string; optional because the server may inject it")
                        .build())
                .build();
    }

    private static ToolSpecification httpCall() {
        return ToolSpecification.builder()
                .name(HttpCallTool.NAME)
                .description("Perform one HTTP request using the configured WebClient.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("url", "Full URL including scheme and host")
                        .addStringProperty("method", "HTTP method, e.g. GET, POST")
                        .addStringProperty("body", "Optional JSON body for POST, PUT, or PATCH")
                        .required("url", "method")
                        .build())
                .build();
    }

    private static ToolSpecification codeGen() {
        return ToolSpecification.builder()
                .name(CodeGenTool.NAME)
                .description(
                        "Generate Java WebClient integration code plus a JUnit 5 test sketch for one API operation "
                                + "(step action generate_client_code). Output includes code and testCode.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("method", "HTTP method")
                        .addStringProperty("path", "Path template, e.g. /users")
                        .addStringProperty("baseUrl", "Optional base URL from the OpenAPI servers list")
                        .addStringProperty("operationId", "Optional identifier for the generated method name")
                        .addStringProperty("auth", "Optional auth summary (e.g. bearer, basic) to reflect in generated snippets")
                        .addStringProperty("openApiSpec",
                                "Optional OpenAPI document (JSON/YAML); when set, response DTO fields match the operation's JSON schema")
                        .required("method", "path")
                        .build())
                .build();
    }

    private static ToolSpecification done() {
        return ToolSpecification.builder()
                .name(ToolAction.DONE)
                .description("Finish planning; no further tools for this request.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("reason", "Why the run is complete")
                        .build())
                .build();
    }
}
