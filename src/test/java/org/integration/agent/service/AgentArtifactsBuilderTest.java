package org.integration.agent.service;

import org.integration.agent.model.AgentArtifact;
import org.integration.agent.model.AgentStep;
import org.integration.agent.model.StepStatus;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentArtifactsBuilderTest {

    @Test
    void buildsCodegenAndHttpArtifacts() {
        AgentStep codegen = new AgentStep();
        codegen.setStepNumber(2);
        codegen.setAction(CodeGenTool.NAME);
        codegen.setStatus(StepStatus.SUCCESS);
        Map<String, Object> cgOut = new LinkedHashMap<>();
        cgOut.put("success", true);
        cgOut.put("language", "java");
        cgOut.put("code", "class A {}");
        cgOut.put("testCode", "@Test void t() {}");
        cgOut.put("responseClassCode", "class Dto {}");
        cgOut.put("metadata", Map.of("path", "/users", "method", "GET"));
        codegen.setOutput(cgOut);

        AgentStep http = new AgentStep();
        http.setStepNumber(3);
        http.setAction(HttpCallTool.NAME);
        http.setInput(Map.of("method", "GET", "url", "https://example.com/users"));
        http.setOutput(Map.of("success", true, "statusCode", 200, "body", "[]"));

        List<AgentArtifact> artifacts = AgentArtifactsBuilder.fromSteps(List.of(codegen, http));

        assertThat(artifacts).hasSize(3);
        assertThat(artifacts.get(0).getKind()).isEqualTo(AgentArtifactsBuilder.KIND_GENERATED_CLIENT_CODE);
        assertThat(artifacts.get(0).getStepNumber()).isEqualTo(2);
        assertThat(artifacts.get(0).getData()).containsKeys("code", "metadata", "language", "testCode", "responseClassCode");
        assertThat(artifacts.get(0).getData().get("testCode")).isEqualTo("@Test void t() {}");
        assertThat(artifacts.get(0).getData().get("responseClassCode")).isEqualTo("class Dto {}");
        assertThat(artifacts.get(1).getKind()).isEqualTo(AgentArtifactsBuilder.KIND_GENERATED_JAVA_TYPE);
        assertThat(artifacts.get(1).getStepNumber()).isEqualTo(2);
        assertThat(artifacts.get(1).getData()).containsEntry("className", "Dto");
        assertThat(artifacts.get(1).getData().get("code").toString()).contains("class Dto");
        assertThat(artifacts.get(2).getKind()).isEqualTo(AgentArtifactsBuilder.KIND_HTTP_CALL);
        assertThat(artifacts.get(2).getData()).containsEntry("url", "https://example.com/users");
    }
}
