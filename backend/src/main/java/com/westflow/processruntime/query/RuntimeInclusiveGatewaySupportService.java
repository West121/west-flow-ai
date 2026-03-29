package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.InclusiveGatewayHitResponse;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeInclusiveGatewaySupportService {

    private static final String INCLUSIVE_SELECTION_SUMMARY_PREFIX = "westflowInclusiveSelectionSummary_";
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private record InclusiveSelectionSummary(
            Integer eligibleTargetCount,
            List<String> selectedEdgeIds,
            List<String> selectedTargetNodeIds,
            List<String> selectedBranchLabels,
            List<Integer> selectedBranchPriorities,
            List<String> selectedDecisionReasons,
            boolean defaultBranchSelected
    ) {
        private static InclusiveSelectionSummary empty() {
            return new InclusiveSelectionSummary(null, List.of(), List.of(), List.of(), List.of(), List.of(), false);
        }
    }

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;

    public Map<String, Object> inclusiveGatewayEventDetails(InclusiveGatewayHitResponse hit, boolean joined) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (joined) {
            details.put("splitNodeId", hit.splitNodeId());
            details.put("splitNodeName", hit.splitNodeName());
        } else {
            details.put("joinNodeId", hit.joinNodeId());
            details.put("joinNodeName", hit.joinNodeName());
            details.put("skippedTargetNodeIds", hit.skippedTargetNodeIds());
            details.put("skippedTargetNodeNames", hit.skippedTargetNodeNames());
        }
        details.put("branchMergePolicy", hit.branchMergePolicy());
        details.put("defaultBranchId", hit.defaultBranchId());
        details.put("requiredBranchCount", hit.requiredBranchCount());
        details.put("eligibleTargetCount", hit.eligibleTargetCount());
        details.put("selectedEdgeIds", hit.selectedEdgeIds());
        details.put("selectedBranchLabels", hit.selectedBranchLabels());
        details.put("selectedBranchPriorities", hit.selectedBranchPriorities());
        details.put("selectedDecisionReasons", hit.selectedDecisionReasons());
        details.put("completedSelectedTargetCount", hit.completedSelectedTargetCount());
        details.put("pendingSelectedTargetCount", hit.pendingSelectedTargetCount());
        details.put("defaultBranchSelected", hit.defaultBranchSelected());
        details.put("activatedTargetNodeIds", hit.activatedTargetNodeIds());
        details.put("activatedTargetNodeNames", hit.activatedTargetNodeNames());
        return details;
    }

    public ProcessDslPayload.Node resolveNearestInclusiveJoin(
            String splitNodeId,
            Map<String, ProcessDslPayload.Node> nodeById,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges
    ) {
        Set<String> visited = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(splitNodeId);
        while (!queue.isEmpty()) {
            String currentNodeId = queue.remove(0);
            if (!visited.add(currentNodeId)) {
                continue;
            }
            if (!splitNodeId.equals(currentNodeId)) {
                ProcessDslPayload.Node node = nodeById.get(currentNodeId);
                if (node != null && "inclusive_join".equals(node.type())) {
                    return node;
                }
            }
            for (ProcessDslPayload.Edge edge : outgoingEdges.getOrDefault(currentNodeId, List.of())) {
                if (edge.target() != null && !edge.target().isBlank()) {
                    queue.add(edge.target());
                }
            }
        }
        return null;
    }

    public boolean hasReachedInclusiveBranchTarget(
            String nodeId,
            String joinNodeId,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
            Set<String> reachedNodeIds,
            Set<String> visited
    ) {
        if (nodeId == null || nodeId.isBlank() || !visited.add(nodeId)) {
            return false;
        }
        if (nodeId.equals(joinNodeId)) {
            return false;
        }
        if (reachedNodeIds.contains(nodeId)) {
            return true;
        }
        for (ProcessDslPayload.Edge edge : outgoingEdges.getOrDefault(nodeId, List.of())) {
            if (hasReachedInclusiveBranchTarget(edge.target(), joinNodeId, outgoingEdges, reachedNodeIds, visited)) {
                return true;
            }
        }
        return false;
    }

    public OffsetDateTime resolveInclusiveBranchOccurredAt(
            String nodeId,
            String joinNodeId,
            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
            Map<String, OffsetDateTime> occurredAtByNodeId,
            Set<String> visited
    ) {
        if (nodeId == null || nodeId.isBlank() || !visited.add(nodeId)) {
            return null;
        }
        if (nodeId.equals(joinNodeId)) {
            return null;
        }
        OffsetDateTime directOccurredAt = occurredAtByNodeId.get(nodeId);
        if (directOccurredAt != null) {
            return directOccurredAt;
        }
        OffsetDateTime earliest = null;
        for (ProcessDslPayload.Edge edge : outgoingEdges.getOrDefault(nodeId, List.of())) {
            OffsetDateTime occurredAt = resolveInclusiveBranchOccurredAt(
                    edge.target(),
                    joinNodeId,
                    outgoingEdges,
                    occurredAtByNodeId,
                    visited
            );
            if (occurredAt != null && (earliest == null || occurredAt.isBefore(earliest))) {
                earliest = occurredAt;
            }
        }
        return earliest;
    }

    public String resolveInclusiveGatewayStatus(
            HistoricProcessInstance historicProcessInstance,
            String joinNodeId,
            OffsetDateTime finishedAt,
            List<String> activatedTargetNodeIds
    ) {
        if (finishedAt != null || (joinNodeId != null && isNodeCurrentlyActive(historicProcessInstance.getId(), joinNodeId))) {
            return finishedAt != null ? "COMPLETED" : "RUNNING";
        }
        if (historicProcessInstance.getEndTime() != null) {
            return "COMPLETED";
        }
        return activatedTargetNodeIds.isEmpty() ? "PENDING" : "RUNNING";
    }

    public SelectionSummary selectionSummary(
            Map<String, Object> processVariables,
            String splitNodeId,
            String defaultBranchId
    ) {
        InclusiveSelectionSummary summary = inclusiveSelectionSummary(processVariables, splitNodeId, defaultBranchId);
        return new SelectionSummary(
                summary.eligibleTargetCount(),
                summary.selectedEdgeIds(),
                summary.selectedTargetNodeIds(),
                summary.selectedBranchLabels(),
                summary.selectedBranchPriorities(),
                summary.selectedDecisionReasons(),
                summary.defaultBranchSelected()
        );
    }

    public String edgeConditionExpression(ProcessDslPayload.Edge edge) {
        if (edge == null || edge.condition() == null || edge.condition().isEmpty()) {
            return null;
        }
        Object expression = edge.condition().get("expression");
        return stringValue(expression);
    }

    public String buildInclusiveDecisionSummary(
            int branchCount,
            int eligibleCount,
            int activatedCount,
            String branchMergePolicy,
            boolean defaultBranchSelected,
            int selectedTargetCount,
            int completedSelectedTargetCount,
            boolean hasJoin
    ) {
        String base = "已激活 " + activatedCount + "/" + branchCount + " 条分支";
        StringBuilder summary = new StringBuilder(base)
                .append("，命中候选 ").append(eligibleCount).append(" 条");
        if (branchMergePolicy != null && !branchMergePolicy.isBlank()) {
            summary.append("，策略 ").append(branchMergePolicy);
        }
        if (defaultBranchSelected) {
            summary.append("，已走默认分支");
        }
        if (selectedTargetCount > 0) {
            int pendingSelectedTargetCount = Math.max(0, selectedTargetCount - completedSelectedTargetCount);
            summary.append("，汇聚进度 ")
                    .append(completedSelectedTargetCount)
                    .append("/")
                    .append(selectedTargetCount)
                    .append("，待完成 ")
                    .append(pendingSelectedTargetCount)
                    .append(" 条");
        }
        if (!hasJoin) {
            return summary.append("，未找到汇聚节点").toString();
        }
        return summary.toString();
    }

    public int countCompletedInclusiveTargets(
            List<String> selectedTargetNodeIds,
            Set<String> activeTaskNodeIds,
            Map<String, List<HistoricActivityInstance>> historicActivitiesByNodeId
    ) {
        if (selectedTargetNodeIds == null || selectedTargetNodeIds.isEmpty()) {
            return 0;
        }
        int completedCount = 0;
        for (String targetNodeId : selectedTargetNodeIds) {
            if (targetNodeId == null || targetNodeId.isBlank()) {
                continue;
            }
            if (activeTaskNodeIds.contains(targetNodeId)) {
                continue;
            }
            List<HistoricActivityInstance> activities = historicActivitiesByNodeId.get(targetNodeId);
            boolean completed = activities != null && activities.stream().anyMatch(activity -> activity.getEndTime() != null);
            if (completed) {
                completedCount++;
            }
        }
        return completedCount;
    }

    public OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    public String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    public String stringValueOrDefault(Object value, String defaultValue) {
        String text = stringValue(value);
        return text == null ? defaultValue : text;
    }

    public record SelectionSummary(
            Integer eligibleTargetCount,
            List<String> selectedEdgeIds,
            List<String> selectedTargetNodeIds,
            List<String> selectedBranchLabels,
            List<Integer> selectedBranchPriorities,
            List<String> selectedDecisionReasons,
            boolean defaultBranchSelected
    ) {
    }

    private boolean isNodeCurrentlyActive(String processInstanceId, String nodeId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(nodeId)
                .active()
                .count() > 0;
    }

    private InclusiveSelectionSummary inclusiveSelectionSummary(
            Map<String, Object> processVariables,
            String splitNodeId,
            String defaultBranchId
    ) {
        Map<String, Object> summary = mapValue(processVariables.get(INCLUSIVE_SELECTION_SUMMARY_PREFIX + splitNodeId));
        if (summary.isEmpty()) {
            return InclusiveSelectionSummary.empty();
        }
        String resolvedDefaultBranchId = stringValueOrDefault(summary.get("defaultBranchId"), defaultBranchId);
        List<String> selectedEdgeIds = stringListValue(summary.get("selectedEdgeIds"));
        return new InclusiveSelectionSummary(
                integerValue(summary.get("eligibleBranchCount")),
                selectedEdgeIds,
                stringListValue(summary.get("selectedTargetNodeIds")),
                stringListValue(summary.get("selectedLabels")),
                integerListValue(summary.get("selectedPriorities")),
                stringListValue(summary.get("selectedDecisionReasons")),
                resolvedDefaultBranchId != null && selectedEdgeIds.contains(resolvedDefaultBranchId)
        );
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (Object item : values) {
            String text = stringValue(item);
            if (text != null) {
                results.add(text);
            }
        }
        return List.copyOf(results);
    }

    private List<Integer> integerListValue(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<Integer> results = new ArrayList<>();
        for (Object item : values) {
            Integer resolved = integerValue(item);
            if (resolved != null) {
                results.add(resolved);
            }
        }
        return List.copyOf(results);
    }

    private Integer integerValue(Object value) {
        return runtimeProcessMetadataService.integerValue(value);
    }
}
