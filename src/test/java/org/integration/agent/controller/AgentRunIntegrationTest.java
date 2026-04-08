package org.integration.agent.controller;

import org.integration.agent.model.AgentArtifact;
import org.integration.agent.model.AgentResponse;
import org.integration.agent.model.AgentStep;
import org.integration.agent.service.AgentArtifactsBuilder;
import org.integration.agent.tools.CodeGenTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "integration.agent.llm.mode=mock")
class AgentRunIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void mockRunReturnsGenerateClientCodeStepAndArtifacts() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1"},
                  "servers": [{"url": "https://api.example.com"}],
                  "paths": {
                    "/users": {
                      "get": {
                        "summary": "List users",
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "userId": {"type": "string"},
                                    "displayName": {"type": "string"}
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

        ResponseEntity<AgentResponse> response = restTemplate.postForEntity(
                "/agent/run",
                Map.of("goal", "fetch users", "openApiSpec", spec),
                AgentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AgentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSteps().stream().map(AgentStep::getAction))
                .contains(CodeGenTool.NAME);
        assertThat(body.getArtifacts()).isNotEmpty();
        assertThat(body.getArtifacts().stream().map(AgentArtifact::getKind))
                .contains(AgentArtifactsBuilder.KIND_GENERATED_CLIENT_CODE, AgentArtifactsBuilder.KIND_HTTP_CALL);
        AgentArtifact codegen = body.getArtifacts().stream()
                .filter(a -> AgentArtifactsBuilder.KIND_GENERATED_CLIENT_CODE.equals(a.getKind()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) codegen.getData();
        assertThat(data.get("responseClassCode").toString()).contains("userId").contains("displayName");
    }
}
