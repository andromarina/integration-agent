package org.integration.agent.tools;

import org.integration.agent.tools.openapi.OpenApiResponseDtoGenerator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Input keys: {@code method}, {@code path}, optional {@code baseUrl}, optional {@code operationId},
 * optional {@code auth}, optional {@code openApiSpec} (raw OpenAPI JSON/YAML — when present, response DTO
 * fields are derived from the operation's success JSON response schema).
 * <p>
 * On success, output includes {@code success}, {@code language} ({@code java}), {@code code} (WebClient
 * integration sketch), {@code testCode} (JUnit 5 sketch for the same operation),
 * {@code responseClassCode} (generated DTO class or classes), and structured {@code metadata}
 * (including {@code title}, {@code method}, {@code path}, {@code baseUrl}, {@code responseClassName},
 * optional {@code responseRootIsJsonArray}, plus optional {@code auth}).
 */
@Component
public class CodeGenTool implements Tool {

    public static final String NAME = "generate_client_code";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        Object methodObj = input.get("method");
        Object pathObj = input.get("path");
        if (!(methodObj instanceof String method) || method.isBlank()) {
            return Map.of("success", false, "error", "method is required");
        }
        if (!(pathObj instanceof String path) || path.isBlank()) {
            return Map.of("success", false, "error", "path is required");
        }
        String baseUrl = input.get("baseUrl") instanceof String s && !s.isBlank() ? s : "http://localhost:8080";
        String methodUpper = method.trim().toUpperCase();
        String operationId = input.get("operationId") instanceof String oid && !oid.isBlank()
                ? sanitizeJavaIdentifier(oid)
                : sanitizeJavaIdentifier(toMethodName(path, method));
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String fullUrl = normalizedBase + normalizedPath;
        String authSummary = input.get("auth") instanceof String auth && !auth.isBlank() ? auth : null;
        String fallbackClassName = buildResponseClassName(operationId);

        String openApiSpec = input.get("openApiSpec") instanceof String spec && !spec.isBlank() ? spec : null;
        Optional<OpenApiResponseDtoGenerator.Result> openapiDto = openApiSpec == null
                ? Optional.empty()
                : OpenApiResponseDtoGenerator.tryGenerate(openApiSpec, normalizedPath, methodUpper, fallbackClassName);

        String responseClassName;
        String responseClassCode;
        boolean rootIsJsonArray = false;
        if (openapiDto.isPresent()) {
            OpenApiResponseDtoGenerator.Result generated = openapiDto.get();
            responseClassCode = generated.javaSource();
            responseClassName = generated.responseClassName();
            rootIsJsonArray = generated.rootIsJsonArray();
        } else {
            responseClassName = fallbackClassName;
            responseClassCode = buildResponseClassCode(fallbackClassName);
        }

        String code = buildIntegrationSnippet(
                operationId,
                methodUpper,
                fullUrl,
                authSummary,
                openapiDto,
                responseClassName,
                rootIsJsonArray
        );
        String testCode = buildTestSnippet(
                operationId,
                methodUpper,
                normalizedPath,
                normalizedBase,
                authSummary,
                openapiDto,
                responseClassName,
                rootIsJsonArray
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", buildTitle(methodUpper, normalizedPath, operationId));
        metadata.put("method", methodUpper);
        metadata.put("path", normalizedPath);
        metadata.put("baseUrl", normalizedBase);
        metadata.put("responseClassName", responseClassName);
        metadata.put("responseRootIsJsonArray", rootIsJsonArray);
        if (authSummary != null) {
            metadata.put("auth", authSummary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("language", "java");
        result.put("code", code);
        result.put("testCode", testCode);
        result.put("responseClassCode", responseClassCode);
        result.put("metadata", metadata);
        return result;
    }

    private static String buildTitle(String methodUpper, String normalizedPath, String operationId) {
        return "Java client: " + methodUpper + " " + normalizedPath + " (" + operationId + ")";
    }

    private static String toMethodName(String path, String method) {
        String slug = path.replaceAll("[^a-zA-Z0-9]+", " ").trim().replace(' ', '_');
        if (slug.isEmpty()) {
            slug = "resource";
        }
        return method.toLowerCase() + "_" + slug;
    }

    private static String sanitizeJavaIdentifier(String raw) {
        String s = raw.replaceAll("[^a-zA-Z0-9_]+", "_");
        if (s.isEmpty()) {
            return "operation";
        }
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return "op_" + s;
        }
        return s;
    }

    private static String buildResponseClassName(String operationId) {
        String[] parts = operationId.split("[^a-zA-Z0-9]+");
        StringBuilder className = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            className.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                className.append(part.substring(1));
            }
        }
        if (className.length() == 0) {
            return "ApiResponse";
        }
        if (!className.toString().endsWith("Response")) {
            className.append("Response");
        }
        return className.toString();
    }

    private static boolean expectsJsonBody(String methodUpper) {
        return "POST".equals(methodUpper) || "PUT".equals(methodUpper) || "PATCH".equals(methodUpper);
    }

    private static String httpMethodConstant(String methodUpper) {
        return switch (methodUpper) {
            case "GET" -> "HttpMethod.GET";
            case "HEAD" -> "HttpMethod.HEAD";
            case "POST" -> "HttpMethod.POST";
            case "PUT" -> "HttpMethod.PUT";
            case "PATCH" -> "HttpMethod.PATCH";
            case "DELETE" -> "HttpMethod.DELETE";
            case "OPTIONS" -> "HttpMethod.OPTIONS";
            case "TRACE" -> "HttpMethod.TRACE";
            default -> "new HttpMethod(\"" + methodUpper + "\")";
        };
    }

    private static String authComment(String authSummary) {
        if (authSummary == null) {
            return "";
        }
        return "\n                // TODO: configure credentials (%s)\n".formatted(escapeForJavaString(authSummary));
    }

    private static String escapeForJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String buildIntegrationSnippet(
            String operationId,
            String methodUpper,
            String fullUrl,
            String authSummary,
            Optional<OpenApiResponseDtoGenerator.Result> openapiDto,
            String responseClassName,
            boolean rootIsJsonArray
    ) {
        String auth = authComment(authSummary);
        String methodRef = httpMethodConstant(methodUpper);
        boolean useConcreteDto = openapiDto.isPresent() && !rootIsJsonArray;
        boolean useConcreteList = openapiDto.isPresent() && rootIsJsonArray;

        if (expectsJsonBody(methodUpper)) {
            if (useConcreteList) {
                return """
                        // Integration sketch: %s %s (JSON array root — WebClient + ParameterizedTypeReference)
                        public Mono<java.util.List<%s>> %s(WebClient client, String requestBodyJson) {%s
                            org.springframework.core.ParameterizedTypeReference<java.util.List<%s>> responseType =
                                    new org.springframework.core.ParameterizedTypeReference<java.util.List<%s>>() {};
                            return client
                                    .method(%s)
                                    .uri("%s")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(requestBodyJson)
                                    .retrieve()
                                    .bodyToMono(responseType);
                        }
                        """.formatted(
                        methodUpper,
                        fullUrl,
                        responseClassName,
                        operationId,
                        auth,
                        responseClassName,
                        responseClassName,
                        methodRef,
                        escapeForJavaString(fullUrl)
                );
            }
            if (useConcreteDto) {
                return """
                        // Integration sketch: %s %s (requires WebClient, HttpMethod, MediaType, Mono on classpath)
                        public Mono<%s> %s(WebClient client, String requestBodyJson) {%s
                            return client
                                    .method(%s)
                                    .uri("%s")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(requestBodyJson)
                                    .retrieve()
                                    .bodyToMono(%s.class);
                        }
                        """.formatted(
                        methodUpper,
                        fullUrl,
                        responseClassName,
                        operationId,
                        auth,
                        methodRef,
                        escapeForJavaString(fullUrl),
                        responseClassName
                );
            }
            return """
                    // Integration sketch: %s %s (requires WebClient, HttpMethod, MediaType, Mono on classpath)
                    public <T> Mono<T> %s(WebClient client, String requestBodyJson, Class<T> responseType) {%s
                        return client
                                .method(%s)
                                .uri("%s")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestBodyJson)
                                .retrieve()
                                .bodyToMono(responseType);
                    }
                    """.formatted(methodUpper, fullUrl, operationId, auth, methodRef, escapeForJavaString(fullUrl));
        }
        if (useConcreteList) {
            return """
                    // Integration sketch: %s %s (JSON array root — WebClient + ParameterizedTypeReference)
                    public Mono<java.util.List<%s>> %s(WebClient client) {%s
                        org.springframework.core.ParameterizedTypeReference<java.util.List<%s>> responseType =
                                new org.springframework.core.ParameterizedTypeReference<java.util.List<%s>>() {};
                        return client
                                .method(%s)
                                .uri("%s")
                                .retrieve()
                                .bodyToMono(responseType);
                    }
                    """.formatted(
                    methodUpper,
                    fullUrl,
                    responseClassName,
                    operationId,
                    auth,
                    responseClassName,
                    responseClassName,
                    methodRef,
                    escapeForJavaString(fullUrl)
            );
        }
        if (useConcreteDto) {
            return """
                    // Integration sketch: %s %s (requires WebClient, HttpMethod, Mono on classpath)
                    public Mono<%s> %s(WebClient client) {%s
                        return client
                                .method(%s)
                                .uri("%s")
                                .retrieve()
                                .bodyToMono(%s.class);
                    }
                    """.formatted(
                    methodUpper,
                    fullUrl,
                    responseClassName,
                    operationId,
                    auth,
                    methodRef,
                    escapeForJavaString(fullUrl),
                    responseClassName
            );
        }
        return """
                // Integration sketch: %s %s (requires WebClient, HttpMethod, Mono on classpath)
                public <T> Mono<T> %s(WebClient client, Class<T> responseType) {%s
                    return client
                            .method(%s)
                            .uri("%s")
                            .retrieve()
                            .bodyToMono(responseType);
                }
                """.formatted(methodUpper, fullUrl, operationId, auth, methodRef, escapeForJavaString(fullUrl));
    }

    private static String buildResponseClassCode(String responseClassName) {
        return """
                public class %s {
                    private String id;
                    private String status;
                    private String message;

                    public String getId() {
                        return id;
                    }

                    public void setId(String id) {
                        this.id = id;
                    }

                    public String getStatus() {
                        return status;
                    }

                    public void setStatus(String status) {
                        this.status = status;
                    }

                    public String getMessage() {
                        return message;
                    }

                    public void setMessage(String message) {
                        this.message = message;
                    }
                }
                """.formatted(responseClassName);
    }

    private static String testAuthBlock(String authSummary, int indentSpaces) {
        if (authSummary == null) {
            return "";
        }
        return " ".repeat(indentSpaces) + "// Auth hint: " + escapeForJavaString(authSummary) + "\n";
    }

    private static String buildTestSnippet(
            String operationId,
            String methodUpper,
            String normalizedPath,
            String normalizedBase,
            String authSummary,
            Optional<OpenApiResponseDtoGenerator.Result> openapiDto,
            String responseClassName,
            boolean rootIsJsonArray
    ) {
        String methodRef = httpMethodConstant(methodUpper);
        boolean useConcreteList = openapiDto.isPresent() && rootIsJsonArray;

        if (expectsJsonBody(methodUpper)) {
            String authBlock = testAuthBlock(authSummary, 28);
            if (useConcreteList) {
                return """
                        import org.junit.jupiter.api.Test;
                        import org.springframework.http.HttpMethod;
                        import org.springframework.http.MediaType;
                        import org.springframework.web.reactive.function.client.WebClient;
                        import reactor.core.publisher.Mono;

                        class %sIntegrationTest {

                            @Test
                            void %s_shouldPostJson() {
                                String baseUrl = "%s";
                                WebClient client = WebClient.builder().baseUrl(baseUrl).build();
                                String body = "{}";
                        %s                        org.springframework.core.ParameterizedTypeReference<java.util.List<%s>> responseType =
                                        new org.springframework.core.ParameterizedTypeReference<java.util.List<%s>>() {};
                                Mono<java.util.List<%s>> response = client
                                        .method(%s)
                                        .uri("%s")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(body)
                                        .retrieve()
                                        .bodyToMono(responseType);
                                // TODO: assert with StepVerifier or block(); add WireMock/MockWebServer as needed
                            }
                        }
                        """.formatted(
                        operationId,
                        operationId,
                        escapeForJavaString(normalizedBase),
                        authBlock,
                        responseClassName,
                        responseClassName,
                        responseClassName,
                        methodRef,
                        escapeForJavaString(normalizedPath)
                );
            }
            return """
                    import org.junit.jupiter.api.Test;
                    import org.springframework.http.HttpMethod;
                    import org.springframework.http.MediaType;
                    import org.springframework.web.reactive.function.client.WebClient;
                    import reactor.core.publisher.Mono;

                    class %sIntegrationTest {

                        @Test
                        void %s_shouldPostJson() {
                            String baseUrl = "%s";
                            WebClient client = WebClient.builder().baseUrl(baseUrl).build();
                            String body = "{}";
                    %s                        Mono<%s> response = client
                                    .method(%s)
                                    .uri("%s")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(body)
                                    .retrieve()
                                    .bodyToMono(%s.class);
                            // TODO: assert with StepVerifier or block(); add WireMock/MockWebServer as needed
                        }
                    }
                    """.formatted(
                    operationId,
                    operationId,
                    escapeForJavaString(normalizedBase),
                    authBlock,
                    responseClassName,
                    methodRef,
                    escapeForJavaString(normalizedPath),
                    responseClassName
            );
        }
        String authBlock = testAuthBlock(authSummary, 24);
        if (useConcreteList) {
            return """
                    import org.junit.jupiter.api.Test;
                    import org.springframework.http.HttpMethod;
                    import org.springframework.web.reactive.function.client.WebClient;
                    import reactor.core.publisher.Mono;

                    class %sIntegrationTest {

                        @Test
                        void %s_shouldCallEndpoint() {
                            String baseUrl = "%s";
                            WebClient client = WebClient.builder().baseUrl(baseUrl).build();
                    %s                        org.springframework.core.ParameterizedTypeReference<java.util.List<%s>> responseType =
                                    new org.springframework.core.ParameterizedTypeReference<java.util.List<%s>>() {};
                            Mono<java.util.List<%s>> response = client
                                    .method(%s)
                                    .uri("%s")
                                    .retrieve()
                                    .bodyToMono(responseType);
                            // TODO: assert with StepVerifier or block(); add WireMock/MockWebServer as needed
                        }
                    }
                    """.formatted(
                    operationId,
                    operationId,
                    escapeForJavaString(normalizedBase),
                    authBlock,
                    responseClassName,
                    responseClassName,
                    responseClassName,
                    methodRef,
                    escapeForJavaString(normalizedPath)
            );
        }
        return """
                import org.junit.jupiter.api.Test;
                import org.springframework.http.HttpMethod;
                import org.springframework.web.reactive.function.client.WebClient;
                import reactor.core.publisher.Mono;

                class %sIntegrationTest {

                    @Test
                    void %s_shouldCallEndpoint() {
                        String baseUrl = "%s";
                        WebClient client = WebClient.builder().baseUrl(baseUrl).build();
                %s                        Mono<%s> response = client
                                .method(%s)
                                .uri("%s")
                                .retrieve()
                                .bodyToMono(%s.class);
                        // TODO: assert with StepVerifier or block(); add WireMock/MockWebServer as needed
                    }
                }
                """.formatted(
                operationId,
                operationId,
                escapeForJavaString(normalizedBase),
                authBlock,
                responseClassName,
                methodRef,
                escapeForJavaString(normalizedPath),
                responseClassName
        );
    }
}
