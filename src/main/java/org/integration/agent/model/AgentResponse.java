package org.integration.agent.model;

import java.util.ArrayList;
import java.util.List;

public class AgentResponse {

    private List<AgentStep> steps = new ArrayList<>();
    private Object finalResult;
    private List<AgentArtifact> artifacts = new ArrayList<>();

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }

    public Object getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Object finalResult) {
        this.finalResult = finalResult;
    }

    public List<AgentArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<AgentArtifact> artifacts) {
        this.artifacts = artifacts;
    }
}
