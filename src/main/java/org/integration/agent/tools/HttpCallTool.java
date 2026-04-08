package org.integration.agent.tools;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input keys: {@code url} (full URL), {@code method} (e.g. GET), optional {@code headers}
 * (map string→string), optional {@code queryParams}, optional {@code body} for non-GET.
 */
@Component
public class HttpCallTool implements Tool {

    public static final String NAME = "HttpCallTool";

    private final WebClient webClient;

    public HttpCallTool(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        Object urlObj = input.get("url");
        Object methodObj = input.get("method");
        if (!(urlObj instanceof String url) || url.isBlank()) {
            return Map.of("success", false, "error", "url is required");
        }
        if (!(methodObj instanceof String method) || method.isBlank()) {
            return Map.of("success", false, "error", "method is required");
        }
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", "Unsupported HTTP method: " + method);
        }
        @SuppressWarnings("unchecked")
        Map<String, String> queryParams = (Map<String, String>) input.getOrDefault("queryParams", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, String> headerMap = (Map<String, String>) input.getOrDefault("headers", Map.of());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(url);
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            uriBuilder.queryParam(entry.getKey(), entry.getValue());
        }
        URI uri = uriBuilder.build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        headerMap.forEach(headers::add);
        if (!headers.containsKey(HttpHeaders.ACCEPT)) {
            headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        }

        try {
            WebClient.RequestBodySpec request = webClient.method(httpMethod).uri(uri).headers(h -> h.addAll(headers));
            Object body = input.get("body");
            WebClient.ResponseSpec responseSpec;
            if (body instanceof String bodyString && !bodyString.isEmpty()
                    && (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
                responseSpec = request.contentType(MediaType.APPLICATION_JSON).bodyValue(bodyString).retrieve();
            } else {
                responseSpec = request.retrieve();
            }
            var entity = responseSpec.toEntity(String.class).block();
            Map<String, Object> result = new LinkedHashMap<>();
            if (entity == null) {
                result.put("success", false);
                result.put("error", "Empty response");
                return result;
            }
            result.put("success", entity.getStatusCode().is2xxSuccessful());
            result.put("statusCode", entity.getStatusCode().value());
            result.put("body", entity.getBody() != null ? entity.getBody() : "");
            if (!entity.getStatusCode().is2xxSuccessful()) {
                result.put("error", "HTTP status " + entity.getStatusCode().value());
            }
            return result;
        } catch (WebClientResponseException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("statusCode", ex.getStatusCode().value());
            result.put("body", ex.getResponseBodyAsString());
            result.put("error", "HTTP error: " + ex.getStatusCode());
            return result;
        } catch (WebClientRequestException ex) {
            return Map.of(
                    "success", false,
                    "error", "Request failed: " + ex.getMessage()
            );
        } catch (Exception ex) {
            return Map.of(
                    "success", false,
                    "error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()
            );
        }
    }
}
