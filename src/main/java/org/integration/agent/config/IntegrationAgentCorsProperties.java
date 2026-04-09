package org.integration.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "integration.agent.cors")
public class IntegrationAgentCorsProperties {

    /**
     * Browser origins allowed to call the REST API (e.g. Angular dev server or deployed SPA).
     * Empty list disables explicit CORS mappings (same-origin or reverse-proxy only).
     */
    private List<String> allowedOrigins = List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200"
    );

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : List.of();
    }
}
