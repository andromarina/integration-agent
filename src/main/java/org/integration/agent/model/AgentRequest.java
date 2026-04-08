package org.integration.agent.model;

import jakarta.validation.constraints.NotBlank;

public class AgentRequest {

    @NotBlank(message = "goal is required")
    private String goal;

    @NotBlank(message = "openApiSpec is required")
    private String openApiSpec;

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getOpenApiSpec() {
        return openApiSpec;
    }

    public void setOpenApiSpec(String openApiSpec) {
        this.openApiSpec = openApiSpec;
    }
}
