package org.integration.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Denormalized artifact for clients (generated code, HTTP summaries) alongside {@link AgentStep} traces.
 */
public class AgentArtifact {

    private String kind;
    private int stepNumber;
    private Map<String, Object> data = new LinkedHashMap<>();

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
    }
}
