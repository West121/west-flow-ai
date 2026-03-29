package com.westflow.processruntime.service.append;

import com.westflow.common.error.ContractException;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.support.CountersignAssigneeResolver;
import com.westflow.processruntime.support.WorkflowFormulaEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicBuildResolutionService {

    private static final String DYNAMIC_RESOLVED_SOURCE_MODE = "westflowDynamicResolvedSourceMode";
    private static final String DYNAMIC_RESOLUTION_PATH = "westflowDynamicResolutionPath";
    private static final String DYNAMIC_TEMPLATE_SOURCE = "westflowDynamicTemplateSource";
    private static final String DYNAMIC_EXECUTION_STRATEGY = "westflowDynamicExecutionStrategy";
    private static final String DYNAMIC_FALLBACK_STRATEGY = "westflowDynamicFallbackStrategy";
    private static final String DYNAMIC_MAX_GENERATED_COUNT = "westflowDynamicMaxGeneratedCount";
    private static final String DYNAMIC_GENERATED_COUNT = "westflowDynamicGeneratedCount";
    private static final String DYNAMIC_GENERATION_TRUNCATED = "westflowDynamicGenerationTruncated";

    private final BusinessProcessBindingService businessProcessBindingService;
    private final CountersignAssigneeResolver countersignAssigneeResolver;

    public DynamicBuildResolutionResult resolveDynamicBuilderItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            int maxGeneratedCount
    ) {
        String sourceMode = normalizeSourceMode(stringValue(config.get("sourceMode")));
        String executionStrategy = normalizeExecutionStrategy(stringValue(config.get("executionStrategy")), sourceMode);
        String fallbackStrategy = normalizeFallbackStrategy(stringValue(config.get("fallbackStrategy")));
        DynamicBuildSelectionResult selection = resolveDynamicBuilderItemsByStrategy(
                buildMode,
                config,
                processVariables,
                sourceMode,
                executionStrategy,
                fallbackStrategy
        );
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Object item : selection.items()) {
            Map<String, Object> map = mapValue(item);
            if (!map.isEmpty()) {
                resolved.add(map);
                continue;
            }
            if ("SUBPROCESS_CALLS".equals(buildMode) && item instanceof String calledProcessKey && !calledProcessKey.isBlank()) {
                resolved.add(Map.of("calledProcessKey", calledProcessKey));
                continue;
            }
            if ("APPROVER_TASKS".equals(buildMode) && item instanceof String userId && !userId.isBlank()) {
                resolved.add(Map.of("userId", userId));
            }
        }
        boolean truncated = resolved.size() > maxGeneratedCount;
        List<Map<String, Object>> finalItems = truncated
                ? List.copyOf(resolved.subList(0, maxGeneratedCount))
                : List.copyOf(resolved);
        return new DynamicBuildResolutionResult(
                sourceMode,
                executionStrategy,
                fallbackStrategy,
                selection.resolvedSourceMode(),
                selection.resolutionPath(),
                selection.templateSource(),
                maxGeneratedCount,
                finalItems.size(),
                truncated,
                finalItems
        );
    }

    public String buildDynamicBuildSkipReason(DynamicBuildResolutionResult resolution) {
        List<String> reasons = new ArrayList<>();
        reasons.add("动态构建未生成附属结构");
        if (resolution != null) {
            reasons.add("resolutionPath=" + resolution.resolutionPath());
            reasons.add("executionStrategy=" + resolution.executionStrategy());
            reasons.add("fallbackStrategy=" + resolution.fallbackStrategy());
            reasons.add("generatedCount=" + resolution.generatedCount());
            reasons.add("maxGeneratedCount=" + resolution.maxGeneratedCount());
        }
        return String.join("；", reasons);
    }

    public String buildDynamicBuildFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return "动态构建执行失败；error=" + message;
    }

    public String normalizeBuildMode(String buildMode) {
        if (buildMode == null || buildMode.isBlank()) {
            return "APPROVER_TASKS";
        }
        String normalized = buildMode.trim().toUpperCase();
        return List.of("APPROVER_TASKS", "SUBPROCESS_CALLS").contains(normalized) ? normalized : "APPROVER_TASKS";
    }

    public String normalizeAppendPolicy(String appendPolicy) {
        if (appendPolicy == null || appendPolicy.isBlank()) {
            return "SERIAL_AFTER_CURRENT";
        }
        String normalized = appendPolicy.trim().toUpperCase();
        return List.of("SERIAL_AFTER_CURRENT", "PARALLEL_WITH_CURRENT", "SERIAL_BEFORE_NEXT").contains(normalized)
                ? normalized
                : "SERIAL_AFTER_CURRENT";
    }

    public int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    public String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private DynamicBuildSelectionResult resolveDynamicBuilderItemsByStrategy(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String sourceMode,
            String executionStrategy,
            String fallbackStrategy
    ) {
        List<?> ruleItems = resolveDynamicBuilderRuleItems(
                buildMode,
                config,
                processVariables,
                stringValue(config.get("ruleExpression")),
                false
        );
        DynamicBuildTemplateSelection templateSelection = resolveDynamicBuilderModelItems(
                buildMode,
                stringValue(config.get("manualTemplateCode")),
                stringValue(config.get("sceneCode")),
                stringValue(config.get("businessType")),
                mapValue(config.get("targets")),
                processVariables
        );
        DynamicBuildSelectionResult preferred = switch (executionStrategy) {
            case "RULE_ONLY" -> selectRuleItems(ruleItems, "RULE_PRIMARY");
            case "TEMPLATE_ONLY" -> selectTemplateItems(templateSelection, "TEMPLATE_PRIMARY");
            case "TEMPLATE_FIRST" -> !templateSelection.items().isEmpty()
                    ? selectTemplateItems(templateSelection, "TEMPLATE_PRIMARY")
                    : selectRuleItems(ruleItems, "RULE_SECONDARY");
            default -> !ruleItems.isEmpty()
                    ? selectRuleItems(ruleItems, "RULE_PRIMARY")
                    : selectTemplateItems(templateSelection, "TEMPLATE_SECONDARY");
        };
        if (!preferred.items().isEmpty()) {
            return preferred;
        }
        List<?> keepCurrentRuleItems = resolveDynamicBuilderRuleItems(
                buildMode,
                config,
                processVariables,
                stringValue(config.get("ruleExpression")),
                true
        );
        return switch (fallbackStrategy) {
            case "USE_RULE" -> selectRuleItems(keepCurrentRuleItems, "FALLBACK_RULE");
            case "USE_TEMPLATE" -> selectTemplateItems(templateSelection, "FALLBACK_TEMPLATE");
            case "SKIP_GENERATION" -> DynamicBuildSelectionResult.skipped(sourceMode);
            default -> switch (executionStrategy) {
                case "RULE_ONLY", "RULE_FIRST" -> selectRuleItems(keepCurrentRuleItems, "KEEP_CURRENT_RULE");
                default -> selectTemplateItems(templateSelection, "KEEP_CURRENT_TEMPLATE");
            };
        };
    }

    private List<?> resolveDynamicBuilderRuleItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String ruleExpression,
            boolean allowFallback
    ) {
        if (ruleExpression == null || ruleExpression.isBlank()) {
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config, processVariables) : List.of();
        }
        Object value = evaluateDynamicBuilderRule(ruleExpression.trim(), processVariables);
        if (value instanceof Boolean booleanValue) {
            if (!booleanValue) {
                return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config, processVariables) : List.of();
            }
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config, processVariables) : List.of();
        }
        if (value instanceof List<?> items) {
            return items;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : items) {
                resolved.add(item);
            }
            return resolved;
        }
        if (value instanceof Object[] items) {
            return List.of(items);
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return "SUBPROCESS_CALLS".equals(buildMode)
                    ? List.of(Map.of("calledProcessKey", stringValue))
                    : List.of(Map.of("userId", stringValue));
        }
        if (value instanceof Map<?, ?> map) {
            return List.of(map);
        }
        if (value == null) {
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config, processVariables) : List.of();
        }
        return List.of(value);
    }

    private Object evaluateDynamicBuilderRule(String ruleExpression, Map<String, Object> processVariables) {
        try {
            return WorkflowFormulaEvaluator.execute(ruleExpression, processVariables);
        } catch (RuntimeException exception) {
            throw new ContractException(
                    "PROCESS.DYNAMIC_BUILD_RULE_FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "动态构建规则执行失败",
                    Map.of(
                            "ruleExpression", ruleExpression,
                            "error", exception.getMessage()
                    )
            );
        }
    }

    private List<?> resolveDynamicBuilderFallbackItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables
    ) {
        if ("SUBPROCESS_CALLS".equals(buildMode)) {
            String calledProcessKey = stringValue(config.get("calledProcessKey"));
            if (calledProcessKey == null) {
                return List.of();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("calledProcessKey", calledProcessKey);
            String calledVersionPolicy = stringValue(config.get("calledVersionPolicy"));
            if (calledVersionPolicy != null) {
                item.put("calledVersionPolicy", calledVersionPolicy);
            }
            Object calledVersion = config.get("calledVersion");
            if (calledVersion instanceof Number || calledVersion instanceof String) {
                item.put("calledVersion", calledVersion);
            }
            return List.of(item);
        }
        Map<String, Object> targets = mapValue(config.get("targets"));
        List<String> userIds = countersignAssigneeResolver.resolve(targets, processVariables);
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (String userId : userIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            resolved.add(item);
        }
        return resolved;
    }

    private List<?> resolveDynamicBuilderManualTemplate(String buildMode, String manualTemplateCode) {
        if (manualTemplateCode == null || manualTemplateCode.isBlank()) {
            return List.of();
        }
        return switch (manualTemplateCode) {
            case "append_leave_audit", "append_manager_review" -> List.of(Map.of("userId", "usr_002"));
            case "append_parallel_review" -> List.of(Map.of("userId", "usr_002"), Map.of("userId", "usr_003"));
            case "append_sub_review" -> List.of(Map.of("calledProcessKey", "oa_sub_review"));
            default -> "SUBPROCESS_CALLS".equals(buildMode)
                    ? List.of(Map.of("calledProcessKey", manualTemplateCode))
                    : List.of(Map.of("userId", manualTemplateCode));
        };
    }

    private DynamicBuildTemplateSelection resolveDynamicBuilderModelItems(
            String buildMode,
            String manualTemplateCode,
            String sceneCode,
            String configuredBusinessType,
            Map<String, Object> targets,
            Map<String, Object> processVariables
    ) {
        if (manualTemplateCode != null && !manualTemplateCode.isBlank()) {
            return new DynamicBuildTemplateSelection(
                    "MANUAL_TEMPLATE",
                    "MANUAL_TEMPLATE",
                    resolveDynamicBuilderManualTemplate(buildMode, manualTemplateCode)
            );
        }
        if (sceneCode == null || sceneCode.isBlank()) {
            return DynamicBuildTemplateSelection.empty();
        }
        List<?> items;
        if ("SUBPROCESS_CALLS".equals(buildMode)) {
            String businessType = configuredBusinessType;
            if (businessType == null || businessType.isBlank()) {
                businessType = stringValue(processVariables.get("westflowBusinessType"));
            }
            String calledProcessKey = businessType == null || businessType.isBlank()
                    ? sceneCode
                    : businessProcessBindingService.resolveProcessKey(businessType, sceneCode);
            items = List.of(Map.of(
                    "calledProcessKey", calledProcessKey,
                    "sceneCode", sceneCode,
                    "businessType", businessType == null ? "" : businessType
            ));
        } else {
            List<String> resolvedUserIds = countersignAssigneeResolver.resolve(targets, processVariables);
            if (resolvedUserIds.isEmpty()) {
                return DynamicBuildTemplateSelection.empty();
            }
            List<Map<String, Object>> resolvedItems = new ArrayList<>();
            for (String userId : resolvedUserIds) {
                resolvedItems.add(Map.of("userId", userId, "sceneCode", sceneCode));
            }
            items = List.copyOf(resolvedItems);
        }
        return new DynamicBuildTemplateSelection(
                "MODEL_DRIVEN",
                "SCENE_CODE",
                items
        );
    }

    private String normalizeSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return "RULE_DRIVEN";
        }
        return switch (sourceMode.trim().toUpperCase()) {
            case "RULE", "RULE_DRIVEN" -> "RULE_DRIVEN";
            case "MANUAL_TEMPLATE" -> "MANUAL_TEMPLATE";
            case "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> "RULE_DRIVEN";
        };
    }

    private String normalizeExecutionStrategy(String executionStrategy, String sourceMode) {
        if (executionStrategy == null || executionStrategy.isBlank()) {
            return "RULE_DRIVEN".equals(sourceMode) ? "RULE_FIRST" : "TEMPLATE_FIRST";
        }
        return switch (executionStrategy.trim().toUpperCase()) {
            case "RULE_ONLY", "TEMPLATE_FIRST", "TEMPLATE_ONLY" -> executionStrategy.trim().toUpperCase();
            default -> "RULE_FIRST";
        };
    }

    private String normalizeFallbackStrategy(String fallbackStrategy) {
        if (fallbackStrategy == null || fallbackStrategy.isBlank()) {
            return "KEEP_CURRENT";
        }
        return switch (fallbackStrategy.trim().toUpperCase()) {
            case "USE_RULE", "USE_TEMPLATE", "SKIP_GENERATION" -> fallbackStrategy.trim().toUpperCase();
            default -> "KEEP_CURRENT";
        };
    }

    private DynamicBuildSelectionResult selectRuleItems(List<?> ruleItems, String resolutionPath) {
        return new DynamicBuildSelectionResult("RULE_DRIVEN", resolutionPath, null, ruleItems == null ? List.of() : ruleItems);
    }

    private DynamicBuildSelectionResult selectTemplateItems(DynamicBuildTemplateSelection selection, String resolutionPath) {
        if (selection == null) {
            return new DynamicBuildSelectionResult("MODEL_DRIVEN", resolutionPath, null, List.of());
        }
        return new DynamicBuildSelectionResult(
                selection.resolvedSourceMode(),
                resolutionPath,
                selection.templateSource(),
                selection.items()
        );
    }

    public record DynamicBuildResolutionResult(
            String sourceMode,
            String executionStrategy,
            String fallbackStrategy,
            String resolvedSourceMode,
            String resolutionPath,
            String templateSource,
            int maxGeneratedCount,
            int generatedCount,
            boolean generationTruncated,
            List<Map<String, Object>> items
    ) {
        public Map<String, Object> runtimeMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(DYNAMIC_RESOLVED_SOURCE_MODE, resolvedSourceMode);
            metadata.put(DYNAMIC_RESOLUTION_PATH, resolutionPath);
            metadata.put(DYNAMIC_EXECUTION_STRATEGY, executionStrategy);
            metadata.put(DYNAMIC_FALLBACK_STRATEGY, fallbackStrategy);
            metadata.put(DYNAMIC_MAX_GENERATED_COUNT, maxGeneratedCount);
            metadata.put(DYNAMIC_GENERATED_COUNT, generatedCount);
            metadata.put(DYNAMIC_GENERATION_TRUNCATED, generationTruncated);
            if (templateSource != null && !templateSource.isBlank()) {
                metadata.put(DYNAMIC_TEMPLATE_SOURCE, templateSource);
            }
            return metadata;
        }
    }

    private record DynamicBuildSelectionResult(
            String resolvedSourceMode,
            String resolutionPath,
            String templateSource,
            List<?> items
    ) {
        private static DynamicBuildSelectionResult skipped(String sourceMode) {
            return new DynamicBuildSelectionResult(sourceMode, "SKIPPED", null, List.of());
        }
    }

    private record DynamicBuildTemplateSelection(
            String resolvedSourceMode,
            String templateSource,
            List<?> items
    ) {
        private static DynamicBuildTemplateSelection empty() {
            return new DynamicBuildTemplateSelection("MODEL_DRIVEN", null, List.of());
        }
    }
}
