package org.integration.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.integration.agent.agent.AgentLoopPhases;
import org.integration.agent.model.AgentRequest;
import org.integration.agent.model.AgentResponse;
import org.integration.agent.model.AgentStep;
import org.integration.agent.model.StepStatus;
import org.integration.agent.model.ToolAction;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.integration.agent.tools.OpenApiParserTool;
import org.integration.agent.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int LOG_PREVIEW_MAX = 4096;

    private final Map<String, Tool> tools;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public AgentService(
            @Qualifier("toolIndex") Map<String, Tool> tools,
            LlmService llmService,
            ObjectMapper objectMapper) {
        this.tools = tools;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public AgentResponse run(AgentRequest request) {
        List<AgentStep> steps = new java.util.ArrayList<>();
        Object finalResult = null;
        for (int iteration = 0; iteration < AgentLoopPhases.MAX_ITERATIONS; iteration++) {
            ToolAction action = llmService.decideNextAction(request.getGoal(), steps);
            if (ToolAction.DONE.equals(action.getName())) {
                finalResult = resolveFinalResult(steps, action);
                log.info("Agent loop finished with DONE after {} tool steps: {}", steps.size(), preview(finalResult));
                break;
            }
            Tool tool = tools.get(action.getName());
            Map<String, Object> effectiveInput = buildEffectiveInput(request, action);
            if (tool == null) {
                AgentStep missing = recordStep(steps.size() + 1, action.getName(), effectiveInput, null,
                        StepStatus.FAILED, "Unknown tool: " + action.getName());
                steps.add(missing);
                log.warn("Unknown tool requested: {}", action.getName());
                continue;
            }
            logStepStart(action.getName(), effectiveInput);
            Object output;
            StepStatus status;
            String errorMessage = null;
            try {
                output = tool.execute(effectiveInput);
                status = interpretStatus(output);
                if (status == StepStatus.FAILED) {
                    errorMessage = extractErrorMessage(output);
                }
            } catch (Exception ex) {
                output = Map.of("error", ex.getClass().getSimpleName(), "message", String.valueOf(ex.getMessage()));
                status = StepStatus.FAILED;
                errorMessage = ex.getMessage();
                log.warn("Tool {} threw: {}", action.getName(), ex.toString());
            }
            AgentStep step = recordStep(steps.size() + 1, action.getName(), effectiveInput, output, status, errorMessage);
            steps.add(step);
            logStepEnd(action.getName(), output, status);
            reflect();
        }
        if (finalResult == null) {
            finalResult = resolveFinalResult(steps, new ToolAction(ToolAction.DONE, Map.of("reason", "max iterations reached")));
        }
        AgentResponse response = new AgentResponse();
        response.setSteps(steps);
        response.setFinalResult(finalResult);
        response.setArtifacts(AgentArtifactsBuilder.fromSteps(steps));
        return response;
    }

    private static void reflect() {
        // MVP: placeholder for richer state updates between iterations.
    }

    private Map<String, Object> buildEffectiveInput(AgentRequest request, ToolAction action) {
        Map<String, Object> effectiveInput = new LinkedHashMap<>(action.getInput());
        if (OpenApiParserTool.NAME.equals(action.getName()) || CodeGenTool.NAME.equals(action.getName())) {
            Object existing = effectiveInput.get("openApiSpec");
            if (!(existing instanceof String existingSpec) || existingSpec.isBlank()) {
                effectiveInput.put("openApiSpec", request.getOpenApiSpec());
            }
        }
        return effectiveInput;
    }

    private AgentStep recordStep(int stepNumber, String action, Map<String, Object> input, Object output,
                                 StepStatus status, String errorMessage) {
        AgentStep step = new AgentStep();
        step.setStepNumber(stepNumber);
        step.setAction(action);
        step.setInput(input);
        step.setOutput(output);
        step.setStatus(status);
        step.setErrorMessage(errorMessage);
        return step;
    }

    private StepStatus interpretStatus(Object output) {
        if (!(output instanceof Map<?, ?> map)) {
            return StepStatus.SUCCESS;
        }
        Object success = map.get("success");
        if (success instanceof Boolean b) {
            return b ? StepStatus.SUCCESS : StepStatus.FAILED;
        }
        return StepStatus.SUCCESS;
    }

    private String extractErrorMessage(Object output) {
        if (!(output instanceof Map<?, ?> map)) {
            return null;
        }
        Object error = map.get("error");
        if (error != null) {
            return error.toString();
        }
        return null;
    }

    private Object resolveFinalResult(List<AgentStep> steps, ToolAction doneAction) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            AgentStep step = steps.get(i);
            if (HttpCallTool.NAME.equals(step.getAction())) {
                return step.getOutput();
            }
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            AgentStep step = steps.get(i);
            if (OpenApiParserTool.NAME.equals(step.getAction()) && step.getStatus() == StepStatus.SUCCESS) {
                return step.getOutput();
            }
        }
        return doneAction.getInput();
    }

    private void logStepStart(String action, Map<String, Object> input) {
        log.info("Agent step tool={} input={}", action, preview(input));
    }

    private void logStepEnd(String action, Object output, StepStatus status) {
        log.info("Agent step tool={} status={} output={}", action, status, preview(output));
    }

    private String preview(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return truncate(stringValue, LOG_PREVIEW_MAX);
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), LOG_PREVIEW_MAX);
        } catch (JsonProcessingException e) {
            return truncate(String.valueOf(value), LOG_PREVIEW_MAX);
        }
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
