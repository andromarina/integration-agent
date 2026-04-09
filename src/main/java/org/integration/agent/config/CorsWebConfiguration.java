package org.integration.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties(IntegrationAgentCorsProperties.class)
public class CorsWebConfiguration implements WebMvcConfigurer {

    private final IntegrationAgentCorsProperties corsProperties;

    public CorsWebConfiguration(IntegrationAgentCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return;
        }

        registry.addMapping("/agent/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
