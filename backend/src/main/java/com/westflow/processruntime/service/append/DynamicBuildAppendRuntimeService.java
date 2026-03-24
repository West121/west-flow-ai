package com.westflow.processruntime.service.append;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
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

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final ProcessLinkService processLinkService;

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
        List<Map<String, Object>> generatedItems = resolveDynamicBuilderItems(buildMode, config, processVariables, maxGeneratedCount);
        if (generatedItems.isEmpty()) {
            return;
        }
        String operatorUserId = resolveRuntimeOperatorUserId(processVariables);
        if ("APPROVER_TASKS".equals(buildMode)) {
            createDynamicBuildTasks(processInstanceId, sourceNodeId, node.name(), appendPolicy, generatedItems, operatorUserId);
            return;
        }
        createDynamicBuildSubprocesses(
                processInstanceId,
                sourceNodeId,
                node.name(),
                parentDefinition,
                appendPolicy,
                processVariables,
                generatedItems,
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
            List<Map<String, Object>> generatedItems,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String processDefinitionId = activeFlowableDefinitionId(processInstanceId);
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < generatedItems.size(); index++) {
            Map<String, Object> item = generatedItems.get(index);
            String targetUserId = stringValue(item.get("userId"));
            if (targetUserId == null) {
                targetUserId = stringValue(item.get("targetUserId"));
            }
            if (targetUserId == null || targetUserId.isBlank()) {
                continue;
            }
            String generatedNodeId = sourceNodeId + "__dynamic_task_" + (index + 1);
            Task generatedTask = flowableTaskActionService.createAdhocTask(
                    processInstanceId,
                    processDefinitionId,
                    generatedNodeId,
                    nodeName + " / 动态生成审批",
                    "APPEND",
                    targetUserId,
                    List.of(),
                    null,
                    new LinkedHashMap<>(Map.of(
                            "westflowTaskKind", "APPEND",
                            "westflowAppendType", "TASK",
                            "westflowAppendPolicy", appendPolicy,
                            "westflowTriggerMode", "DYNAMIC_BUILD",
                            "westflowSourceTaskId", sourceStructureId,
                            "westflowSourceNodeId", sourceNodeId,
                            "westflowOperatorUserId", operatorUserId
                    ))
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
            List<Map<String, Object>> generatedItems,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String parentBusinessKey = stringValue(processVariables.get("westflowBusinessKey"));
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < generatedItems.size(); index++) {
            Map<String, Object> item = generatedItems.get(index);
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

    private List<Map<String, Object>> resolveDynamicBuilderItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            int maxGeneratedCount
    ) {
        List<?> rawItems;
        String sourceMode = normalizeSourceMode(stringValue(config.get("sourceMode")));
        if ("MODEL_DRIVEN".equals(sourceMode)) {
            rawItems = resolveDynamicBuilderModelItems(
                    buildMode,
                    stringValue(config.get("manualTemplateCode")),
                    stringValue(config.get("sceneCode"))
            );
        } else {
            rawItems = resolveDynamicBuilderRuleItems(buildMode, config, processVariables, stringValue(config.get("ruleExpression")));
        }
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Object item : rawItems) {
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
        return resolved;
    }

    private List<?> resolveDynamicBuilderRuleItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String ruleExpression
    ) {
        if (ruleExpression == null || ruleExpression.isBlank()) {
            return resolveDynamicBuilderFallbackItems(buildMode, config);
        }
        Object value = evaluateDynamicBuilderRule(ruleExpression.trim(), processVariables);
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? resolveDynamicBuilderFallbackItems(buildMode, config) : List.of();
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
            return resolveDynamicBuilderFallbackItems(buildMode, config);
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

    private List<?> resolveDynamicBuilderFallbackItems(String buildMode, Map<String, Object> config) {
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
        List<String> userIds = stringListValue(targets.get("userIds"));
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

    private List<?> resolveDynamicBuilderModelItems(String buildMode, String manualTemplateCode, String sceneCode) {
        if (manualTemplateCode != null && !manualTemplateCode.isBlank()) {
            return resolveDynamicBuilderManualTemplate(buildMode, manualTemplateCode);
        }
        if (sceneCode == null || sceneCode.isBlank()) {
            return List.of();
        }
        return "SUBPROCESS_CALLS".equals(buildMode)
                ? List.of(Map.of("calledProcessKey", sceneCode))
                : List.of(Map.of("userId", sceneCode));
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
            case "MANUAL_TEMPLATE", "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> "RULE_DRIVEN";
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
}
