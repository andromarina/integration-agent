package org.integration.agent.service;

import org.integration.agent.model.AgentStep;
import org.integration.agent.model.ToolAction;

import java.util.List;

/**
 * Decides the next tool to run from the goal and prior steps. Replace {@link org.integration.agent.service.MockLlmService}
 * with a real LLM-backed implementation when ready.
 */
public interface LlmService {

    ToolAction decideNextAction(String goal, List<AgentStep> steps);
}
