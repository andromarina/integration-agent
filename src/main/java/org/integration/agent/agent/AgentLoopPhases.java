package org.integration.agent.agent;

/**
 * Names the phases used by the orchestrator: plan (LLM), act (tool), observe (record step),
 * reflect (update lightweight in-memory flags for the mock planner).
 */
public final class AgentLoopPhases {

    public static final int MAX_ITERATIONS = 5;

    private AgentLoopPhases() {
    }
}
