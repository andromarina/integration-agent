package org.integration.agent.config;

import org.integration.agent.tools.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AgentConfiguration {

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

    @Bean
    public Map<String, Tool> toolIndex(List<Tool> tools) {
        return tools.stream().collect(Collectors.toMap(Tool::getName, Function.identity()));
    }
}
