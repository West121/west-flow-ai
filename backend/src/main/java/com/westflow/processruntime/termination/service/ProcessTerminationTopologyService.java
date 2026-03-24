package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.termination.api.ProcessTerminationNodeResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationSnapshotResponse;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.processruntime.service.RuntimeAppendLinkService;
import com.westflow.processruntime.termination.model.ProcessTerminationCommand;
import com.westflow.processruntime.termination.model.ProcessTerminationNodeKind;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 终止拓扑与监控数据构建服务。
 */
@Service
@RequiredArgsConstructor
public class ProcessTerminationTopologyService {

    private final ProcessLinkService processLinkService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;

    public ProcessTerminationPlanResponse preview(ProcessTerminationCommand command) {
        List<ProcessTerminationNodeResponse> nodes = buildNodes(command);
        return new ProcessTerminationPlanResponse(
                resolveRootInstanceId(command),
                resolveTargetInstanceId(command),
                command.scope(),
                command.propagationPolicy(),
                normalizeReason(command.reason()),
                normalizeOperator(command.operatorUserId()),
                Instant.now(),
                countTargets(nodes),
                nodes
        );
    }

    public ProcessTerminationSnapshotResponse snapshot(ProcessTerminationCommand command) {
        List<ProcessTerminationNodeResponse> nodes = buildNodes(command);
        String summary = String.format(
                "scope=%s, propagation=%s, targets=%d",
                command.scope(),
                command.propagationPolicy(),
                countTargets(nodes)
        );
        return new ProcessTerminationSnapshotResponse(
                resolveRootInstanceId(command),
                command.scope(),
                command.propagationPolicy(),
                normalizeReason(command.reason()),
                normalizeOperator(command.operatorUserId()),
                summary,
                countTargets(nodes),
                Instant.now(),
                nodes
        );
    }

    public ProcessTerminationCommand normalize(ProcessTerminationCommand command) {
        return new ProcessTerminationCommand(
                resolveRootInstanceId(command),
                resolveTargetInstanceId(command),
                command.scope(),
                command.propagationPolicy(),
                normalizeReason(command.reason()),
                normalizeOperator(command.operatorUserId())
        );
    }

    private List<ProcessTerminationNodeResponse> buildNodes(ProcessTerminationCommand command) {
        String rootInstanceId = resolveRootInstanceId(command);
        String targetInstanceId = resolveTargetInstanceId(command);
        Map<String, List<ProcessLinkRecord>> childProcessMap = processLinkService.listByRootInstanceId(rootInstanceId).stream()
                .collect(Collectors.groupingBy(ProcessLinkRecord::parentInstanceId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<RuntimeAppendLinkRecord>> appendMap = runtimeAppendLinkService.listByRootInstanceId(rootInstanceId).stream()
                .collect(Collectors.groupingBy(RuntimeAppendLinkRecord::parentInstanceId, LinkedHashMap::new, Collectors.toList()));

        String startInstanceId = command.scope() == ProcessTerminationScope.CHILD && targetInstanceId != null
                ? targetInstanceId
                : rootInstanceId;
        if (startInstanceId == null || startInstanceId.isBlank()) {
            return List.of();
        }
        List<ProcessTerminationNodeResponse> nodes = new ArrayList<>();
        if (command.scope() != ProcessTerminationScope.CHILD || targetInstanceId == null || targetInstanceId.equals(rootInstanceId)) {
            collectChildren(startInstanceId, rootInstanceId, null, command.propagationPolicy(), childProcessMap, appendMap, nodes);
        } else {
            Optional<ProcessTerminationNodeResponse> targetNode = findSubtreeNode(
                    startInstanceId,
                    rootInstanceId,
                    null,
                    command.propagationPolicy(),
                    childProcessMap,
                    appendMap
            );
            targetNode.ifPresent(nodes::add);
        }
        return nodes;
    }

    private void collectChildren(
            String parentInstanceId,
            String rootInstanceId,
            String parentNodeId,
            ProcessTerminationPropagationPolicy propagationPolicy,
            Map<String, List<ProcessLinkRecord>> childProcessMap,
            Map<String, List<RuntimeAppendLinkRecord>> appendMap,
            List<ProcessTerminationNodeResponse> targetNodes
    ) {
        if (propagationPolicy.includesChildProcesses()) {
            childProcessMap.getOrDefault(parentInstanceId, List.of()).forEach(link -> {
                List<ProcessTerminationNodeResponse> children = new ArrayList<>();
                if (shouldTraverseDescendants(link)) {
                    collectChildren(
                            link.childInstanceId(),
                            rootInstanceId,
                            link.parentNodeId(),
                            propagationPolicy,
                            childProcessMap,
                            appendMap,
                            children
                    );
                }
                targetNodes.add(processLinkNode(link, parentInstanceId, parentNodeId, children));
            });
        }

        if (propagationPolicy.includesAppends()) {
            appendMap.getOrDefault(parentInstanceId, List.of()).forEach(link -> {
                List<ProcessTerminationNodeResponse> children = new ArrayList<>();
                if (link.targetInstanceId() != null && !link.targetInstanceId().isBlank()) {
                    collectChildren(
                            link.targetInstanceId(),
                            rootInstanceId,
                            link.sourceNodeId(),
                            propagationPolicy,
                            childProcessMap,
                            appendMap,
                            children
                    );
                }
                targetNodes.add(appendLinkNode(link, parentInstanceId, parentNodeId, children));
            });
        }
    }

    private Optional<ProcessTerminationNodeResponse> findSubtreeNode(
            String targetInstanceId,
            String rootInstanceId,
            String parentNodeId,
            ProcessTerminationPropagationPolicy propagationPolicy,
            Map<String, List<ProcessLinkRecord>> childProcessMap,
            Map<String, List<RuntimeAppendLinkRecord>> appendMap
    ) {
        List<ProcessLinkRecord> processLinks = childProcessMap.getOrDefault(rootInstanceId, List.of());
        for (ProcessLinkRecord link : processLinks) {
            if (targetInstanceId.equals(link.childInstanceId())) {
                List<ProcessTerminationNodeResponse> children = new ArrayList<>();
                if (shouldTraverseDescendants(link)) {
                    collectChildren(link.childInstanceId(), rootInstanceId, link.parentNodeId(), propagationPolicy, childProcessMap, appendMap, children);
                }
                return Optional.of(processLinkNode(link, rootInstanceId, parentNodeId, children));
            }
            if (shouldTraverseDescendants(link)) {
                Optional<ProcessTerminationNodeResponse> nested = findSubtreeNode(
                        targetInstanceId,
                        link.childInstanceId(),
                        link.parentNodeId(),
                        propagationPolicy,
                        childProcessMap,
                        appendMap
                );
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }

        for (RuntimeAppendLinkRecord link : appendMap.getOrDefault(rootInstanceId, List.of())) {
            String appendTargetId = resolveAppendTargetId(link);
            if (targetInstanceId.equals(appendTargetId)) {
                List<ProcessTerminationNodeResponse> children = new ArrayList<>();
                if (link.targetInstanceId() != null && !link.targetInstanceId().isBlank()) {
                    collectChildren(link.targetInstanceId(), rootInstanceId, link.sourceNodeId(), propagationPolicy, childProcessMap, appendMap, children);
                }
                return Optional.of(appendLinkNode(link, rootInstanceId, parentNodeId, children));
            }
            if (link.targetInstanceId() != null && !link.targetInstanceId().isBlank()) {
                Optional<ProcessTerminationNodeResponse> nested = findSubtreeNode(
                        targetInstanceId,
                        link.targetInstanceId(),
                        link.sourceNodeId(),
                        propagationPolicy,
                        childProcessMap,
                        appendMap
                );
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private ProcessTerminationNodeResponse processLinkNode(
            ProcessLinkRecord link,
            String parentInstanceId,
            String parentNodeId,
            List<ProcessTerminationNodeResponse> children
    ) {
        return new ProcessTerminationNodeResponse(
                link.id(),
                link.childInstanceId(),
                parentInstanceId,
                parentNodeId,
                ProcessTerminationNodeKind.SUBPROCESS.name(),
                link.linkType(),
                null,
                null,
                null,
                link.status(),
                link.terminatePolicy(),
                link.childFinishPolicy(),
                null,
                link.parentNodeId(),
                link.calledProcessKey(),
                link.calledDefinitionId(),
                null,
                null,
                null,
                link.createdAt(),
                link.finishedAt(),
                children
        );
    }

    private ProcessTerminationNodeResponse appendLinkNode(
            RuntimeAppendLinkRecord link,
            String parentInstanceId,
            String parentNodeId,
            List<ProcessTerminationNodeResponse> children
    ) {
        return new ProcessTerminationNodeResponse(
                link.id(),
                resolveAppendTargetId(link),
                parentInstanceId,
                parentNodeId,
                determineAppendKind(link).name(),
                null,
                link.runtimeLinkType(),
                link.triggerMode(),
                link.appendType(),
                link.status(),
                link.policy(),
                null,
                link.sourceTaskId(),
                link.sourceNodeId(),
                link.calledProcessKey(),
                link.calledDefinitionId(),
                link.targetUserId(),
                link.operatorUserId(),
                link.commentText(),
                link.createdAt(),
                link.finishedAt(),
                children
        );
    }

    private ProcessTerminationNodeKind determineAppendKind(RuntimeAppendLinkRecord link) {
        if ("SUBPROCESS".equalsIgnoreCase(link.appendType()) || "ADHOC_SUBPROCESS".equalsIgnoreCase(link.runtimeLinkType())) {
            return ProcessTerminationNodeKind.APPEND_SUBPROCESS;
        }
        return ProcessTerminationNodeKind.APPEND_TASK;
    }

    private boolean shouldTraverseDescendants(ProcessLinkRecord link) {
        String callScope = link.callScope();
        return callScope == null || callScope.isBlank() || "CHILD_AND_DESCENDANTS".equals(callScope);
    }

    private String resolveAppendTargetId(RuntimeAppendLinkRecord link) {
        if (link.targetInstanceId() != null && !link.targetInstanceId().isBlank()) {
            return link.targetInstanceId();
        }
        return link.targetTaskId();
    }

    private int countTargets(List<ProcessTerminationNodeResponse> nodes) {
        return nodes.stream()
                .mapToInt(node -> 1 + countTargets(node.children()))
                .sum();
    }

    private String resolveRootInstanceId(ProcessTerminationCommand command) {
        return command.rootInstanceId() == null || command.rootInstanceId().isBlank()
                ? command.targetInstanceId()
                : command.rootInstanceId();
    }

    private String resolveTargetInstanceId(ProcessTerminationCommand command) {
        return command.targetInstanceId() == null || command.targetInstanceId().isBlank()
                ? resolveRootInstanceId(command)
                : command.targetInstanceId();
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "未填写原因" : reason.trim();
    }

    private String normalizeOperator(String operatorUserId) {
        return operatorUserId == null || operatorUserId.isBlank() ? "system" : operatorUserId.trim();
    }
}
