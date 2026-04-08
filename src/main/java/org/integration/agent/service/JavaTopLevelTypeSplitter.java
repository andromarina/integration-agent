package org.integration.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Java source blob that may contain several top-level types into separate fragments,
 * each prefixed with the leading import (and package) block when present.
 */
public final class JavaTopLevelTypeSplitter {

    private static final Pattern TOP_LEVEL_DECL = Pattern.compile(
            "(?m)^(?:(?:public|private|protected)\\s+)?(?:abstract\\s+|final\\s+|strictfp\\s+)*(class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private JavaTopLevelTypeSplitter() {
    }

    public record TopLevelType(String simpleName, String source) {
    }

    public static List<TopLevelType> split(String combinedSource) {
        if (combinedSource == null || combinedSource.isBlank()) {
            return List.of();
        }
        String text = combinedSource.replace("\r\n", "\n");
        String importPrefix = extractImportPrefix(text);
        int bodyStart = importPrefix.length();
        List<int[]> ranges = findTopLevelRanges(text, bodyStart);
        List<TopLevelType> result = new ArrayList<>();
        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];
            String header = text.substring(start, Math.min(start + 400, end));
            String name = extractName(header);
            if (name == null) {
                continue;
            }
            String fragment = (importPrefix + text.substring(start, end)).trim() + "\n";
            result.add(new TopLevelType(name, fragment));
        }
        if (result.isEmpty() && bodyStart < text.length() && !text.substring(bodyStart).isBlank()) {
            result.add(new TopLevelType("Types", text.trim() + "\n"));
        }
        return result;
    }

    private static String extractImportPrefix(String text) {
        int index = 0;
        while (index < text.length()) {
            int lineEnd = text.indexOf('\n', index);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(index, lineEnd).trim();
            if (line.isEmpty() || line.startsWith("//")) {
                index = lineEnd + 1;
                continue;
            }
            if (line.startsWith("import ") || line.startsWith("package ")) {
                index = lineEnd + 1;
                continue;
            }
            break;
        }
        return text.substring(0, index);
    }

    private static List<int[]> findTopLevelRanges(String fullText, int bodyStart) {
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = TOP_LEVEL_DECL.matcher(fullText);
        while (matcher.find()) {
            int declStart = matcher.start();
            if (declStart < bodyStart) {
                continue;
            }
            String matchedLine = fullText.substring(declStart, fullText.indexOf('\n', declStart) >= 0
                    ? fullText.indexOf('\n', declStart)
                    : fullText.length());
            if (matchedLine.contains(" static class ") || matchedLine.trim().startsWith("public static class ")) {
                continue;
            }
            if (braceDepthBefore(fullText, declStart) != 0) {
                continue;
            }
            int openBrace = fullText.indexOf('{', declStart);
            if (openBrace < 0) {
                continue;
            }
            int closeBrace = indexOfMatchingClosingBrace(fullText, openBrace);
            if (closeBrace > openBrace) {
                ranges.add(new int[] { declStart, closeBrace + 1 });
            }
        }
        return ranges;
    }

    private static int braceDepthBefore(String text, int endExclusive) {
        int depth = 0;
        for (int i = 0; i < endExclusive && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    private static int indexOfMatchingClosingBrace(String text, int openBraceIndex) {
        int depth = 1;
        for (int i = openBraceIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String extractName(String headerSlice) {
        Matcher matcher = TOP_LEVEL_DECL.matcher(headerSlice);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }
}
