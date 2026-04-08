package org.integration.agent.model;

import java.util.Collections;
import java.util.Map;

/**
 * Planned tool invocation produced by the LLM layer (mock or real).
 */
public class ToolAction {

    public static final String DONE = "DONE";

    private final String name;
    private final Map<String, Object> input;

    public ToolAction(String name, Map<String, Object> input) {
        this.name = name;
        this.input = input != null ? input : Collections.emptyMap();
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getInput() {
        return input;
    }
}
