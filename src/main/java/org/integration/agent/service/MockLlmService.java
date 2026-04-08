package org.integration.agent.service;

import org.integration.agent.model.AgentStep;
import org.integration.agent.model.StepStatus;
import org.integration.agent.model.ToolAction;
import org.integration.agent.tools.CodeGenTool;
import org.integration.agent.tools.HttpCallTool;
import org.integration.agent.tools.OpenApiParserTool;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rule-based planner: parse OpenAPI, generate a code sketch for the chosen operation, perform one HTTP call, then stop.
 * If the goal contains "users" (case-insensitive), prefers an operation whose path contains "/users" (GET first).
 * Otherwise chooses among GETs using a small score: e.g. Petstore {@code /pet/findByStatus} is not picked when the goal
 * asks for a pet by numeric id (prefers {@code /pet/{petId}}).
 */
public class MockLlmService implements LlmService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    @Override
    public ToolAction decideNextAction(String goal, List<AgentStep> steps) {
        AgentStep lastParse = findLastStepForAction(steps, OpenApiParserTool.NAME);
        if (lastParse == null) {
            return new ToolAction(OpenApiParserTool.NAME, new LinkedHashMap<>());
        }
        if (lastParse.getStatus() == StepStatus.FAILED) {
            return new ToolAction(ToolAction.DONE, Map.of(
                    "reason", "OpenAPI parse failed",
                    "detail", lastParse.getErrorMessage() != null ? lastParse.getErrorMessage() : "unknown"
            ));
        }
        Map<String, Object> parseOutput = asMap(lastParse.getOutput());
        if (!Boolean.TRUE.equals(parseOutput.get("success"))) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "OpenAPI parse did not succeed"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, String>> endpoints = (List<Map<String, String>>) parseOutput.get("endpoints");
        if (endpoints == null || endpoints.isEmpty()) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "No operations found in OpenAPI"));
        }
        String baseUrl = parseOutput.get("baseUrl") instanceof String s ? s : "http://localhost:8080";
        Map<String, String> chosen = pickEndpoint(goal, endpoints);
        if (chosen == null) {
            return new ToolAction(ToolAction.DONE, Map.of("reason", "Could not select an endpoint"));
        }
        boolean codegenDone = steps.stream().anyMatch(s -> CodeGenTool.NAME.equals(s.getAction()));
        if (!codegenDone) {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("method", chosen.get("method"));
            in.put("path", chosen.get("path"));
            in.put("baseUrl", baseUrl);
            return new ToolAction(CodeGenTool.NAME, in);
        }
        boolean httpAttempted = steps.stream().anyMatch(s -> HttpCallTool.NAME.equals(s.getAction()));
        if (!httpAttempted) {
            String path = chosen.get("path");
            String method = chosen.get("method");
            String url = joinUrl(baseUrl, path);
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("method", method);
            in.put("url", url);
            return new ToolAction(HttpCallTool.NAME, in);
        }
        return new ToolAction(ToolAction.DONE, Map.of("reason", "Agent finished after HTTP attempt"));
    }

    private static AgentStep findLastStepForAction(List<AgentStep> steps, String action) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            AgentStep step = steps.get(i);
            if (action.equals(step.getAction())) {
                return step;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object output) {
        if (output instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static Map<String, String> pickEndpoint(String goal, List<Map<String, String>> endpoints) {
        String goalLower = goal.toLowerCase(Locale.ROOT);
        List<Map<String, String>> copy = new ArrayList<>(endpoints);
        if (goalLower.contains("users")) {
            List<Map<String, String>> users = copy.stream()
                    .filter(e -> e.get("path") != null && e.get("path").toLowerCase(Locale.ROOT).contains("/users"))
                    .toList();
            if (!users.isEmpty()) {
                return preferGet(users);
            }
        }
        List<Map<String, String>> gets = copy.stream()
                .filter(e -> "GET".equalsIgnoreCase(e.get("method")))
                .toList();
        if (!gets.isEmpty()) {
            return pickBestGet(goalLower, gets);
        }
        return copy.get(0);
    }

    /**
     * Highest-scoring GET wins; avoids always taking the first GET in spec order (often {@code findByStatus} before {@code {petId}}).
     */
    static Map<String, String> pickBestGet(String goalLower, List<Map<String, String>> gets) {
        if (gets.size() == 1) {
            return gets.get(0);
        }
        boolean goalMentionsConcreteId = goalMentionsConcreteId(goalLower);
        boolean goalMentionsStatusQuery = goalMentionsStatusQuery(goalLower);
        return gets.stream()
                .max(Comparator.comparingInt((Map<String, String> endpoint) -> scoreGetEndpoint(
                        goalLower, endpoint, goalMentionsConcreteId, goalMentionsStatusQuery))
                        .thenComparing(e -> e.get("path") != null ? e.get("path") : ""))
                .orElse(gets.get(0));
    }

    private static boolean goalMentionsConcreteId(String goalLower) {
        if (!DIGITS.matcher(goalLower).find()) {
            return false;
        }
        if (goalLower.contains("by id")) {
            return true;
        }
        if (goalLower.contains("pet id")) {
            return true;
        }
        if (goalLower.contains("get ") && goalLower.contains("pet")) {
            return true;
        }
        return goalLower.matches(".*\\bpet\\s+\\d+.*") || goalLower.matches(".*\\bid\\s*[:=]\\s*\\d+.*");
    }

    private static boolean goalMentionsStatusQuery(String goalLower) {
        if (goalLower.contains("findbystatus")) {
            return true;
        }
        if (!goalLower.contains("status")) {
            return false;
        }
        return goalLower.contains("available") || goalLower.contains("pending") || goalLower.contains("sold")
                || goalLower.contains("filter");
    }

    private static int scoreGetEndpoint(
            String goalLower,
            Map<String, String> endpoint,
            boolean goalMentionsConcreteId,
            boolean goalMentionsStatusQuery
    ) {
        String path = endpoint.get("path");
        if (path == null) {
            return 0;
        }
        String p = path.toLowerCase(Locale.ROOT);
        String description = endpoint.get("description");
        String summary = description != null ? description.toLowerCase(Locale.ROOT) : "";
        int score = 0;
        if (p.contains("findbystatus")) {
            if (goalMentionsConcreteId && !goalMentionsStatusQuery) {
                score -= 200;
            } else if (goalMentionsStatusQuery) {
                score += 40;
            }
        }
        if (p.contains("findbytags") && !goalLower.contains("tag")) {
            score -= 80;
        }
        if (goalMentionsConcreteId && p.contains("{")) {
            score += 120;
        }
        for (String token : goalLower.split("[^a-z0-9]+")) {
            if (token.length() < 3) {
                continue;
            }
            if (p.contains(token)) {
                score += 5;
            }
            if (summary.contains(token)) {
                score += 3;
            }
        }
        return score;
    }

    private static Map<String, String> preferGet(List<Map<String, String>> endpoints) {
        return endpoints.stream()
                .filter(e -> "GET".equalsIgnoreCase(e.get("method")))
                .findFirst()
                .orElse(endpoints.get(0));
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return base + p;
    }
}
