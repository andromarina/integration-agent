package org.integration.agent.service;

import org.integration.agent.model.AgentArtifact;
import org.integration.agent.model.AgentStep;
import org.integration.agent.model.StepStatus;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link AgentArtifact} entries from completed steps for HTTP clients.
 */
public final class AgentArtifactsBuilder {

    public static final String KIND_GENERATED_CLIENT_CODE = "generated_client_code";
    /**
     * One artifact per top-level Java type parsed from {@code responseClassCode} (e.g. DTO classes).
     */
    public static final String KIND_GENERATED_JAVA_TYPE = "generated_java_type";
    public static final String KIND_HTTP_CALL = "http_call";

    private AgentArtifactsBuilder() {
    }

    public static List<AgentArtifact> fromSteps(List<AgentStep> steps) {
        List<AgentArtifact> result = new ArrayList<>();
        if (steps == null) {
            return result;
        }
        for (AgentStep step : steps) {
            if (CodeGenTool.NAME.equals(step.getAction())) {
                addCodegenArtifact(result, step);
            } else if (HttpCallTool.NAME.equals(step.getAction())) {
                addHttpArtifact(result, step);
            }
        }
        return result;
    }

    private static void addCodegenArtifact(List<AgentArtifact> result, AgentStep step) {
        if (step.getStatus() != StepStatus.SUCCESS) {
            return;
        }
        Object output = step.getOutput();
        if (!(output instanceof Map<?, ?> map)) {
            return;
        }
        if (!Boolean.TRUE.equals(map.get("success"))) {
            return;
        }
        AgentArtifact artifact = new AgentArtifact();
        artifact.setKind(KIND_GENERATED_CLIENT_CODE);
        artifact.setStepNumber(step.getStepNumber());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("language", map.get("language"));
        data.put("code", map.get("code"));
        if (map.get("testCode") != null) {
            data.put("testCode", map.get("testCode"));
        }
        if (map.get("responseClassCode") != null) {
            data.put("responseClassCode", map.get("responseClassCode"));
        }
        data.put("metadata", map.get("metadata"));
        artifact.setData(data);
        result.add(artifact);

        Object responseClassCode = map.get("responseClassCode");
        if (responseClassCode instanceof String responseSources && !responseSources.isBlank()) {
            for (JavaTopLevelTypeSplitter.TopLevelType type : JavaTopLevelTypeSplitter.split(responseSources)) {
                AgentArtifact typeArtifact = new AgentArtifact();
                typeArtifact.setKind(KIND_GENERATED_JAVA_TYPE);
                typeArtifact.setStepNumber(step.getStepNumber());
                Map<String, Object> typeData = new LinkedHashMap<>();
                typeData.put("language", map.get("language"));
                typeData.put("className", type.simpleName());
                typeData.put("code", type.source());
                typeArtifact.setData(typeData);
                result.add(typeArtifact);
            }
        }
    }

    private static void addHttpArtifact(List<AgentArtifact> result, AgentStep step) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setKind(KIND_HTTP_CALL);
        artifact.setStepNumber(step.getStepNumber());
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> input = step.getInput();
        if (input != null) {
            data.put("method", input.get("method"));
            data.put("url", input.get("url"));
        }
        Object output = step.getOutput();
        if (output instanceof Map<?, ?> out) {
            data.put("statusCode", out.get("statusCode"));
            data.put("success", out.get("success"));
            data.put("body", out.get("body"));
        }
        artifact.setData(data);
        result.add(artifact);
    }
}
