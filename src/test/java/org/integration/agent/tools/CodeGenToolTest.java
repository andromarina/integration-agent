package org.integration.agent.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenToolTest {

    private final CodeGenTool tool = new CodeGenTool();

    @Test
    void generatesStructuredJavaArtifact() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of(
                "method", "get",
                "path", "/users",
                "baseUrl", "https://api.example.com"
        ));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("language")).isEqualTo("java");
        assertThat(result.get("code").toString()).contains("WebClient").contains("Mono<T>").contains("Class<T> responseType");
        assertThat(result.get("testCode").toString()).contains("@Test").contains("GetUsersResponse");
        assertThat(result.get("responseClassCode").toString()).contains("class GetUsersResponse");
        assertThat(result.get("metadata")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertThat(metadata.get("method")).isEqualTo("GET");
        assertThat(metadata.get("path")).isEqualTo("/users");
        assertThat(metadata.get("title").toString()).contains("GET").contains("/users");
        assertThat(metadata.get("baseUrl")).isEqualTo("https://api.example.com");
        assertThat(metadata.get("responseClassName")).isEqualTo("GetUsersResponse");
    }

    @Test
    void derivesDtoFieldsFromOpenApiResponseSchema() {
        String openApiSpec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1"},
                  "paths": {
                    "/users": {
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "userId": {"type": "string"},
                                    "score": {"type": "number"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of(
                "method", "GET",
                "path", "/users",
                "openApiSpec", openApiSpec
        ));
        assertThat(result.get("success")).isEqualTo(true);
        String dto = result.get("responseClassCode").toString();
        assertThat(dto).contains("userId").contains("score").contains("Double");
        assertThat(result.get("code").toString()).contains("Mono<GetUsersResponse>").contains(".bodyToMono(GetUsersResponse.class)");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertThat(metadata.get("responseRootIsJsonArray")).isEqualTo(false);
    }

    @Test
    void matchesPathTemplateFromOpenApi() {
        String openApiSpec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1"},
                  "paths": {
                    "/pets/{petId}": {
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "name": {"type": "string"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of(
                "method", "GET",
                "path", "/pets/42",
                "openApiSpec", openApiSpec
        ));
        assertThat(result.get("responseClassCode").toString()).contains("name");
    }

    @Test
    void getAndPostSnippetsDifferInStructure() {
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = (Map<String, Object>) tool.execute(Map.of(
                "method", "GET",
                "path", "/users"
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> postResult = (Map<String, Object>) tool.execute(Map.of(
                "method", "POST",
                "path", "/users"
        ));

        String getCode = normalizeWhitespace(getResult.get("code").toString());
        String postCode = normalizeWhitespace(postResult.get("code").toString());
        assertThat(getCode).isNotEqualTo(postCode);
        assertThat(getCode).doesNotContain("requestBodyJson");
        assertThat(postCode).contains("requestBodyJson").contains("APPLICATION_JSON").contains("Class<T> responseType");
    }

    @Test
    void testCodeReferencesOperation() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of(
                "method", "GET",
                "path", "/pets",
                "baseUrl", "https://api.example.com"
        ));
        String testCode = result.get("testCode").toString();
        assertThat(testCode).contains("GET").contains("/pets").contains("api.example.com");
    }

    @Test
    void metadataTitleVariesByPath() {
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) tool.execute(Map.of("method", "GET", "path", "/users"));
        @SuppressWarnings("unchecked")
        Map<String, Object> orders = (Map<String, Object>) tool.execute(Map.of("method", "GET", "path", "/orders"));
        String titleUsers = ((Map<?, ?>) users.get("metadata")).get("title").toString();
        String titleOrders = ((Map<?, ?>) orders.get("metadata")).get("title").toString();
        assertThat(titleUsers).isNotEqualTo(titleOrders);
    }

    @Test
    void authChangesIntegrationSnippet() {
        @SuppressWarnings("unchecked")
        Map<String, Object> without = (Map<String, Object>) tool.execute(Map.of("method", "GET", "path", "/x"));
        @SuppressWarnings("unchecked")
        Map<String, Object> with = (Map<String, Object>) tool.execute(Map.of(
                "method", "GET",
                "path", "/x",
                "auth", "Bearer"
        ));
        assertThat(with.get("code").toString()).contains("TODO: configure credentials");
        assertThat(without.get("code").toString()).doesNotContain("TODO: configure credentials");
        assertThat(with.get("testCode").toString()).contains("Auth hint:").contains("GetXResponse.class");
        assertThat(without.get("testCode").toString()).doesNotContain("Auth hint:").contains("GetXResponse.class");
    }

    @Test
    void toolNameIsGenerateClientCode() {
        assertThat(tool.getName()).isEqualTo("generate_client_code");
    }

    @Test
    void rejectsMissingMethod() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("path", "/x"));
        assertThat(result.get("success")).isEqualTo(false);
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
