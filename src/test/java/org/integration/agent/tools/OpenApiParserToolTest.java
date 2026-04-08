package org.integration.agent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiParserToolTest {

    private final OpenApiParserTool tool = new OpenApiParserTool(WebClient.builder().build());

    @Test
    void parsesSwagger2PetstoreStyleSpec() {
        String swagger2 = """
                {
                  "swagger": "2.0",
                  "info": {"title": "t", "version": "1"},
                  "host": "petstore.swagger.io",
                  "basePath": "/v2",
                  "schemes": ["https"],
                  "paths": {
                    "/pet/{petId}": {
                      "get": {"summary": "Get pet by ID"}
                    }
                  }
                }
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("openApiSpec", swagger2));

        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("baseUrl")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> endpoints = (List<Map<String, String>>) result.get("endpoints");
        assertThat(endpoints).anyMatch(row ->
                "/pet/{petId}".equals(row.get("path")) && "GET".equalsIgnoreCase(row.get("method")));
    }

    @Test
    void parsesOpenApi3MinimalSpec() {
        String oas3 = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1"},
                  "servers": [{"url": "https://example.com"}],
                  "paths": {
                    "/items": {
                      "get": {"summary": "list"}
                    }
                  }
                }
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("openApiSpec", oas3));

        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("baseUrl")).isEqualTo("https://example.com");
    }
}
