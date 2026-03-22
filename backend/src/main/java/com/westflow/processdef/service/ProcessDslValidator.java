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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProcessDslValidator {

    private static final List<String> SUPPORTED_TIMEOUT_ACTIONS = List.of("APPROVE", "REJECT");
    private static final List<String> SUPPORTED_REMINDER_CHANNELS = List.of(
            "IN_APP",
            "EMAIL",
            "WEBHOOK",
            "SMS",
            "WECHAT",
            "DINGTALK"
    );
    private static final List<String> SUPPORTED_SCHEDULE_TYPES = List.of("ABSOLUTE_TIME", "RELATIVE_TO_ARRIVAL");
    private static final List<String> SUPPORTED_TRIGGER_MODES = List.of("IMMEDIATE", "SCHEDULED");

    public void validate(ProcessDslPayload payload) {
        Map<String, ProcessDslPayload.Node> nodeById = indexNodes(payload.nodes());
        Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::source);
        Map<String, List<ProcessDslPayload.Edge>> incomingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::target);

        validateEdgeReferences(payload.edges(), nodeById);
        validateSingleStart(nodeById.values());
        validateAtLeastOneEnd(nodeById.values());
        validateStartConfig(nodeById.values());
        validateApproverAssignments(nodeById.values());
        validateCcTargets(nodeById.values());
        validateTimerNodes(nodeById.values());
        validateTriggerNodes(nodeById.values());
        validateIsolatedNodes(nodeById.values(), outgoingEdges, incomingEdges);
        validateReachability(nodeById.values(), outgoingEdges);
        validateConditionFanout(nodeById.values(), outgoingEdges);
        validateParallelPairs(nodeById.values(), outgoingEdges, incomingEdges, nodeById);
    }

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

    private void validateSingleStart(Collection<ProcessDslPayload.Node> nodes) {
        long startCount = nodes.stream().filter(node -> "start".equals(node.type())).count();
        if (startCount != 1) {
            throw invalid("必须且只能有一个 start", Map.of("startCount", startCount));
        }
    }

    private void validateAtLeastOneEnd(Collection<ProcessDslPayload.Node> nodes) {
        long endCount = nodes.stream().filter(node -> "end".equals(node.type())).count();
        if (endCount < 1) {
            throw invalid("必须至少有一个 end", Map.of("endCount", endCount));
        }
    }

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
                default -> throw invalid("approver 节点 assignment.mode 不合法", Map.of("nodeId", node.id(), "mode", mode));
            }

            Map<String, Object> approvalPolicy = mapValue(config.get("approvalPolicy"));
            String approvalType = asString(approvalPolicy.get("type"));
            if (approvalType == null) {
                throw invalid("approver 节点 approvalPolicy.type 不能为空", Map.of("nodeId", node.id()));
            }
            switch (approvalType) {
                case "SEQUENTIAL", "PARALLEL" -> {
                }
                case "VOTE" -> {
                    Integer threshold = integerValue(approvalPolicy.get("voteThreshold"));
                    if (threshold == null || threshold < 1 || threshold > 100) {
                        throw invalid("approver 节点票签阈值必须在 1-100 之间", Map.of("nodeId", node.id()));
                    }
                }
                default -> throw invalid("approver 节点 approvalPolicy.type 不合法", Map.of("nodeId", node.id(), "type", approvalType));
            }

            if (stringList(config.get("operations")).isEmpty()) {
                throw invalid("approver 节点 operations 不能为空", Map.of("nodeId", node.id()));
            }

            validateTimeoutPolicy(node);
            validateReminderPolicy(node);
        }
    }

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

    private void validateCcTargets(Collection<ProcessDslPayload.Node> nodes) {
        for (ProcessDslPayload.Node node : nodes) {
            if (!"cc".equals(node.type())) {
                continue;
            }
            Map<String, Object> targets = mapValue(safeConfig(node).get("targets"));
            if (targets.isEmpty()) {
                throw invalid("cc 节点必须配置 targets", Map.of("nodeId", node.id()));
            }
            String mode = asString(targets.get("mode"));
            if (mode == null) {
                throw invalid("cc 节点 targets.mode 不能为空", Map.of("nodeId", node.id()));
            }
            switch (mode) {
                case "USER" -> {
                    if (stringList(targets.get("userIds")).isEmpty()) {
                        throw invalid("cc 节点 USER 目标不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "ROLE" -> {
                    if (stringList(targets.get("roleCodes")).isEmpty()) {
                        throw invalid("cc 节点 ROLE 目标不能为空", Map.of("nodeId", node.id()));
                    }
                }
                case "DEPARTMENT" -> {
                    if (asString(targets.get("departmentRef")) == null) {
                        throw invalid("cc 节点部门引用不能为空", Map.of("nodeId", node.id()));
                    }
                }
                default -> throw invalid("cc 节点 targets.mode 不合法", Map.of("nodeId", node.id(), "mode", mode));
            }
        }
    }

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
                Map<String, Object> condition = mapValue(edge.condition());
                String expression = asString(condition.get("expression"));
                String type = asString(condition.get("type"));
                if (type == null) {
                    throw invalid("condition 分支必须配置 condition.type", Map.of("nodeId", node.id(), "edgeId", edge.id()));
                }
                if (!"EXPRESSION".equals(type)) {
                    throw invalid("condition 分支 condition.type 不合法", Map.of("nodeId", node.id(), "edgeId", edge.id(), "type", type));
                }
                if (expression == null) {
                    throw invalid("condition 分支必须配置 expression", Map.of("nodeId", node.id(), "edgeId", edge.id()));
                }
            }
        }
    }

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

    private Comparator<ProcessDslPayload.Edge> edgeComparator() {
        return Comparator
                .comparing(ProcessDslPayload.Edge::priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ProcessDslPayload.Edge::id);
    }

    private Map<String, Object> safeConfig(ProcessDslPayload.Node node) {
        return node.config() == null ? Map.of() : node.config();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

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

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private ContractException invalid(String message, Map<String, Object> details) {
        return new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, message, details);
    }
}
