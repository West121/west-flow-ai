package com.westflow.processdef.service;

import com.westflow.common.error.ContractException;
import com.westflow.processdef.model.ProcessDslPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 校验流程 DSL 的结构、引用关系和节点配置完整性。
public class ProcessDslValidator {

    private static final Set<String> COLLABORATION_NODE_TYPES = Set.of(
            "cc",
            "supervise",
            "meeting",
            "read",
            "circulate"
    );

    private static final List<String> SUPPORTED_TIMEOUT_ACTIONS = List.of("APPROVE", "REJECT");
    private static final List<String> SUPPORTED_REMINDER_CHANNELS = List.of(
            "IN_APP",
            "EMAIL",
            "WEBHOOK",
            "SMS",
            "WECHAT",
            "DINGTALK"
    );
    private static final List<String> SUPPORTED_ESCALATION_TARGET_MODES = List.of("USER", "ROLE");
    private static final List<String> SUPPORTED_SCHEDULE_TYPES = List.of("ABSOLUTE_TIME", "RELATIVE_TO_ARRIVAL");
    private static final List<String> SUPPORTED_TRIGGER_MODES = List.of("IMMEDIATE", "SCHEDULED");
    private static final List<String> SUPPORTED_SUBPROCESS_VERSION_POLICIES = List.of("LATEST_PUBLISHED", "FIXED_VERSION");
    private static final List<String> SUPPORTED_SUBPROCESS_BUSINESS_BINDING_MODES = List.of("INHERIT_PARENT", "OVERRIDE");
    private static final List<String> SUPPORTED_SUBPROCESS_TERMINATE_POLICIES = List.of(
            "TERMINATE_SUBPROCESS_ONLY",
            "TERMINATE_PARENT_AND_SUBPROCESS"
    );
    private static final List<String> SUPPORTED_SUBPROCESS_CHILD_FINISH_POLICIES = List.of(
            "RETURN_TO_PARENT",
            "TERMINATE_PARENT"
    );
    private static final List<String> SUPPORTED_SUBPROCESS_CALL_SCOPES = List.of(
            "CHILD_ONLY",
            "CHILD_AND_DESCENDANTS"
    );
    private static final List<String> SUPPORTED_SUBPROCESS_JOIN_MODES = List.of(
            "AUTO_RETURN",
            "WAIT_PARENT_CONFIRM"
    );
    private static final List<String> SUPPORTED_SUBPROCESS_CHILD_START_STRATEGIES = List.of(
            "LATEST_PUBLISHED",
            "FIXED_VERSION",
            "SCENE_BINDING"
    );
    private static final List<String> SUPPORTED_SUBPROCESS_PARENT_RESUME_STRATEGIES = List.of(
            "AUTO_RETURN",
            "WAIT_PARENT_CONFIRM"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_BUILD_MODES = List.of(
            "APPROVER_TASKS",
            "SUBPROCESS_CALLS"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_SOURCE_MODES = List.of(
            "RULE",
            "RULE_DRIVEN",
            "MODEL_DRIVEN",
            "MANUAL_TEMPLATE"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_APPEND_POLICIES = List.of(
            "SERIAL_AFTER_CURRENT",
            "PARALLEL_WITH_CURRENT",
            "SERIAL_BEFORE_NEXT"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_EXECUTION_STRATEGIES = List.of(
            "RULE_FIRST",
            "RULE_ONLY",
            "TEMPLATE_FIRST",
            "TEMPLATE_ONLY"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_FALLBACK_STRATEGIES = List.of(
            "KEEP_CURRENT",
            "USE_RULE",
            "USE_TEMPLATE",
            "SKIP_GENERATION"
    );
    private static final List<String> SUPPORTED_DYNAMIC_BUILDER_TERMINATE_POLICIES = List.of(
            "TERMINATE_GENERATED_ONLY",
            "TERMINATE_PARENT_AND_GENERATED"
    );
    private static final List<String> SUPPORTED_BRANCH_CONDITION_TYPES = List.of(
            "EXPRESSION",
            "FIELD",
            "FORMULA"
    );
    private static final List<String> SUPPORTED_INCLUSIVE_BRANCH_MERGE_POLICIES = List.of(
            "ALL_SELECTED",
            "REQUIRED_COUNT",
            "DEFAULT_BRANCH"
    );
    private static final List<String> SUPPORTED_FIELD_CONDITION_OPERATORS = List.of(
            "EQ",
            "NE",
            "GT",
            "GE",
            "LT",
            "LE"
    );
    private static final List<String> SUPPORTED_APPROVAL_MODES = List.of(
            "SINGLE",
            "SEQUENTIAL",
            "PARALLEL",
            "OR_SIGN",
            "VOTE"
    );
    private static final List<String> SUPPORTED_REAPPROVE_POLICIES = List.of(
            "RESTART_ALL",
            "CONTINUE_PROGRESS"
    );

    // 对整份 DSL 做一致性校验，失败时直接抛出请求异常。
    public void validate(ProcessDslPayload payload) {
        Map<String, ProcessDslPayload.Node> nodeById = indexNodes(payload.nodes());
        Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::source);
        Map<String, List<ProcessDslPayload.Edge>> incomingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::target);

        validateEdgeReferences(payload.edges(), nodeById);
        validateSingleStart(nodeById.values());
        validateAtLeastOneEnd(nodeById.values());
        validateStartConfig(nodeById.values());
        validateApproverAssignments(nodeById.values());
        validateSubprocessNodes(nodeById.values());
        validateDynamicBuilderNodes(nodeById.values(), nodeById, outgoingEdges);
        validateCcTargets(nodeById.values());
        validateTimerNodes(nodeById.values());
        validateTriggerNodes(nodeById.values());
        validateIsolatedNodes(nodeById.values(), outgoingEdges, incomingEdges);
        validateReachability(nodeById.values(), outgoingEdges);
        validateConditionFanout(nodeById.values(), outgoingEdges);
        validateParallelPairs(nodeById.values(), outgoingEdges, incomingEdges, nodeById);
        validateInclusivePairs(nodeById.values(), outgoingEdges, incomingEdges, nodeById);
    }

    // 校验子流程节点的调用目标、版本策略和父子联动配置。
    private void validateSubprocessNodes(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"subprocess".equals(node.type())) {
                continue;
            }
            Map<String, Object> config = safeConfig(node);
            String calledProcessKey = asString(config.get("calledProcessKey"));
            if (calledProcessKey == null) {
                throw invalid("subprocess 节点必须配置 calledProcessKey", Map.of("nodeId", node.id()));
            }

            String calledVersionPolicy = asString(config.get("calledVersionPolicy"));
            if (calledVersionPolicy == null || !SUPPORTED_SUBPROCESS_VERSION_POLICIES.contains(calledVersionPolicy)) {
                throw invalid("subprocess 节点 calledVersionPolicy 不合法", Map.of("nodeId", node.id(), "calledVersionPolicy", calledVersionPolicy));
            }

            if ("FIXED_VERSION".equals(calledVersionPolicy) && integerValue(config.get("calledVersion")) == null) {
                throw invalid("subprocess 节点 FIXED_VERSION 模式必须配置 calledVersion", Map.of("nodeId", node.id()));
            }

            String childStartStrategy = asString(config.get("childStartStrategy"));
            if ("SCENE_BINDING".equals(childStartStrategy)) {
                if (!"LATEST_PUBLISHED".equals(calledVersionPolicy)) {
                    throw invalid("subprocess 节点 SCENE_BINDING 模式仅支持 LATEST_PUBLISHED", Map.of("nodeId", node.id()));
                }
                if (asString(config.get("sceneCode")) == null) {
                    throw invalid("subprocess 节点 SCENE_BINDING 模式必须配置 sceneCode", Map.of("nodeId", node.id()));
                }
            }

            String businessBindingMode = asString(config.get("businessBindingMode"));
            if (businessBindingMode == null || !SUPPORTED_SUBPROCESS_BUSINESS_BINDING_MODES.contains(businessBindingMode)) {
                throw invalid("subprocess 节点 businessBindingMode 不合法", Map.of("nodeId", node.id(), "businessBindingMode", businessBindingMode));
            }

            String terminatePolicy = asString(config.get("terminatePolicy"));
            if (terminatePolicy == null || !SUPPORTED_SUBPROCESS_TERMINATE_POLICIES.contains(terminatePolicy)) {
                throw invalid("subprocess 节点 terminatePolicy 不合法", Map.of("nodeId", node.id(), "terminatePolicy", terminatePolicy));
            }

            String childFinishPolicy = asString(config.get("childFinishPolicy"));
            if (childFinishPolicy == null || !SUPPORTED_SUBPROCESS_CHILD_FINISH_POLICIES.contains(childFinishPolicy)) {
                throw invalid("subprocess 节点 childFinishPolicy 不合法", Map.of("nodeId", node.id(), "childFinishPolicy", childFinishPolicy));
            }

            validateOptionalEnum(config, "callScope", SUPPORTED_SUBPROCESS_CALL_SCOPES, node.id());
            validateOptionalEnum(config, "joinMode", SUPPORTED_SUBPROCESS_JOIN_MODES, node.id());
            validateOptionalEnum(config, "childStartStrategy", SUPPORTED_SUBPROCESS_CHILD_START_STRATEGIES, node.id());
            validateOptionalEnum(config, "parentResumeStrategy", SUPPORTED_SUBPROCESS_PARENT_RESUME_STRATEGIES, node.id());
        }
    }

    private void validateOptionalEnum(
            Map<String, Object> config,
            String fieldName,
            List<String> supportedValues,
            String nodeId
    ) {
        validateOptionalEnum(config, fieldName, supportedValues, nodeId, "subprocess");
    }

    private void validateOptionalEnum(
            Map<String, Object> config,
            String fieldName,
            List<String> supportedValues,
            String nodeId,
            String nodeType
    ) {
        String value = asString(config.get(fieldName));
        if (value != null && !supportedValues.contains(value)) {
            throw invalid(nodeType + " 节点 " + fieldName + " 不合法", Map.of("nodeId", nodeId, fieldName, value));
        }
    }

    // 先按节点 id 建索引，顺便拦截重复节点。
    private Map<String, ProcessDslPayload.Node> indexNodes(List<ProcessDslPayload.Node> nodes) {
        Map<String, ProcessDslPayload.Node> nodeById = new HashMap<>();
        for (ProcessDslPayload.Node node : nodes) {
            ProcessDslPayload.Node existing = nodeById.putIfAbsent(node.id(), node);
            if (existing != null) {
                throw invalid("节点 id 重复", Map.of("nodeId", node.id()));
            }
        }
        return nodeById;
    }

    // 按源节点或目标节点建立边索引，便于后续做图遍历和分支校验。
    private Map<String, List<ProcessDslPayload.Edge>> indexEdges(
            List<ProcessDslPayload.Edge> edges,
            Function<ProcessDslPayload.Edge, String> classifier
    ) {
        Map<String, List<ProcessDslPayload.Edge>> indexed = new HashMap<>();
        for (ProcessDslPayload.Edge edge : edges) {
            indexed.computeIfAbsent(classifier.apply(edge), ignored -> new ArrayList<>()).add(edge);
        }
        indexed.values().forEach(list -> list.sort(edgeComparator()));
        return indexed;
    }

    // 校验边的 source 和 target 是否都能在节点集合中找到。
    private void validateEdgeReferences(
            List<ProcessDslPayload.Edge> edges,
            Map<String, ProcessDslPayload.Node> nodeById
    ) {
        for (ProcessDslPayload.Edge edge : edges) {
            if (!nodeById.containsKey(edge.source()) || !nodeById.containsKey(edge.target())) {
                throw invalid("边引用了不存在的节点", Map.of(
                        "edgeId", edge.id(),
                        "source", edge.source(),
                        "target", edge.target()
                ));
            }
        }
    }

    // 只允许一个 start 节点。
    private void validateSingleStart(Collection<ProcessDslPayload.Node> nodes) {
        long startCount = nodes.stream().filter(node -> "start".equals(node.type())).count();
        if (startCount != 1) {
            throw invalid("必须且只能有一个 start", Map.of("startCount", startCount));
        }
    }

    // 至少要有一个 end 节点，保证流程可闭合。
    private void validateAtLeastOneEnd(Collection<ProcessDslPayload.Node> nodes) {
        long endCount = nodes.stream().filter(node -> "end".equals(node.type())).count();
        if (endCount < 1) {
            throw invalid("必须至少有一个 end", Map.of("endCount", endCount));
        }
    }

    // 校验审批节点的选人、会签和操作配置。
    private void validateApproverAssignments(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"approver".equals(node.type())) {
                continue;
            }
            Map<String, Object> config = safeConfig(node);
            Map<String, Object> assignment = mapValue(config.get("assignment"));
            if (assignment.isEmpty()) {
                throw invalid("approver 节点必须配置 assignment", Map.of("nodeId", node.id()));
            }
            String mode = asString(assignment.get("mode"));
            if (mode == null) {
                throw invalid("approver 节点 assignment.mode 不能为空", Map.of("nodeId", node.id()));
            }
            switch (mode) {
                case "USER" -> {
                    if (stringList(assignment.get("userIds")).isEmpty()) {
                        throw invalid("approver 节点 USER 处理人不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "ROLE" -> {
                    if (stringList(assignment.get("roleCodes")).isEmpty()) {
                        throw invalid("approver 节点 ROLE 不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "DEPARTMENT", "DEPARTMENT_AND_CHILDREN" -> {
                    if (asString(assignment.get("departmentRef")) == null) {
                        throw invalid("approver 节点部门引用不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "FORM_FIELD" -> {
                    if (asString(assignment.get("formFieldKey")) == null) {
                        throw invalid("approver 节点表单字段不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "FORMULA" -> {
                    if (asString(assignment.get("formulaExpression")) == null) {
                        throw invalid("approver 节点自定义公式不能为空", Map.of("nodeId", node.id()));
                    }
                }
                default -> throw invalid("approver 节点 assignment.mode 不合法", Map.of("nodeId", node.id(), "mode", mode));
            }

            Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
            String approvalMode = asString(config.get("approvalMode"));
            if (approvalMode == null) {
                approvalMode = asString(approvalPolicy.get("type"));
            }
            if (approvalMode == null || !SUPPORTED_APPROVAL_MODES.contains(approvalMode)) {
                throw invalid("approver 节点 approvalMode 不合法", Map.of("nodeId", node.id(), "approvalMode", approvalMode));
            }
            validateApproverApprovalMode(node, assignment, config, approvalPolicy, approvalMode);

            String reapprovePolicy = asString(config.get("reapprovePolicy"));
            if (reapprovePolicy != null && !SUPPORTED_REAPPROVE_POLICIES.contains(reapprovePolicy)) {
                throw invalid("approver 节点 reapprovePolicy 不合法", Map.of("nodeId", node.id(), "reapprovePolicy", reapprovePolicy));
            }

            if (stringList(config.get("operations")).isEmpty()) {
                throw invalid("approver 节点 operations 不能为空", Map.of("nodeId", node.id()));
            }

            validateTimeoutPolicy(node);
            validateReminderPolicy(node);
            validateEscalationPolicy(node);
        }
    }

    // 校验动态构建节点的运行时生成规则和终止策略。
    private void validateDynamicBuilderNodes(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, ProcessDslPayload.Node> nodeById,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges
    ) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"dynamic-builder".equals(node.type())) {
                continue;
            }
            Map<String, Object> config = safeConfig(node);

            String buildMode = asString(config.get("buildMode"));
            if (buildMode == null || !SUPPORTED_DYNAMIC_BUILDER_BUILD_MODES.contains(buildMode)) {
                throw invalid("dynamic-builder 节点 buildMode 不合法", details("nodeId", node.id(), "buildMode", buildMode));
            }

            String sourceMode = normalizeDynamicBuilderSourceMode(asString(config.get("sourceMode")));
            if (sourceMode == null || !SUPPORTED_DYNAMIC_BUILDER_SOURCE_MODES.contains(sourceMode)) {
                throw invalid("dynamic-builder 节点 sourceMode 不合法", details("nodeId", node.id(), "sourceMode", sourceMode));
            }

            if ("RULE_DRIVEN".equals(sourceMode) && asString(config.get("ruleExpression")) == null) {
                throw invalid("dynamic-builder 节点 ruleExpression 不能为空", details("nodeId", node.id()));
            }
            if (
                    "MODEL_DRIVEN".equals(sourceMode)
                            && asString(config.get("manualTemplateCode")) == null
                            && asString(config.get("sceneCode")) == null
            ) {
                throw invalid("dynamic-builder 节点 MODEL_DRIVEN 模式必须配置 manualTemplateCode 或 sceneCode", details("nodeId", node.id()));
            }
            validateOptionalEnum(
                    config,
                    "executionStrategy",
                    SUPPORTED_DYNAMIC_BUILDER_EXECUTION_STRATEGIES,
                    node.id(),
                    "dynamic-builder"
            );
            validateOptionalEnum(
                    config,
                    "fallbackStrategy",
                    SUPPORTED_DYNAMIC_BUILDER_FALLBACK_STRATEGIES,
                    node.id(),
                    "dynamic-builder"
            );

            String appendPolicy = asString(config.get("appendPolicy"));
            if (appendPolicy == null || !SUPPORTED_DYNAMIC_BUILDER_APPEND_POLICIES.contains(appendPolicy)) {
                throw invalid("dynamic-builder 节点 appendPolicy 不合法", details("nodeId", node.id(), "appendPolicy", appendPolicy));
            }

            Integer maxGeneratedCount = integerValue(config.get("maxGeneratedCount"));
            if (maxGeneratedCount == null || maxGeneratedCount < 1) {
                throw invalid("dynamic-builder 节点 maxGeneratedCount 必须大于 0", details("nodeId", node.id()));
            }

            String terminatePolicy = asString(config.get("terminatePolicy"));
            if (terminatePolicy == null || !SUPPORTED_DYNAMIC_BUILDER_TERMINATE_POLICIES.contains(terminatePolicy)) {
                throw invalid("dynamic-builder 节点 terminatePolicy 不合法", details("nodeId", node.id(), "terminatePolicy", terminatePolicy));
            }

            if ("APPROVER_TASKS".equals(buildMode)) {
                validateDynamicBuilderTargets(node, config);
                boolean hasNonEndSuccessor = outgoingEdges.getOrDefault(node.id(), List.of()).stream()
                        .map(ProcessDslPayload.Edge::target)
                        .map(nodeById::get)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(target -> !"end".equals(target.type()));
                if (!hasNonEndSuccessor) {
                    throw invalid("dynamic-builder 节点 APPROVER_TASKS 模式后续必须保留活跃等待节点", details("nodeId", node.id()));
                }
            }

            if ("SUBPROCESS_CALLS".equals(buildMode)) {
                String calledVersionPolicy = asString(config.get("calledVersionPolicy"));
                if (calledVersionPolicy != null && !SUPPORTED_SUBPROCESS_VERSION_POLICIES.contains(calledVersionPolicy)) {
                    throw invalid(
                            "dynamic-builder 节点 calledVersionPolicy 不合法",
                            details("nodeId", node.id(), "calledVersionPolicy", calledVersionPolicy)
                    );
                }
                if ("FIXED_VERSION".equals(calledVersionPolicy) && integerValue(config.get("calledVersion")) == null) {
                    throw invalid("dynamic-builder 节点 FIXED_VERSION 模式必须配置 calledVersion", details("nodeId", node.id()));
                }
            }
        }
    }

    private void validateDynamicBuilderTargets(ProcessDslPayload.Node node, Map<String, Object> config) {
        Map<String, Object> targets = mapValue(config.get("targets"));
        if (targets.isEmpty()) {
            return;
        }
        String mode = asString(targets.get("mode"));
        if (mode == null) {
            throw invalid("dynamic-builder 节点 targets.mode 不能为空", details("nodeId", node.id()));
        }
        switch (mode) {
            case "USER" -> {
                if (stringList(targets.get("userIds")).isEmpty()) {
                    throw invalid("dynamic-builder 节点 USER 默认目标不能为空", details("nodeId", node.id()));
                }
            }
            case "ROLE" -> {
                if (stringList(targets.get("roleCodes")).isEmpty()) {
                    throw invalid("dynamic-builder 节点 ROLE 默认目标不能为空", details("nodeId", node.id()));
                }
            }
            case "DEPARTMENT", "DEPARTMENT_AND_CHILDREN" -> {
                if (asString(targets.get("departmentRef")) == null) {
                    throw invalid("dynamic-builder 节点部门默认目标不能为空", details("nodeId", node.id()));
                }
            }
            case "FORM_FIELD" -> {
                if (asString(targets.get("formFieldKey")) == null) {
                    throw invalid("dynamic-builder 节点表单字段默认目标不能为空", details("nodeId", node.id()));
                }
            }
            case "FORMULA" -> {
                if (asString(targets.get("formulaExpression")) == null) {
                    throw invalid("dynamic-builder 节点自定义公式默认目标不能为空", details("nodeId", node.id()));
                }
            }
            default -> throw invalid("dynamic-builder 节点 targets.mode 不合法", details("nodeId", node.id(), "mode", mode));
        }
    }

    private String normalizeDynamicBuilderSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return null;
        }
        return switch (sourceMode.trim().toUpperCase()) {
            case "RULE", "RULE_DRIVEN" -> "RULE_DRIVEN";
            case "MANUAL_TEMPLATE", "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> sourceMode;
        };
    }

    // 校验审批节点的会签模式、票签规则和自动结束配置。
    private void validateApproverApprovalMode(
            ProcessDslPayload.Node node,
            Map<String, Object> assignment,
            Map<String, Object> config,
            Map<String, Object> approvalPolicy,
            String approvalMode
    ) {
        String assignmentMode = asString(assignment.get("mode"));
        List<String> userIds = stringList(assignment.get("userIds"));
        List<Map<String, Object>> voteWeights = listMapValue(mapValue(config.get("voteRule")).get("weights"));
        boolean isCountersignMode = List.of("SEQUENTIAL", "PARALLEL", "OR_SIGN", "VOTE").contains(approvalMode)
                && config.containsKey("approvalMode");
        if (isCountersignMode) {
            if ("VOTE".equals(approvalMode)) {
                if ("USER".equals(assignmentMode) && userIds.size() < 2) {
                    throw invalid("approver 节点票签模式至少配置 2 名指定处理人", Map.of("nodeId", node.id(), "approvalMode", approvalMode));
                }
            } else if ("USER".equals(assignmentMode) && userIds.size() < 2) {
                throw invalid("approver 节点会签模式至少配置 2 名处理人", Map.of("nodeId", node.id(), "approvalMode", approvalMode));
            }
        }

        Map<String, Object> voteRule = mapValue(config.get("voteRule"));
        if ("VOTE".equals(approvalMode)) {
            Integer threshold = integerValue(voteRule.get("thresholdPercent"));
            if (threshold == null) {
                threshold = integerValue(approvalPolicy.get("voteThreshold"));
            }
            if (threshold == null || threshold < 1 || threshold > 100) {
                throw invalid("approver 节点票签阈值必须在 1-100 之间", Map.of("nodeId", node.id()));
            }
            if (!"USER".equals(assignmentMode) && !voteWeights.isEmpty()) {
                throw invalid(
                        "approver 节点非指定人员票签不允许配置自定义权重",
                        Map.of("nodeId", node.id(), "assignmentMode", assignmentMode)
                );
            }
            if ("USER".equals(assignmentMode)) {
                validateVoteWeights(node, userIds, voteRule);
            }
            return;
        }

        if ("OR_SIGN".equals(approvalMode) && !Boolean.TRUE.equals(config.get("autoFinishRemaining"))) {
            throw invalid("approver 节点或签必须启用自动结束剩余任务", Map.of("nodeId", node.id()));
        }

        if (!voteRule.isEmpty() && !listMapValue(voteRule.get("weights")).isEmpty()) {
            throw invalid("approver 节点非票签模式不允许配置票签权重", Map.of("nodeId", node.id(), "approvalMode", approvalMode));
        }
    }

    // 票签必须为每位处理人提供正数权重，且总权重大于 0。
    private void validateVoteWeights(
            ProcessDslPayload.Node node,
            List<String> userIds,
            Map<String, Object> voteRule
    ) {
        List<Map<String, Object>> weights = listMapValue(voteRule.get("weights"));
        if (weights.isEmpty()) {
            throw invalid("approver 节点票签权重必须覆盖所有处理人", Map.of("nodeId", node.id()));
        }
        Map<String, Integer> weightByUserId = new HashMap<>();
        int totalWeight = 0;
        for (Map<String, Object> item : weights) {
            String userId = asString(item.get("userId"));
            Integer weight = integerValue(item.get("weight"));
            if (userId == null || weight == null || weight <= 0) {
                throw invalid("approver 节点票签权重必须是大于 0 的整数", Map.of("nodeId", node.id()));
            }
            weightByUserId.put(userId, weight);
            totalWeight += weight;
        }
        if (userIds.stream().anyMatch(userId -> !weightByUserId.containsKey(userId))) {
            throw invalid("approver 节点票签权重必须覆盖所有处理人", Map.of("nodeId", node.id(), "userIds", userIds));
        }
        if (totalWeight <= 0) {
            throw invalid("approver 节点票签权重总和必须大于 0", Map.of("nodeId", node.id()));
        }
    }

    // 校验 start 节点是否带有发起人是否可编辑的开关。
    private void validateStartConfig(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"start".equals(node.type())) {
                continue;
            }
            Object initiatorEditable = safeConfig(node).get("initiatorEditable");
            if (!(initiatorEditable instanceof Boolean)) {
                throw invalid("start 节点必须配置 initiatorEditable", Map.of("nodeId", node.id()));
            }
        }
    }

    // 校验抄送节点的目标配置。
    private void validateCcTargets(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!COLLABORATION_NODE_TYPES.contains(node.type())) {
                continue;
            }
            String nodeLabel = collaborationNodeLabel(node.type());
            Map<String, Object> targets = mapValue(safeConfig(node).get("targets"));
            if (targets.isEmpty()) {
                throw invalid(nodeLabel + "节点必须配置 targets", Map.of("nodeId", node.id()));
            }
            String mode = asString(targets.get("mode"));
            if (mode == null) {
                throw invalid(nodeLabel + "节点 targets.mode 不能为空", Map.of("nodeId", node.id()));
            }
            switch (mode) {
                case "USER" -> {
                    if (stringList(targets.get("userIds")).isEmpty()) {
                        throw invalid(nodeLabel + "节点 USER 目标不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "ROLE" -> {
                    if (stringList(targets.get("roleCodes")).isEmpty()) {
                        throw invalid(nodeLabel + "节点 ROLE 目标不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "DEPARTMENT" -> {
                    if (asString(targets.get("departmentRef")) == null) {
                        throw invalid(nodeLabel + "节点部门引用不能为空", Map.of("nodeId", node.id()));
                    }
                }
                default -> throw invalid(nodeLabel + "节点 targets.mode 不合法", Map.of("nodeId", node.id(), "mode", mode));
            }
        }
    }

    private String collaborationNodeLabel(String type) {
        return switch (type) {
            case "cc" -> "抄送";
            case "supervise" -> "督办";
            case "meeting" -> "会办";
            case "read" -> "阅办";
            case "circulate" -> "传阅";
            default -> type;
        };
    }

    // 校验定时节点的调度类型和调度参数。
    private void validateTimerNodes(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"timer".equals(node.type())) {
                continue;
            }
            Map<String, Object> config = safeConfig(node);
            String scheduleType = asString(config.get("scheduleType"));
            if (scheduleType == null || !SUPPORTED_SCHEDULE_TYPES.contains(scheduleType)) {
                throw invalid("timer 节点 scheduleType 不合法", Map.of("nodeId", node.id(), "scheduleType", scheduleType));
            }
            validateScheduleConfig(node, config, "timer");
        }
    }

    // 校验触发节点的触发模式、重试参数和可选调度配置。
    private void validateTriggerNodes(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"trigger".equals(node.type())) {
                continue;
            }
            Map<String, Object> config = safeConfig(node);
            String triggerMode = asString(config.get("triggerMode"));
            if (triggerMode == null || !SUPPORTED_TRIGGER_MODES.contains(triggerMode)) {
                throw invalid("trigger 节点 triggerMode 不合法", Map.of("nodeId", node.id(), "triggerMode", triggerMode));
            }
            String triggerKey = asString(config.get("triggerKey"));
            if (triggerKey == null) {
                throw invalid("trigger 节点 triggerKey 不能为空", Map.of("nodeId", node.id()));
            }
            if ("SCHEDULED".equals(triggerMode)) {
                String scheduleType = asString(config.get("scheduleType"));
                if (scheduleType == null || !SUPPORTED_SCHEDULE_TYPES.contains(scheduleType)) {
                    throw invalid("trigger 节点 scheduleType 不合法", Map.of("nodeId", node.id(), "scheduleType", scheduleType));
                }
                validateScheduleConfig(node, config, "trigger");
            }
            Integer retryTimes = integerValue(config.get("retryTimes"));
            if (retryTimes == null || retryTimes < 0) {
                throw invalid("trigger 节点 retryTimes 必须大于等于 0", Map.of("nodeId", node.id()));
            }
            Integer retryIntervalMinutes = integerValue(config.get("retryIntervalMinutes"));
            if (retryTimes > 0 && (retryIntervalMinutes == null || retryIntervalMinutes <= 0)) {
                throw invalid("trigger 节点 retryIntervalMinutes 不能为空", Map.of("nodeId", node.id()));
            }
        }
    }

    // 孤立节点既没有入边也没有出边，直接判定为非法。
    private void validateIsolatedNodes(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
            Map<String, List<ProcessDslPayload.Edge>> incomingEdges
    ) {
        List<String> isolatedNodeIds = nodes.stream()
                .filter(node -> outgoingEdges.getOrDefault(node.id(), List.of()).isEmpty())
                .filter(node -> incomingEdges.getOrDefault(node.id(), List.of()).isEmpty())
                .map(ProcessDslPayload.Node::id)
                .sorted()
                .toList();
        if (!isolatedNodeIds.isEmpty()) {
            throw invalid("存在孤立节点", Map.of("nodeIds", isolatedNodeIds));
        }
    }

    // 从 start 节点出发做可达性遍历，找出未被访问的节点。
    private void validateReachability(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges
    ) {
        ProcessDslPayload.Node startNode = nodes.stream()
                .filter(node -> "start".equals(node.type()))
                .findFirst()
                .orElseThrow(() -> invalid("必须且只能有一个 start", Map.of()));

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startNode.id());

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (ProcessDslPayload.Edge edge : outgoingEdges.getOrDefault(current, List.of())) {
                queue.addLast(edge.target());
            }
        }

        List<String> unreachableNodeIds = nodes.stream()
                .map(ProcessDslPayload.Node::id)
                .filter(nodeId -> !visited.contains(nodeId))
                .sorted()
                .toList();
        if (!unreachableNodeIds.isEmpty()) {
            throw invalid("存在不可达节点", Map.of("nodeIds", unreachableNodeIds));
        }
    }

    // condition 节点必须形成至少两条出边的条件分支。
    private void validateConditionFanout(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges
    ) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"condition".equals(node.type())) {
                continue;
            }
            List<ProcessDslPayload.Edge> edges = outgoingEdges.getOrDefault(node.id(), List.of());
            if (edges.size() < 2) {
                throw invalid("condition 节点至少需要两条出边", Map.of("nodeId", node.id(), "outgoingCount", edges.size()));
            }

            String defaultEdgeId = asString(safeConfig(node).get("defaultEdgeId"));
            if (defaultEdgeId == null) {
                throw invalid("condition 节点必须配置 defaultEdgeId", Map.of("nodeId", node.id()));
            }
            if (edges.stream().noneMatch(edge -> edge.id().equals(defaultEdgeId))) {
                throw invalid("condition 节点 defaultEdgeId 必须指向出边", Map.of("nodeId", node.id(), "defaultEdgeId", defaultEdgeId));
            }

            List<ProcessDslPayload.Edge> nonDefaultEdges = edges.stream()
                    .filter(edge -> !edge.id().equals(defaultEdgeId))
                    .toList();
            for (ProcessDslPayload.Edge edge : nonDefaultEdges) {
                validateBranchCondition(node, edge, "condition");
            }
        }
    }

    // 并行分支必须成对出现，且 split 和 join 的连接关系要闭合。
    private void validateParallelPairs(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
            Map<String, List<ProcessDslPayload.Edge>> incomingEdges,
            Map<String, ProcessDslPayload.Node> nodeById
    ) {
        List<ProcessDslPayload.Node> splits = nodes.stream()
                .filter(node -> "parallel_split".equals(node.type()))
                .toList();
        List<ProcessDslPayload.Node> joins = nodes.stream()
                .filter(node -> "parallel_join".equals(node.type()))
                .toList();

        if (splits.size() != joins.size()) {
            throw invalid("parallel_split 与 parallel_join 必须成对出现", Map.of(
                    "parallelSplitCount", splits.size(),
                    "parallelJoinCount", joins.size()
            ));
        }

        for (ProcessDslPayload.Node split : splits) {
            if (!canReachNodeType(split.id(), "parallel_join", outgoingEdges, nodeById, new HashSet<>(), false)) {
                throw invalid("parallel_split 与 parallel_join 必须成对出现", Map.of("nodeId", split.id()));
            }
        }

        for (ProcessDslPayload.Node join : joins) {
            if (!canReachNodeType(join.id(), "parallel_split", incomingEdges, nodeById, new HashSet<>(), true)) {
                throw invalid("parallel_split 与 parallel_join 必须成对出现", Map.of("nodeId", join.id()));
            }
        }
    }

    // 包容分支必须成对出现，split 至少两条出边，join 至少两条入边。
    private void validateInclusivePairs(
            Collection<ProcessDslPayload.Node> nodes,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
            Map<String, List<ProcessDslPayload.Edge>> incomingEdges,
            Map<String, ProcessDslPayload.Node> nodeById
    ) {
        List<ProcessDslPayload.Node> splits = nodes.stream()
                .filter(node -> "inclusive_split".equals(node.type()))
                .toList();
        List<ProcessDslPayload.Node> joins = nodes.stream()
                .filter(node -> "inclusive_join".equals(node.type()))
                .toList();

        if (splits.size() != joins.size()) {
            throw invalid("inclusive_split 与 inclusive_join 必须成对出现", Map.of(
                    "inclusiveSplitCount", splits.size(),
                    "inclusiveJoinCount", joins.size()
            ));
        }

        for (ProcessDslPayload.Node split : splits) {
            List<ProcessDslPayload.Edge> edges = outgoingEdges.getOrDefault(split.id(), List.of());
            if (edges.size() < 2) {
                throw invalid("inclusive_split 节点至少需要两条出边", Map.of("nodeId", split.id(), "outgoingCount", edges.size()));
            }
            validateInclusiveSplitConfig(split, edges);
            for (ProcessDslPayload.Edge edge : edges) {
                validateBranchCondition(split, edge, "inclusive_split");
            }
            if (!canReachNodeType(split.id(), "inclusive_join", outgoingEdges, nodeById, new HashSet<>(), false)) {
                throw invalid("inclusive_split 与 inclusive_join 必须成对出现", Map.of("nodeId", split.id()));
            }
        }

        for (ProcessDslPayload.Node join : joins) {
            List<ProcessDslPayload.Edge> edges = incomingEdges.getOrDefault(join.id(), List.of());
            if (edges.size() < 2) {
                throw invalid("inclusive_join 节点至少需要两条入边", Map.of("nodeId", join.id(), "incomingCount", edges.size()));
            }
            if (!canReachNodeType(join.id(), "inclusive_split", incomingEdges, nodeById, new HashSet<>(), true)) {
                throw invalid("inclusive_split 与 inclusive_join 必须成对出现", Map.of("nodeId", join.id()));
            }
        }
    }

    // 校验包容分支的第一批策略配置，保证分支优先级、默认分支和汇聚规则可被稳定识别。
    private void validateInclusiveSplitConfig(ProcessDslPayload.Node split, List<ProcessDslPayload.Edge> outgoingEdges) {
        Map<String, Object> config = safeConfig(split);

        String branchMergePolicy = asString(config.get("branchMergePolicy"));
        if (branchMergePolicy != null && !SUPPORTED_INCLUSIVE_BRANCH_MERGE_POLICIES.contains(branchMergePolicy)) {
            throw invalid(
                    "inclusive_split 节点 branchMergePolicy 不合法",
                    Map.of("nodeId", split.id(), "branchMergePolicy", branchMergePolicy)
            );
        }

        Integer requiredBranchCount = integerValue(config.get("requiredBranchCount"));
        if (requiredBranchCount != null) {
            if (requiredBranchCount <= 0 || requiredBranchCount > outgoingEdges.size()) {
                throw invalid(
                        "inclusive_split 节点 requiredBranchCount 不合法",
                        Map.of("nodeId", split.id(), "requiredBranchCount", requiredBranchCount, "outgoingCount", outgoingEdges.size())
                );
            }
        }

        String defaultBranchId = asString(config.get("defaultBranchId"));
        if (defaultBranchId != null && outgoingEdges.stream().noneMatch(edge -> defaultBranchId.equals(edge.id()))) {
            throw invalid(
                    "inclusive_split 节点 defaultBranchId 必须指向出边",
                    Map.of("nodeId", split.id(), "defaultBranchId", defaultBranchId)
            );
        }

        if ("REQUIRED_COUNT".equals(branchMergePolicy) && requiredBranchCount == null) {
            throw invalid(
                    "inclusive_split 节点 branchMergePolicy=REQUIRED_COUNT 时必须配置 requiredBranchCount",
                    Map.of("nodeId", split.id())
            );
        }
        if ("DEFAULT_BRANCH".equals(branchMergePolicy) && defaultBranchId == null) {
            throw invalid(
                    "inclusive_split 节点 branchMergePolicy=DEFAULT_BRANCH 时必须配置 defaultBranchId",
                    Map.of("nodeId", split.id())
            );
        }

        List<Integer> branchPriorities = outgoingEdges.stream()
                .map(ProcessDslPayload.Edge::priority)
                .toList();
        if (branchPriorities.stream().anyMatch(priority -> priority == null || priority <= 0)) {
            throw invalid(
                    "inclusive_split 分支 branchPriority 必须为正整数",
                    Map.of("nodeId", split.id(), "branchPriorities", branchPriorities)
            );
        }
    }

    // 校验条件分支的表达式类型，支持表达式、字段与公式三类第一批增强。
    private void validateBranchCondition(ProcessDslPayload.Node node, ProcessDslPayload.Edge edge, String branchNodeType) {
        Map<String, Object> condition = mapValue(edge.condition());
        String type = asString(condition.get("type"));
        if (type == null) {
            throw invalid(branchNodeType + " 分支必须配置 condition.type", Map.of("nodeId", node.id(), "edgeId", edge.id()));
        }
        if (!SUPPORTED_BRANCH_CONDITION_TYPES.contains(type)) {
            throw invalid(
                    branchNodeType + " 分支 condition.type 不合法",
                    Map.of("nodeId", node.id(), "edgeId", edge.id(), "type", type)
            );
        }

        switch (type) {
            case "EXPRESSION" -> {
                if (asString(condition.get("expression")) == null) {
                    throw invalid(branchNodeType + " 分支必须配置 expression", Map.of("nodeId", node.id(), "edgeId", edge.id()));
                }
            }
            case "FIELD" -> validateFieldBranchCondition(node, edge, condition, branchNodeType);
            case "FORMULA" -> {
                String formulaExpression = asString(condition.get("expression"));
                if (formulaExpression == null) {
                    formulaExpression = asString(condition.get("formulaExpression"));
                }
                if (formulaExpression == null) {
                    throw invalid(branchNodeType + " 分支 FORMULA 类型必须配置 expression", details("nodeId", node.id(), "edgeId", edge.id()));
                }
            }
            default -> throw invalid(
                    branchNodeType + " 分支 condition.type 不合法",
                    Map.of("nodeId", node.id(), "edgeId", edge.id(), "type", type)
            );
        }
    }

    // 字段分支要求指定字段、比较符和比较值。
    private void validateFieldBranchCondition(
            ProcessDslPayload.Node node,
            ProcessDslPayload.Edge edge,
            Map<String, Object> condition,
            String branchNodeType
    ) {
        String fieldKey = asString(condition.get("fieldKey"));
        if (fieldKey == null) {
            throw invalid(branchNodeType + " 分支 FIELD 类型必须配置 fieldKey", Map.of("nodeId", node.id(), "edgeId", edge.id()));
        }

        String operator = asString(condition.get("operator"));
        if (operator == null || !SUPPORTED_FIELD_CONDITION_OPERATORS.contains(operator)) {
            throw invalid(
                    branchNodeType + " 分支 FIELD 类型 operator 不合法",
                    details("nodeId", node.id(), "edgeId", edge.id(), "operator", operator)
            );
        }

        if (condition.get("value") == null) {
            throw invalid(branchNodeType + " 分支 FIELD 类型必须配置 value", Map.of("nodeId", node.id(), "edgeId", edge.id()));
        }
    }

    // 校验审批节点的超时策略。
    private void validateTimeoutPolicy(ProcessDslPayload.Node node) {
        Map<String, Object> timeoutPolicy = mapValue(safeConfig(node).get("timeoutPolicy"));
        if (timeoutPolicy.isEmpty() || !Boolean.TRUE.equals(timeoutPolicy.get("enabled"))) {
            return;
        }
        Integer durationMinutes = integerValue(timeoutPolicy.get("durationMinutes"));
        if (durationMinutes == null || durationMinutes <= 0) {
            throw invalid("approver 节点 timeoutPolicy.durationMinutes 不能为空", Map.of("nodeId", node.id()));
        }
        String action = asString(timeoutPolicy.get("action"));
        if (action == null || !SUPPORTED_TIMEOUT_ACTIONS.contains(action)) {
            throw invalid("approver 节点 timeoutPolicy.action 不合法", Map.of("nodeId", node.id(), "action", action));
        }
    }

    // 校验审批节点的催办策略。
    private void validateReminderPolicy(ProcessDslPayload.Node node) {
        Map<String, Object> reminderPolicy = mapValue(safeConfig(node).get("reminderPolicy"));
        if (reminderPolicy.isEmpty() || !Boolean.TRUE.equals(reminderPolicy.get("enabled"))) {
            return;
        }
        Integer firstReminderAfterMinutes = integerValue(reminderPolicy.get("firstReminderAfterMinutes"));
        Integer repeatIntervalMinutes = integerValue(reminderPolicy.get("repeatIntervalMinutes"));
        Integer maxTimes = integerValue(reminderPolicy.get("maxTimes"));
        if (firstReminderAfterMinutes == null || firstReminderAfterMinutes <= 0) {
            throw invalid("approver 节点 reminderPolicy.firstReminderAfterMinutes 不能为空", Map.of("nodeId", node.id()));
        }
        if (repeatIntervalMinutes == null || repeatIntervalMinutes <= 0) {
            throw invalid("approver 节点 reminderPolicy.repeatIntervalMinutes 不能为空", Map.of("nodeId", node.id()));
        }
        if (maxTimes == null || maxTimes <= 0) {
            throw invalid("approver 节点 reminderPolicy.maxTimes 不能为空", Map.of("nodeId", node.id()));
        }
        List<String> channels = stringList(reminderPolicy.get("channels"));
        if (channels.isEmpty()) {
            throw invalid("approver 节点 reminderPolicy.channels 不能为空", Map.of("nodeId", node.id()));
        }
        if (channels.stream().anyMatch(channel -> !SUPPORTED_REMINDER_CHANNELS.contains(channel))) {
            throw invalid("approver 节点 reminderPolicy.channels 不合法", Map.of("nodeId", node.id(), "channels", channels));
        }
    }

    private void validateEscalationPolicy(ProcessDslPayload.Node node) {
        Map<String, Object> escalationPolicy = mapValue(safeConfig(node).get("escalationPolicy"));
        if (escalationPolicy.isEmpty() || !Boolean.TRUE.equals(escalationPolicy.get("enabled"))) {
            return;
        }
        Integer afterMinutes = integerValue(escalationPolicy.get("afterMinutes"));
        if (afterMinutes == null || afterMinutes <= 0) {
            throw invalid("approver 节点 escalationPolicy.afterMinutes 不能为空", Map.of("nodeId", node.id()));
        }
        String targetMode = asString(escalationPolicy.get("targetMode"));
        if (targetMode == null || !SUPPORTED_ESCALATION_TARGET_MODES.contains(targetMode)) {
            throw invalid("approver 节点 escalationPolicy.targetMode 不合法", Map.of("nodeId", node.id(), "targetMode", targetMode));
        }
        if ("USER".equals(targetMode) && stringList(escalationPolicy.get("targetUserIds")).isEmpty()) {
            throw invalid("approver 节点 escalationPolicy.targetUserIds 不能为空", Map.of("nodeId", node.id()));
        }
        if ("ROLE".equals(targetMode) && stringList(escalationPolicy.get("targetRoleCodes")).isEmpty()) {
            throw invalid("approver 节点 escalationPolicy.targetRoleCodes 不能为空", Map.of("nodeId", node.id()));
        }
        List<String> channels = stringList(escalationPolicy.get("channels"));
        if (channels.isEmpty()) {
            throw invalid("approver 节点 escalationPolicy.channels 不能为空", Map.of("nodeId", node.id()));
        }
        if (channels.stream().anyMatch(channel -> !SUPPORTED_REMINDER_CHANNELS.contains(channel))) {
            throw invalid("approver 节点 escalationPolicy.channels 不合法", Map.of("nodeId", node.id(), "channels", channels));
        }
    }

    // 校验定时节点和触发节点共用的调度配置。
    private void validateScheduleConfig(ProcessDslPayload.Node node, Map<String, Object> config, String nodeType) {
        String scheduleType = asString(config.get("scheduleType"));
        if ("ABSOLUTE_TIME".equals(scheduleType)) {
            if (asString(config.get("runAt")) == null) {
                throw invalid(nodeType + " 节点 runAt 不能为空", Map.of("nodeId", node.id()));
            }
            return;
        }
        Integer delayMinutes = integerValue(config.get("delayMinutes"));
        if (delayMinutes == null || delayMinutes <= 0) {
            throw invalid(nodeType + " 节点 delayMinutes 不能为空", Map.of("nodeId", node.id()));
        }
    }

    // 递归判断某个节点是否能够到达目标节点类型。
    private boolean canReachNodeType(
            String nodeId,
            String targetType,
            Map<String, List<ProcessDslPayload.Edge>> edges,
            Map<String, ProcessDslPayload.Node> nodeById,
            Set<String> visited,
            boolean reverse
    ) {
        if (!visited.add(nodeId)) {
            return false;
        }
        for (ProcessDslPayload.Edge edge : edges.getOrDefault(nodeId, List.of())) {
            String nextNodeId = reverse ? edge.source() : edge.target();
            ProcessDslPayload.Node nextNode = nodeById.get(nextNodeId);
            if (nextNode == null) {
                continue;
            }
            if (targetType.equals(nextNode.type())) {
                return true;
            }
            if (canReachNodeType(nextNode.id(), targetType, edges, nodeById, visited, reverse)) {
                return true;
            }
        }
        return false;
    }

    // 边排序时按 priority 和 id 保持稳定结果。
    private Comparator<ProcessDslPayload.Edge> edgeComparator() {
        return Comparator
                .comparing(ProcessDslPayload.Edge::priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ProcessDslPayload.Edge::id);
    }

    // 统一把节点配置转成可读写的 Map。

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key == null) {
                continue;
            }
            details.put(String.valueOf(key), keyValues[i + 1]);
        }
        return details;
    }

    private Map<String, Object> safeConfig(ProcessDslPayload.Node node) {
        return node.config() == null ? Map.of() : node.config();
    }

    // 把任意对象安全转换成 Map。
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    // 把任意对象安全转换成字符串列表。
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

    // 把数组里的每一项都安全转换成 Map，供复杂配置校验复用。
    private List<Map<String, Object>> listMapValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::mapValue)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    // 把任意对象安全转换成整数。
    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // 把任意对象安全转换成字符串。
    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    // 统一构造 DSL 校验异常，便于前端展示。
    private ContractException invalid(String message, Map<String, Object> details) {
        return new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, message, details);
    }
}
