package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProcessDemoService {

    private final ProcessDefinitionService processDefinitionService;
    private final Map<String, DemoProcessInstance> instancesById = new ConcurrentHashMap<>();
    private final Map<String, DemoTask> tasksById = new ConcurrentHashMap<>();

    public ProcessDemoService(ProcessDefinitionService processDefinitionService) {
        this.processDefinitionService = processDefinitionService;
    }

    public synchronized StartProcessResponse start(StartProcessRequest request) {
        PublishedProcessDefinition definition = processDefinitionService.getLatestByProcessKey(request.processKey());
        Graph graph = Graph.from(definition.dsl());
        ProcessDslPayload.Node startNode = graph.startNode();

        DemoProcessInstance instance = new DemoProcessInstance(
                newId("pi"),
                definition.processDefinitionId(),
                definition.processKey(),
                request.businessKey(),
                StpUtil.getLoginIdAsString()
        );
        instancesById.put(instance.instanceId, instance);

        List<DemoTaskView> activeTasks = advanceFromNode(definition, graph, instance, startNode.id());
        refreshStatus(instance);
        return new StartProcessResponse(definition.processDefinitionId(), instance.instanceId, instance.status, activeTasks);
    }

    public synchronized CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        DemoTask task = tasksById.get(taskId);
        if (task == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "任务不存在",
                    Map.of("taskId", taskId)
            );
        }
        if (!"PENDING".equals(task.status)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许重复完成",
                    Map.of(
                            "taskId", taskId,
                            "currentStatus", task.status,
                            "action", request.action()
                    )
            );
        }

        DemoProcessInstance instance = instancesById.get(task.instanceId);
        if (instance == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程实例不存在",
                    Map.of("instanceId", task.instanceId)
            );
        }

        task.status = "COMPLETED";
        task.action = request.action();
        task.operatorUserId = request.operatorUserId() == null || request.operatorUserId().isBlank()
                ? StpUtil.getLoginIdAsString()
                : request.operatorUserId();
        task.comment = request.comment();
        instance.activeTaskIds.remove(taskId);

        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        List<DemoTaskView> nextTasks = continueAlongOutgoing(definition, graph, instance, task.nodeId);
        refreshStatus(instance);

        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, nextTasks);
    }

    private List<DemoTaskView> advanceFromNode(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            String nodeId
    ) {
        ProcessDslPayload.Node node = graph.nodeById.get(nodeId);
        if (node == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "流程图存在未知节点",
                    Map.of("nodeId", nodeId, "processDefinitionId", definition.processDefinitionId())
            );
        }

        return switch (node.type()) {
            case "start", "cc" -> continueAlongOutgoing(definition, graph, instance, node.id());
            case "approver" -> List.of(createTask(instance, node));
            case "condition" -> advanceCondition(definition, graph, instance, node);
            case "parallel_split" -> advanceParallelSplit(definition, graph, instance, node);
            case "parallel_join" -> advanceParallelJoin(definition, graph, instance, node);
            case "end" -> {
                instance.reachedEndNodeIds.add(node.id());
                refreshStatus(instance);
                yield List.of();
            }
            default -> throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前节点类型暂不支持运行",
                    Map.of("nodeType", node.type(), "nodeId", node.id())
            );
        };
    }

    private List<DemoTaskView> continueAlongOutgoing(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            String nodeId
    ) {
        List<DemoTaskView> tasks = new ArrayList<>();
        for (ProcessDslPayload.Edge edge : graph.outgoingEdges.getOrDefault(nodeId, List.of())) {
            tasks.addAll(advanceFromNode(definition, graph, instance, edge.target()));
        }
        refreshStatus(instance);
        return tasks;
    }

    private List<DemoTaskView> advanceCondition(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node
    ) {
        List<ProcessDslPayload.Edge> outgoing = graph.outgoingEdges.getOrDefault(node.id(), List.of());
        if (outgoing.isEmpty()) {
            refreshStatus(instance);
            return List.of();
        }

        String defaultEdgeId = stringValue(safeConfig(node).get("defaultEdgeId"));
        ProcessDslPayload.Edge selected = outgoing.stream()
                .filter(edge -> edge.id().equals(defaultEdgeId))
                .findFirst()
                .orElse(outgoing.get(0));
        return advanceFromNode(definition, graph, instance, selected.target());
    }

    private List<DemoTaskView> advanceParallelSplit(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node
    ) {
        List<DemoTaskView> tasks = new ArrayList<>();
        for (ProcessDslPayload.Edge edge : graph.outgoingEdges.getOrDefault(node.id(), List.of())) {
            tasks.addAll(advanceFromNode(definition, graph, instance, edge.target()));
        }
        refreshStatus(instance);
        return tasks;
    }

    private List<DemoTaskView> advanceParallelJoin(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node
    ) {
        int expectedArrivals = graph.incomingEdges.getOrDefault(node.id(), List.of()).size();
        int currentArrivals = instance.joinArrivals.merge(node.id(), 1, Integer::sum);
        if (currentArrivals < expectedArrivals) {
            refreshStatus(instance);
            return List.of();
        }

        instance.joinArrivals.remove(node.id());
        return continueAlongOutgoing(definition, graph, instance, node.id());
    }

    private DemoTaskView createTask(DemoProcessInstance instance, ProcessDslPayload.Node node) {
        String taskId = newId("task");
        Map<String, Object> assignment = mapValue(safeConfig(node).get("assignment"));
        DemoTask task = new DemoTask(taskId, instance.instanceId, node.id(), node.name(), assignment);
        tasksById.put(taskId, task);
        instance.activeTaskIds.add(taskId);
        refreshStatus(instance);
        return task.toView();
    }

    private void refreshStatus(DemoProcessInstance instance) {
        if (instance.activeTaskIds.isEmpty() && instance.joinArrivals.isEmpty() && !instance.reachedEndNodeIds.isEmpty()) {
            instance.status = "COMPLETED";
            return;
        }
        instance.status = "RUNNING";
    }

    private Map<String, Object> safeConfig(ProcessDslPayload.Node node) {
        return node.config() == null ? Map.of() : node.config();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class Graph {
        private final Map<String, ProcessDslPayload.Node> nodeById;
        private final Map<String, List<ProcessDslPayload.Edge>> outgoingEdges;
        private final Map<String, List<ProcessDslPayload.Edge>> incomingEdges;

        private Graph(
                Map<String, ProcessDslPayload.Node> nodeById,
                Map<String, List<ProcessDslPayload.Edge>> outgoingEdges,
                Map<String, List<ProcessDslPayload.Edge>> incomingEdges
        ) {
            this.nodeById = nodeById;
            this.outgoingEdges = outgoingEdges;
            this.incomingEdges = incomingEdges;
        }

        private static Graph from(ProcessDslPayload payload) {
            Map<String, ProcessDslPayload.Node> nodeById = new HashMap<>();
            for (ProcessDslPayload.Node node : payload.nodes()) {
                nodeById.put(node.id(), node);
            }

            Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = new HashMap<>();
            Map<String, List<ProcessDslPayload.Edge>> incomingEdges = new HashMap<>();
            Comparator<ProcessDslPayload.Edge> comparator = Comparator
                    .comparing(ProcessDslPayload.Edge::priority, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(ProcessDslPayload.Edge::id);

            for (ProcessDslPayload.Edge edge : payload.edges()) {
                outgoingEdges.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
                incomingEdges.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
            }
            outgoingEdges.values().forEach(list -> list.sort(comparator));
            incomingEdges.values().forEach(list -> list.sort(comparator));

            return new Graph(nodeById, outgoingEdges, incomingEdges);
        }

        private ProcessDslPayload.Node startNode() {
            return nodeById.values().stream()
                    .filter(node -> "start".equals(node.type()))
                    .findFirst()
                    .orElseThrow(() -> new ContractException(
                            "VALIDATION.REQUEST_INVALID",
                            HttpStatus.BAD_REQUEST,
                            "流程定义缺少 start 节点",
                            Map.of()
                    ));
        }
    }

    private final class DemoTask {
        private final String taskId;
        private final String instanceId;
        private final String nodeId;
        private final String nodeName;
        private final Map<String, Object> assignment;
        private String status = "PENDING";
        private String action;
        private String operatorUserId;
        private String comment;

        private DemoTask(
                String taskId,
                String instanceId,
                String nodeId,
                String nodeName,
                Map<String, Object> assignment
        ) {
            this.taskId = taskId;
            this.instanceId = instanceId;
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.assignment = assignment;
        }

        private DemoTaskView toView() {
            return new DemoTaskView(
                    taskId,
                    nodeId,
                    nodeName,
                    status,
                    stringValue(assignment.get("mode")),
                    stringListValue(assignment.get("userIds"))
            );
        }
    }

    private static final class DemoProcessInstance {
        private final String instanceId;
        private final String processDefinitionId;
        private final String processKey;
        private final String businessKey;
        private final String initiatorUserId;
        private final Set<String> activeTaskIds = new HashSet<>();
        private final Set<String> reachedEndNodeIds = new HashSet<>();
        private final Map<String, Integer> joinArrivals = new HashMap<>();
        private String status = "RUNNING";

        private DemoProcessInstance(
                String instanceId,
                String processDefinitionId,
                String processKey,
                String businessKey,
                String initiatorUserId
        ) {
            this.instanceId = instanceId;
            this.processDefinitionId = processDefinitionId;
            this.processKey = processKey;
            this.businessKey = businessKey;
            this.initiatorUserId = initiatorUserId;
        }
    }
}
