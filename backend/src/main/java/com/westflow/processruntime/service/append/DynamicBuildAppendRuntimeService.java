package com.westflow.processruntime.service.append;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.CountersignAssigneeResolver;
import com.westflow.processruntime.service.FlowableTaskActionService;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.processruntime.service.RuntimeAppendLinkService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 追加与动态构建的独立运行态服务，负责把 dynamic-builder 命中的结果真正落成附属任务/附属子流程。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class DynamicBuildAppendRuntimeService {

    private static final Pattern SIMPLE_COMPARISON_PATTERN = Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    private static final String DYNAMIC_RESOLVED_SOURCE_MODE = "westflowDynamicResolvedSourceMode";
    private static final String DYNAMIC_RESOLUTION_PATH = "westflowDynamicResolutionPath";
    private static final String DYNAMIC_TEMPLATE_SOURCE = "westflowDynamicTemplateSource";
    private static final String DYNAMIC_EXECUTION_STRATEGY = "westflowDynamicExecutionStrategy";
    private static final String DYNAMIC_FALLBACK_STRATEGY = "westflowDynamicFallbackStrategy";

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final ProcessLinkService processLinkService;
    private final BusinessProcessBindingService businessProcessBindingService;
    private final CountersignAssigneeResolver countersignAssigneeResolver;

    /**
     * 执行 dynamic-builder 节点，按配置生成附属任务或附属子流程。
     */
    public void executeDynamicBuilder(String processInstanceId, String sourceNodeId) {
        if (processInstanceId == null || processInstanceId.isBlank() || sourceNodeId == null || sourceNodeId.isBlank()) {
            return;
        }
        Map<String, Object> processVariables = runtimeVariables(processInstanceId);
        PublishedProcessDefinition parentDefinition = resolveParentDefinition(processInstanceId, processVariables);
        ProcessDslPayload.Node node = parentDefinition.dsl().nodes().stream()
                .filter(item -> sourceNodeId.equals(item.id()))
                .findFirst()
                .orElse(null);
        if (node == null || !"dynamic-builder".equals(node.type())) {
            return;
        }
        Map<String, Object> config = mapValue(node.config());
        String buildMode = normalizeBuildMode(stringValue(config.get("buildMode")));
        if (!List.of("APPROVER_TASKS", "SUBPROCESS_CALLS").contains(buildMode)) {
            return;
        }
        int maxGeneratedCount = integerValue(config.get("maxGeneratedCount"));
        if (maxGeneratedCount <= 0) {
            return;
        }
        String appendPolicy = normalizeAppendPolicy(stringValue(config.get("appendPolicy")));
        DynamicBuildResolutionResult resolution = resolveDynamicBuilderItems(buildMode, config, processVariables, maxGeneratedCount);
        if (resolution.items().isEmpty()) {
            return;
        }
        String operatorUserId = resolveRuntimeOperatorUserId(processVariables);
        if ("APPROVER_TASKS".equals(buildMode)) {
            createDynamicBuildTasks(processInstanceId, sourceNodeId, node.name(), appendPolicy, resolution, operatorUserId);
            return;
        }
        createDynamicBuildSubprocesses(
                processInstanceId,
                sourceNodeId,
                node.name(),
                parentDefinition,
                appendPolicy,
                processVariables,
                resolution,
                operatorUserId
        );
    }

    private PublishedProcessDefinition resolveParentDefinition(String processInstanceId, Map<String, Object> processVariables) {
        String platformDefinitionId = stringValue(processVariables.get("westflowProcessDefinitionId"));
        if (platformDefinitionId != null && !platformDefinitionId.isBlank()) {
            return processDefinitionService.getById(platformDefinitionId);
        }
        String processKey = stringValue(processVariables.get("westflowProcessKey"));
        if (processKey != null && !processKey.isBlank()) {
            return processDefinitionService.getLatestByProcessKey(processKey);
        }
        return processDefinitionService.getById(activeFlowableDefinitionId(processInstanceId));
    }

    private void createDynamicBuildTasks(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            String appendPolicy,
            DynamicBuildResolutionResult resolution,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String processDefinitionId = activeFlowableDefinitionId(processInstanceId);
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < resolution.items().size(); index++) {
            Map<String, Object> item = resolution.items().get(index);
            String targetUserId = stringValue(item.get("userId"));
            if (targetUserId == null) {
                targetUserId = stringValue(item.get("targetUserId"));
            }
            if (targetUserId == null || targetUserId.isBlank()) {
                continue;
            }
            String generatedNodeId = sourceNodeId + "__dynamic_task_" + (index + 1);
            Map<String, Object> localVariables = new LinkedHashMap<>(Map.of(
                    "westflowTaskKind", "APPEND",
                    "westflowAppendType", "TASK",
                    "westflowAppendPolicy", appendPolicy,
                    "westflowTriggerMode", "DYNAMIC_BUILD",
                    "westflowSourceTaskId", sourceStructureId,
                    "westflowSourceNodeId", sourceNodeId,
                    "westflowOperatorUserId", operatorUserId
            ));
            localVariables.putAll(dynamicBuildRuntimeMetadata(resolution));
            Task generatedTask = flowableTaskActionService.createAdhocTask(
                    processInstanceId,
                    processDefinitionId,
                    generatedNodeId,
                    nodeName + " / 动态生成审批",
                    "APPEND",
                    targetUserId,
                    List.of(),
                    null,
                    localVariables
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "TASK",
                    "ADHOC_TASK",
                    appendPolicy,
                    generatedTask.getId(),
                    null,
                    targetUserId,
                    null,
                    null,
                    null,
                    null,
                    "USER",
                    null,
                    null,
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
        }
    }

    private void createDynamicBuildSubprocesses(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            PublishedProcessDefinition parentDefinition,
            String appendPolicy,
            Map<String, Object> processVariables,
            DynamicBuildResolutionResult resolution,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String parentBusinessKey = stringValue(processVariables.get("westflowBusinessKey"));
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < resolution.items().size(); index++) {
            Map<String, Object> item = resolution.items().get(index);
            String calledProcessKey = stringValue(item.get("calledProcessKey"));
            if (calledProcessKey == null || calledProcessKey.isBlank()) {
                continue;
            }
            String versionPolicy = Optional.ofNullable(stringValue(item.get("calledVersionPolicy"))).orElse("LATEST_PUBLISHED");
            Integer calledVersion = item.get("calledVersion") instanceof Number number ? number.intValue() : null;
            PublishedProcessDefinition childDefinition = "FIXED_VERSION".equals(versionPolicy)
                    ? processDefinitionService.getPublishedByProcessKeyAndVersion(calledProcessKey, calledVersion)
                    : processDefinitionService.getLatestByProcessKey(calledProcessKey);

            Map<String, Object> childVariables = new LinkedHashMap<>();
            childVariables.put("westflowProcessDefinitionId", childDefinition.processDefinitionId());
            childVariables.put("westflowProcessKey", childDefinition.processKey());
            childVariables.put("westflowProcessName", childDefinition.processName());
            childVariables.put("westflowBusinessType", stringValue(processVariables.get("westflowBusinessType")));
            childVariables.put("westflowBusinessKey", parentBusinessKey);
            childVariables.put("westflowInitiatorUserId", stringValue(processVariables.get("westflowInitiatorUserId")));
            childVariables.put("westflowParentInstanceId", processInstanceId);
            childVariables.put("westflowRootInstanceId", rootInstanceId);
            childVariables.put("westflowAppendType", "SUBPROCESS");
            childVariables.put("westflowAppendPolicy", appendPolicy);
            childVariables.put("westflowAppendTriggerMode", "DYNAMIC_BUILD");
            childVariables.put("westflowAppendSourceTaskId", sourceStructureId);
            childVariables.put("westflowAppendSourceNodeId", sourceNodeId);
            childVariables.put("westflowAppendOperatorUserId", operatorUserId);
            childVariables.putAll(dynamicBuildRuntimeMetadata(resolution));
            Object appendVariables = item.get("appendVariables");
            if (appendVariables instanceof Map<?, ?> appendVariablesMap) {
                appendVariablesMap.forEach((key, value) -> childVariables.put(String.valueOf(key), value));
            }
            ProcessInstance childInstance = flowableEngineFacade.runtimeService().startProcessInstanceByKey(
                    childDefinition.processKey(),
                    buildGeneratedSubprocessRuntimeBusinessKey(parentBusinessKey, sourceNodeId, index),
                    childVariables
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "SUBPROCESS",
                    "ADHOC_SUBPROCESS",
                    appendPolicy,
                    null,
                    childInstance.getProcessInstanceId(),
                    null,
                    childDefinition.processKey(),
                    childDefinition.processDefinitionId(),
                    versionPolicy,
                    calledVersion,
                    "PROCESS_KEY",
                    stringValue(processVariables.get("westflowBusinessType")),
                    stringValue(item.get("sceneCode")),
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
        }
    }

    private DynamicBuildResolutionResult resolveDynamicBuilderItems(
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
            if (resolved.size() >= maxGeneratedCount) {
                break;
            }
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
        return new DynamicBuildResolutionResult(
                sourceMode,
                executionStrategy,
                fallbackStrategy,
                selection.resolvedSourceMode(),
                selection.resolutionPath(),
                selection.templateSource(),
                List.copyOf(resolved)
        );
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
            String normalizedExpression = ruleExpression;
            if (normalizedExpression.startsWith("${") && normalizedExpression.endsWith("}")) {
                normalizedExpression = normalizedExpression.substring(2, normalizedExpression.length() - 1).trim();
            }
            if (processVariables.containsKey(normalizedExpression)) {
                return processVariables.get(normalizedExpression);
            }
            Matcher matcher = SIMPLE_COMPARISON_PATTERN.matcher(normalizedExpression);
            if (matcher.matches()) {
                String variableName = matcher.group(1);
                String operator = matcher.group(2);
                String rightOperand = matcher.group(3).trim();
                Object leftValue = processVariables.get(variableName);
                if (leftValue == null) {
                    return false;
                }
                return compareDynamicBuilderValues(leftValue, operator, rightOperand);
            }
            if ("true".equalsIgnoreCase(normalizedExpression)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalizedExpression)) {
                return false;
            }
            return processVariables.get(normalizedExpression);
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

    private boolean compareDynamicBuilderValues(Object leftValue, String operator, String rightOperand) {
        if (leftValue instanceof Number numberValue && isNumeric(rightOperand)) {
            BigDecimal leftNumber = new BigDecimal(String.valueOf(numberValue));
            BigDecimal rightNumber = new BigDecimal(rightOperand);
            return switch (operator) {
                case ">" -> leftNumber.compareTo(rightNumber) > 0;
                case ">=" -> leftNumber.compareTo(rightNumber) >= 0;
                case "<" -> leftNumber.compareTo(rightNumber) < 0;
                case "<=" -> leftNumber.compareTo(rightNumber) <= 0;
                case "==" -> leftNumber.compareTo(rightNumber) == 0;
                case "!=" -> leftNumber.compareTo(rightNumber) != 0;
                default -> false;
            };
        }
        String leftText = String.valueOf(leftValue);
        String rightText = normalizeQuotedText(rightOperand);
        return switch (operator) {
            case "==" -> leftText.equals(rightText);
            case "!=" -> !leftText.equals(rightText);
            case ">" -> leftText.compareTo(rightText) > 0;
            case ">=" -> leftText.compareTo(rightText) >= 0;
            case "<" -> leftText.compareTo(rightText) < 0;
            case "<=" -> leftText.compareTo(rightText) <= 0;
            default -> false;
        };
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(normalizeQuotedText(value));
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalizeQuotedText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
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

    private String buildGeneratedSubprocessRuntimeBusinessKey(String parentBusinessKey, String sourceNodeId, int index) {
        String normalizedParentBusinessKey = parentBusinessKey == null || parentBusinessKey.isBlank()
                ? "instance"
                : parentBusinessKey;
        return normalizedParentBusinessKey + "::dynamic-build::" + sourceNodeId + "::" + (index + 1);
    }

    private String buildDynamicBuildSourceTaskId(String processInstanceId, String sourceNodeId) {
        return processInstanceId + "::dynamic-build::" + sourceNodeId;
    }

    private String resolveRuntimeOperatorUserId(Map<String, Object> processVariables) {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        String initiatorUserId = stringValue(processVariables.get("westflowInitiatorUserId"));
        return initiatorUserId == null ? "SYSTEM" : initiatorUserId;
    }

    private String activeFlowableDefinitionId(String processInstanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionId();
        }
        throw new ContractException(
                "PROCESS.INSTANCE_NOT_RUNNING",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "流程实例未运行",
                Map.of("processInstanceId", processInstanceId)
        );
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    private String resolveRuntimeTreeRootInstanceId(String instanceId) {
        ProcessLinkRecord processLink = processLinkService.getByChildInstanceId(instanceId);
        if (processLink != null) {
            return processLink.rootInstanceId();
        }
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetInstanceId(instanceId);
        if (appendLink != null && appendLink.rootInstanceId() != null && !appendLink.rootInstanceId().isBlank()) {
            return appendLink.rootInstanceId();
        }
        return instanceId;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String normalizeBuildMode(String buildMode) {
        if (buildMode == null || buildMode.isBlank()) {
            return "APPROVER_TASKS";
        }
        String normalized = buildMode.trim().toUpperCase();
        return List.of("APPROVER_TASKS", "SUBPROCESS_CALLS").contains(normalized) ? normalized : "APPROVER_TASKS";
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

    private String normalizeAppendPolicy(String appendPolicy) {
        if (appendPolicy == null || appendPolicy.isBlank()) {
            return "SERIAL_AFTER_CURRENT";
        }
        String normalized = appendPolicy.trim().toUpperCase();
        return List.of("SERIAL_AFTER_CURRENT", "PARALLEL_WITH_CURRENT", "SERIAL_BEFORE_NEXT").contains(normalized)
                ? normalized
                : "SERIAL_AFTER_CURRENT";
    }

    private int integerValue(Object value) {
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

    private Map<String, Object> dynamicBuildRuntimeMetadata(DynamicBuildResolutionResult resolution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DYNAMIC_RESOLVED_SOURCE_MODE, resolution.resolvedSourceMode());
        metadata.put(DYNAMIC_RESOLUTION_PATH, resolution.resolutionPath());
        metadata.put(DYNAMIC_EXECUTION_STRATEGY, resolution.executionStrategy());
        metadata.put(DYNAMIC_FALLBACK_STRATEGY, resolution.fallbackStrategy());
        if (resolution.templateSource() != null && !resolution.templateSource().isBlank()) {
            metadata.put(DYNAMIC_TEMPLATE_SOURCE, resolution.templateSource());
        }
        return metadata;
    }

    private record DynamicBuildResolutionResult(
            String sourceMode,
            String executionStrategy,
            String fallbackStrategy,
            String resolvedSourceMode,
            String resolutionPath,
            String templateSource,
            List<Map<String, Object>> items
    ) {
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
