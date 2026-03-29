package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processruntime.api.response.InclusiveGatewayHitResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeInclusiveGatewayQueryService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeInclusiveGatewaySupportService runtimeInclusiveGatewaySupportService;

    public List<InclusiveGatewayHitResponse> inclusiveGatewayHits(String instanceId) {
        HistoricProcessInstance historicProcessInstance = runtimeProcessMetadataService.requireHistoricProcessInstance(instanceId);
        Map<String, Object> processVariables = runtimeProcessMetadataService.runtimeOrHistoricVariables(instanceId);
        PublishedProcessDefinition definition = runtimeProcessMetadataService.resolvePublishedDefinition(
                null,
                runtimeInclusiveGatewaySupportService.stringValue(processVariables.get("westflowProcessDefinitionId")),
                runtimeInclusiveGatewaySupportService.stringValue(processVariables.get("westflowProcessKey")),
                instanceId
        );
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instanceId)
                .active()
                .list();
        return buildInclusiveGatewayHits(instanceId, definition.dsl(), historicProcessInstance, activeTasks);
    }

    public List<InclusiveGatewayHitResponse> buildInclusiveGatewayHits(
            String processInstanceId,
            ProcessDslPayload payload,
            HistoricProcessInstance historicProcessInstance,
            List<Task> activeTasks
    ) {
        if (payload == null || payload.nodes().isEmpty() || payload.edges().isEmpty()) {
            return List.of();
        }
        Map<String, Object> processVariables = runtimeProcessMetadataService.runtimeOrHistoricVariables(processInstanceId);
        Map<String, ProcessDslPayload.Node> nodeById = payload.nodes().stream()
                .collect(Collectors.toMap(ProcessDslPayload.Node::id, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = payload.edges().stream()
                .filter(edge -> edge.source() != null && !edge.source().isBlank())
                .collect(Collectors.groupingBy(ProcessDslPayload.Edge::source, LinkedHashMap::new, Collectors.toList()));
        Map<String, ProcessDslPayload.Edge> edgeById = payload.edges().stream()
                .filter(edge -> edge.id() != null && !edge.id().isBlank())
                .collect(Collectors.toMap(ProcessDslPayload.Edge::id, edge -> edge, (left, right) -> left, LinkedHashMap::new));
        Map<String, OffsetDateTime> firstOccurredAtByNodeId = new LinkedHashMap<>();
        Map<String, List<HistoricActivityInstance>> historicActivitiesByNodeId = flowableEngineFacade.historyService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .stream()
                .collect(Collectors.groupingBy(HistoricActivityInstance::getActivityId, LinkedHashMap::new, Collectors.toList()));
        Set<String> reachedNodeIds = new LinkedHashSet<>();
        for (HistoricActivityInstance activity : historicActivitiesByNodeId.values().stream().flatMap(Collection::stream).toList()) {
            reachedNodeIds.add(activity.getActivityId());
            firstOccurredAtByNodeId.merge(
                    activity.getActivityId(),
                    runtimeInclusiveGatewaySupportService.toOffsetDateTime(activity.getStartTime()),
                    (left, right) -> left == null || right == null ? Objects.requireNonNullElse(left, right) : left.isBefore(right) ? left : right
            );
        }
        Set<String> activeTaskNodeIds = activeTasks.stream()
                .map(Task::getTaskDefinitionKey)
                .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Task activeTask : activeTasks) {
            firstOccurredAtByNodeId.putIfAbsent(
                activeTask.getTaskDefinitionKey(),
                runtimeInclusiveGatewaySupportService.toOffsetDateTime(activeTask.getCreateTime())
            );
        }

        List<InclusiveGatewayHitResponse> hits = new ArrayList<>();
        for (ProcessDslPayload.Node split : payload.nodes().stream().filter(node -> "inclusive_split".equals(node.type())).toList()) {
            ProcessDslPayload.Node join = runtimeInclusiveGatewaySupportService.resolveNearestInclusiveJoin(split.id(), nodeById, outgoingEdges);
            List<ProcessDslPayload.Edge> branchEdges = outgoingEdges.getOrDefault(split.id(), List.of());
            if (branchEdges.isEmpty()) {
                continue;
            }
            Map<String, Object> splitConfig = mapValue(split.config());
            String branchMergePolicy = runtimeInclusiveGatewaySupportService.stringValueOrDefault(splitConfig.get("branchMergePolicy"), "ANY_PASS");
            Integer requiredBranchCount = integerValue(splitConfig.get("requiredBranchCount"));
            String defaultBranchId = runtimeInclusiveGatewaySupportService.stringValue(splitConfig.get("defaultBranchId"));
            if (defaultBranchId == null) {
                defaultBranchId = runtimeInclusiveGatewaySupportService.stringValue(splitConfig.get("defaultEdgeId"));
            }
            RuntimeInclusiveGatewaySupportService.SelectionSummary selectionSummary =
                    runtimeInclusiveGatewaySupportService.selectionSummary(processVariables, split.id(), defaultBranchId);

            List<String> activatedTargetNodeIds = new ArrayList<>();
            List<String> activatedTargetNodeNames = new ArrayList<>();
            List<String> skippedTargetNodeIds = new ArrayList<>();
            List<String> skippedTargetNodeNames = new ArrayList<>();
            List<Integer> branchPriorities = new ArrayList<>();
            List<String> branchLabels = new ArrayList<>();
            List<String> branchExpressions = new ArrayList<>();
            List<String> selectedEdgeIds = new ArrayList<>();
            List<String> selectedBranchLabels = new ArrayList<>();
            List<Integer> selectedBranchPriorities = new ArrayList<>();
            List<String> selectedDecisionReasons = new ArrayList<>();
            boolean defaultBranchSelected = false;
            OffsetDateTime firstActivatedAt = null;
            int branchIndex = 0;
            for (ProcessDslPayload.Edge edge : branchEdges) {
                String targetNodeId = edge.target();
                branchPriorities.add(edge.priority() == null ? branchIndex : edge.priority());
                branchLabels.add(edge.label() == null || edge.label().isBlank() ? targetNodeId : edge.label());
                String expression = runtimeInclusiveGatewaySupportService.edgeConditionExpression(edge);
                if (expression != null) {
                    branchExpressions.add(expression);
                }
                ProcessDslPayload.Node targetNode = nodeById.get(targetNodeId);
                boolean activated = selectionSummary.selectedEdgeIds().isEmpty()
                        ? runtimeInclusiveGatewaySupportService.hasReachedInclusiveBranchTarget(
                                targetNodeId,
                                join == null ? null : join.id(),
                                outgoingEdges,
                                reachedNodeIds,
                                new LinkedHashSet<>()
                        )
                        : selectionSummary.selectedEdgeIds().contains(edge.id());
                if (activated) {
                    selectedEdgeIds.add(edge.id());
                    selectedBranchLabels.add(edge.label() == null || edge.label().isBlank() ? targetNodeId : edge.label());
                    selectedBranchPriorities.add(edge.priority() == null ? branchIndex : edge.priority());
                    if (!selectionSummary.selectedDecisionReasons().isEmpty()) {
                        int selectedIndex = selectionSummary.selectedEdgeIds().indexOf(edge.id());
                        if (selectedIndex >= 0 && selectedIndex < selectionSummary.selectedDecisionReasons().size()) {
                            selectedDecisionReasons.add(selectionSummary.selectedDecisionReasons().get(selectedIndex));
                        }
                    }
                    if (defaultBranchId != null && defaultBranchId.equals(edge.id())) {
                        defaultBranchSelected = true;
                    }
                    activatedTargetNodeIds.add(targetNodeId);
                    activatedTargetNodeNames.add(targetNode == null ? targetNodeId : targetNode.name());
                    OffsetDateTime occurredAt = runtimeInclusiveGatewaySupportService.resolveInclusiveBranchOccurredAt(
                            targetNodeId,
                            join == null ? null : join.id(),
                            outgoingEdges,
                            firstOccurredAtByNodeId,
                            new LinkedHashSet<>()
                    );
                    if (occurredAt != null && (firstActivatedAt == null || occurredAt.isBefore(firstActivatedAt))) {
                        firstActivatedAt = occurredAt;
                    }
                } else {
                    skippedTargetNodeIds.add(targetNodeId);
                    skippedTargetNodeNames.add(targetNode == null ? targetNodeId : targetNode.name());
                }
                branchIndex++;
            }

            List<String> resolvedSelectedEdgeIds = selectionSummary.selectedEdgeIds().isEmpty()
                    ? List.copyOf(selectedEdgeIds)
                    : selectionSummary.selectedEdgeIds();
            List<String> resolvedSelectedBranchLabels = selectionSummary.selectedBranchLabels().isEmpty()
                    ? List.copyOf(selectedBranchLabels)
                    : selectionSummary.selectedBranchLabels();
            List<Integer> resolvedSelectedBranchPriorities = selectionSummary.selectedBranchPriorities().isEmpty()
                    ? List.copyOf(selectedBranchPriorities)
                    : selectionSummary.selectedBranchPriorities();
            List<String> resolvedSelectedDecisionReasons = selectionSummary.selectedDecisionReasons().isEmpty()
                    ? List.copyOf(selectedDecisionReasons)
                    : selectionSummary.selectedDecisionReasons();
            boolean resolvedDefaultBranchSelected = selectionSummary.selectedEdgeIds().isEmpty()
                    ? defaultBranchSelected
                    : selectionSummary.defaultBranchSelected();
            List<String> resolvedSelectedTargetNodeIds = selectionSummary.selectedTargetNodeIds().isEmpty()
                    ? resolvedSelectedEdgeIds.stream()
                            .map(edgeById::get)
                            .filter(Objects::nonNull)
                            .map(ProcessDslPayload.Edge::target)
                            .filter(target -> target != null && !target.isBlank())
                            .toList()
                    : selectionSummary.selectedTargetNodeIds();
            int completedSelectedTargetCount = runtimeInclusiveGatewaySupportService.countCompletedInclusiveTargets(
                    resolvedSelectedTargetNodeIds,
                    activeTaskNodeIds,
                    historicActivitiesByNodeId
            );
            int pendingSelectedTargetCount = Math.max(0, resolvedSelectedTargetNodeIds.size() - completedSelectedTargetCount);
            OffsetDateTime finishedAt = join == null ? null : firstOccurredAtByNodeId.get(join.id());
            String gatewayStatus = runtimeInclusiveGatewaySupportService.resolveInclusiveGatewayStatus(
                    historicProcessInstance,
                    join == null ? null : join.id(),
                    finishedAt,
                    activatedTargetNodeIds
            );

            hits.add(new InclusiveGatewayHitResponse(
                    split.id(),
                    split.name(),
                    join == null ? null : join.id(),
                    join == null ? null : join.name(),
                    defaultBranchId,
                    requiredBranchCount,
                    branchMergePolicy,
                    gatewayStatus,
                    branchEdges.size(),
                    selectionSummary.eligibleTargetCount() == null
                            ? activatedTargetNodeIds.size()
                            : selectionSummary.eligibleTargetCount(),
                    activatedTargetNodeIds.size(),
                    List.copyOf(activatedTargetNodeIds),
                    List.copyOf(activatedTargetNodeNames),
                    List.copyOf(skippedTargetNodeIds),
                    List.copyOf(skippedTargetNodeNames),
                    List.copyOf(branchPriorities),
                    List.copyOf(branchLabels),
                    List.copyOf(branchExpressions),
                    completedSelectedTargetCount,
                    pendingSelectedTargetCount,
                    resolvedSelectedEdgeIds,
                    resolvedSelectedBranchLabels,
                    resolvedSelectedBranchPriorities,
                    resolvedSelectedDecisionReasons,
                    resolvedDefaultBranchSelected,
                    runtimeInclusiveGatewaySupportService.buildInclusiveDecisionSummary(
                            branchEdges.size(),
                            selectionSummary.eligibleTargetCount() == null
                                    ? activatedTargetNodeIds.size()
                                    : selectionSummary.eligibleTargetCount(),
                            activatedTargetNodeIds.size(),
                            branchMergePolicy,
                            resolvedDefaultBranchSelected,
                            resolvedSelectedEdgeIds.size(),
                            completedSelectedTargetCount,
                            join != null
                    ),
                    firstActivatedAt,
                    finishedAt
            ));
        }
        return hits;
    }

    public List<ProcessInstanceEventResponse> buildInclusiveGatewayEvents(
            String instanceId,
            List<InclusiveGatewayHitResponse> hits
    ) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<ProcessInstanceEventResponse> events = new ArrayList<>();
        for (InclusiveGatewayHitResponse hit : hits) {
            if (!hit.activatedTargetNodeIds().isEmpty()) {
                events.add(new ProcessInstanceEventResponse(
                        instanceId + "::inclusive::" + hit.splitNodeId(),
                        instanceId,
                        null,
                        hit.splitNodeId(),
                        "INCLUSIVE_BRANCH_ACTIVATED",
                        "包容分支已命中",
                        "ROUTE",
                        null,
                        null,
                        null,
                        null,
                        hit.firstActivatedAt(),
                        runtimeInclusiveGatewaySupportService.inclusiveGatewayEventDetails(hit, false),
                        null,
                        hit.joinNodeId(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
            if (hit.finishedAt() != null && hit.joinNodeId() != null) {
                events.add(new ProcessInstanceEventResponse(
                        instanceId + "::inclusive-join::" + hit.joinNodeId(),
                        instanceId,
                        null,
                        hit.joinNodeId(),
                        "INCLUSIVE_GATEWAY_JOINED",
                        "包容汇聚已完成",
                        "ROUTE",
                        null,
                        null,
                        null,
                        null,
                        hit.finishedAt(),
                        runtimeInclusiveGatewaySupportService.inclusiveGatewayEventDetails(hit, true),
                        null,
                        hit.joinNodeId(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }
        return events;
    }

    private Integer integerValue(Object value) {
        return runtimeProcessMetadataService.integerValue(value);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
