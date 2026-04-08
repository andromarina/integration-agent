package org.integration.agent.tools.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds Java DTO source from an OpenAPI 3 document and a chosen operation's success JSON response schema.
 */
public final class OpenApiResponseDtoGenerator {

    private OpenApiResponseDtoGenerator() {
    }

    /**
     * @param fallbackRootClassName e.g. {@code GetUsersResponse} when schema is inline object
     */
    public static Optional<Result> tryGenerate(
            String openApiSpec,
            String requestPath,
            String methodUpper,
            String fallbackRootClassName
    ) {
        if (openApiSpec == null || openApiSpec.isBlank()) {
            return Optional.empty();
        }
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(openApiSpec.trim(), null, null);
        OpenAPI openApi = parseResult.getOpenAPI();
        if (openApi == null) {
            return Optional.empty();
        }
        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        String matchedPath = findMatchingPathKey(openApi, normalizedPath);
        if (matchedPath == null) {
            return Optional.empty();
        }
        PathItem pathItem = openApi.getPaths().get(matchedPath);
        if (pathItem == null) {
            return Optional.empty();
        }
        Operation operation = operationForMethod(pathItem, methodUpper);
        if (operation == null) {
            return Optional.empty();
        }
        Schema<?> responseSchema = extractSuccessJsonSchema(openApi, operation);
        if (responseSchema == null) {
            return Optional.empty();
        }
        Schema<?> resolved = resolveSchema(openApi, responseSchema);
        if (resolved == null) {
            return Optional.empty();
        }
        GenerationContext context = new GenerationContext(openApi, fallbackRootClassName);
        if (isArraySchema(resolved)) {
            Schema<?> items = resolved.getItems() != null
                    ? resolved.getItems()
                    : (resolved instanceof ArraySchema arraySchema ? arraySchema.getItems() : null);
            Schema<?> itemResolved = items != null ? resolveSchema(openApi, items) : null;
            if (itemResolved == null) {
                return Optional.empty();
            }
            String elementName = inferClassNameFromSchema(openApi, itemResolved, fallbackRootClassName + "Item");
            String primary = emitPojo(context, elementName, itemResolved, new LinkedHashSet<>());
            String combined = buildCombinedSource(context, elementName, primary);
            return Optional.of(new Result(combined, elementName, true, elementName));
        }
        String rootName = inferClassNameFromSchema(openApi, resolved, fallbackRootClassName);
        String primary = emitPojo(context, rootName, resolved, new LinkedHashSet<>());
        String combined = buildCombinedSource(context, rootName, primary);
        return Optional.of(new Result(combined, rootName, false, null));
    }

    public record Result(
            String javaSource,
            String responseClassName,
            boolean rootIsJsonArray,
            String arrayElementClassName
    ) {
    }

    private static final class GenerationContext {
        private final OpenAPI openApi;
        private final String fallbackRootClassName;
        private final Map<String, String> componentBodies = new LinkedHashMap<>();

        private GenerationContext(OpenAPI openApi, String fallbackRootClassName) {
            this.openApi = openApi;
            this.fallbackRootClassName = fallbackRootClassName;
        }
    }

    private static String emitPojo(GenerationContext context, String className, Schema<?> schema, Set<String> stack) {
        if (!stack.add(className)) {
            return "// Cyclic reference to " + className + " — replace with JsonNode or split DTOs\n";
        }
        try {
            Schema<?> resolved = resolveSchema(context.openApi, schema);
            if (resolved == null) {
                return "public class " + className + " {\n}\n";
            }
            if (resolved.get$ref() != null) {
                String refName = refToSchemaName(resolved.get$ref());
                if (refName != null && context.componentBodies.containsKey(refName)) {
                    return context.componentBodies.get(refName);
                }
            }
            Map<String, Schema> properties = resolved.getProperties();
            if (properties == null || properties.isEmpty()) {
                if (resolved.get$ref() != null) {
                    Schema<?> target = resolveRef(context.openApi, resolved.get$ref());
                    if (target != null && target != resolved) {
                        return emitPojo(context, className, target, stack);
                    }
                }
                return "public class " + className + " {\n}\n";
            }
            List<String> lines = new ArrayList<>();
            lines.add("public class " + className + " {");
            for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                String jsonName = entry.getKey();
                Schema propSchema = entry.getValue();
                String fieldName = toJavaFieldName(jsonName);
                String typeName = mapSchemaTypeToJava(context, fieldName, propSchema, className, stack);
                lines.add("    private " + typeName + " " + fieldName + ";");
            }
            lines.add("");
            for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                String jsonName = entry.getKey();
                Schema propSchema = entry.getValue();
                String fieldName = toJavaFieldName(jsonName);
                String typeName = mapSchemaTypeToJava(context, fieldName, propSchema, className, stack);
                String capitalized = capitalize(fieldName);
                lines.add("    public " + typeName + " get" + capitalized + "() {");
                lines.add("        return " + fieldName + ";");
                lines.add("    }");
                lines.add("");
                lines.add("    public void set" + capitalized + "(" + typeName + " " + fieldName + ") {");
                lines.add("        this." + fieldName + " = " + fieldName + ";");
                lines.add("    }");
                lines.add("");
            }
            lines.add("}");
            return String.join("\n", lines);
        } finally {
            stack.remove(className);
        }
    }

    private static String mapSchemaTypeToJava(
            GenerationContext context,
            String fieldName,
            Schema<?> schema,
            String enclosingClass,
            Set<String> stack
    ) {
        Schema<?> resolved = resolveSchema(context.openApi, schema);
        if (resolved == null) {
            return "Object";
        }
        if (isArraySchema(resolved)) {
            Schema<?> items = resolved.getItems() != null
                    ? resolved.getItems()
                    : (resolved instanceof ArraySchema arraySchema ? arraySchema.getItems() : null);
            Schema<?> itemResolved = items != null ? resolveSchema(context.openApi, items) : null;
            if (itemResolved == null) {
                return "java.util.List<Object>";
            }
            if (itemResolved.get$ref() != null) {
                String itemClass = refToSchemaName(itemResolved.get$ref());
                if (itemClass == null) {
                    return "java.util.List<Object>";
                }
                ensureComponentClass(context, itemClass, itemResolved, stack);
                return "java.util.List<" + itemClass + ">";
            }
            if (hasInlineObjectProperties(itemResolved)) {
                String nested = enclosingClass + capitalize(fieldName) + "Item";
                String nestedBody = emitPojo(context, nested, itemResolved, stack);
                context.componentBodies.putIfAbsent(nested, nestedBody);
                return "java.util.List<" + nested + ">";
            }
            String primitive = mapScalarOrJsonObject(itemResolved);
            return "java.util.List<" + primitive + ">";
        }
        if (resolved.get$ref() != null) {
            String refName = refToSchemaName(resolved.get$ref());
            if (refName == null) {
                return "Object";
            }
            ensureComponentClass(context, refName, resolved, stack);
            return refName;
        }
        if (hasInlineObjectProperties(resolved)) {
            String nested = enclosingClass + capitalize(fieldName) + "Value";
            String nestedBody = emitPojo(context, nested, resolved, stack);
            context.componentBodies.putIfAbsent(nested, nestedBody);
            return nested;
        }
        return mapScalarOrJsonObject(resolved);
    }

    private static void ensureComponentClass(
            GenerationContext context,
            String className,
            Schema<?> refOrResolved,
            Set<String> stack
    ) {
        if (context.componentBodies.containsKey(className)) {
            return;
        }
        Schema<?> target = resolveSchema(context.openApi, refOrResolved);
        if (target == null) {
            return;
        }
        String body = emitPojo(context, className, target, stack);
        context.componentBodies.put(className, body);
    }

    private static String buildCombinedSource(GenerationContext context, String primaryClassName, String primaryBody) {
        List<String> parts = new ArrayList<>();
        boolean needsList = false;
        for (String body : context.componentBodies.values()) {
            if (body != null && body.contains("List<")) {
                needsList = true;
                break;
            }
        }
        if (primaryBody != null && primaryBody.contains("List<")) {
            needsList = true;
        }
        if (needsList) {
            parts.add("import java.util.List;");
            parts.add("");
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : context.componentBodies.entrySet()) {
            if (!entry.getKey().equals(primaryClassName)) {
                ordered.add(entry.getValue());
            }
        }
        ordered.add(primaryBody);
        for (String block : ordered) {
            if (block != null && !block.isBlank()) {
                parts.add(block.trim());
                parts.add("");
            }
        }
        return String.join("\n", parts).trim();
    }

    private static Schema<?> extractSuccessJsonSchema(OpenAPI openApi, Operation operation) {
        if (operation.getResponses() == null) {
            return null;
        }
        io.swagger.v3.oas.models.responses.ApiResponse apiResponse =
                operation.getResponses().get("200");
        if (apiResponse == null) {
            apiResponse = operation.getResponses().get("201");
        }
        if (apiResponse == null) {
            apiResponse = operation.getResponses().get("default");
        }
        if (apiResponse == null && !operation.getResponses().isEmpty()) {
            apiResponse = operation.getResponses().values().iterator().next();
        }
        if (apiResponse == null) {
            return null;
        }
        Content content = apiResponse.getContent();
        if (content == null) {
            return null;
        }
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (mediaType == null) {
            return null;
        }
        return mediaType.getSchema();
    }

    private static Operation operationForMethod(PathItem pathItem, String methodUpper) {
        return switch (methodUpper) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "PATCH" -> pathItem.getPatch();
            case "DELETE" -> pathItem.getDelete();
            case "HEAD" -> pathItem.getHead();
            case "OPTIONS" -> pathItem.getOptions();
            case "TRACE" -> pathItem.getTrace();
            default -> null;
        };
    }

    private static String findMatchingPathKey(OpenAPI openApi, String normalizedPath) {
        if (openApi.getPaths() == null) {
            return null;
        }
        if (openApi.getPaths().containsKey(normalizedPath)) {
            return normalizedPath;
        }
        for (String key : openApi.getPaths().keySet()) {
            if (pathMatchesTemplate(key, normalizedPath)) {
                return key;
            }
        }
        return null;
    }

    private static boolean pathMatchesTemplate(String template, String actual) {
        String[] templateSegments = template.split("/");
        String[] actualSegments = actual.split("/");
        if (templateSegments.length != actualSegments.length) {
            return false;
        }
        for (int i = 0; i < templateSegments.length; i++) {
            String templateSegment = templateSegments[i];
            String actualSegment = actualSegments[i];
            if (templateSegment.startsWith("{") && templateSegment.endsWith("}")) {
                continue;
            }
            if (!templateSegment.equals(actualSegment)) {
                return false;
            }
        }
        return true;
    }

    private static Schema<?> resolveSchema(OpenAPI openApi, Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null) {
            Schema<?> resolved = resolveRef(openApi, schema.get$ref());
            return resolved != null ? resolved : schema;
        }
        if (schema instanceof ComposedSchema composedSchema && composedSchema.getAllOf() != null
                && !composedSchema.getAllOf().isEmpty()) {
            return resolveSchema(openApi, composedSchema.getAllOf().get(0));
        }
        return schema;
    }

    private static Schema<?> resolveRef(OpenAPI openApi, String ref) {
        String name = refToSchemaName(ref);
        if (name == null || openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return null;
        }
        return openApi.getComponents().getSchemas().get(name);
    }

    private static String refToSchemaName(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        int idx = ref.lastIndexOf('/');
        if (idx < 0 || idx >= ref.length() - 1) {
            return null;
        }
        return ref.substring(idx + 1);
    }

    private static boolean isArraySchema(Schema<?> schema) {
        if (schema == null) {
            return false;
        }
        if (schema instanceof ArraySchema) {
            return true;
        }
        return "array".equals(schema.getType());
    }

    private static boolean hasInlineObjectProperties(Schema<?> schema) {
        return schema.getProperties() != null && !schema.getProperties().isEmpty();
    }

    private static String mapScalarOrJsonObject(Schema<?> schema) {
        String type = schema.getType();
        String format = schema.getFormat();
        if (type == null) {
            return "Object";
        }
        return switch (type) {
            case "integer" -> "int64".equals(format) ? "Long" : "Integer";
            case "number" -> "Double";
            case "boolean" -> "Boolean";
            case "string" -> "String";
            case "object" -> "Object";
            default -> "String";
        };
    }

    private static String inferClassNameFromSchema(OpenAPI openApi, Schema<?> schema, String fallback) {
        if (schema != null && schema.get$ref() != null) {
            String fromRef = refToSchemaName(schema.get$ref());
            if (fromRef != null && !fromRef.isBlank()) {
                return sanitizeClassName(fromRef);
            }
        }
        return fallback;
    }

    private static String sanitizeClassName(String name) {
        StringBuilder result = new StringBuilder();
        for (char character : name.toCharArray()) {
            if (Character.isJavaIdentifierPart(character)) {
                result.append(character);
            }
        }
        if (result.isEmpty()) {
            return "Model";
        }
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, 'X');
        }
        return result.toString();
    }

    private static String toJavaFieldName(String jsonPropertyName) {
        if (jsonPropertyName == null || jsonPropertyName.isEmpty()) {
            return "field";
        }
        String[] segments = jsonPropertyName.split("[^a-zA-Z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            if (i == 0) {
                builder.append(Character.toLowerCase(segment.charAt(0)));
                if (segment.length() > 1) {
                    builder.append(segment.substring(1));
                }
            } else {
                builder.append(Character.toUpperCase(segment.charAt(0)));
                if (segment.length() > 1) {
                    builder.append(segment.substring(1));
                }
            }
        }
        if (builder.isEmpty()) {
            return "field";
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "Value";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

}
