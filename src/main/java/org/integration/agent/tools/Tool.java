package org.integration.agent.tools;

import java.util.Map;

/**
 * Executable capability invoked by the agent (parse spec, call HTTP, generate code, etc.).
 */
public interface Tool {

    String getName();

    Object execute(Map<String, Object> input);
}
