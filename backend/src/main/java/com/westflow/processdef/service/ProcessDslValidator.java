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

    public void validate(ProcessDslPayload payload) {
        Map<String, ProcessDslPayload.Node> nodeById = indexNodes(payload.nodes());
        Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::source);
        Map<String, List<ProcessDslPayload.Edge>> incomingEdges = indexEdges(payload.edges(), ProcessDslPayload.Edge::target);

        validateEdgeReferences(payload.edges(), nodeById);
        validateSingleStart(nodeById.values());
        validateAtLeastOneEnd(nodeById.values());
        validateApproverAssignments(nodeById.values());
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
            Object assignment = safeConfig(node).get("assignment");
            if (!(assignment instanceof Map<?, ?> assignmentMap) || assignmentMap.isEmpty()) {
                throw invalid("approver 节点必须配置 assignment", Map.of("nodeId", node.id()));
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
            if (defaultEdgeId != null && edges.stream().noneMatch(edge -> edge.id().equals(defaultEdgeId))) {
                throw invalid("condition 节点 defaultEdgeId 必须指向出边", Map.of("nodeId", node.id(), "defaultEdgeId", defaultEdgeId));
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
