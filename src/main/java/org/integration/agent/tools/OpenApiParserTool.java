package org.integration.agent.tools;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input keys: {@code openApiSpec} (raw JSON or YAML string, or an {@code http(s)} URL to fetch it).
 * Accepts OpenAPI 3.x or Swagger 2.0; Swagger 2 is converted to OpenAPI 3 internally.
 * Output: {@code baseUrl}, {@code endpoints} (list of path/method/description).
 */
@Component
public class OpenApiParserTool implements Tool {

    public static final String NAME = "OpenApiParserTool";

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(45);

    private final WebClient webClient;

    public OpenApiParserTool(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        Object raw = input.get("openApiSpec");
        if (!(raw instanceof String spec) || spec.isBlank()) {
            return Map.of("success", false, "error", "openApiSpec string is required");
        }
        spec = spec.trim();
        String documentUrl = null;
        if (isHttpOrHttpsUrl(spec)) {
            documentUrl = spec;
            try {
                String body = webClient.get()
                        .uri(spec)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(FETCH_TIMEOUT);
                if (body == null || body.isBlank()) {
                    return Map.of("success", false, "error", "Empty response from openApiSpec URL");
                }
                spec = body;
            } catch (WebClientRequestException ex) {
                return Map.of("success", false, "error", "Failed to fetch openApiSpec URL: " + ex.getMessage());
            } catch (Exception ex) {
                return Map.of("success", false, "error", "Failed to fetch openApiSpec URL: " + ex.getMessage());
            }
        }
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();
        if (openAPI == null) {
            String message = parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()
                    ? String.join("; ", parseResult.getMessages())
                    : "Unknown parse error";
            return Map.of("success", false, "error", message);
        }
        String baseUrl = resolveBaseUrl(openAPI, documentUrl);
        List<Map<String, String>> endpoints = listEndpoints(openAPI);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("baseUrl", baseUrl);
        result.put("endpoints", endpoints);
        return result;
    }

    private static boolean isHttpOrHttpsUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String resolveBaseUrl(OpenAPI openAPI, String documentUrl) {
        List<Server> servers = openAPI.getServers();
        if (servers != null && !servers.isEmpty()) {
            String url = servers.get(0).getUrl();
            if (url != null && !url.isBlank()) {
                url = url.trim();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return stripTrailingSlash(url);
                }
                if (documentUrl != null) {
                    try {
                        URI resolved = URI.create(documentUrl).resolve(url);
                        return stripTrailingSlash(resolved.toString());
                    } catch (IllegalArgumentException ignored) {
                        return stripTrailingSlash(url);
                    }
                }
                return stripTrailingSlash(url);
            }
        }
        return "http://localhost:8080";
    }

    private static String stripTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static List<Map<String, String>> listEndpoints(OpenAPI openAPI) {
        List<Map<String, String>> endpoints = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return endpoints;
        }
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null) {
                continue;
            }
            Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
                Operation operation = opEntry.getValue();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", path);
                row.put("method", opEntry.getKey().name());
                String description = operation.getSummary();
                if (description == null || description.isBlank()) {
                    description = operation.getDescription();
                }
                row.put("description", description != null ? description : "");
                endpoints.add(row);
            }
        }
        return endpoints;
    }
}
