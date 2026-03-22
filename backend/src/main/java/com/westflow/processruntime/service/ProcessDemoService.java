package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.ClaimTaskRequest;
import com.westflow.processruntime.api.ClaimTaskResponse;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.ReturnTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.TransferTaskRequest;
import com.westflow.processruntime.api.WorkflowFieldBinding;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProcessDemoService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of(
            "status",
            "processKey",
            "processName",
            "nodeName",
            "businessKey",
            "instanceId",
            "applicantUserId"
    );
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of(
            "createdAt",
            "updatedAt",
            "completedAt",
            "processKey",
            "processName",
            "nodeName",
            "status",
            "businessKey",
            "applicantUserId"
    );

    private final ProcessDefinitionService processDefinitionService;
    private final Map<String, DemoProcessInstance> instancesById = new ConcurrentHashMap<>();
    private final Map<String, DemoTask> tasksById = new ConcurrentHashMap<>();
    private final List<DemoTaskActionLog> actionLogs = new ArrayList<>();

    public ProcessDemoService(ProcessDefinitionService processDefinitionService) {
        this.processDefinitionService = processDefinitionService;
    }

    public synchronized void reset() {
        instancesById.clear();
        tasksById.clear();
        actionLogs.clear();
    }

    public synchronized StartProcessResponse start(StartProcessRequest request) {
        PublishedProcessDefinition definition = processDefinitionService.getLatestByProcessKey(request.processKey());
        Graph graph = Graph.from(definition.dsl());
        ProcessDslPayload.Node startNode = graph.startNode();
        OffsetDateTime now = now();

        DemoProcessInstance instance = new DemoProcessInstance(
                newId("pi"),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                request.businessKey(),
                StpUtil.getLoginIdAsString(),
                request.formData(),
                now
        );
        instancesById.put(instance.instanceId, instance);

        List<DemoTaskView> activeTasks = advanceFromNode(definition, graph, instance, startNode.id(), null, null);
        refreshStatus(instance);
        return new StartProcessResponse(definition.processDefinitionId(), instance.instanceId, instance.status, activeTasks);
    }

    public synchronized PageResponse<ProcessTaskListItemResponse> page(PageRequest request) {
        List<DemoTask> filtered = tasksById.values().stream()
                .filter(task -> matches(task, request))
                .sorted(resolveComparator(request.sorts()))
                .toList();

        long total = filtered.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<ProcessTaskListItemResponse> records = total == 0
                ? List.of()
                : filtered.stream()
                        .skip(offset)
                        .limit(pageSize)
                        .map(task -> toListItem(task, requireInstance(task.instanceId)))
                        .toList();

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public synchronized ProcessTaskDetailResponse detail(String taskId) {
        DemoTask task = requireTask(taskId);
        DemoProcessInstance instance = requireInstance(task.instanceId);
        return toDetailResponse(task, instance);
    }

    public synchronized TaskActionAvailabilityResponse actions(String taskId) {
        DemoTask task = requireTask(taskId);
        return actionAvailability(task, currentUserId());
    }

    public synchronized ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();

        if (!"PENDING_CLAIM".equals(task.status)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不支持认领",
                    Map.of("taskId", taskId, "currentStatus", task.status)
            );
        }
        if (!task.candidateUserIds.contains(currentUserId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "当前用户不在任务候选范围内",
                    Map.of("taskId", taskId, "userId", currentUserId)
            );
        }

        task.status = "PENDING";
        task.assigneeUserId = currentUserId;
        task.operatorUserId = currentUserId;
        task.action = "CLAIM";
        task.comment = request.comment();
        task.updatedAt = now();
        logAction(task, "CLAIM", currentUserId, request.comment());

        return new ClaimTaskResponse(task.taskId, task.instanceId, task.status, task.assigneeUserId);
    }

    public synchronized CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = request.operatorUserId() == null || request.operatorUserId().isBlank()
                ? currentUserId()
                : request.operatorUserId();

        if (!"PENDING".equals(task.status) || task.assigneeUserId == null || !task.assigneeUserId.equals(currentUserId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许由该用户完成",
                    Map.of(
                            "taskId",
                            taskId,
                            "currentStatus",
                            task.status,
                            "action",
                            request.action(),
                            "userId",
                            currentUserId
                    )
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);

        OffsetDateTime now = now();
        task.status = "COMPLETED";
        task.action = request.action();
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.taskFormData = request.taskFormData() == null
                ? Map.of()
                : new HashMap<>(request.taskFormData());
        task.completedAt = now;
        task.updatedAt = now;
        instance.activeTaskIds.remove(taskId);
        instance.updatedAt = now;
        logAction(task, request.action(), currentUserId, request.comment());

        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        List<DemoTaskView> nextTasks = continueAlongOutgoing(definition, graph, instance, task.nodeId, task.taskId, task.originTaskId);
        refreshStatus(instance);

        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, nextTasks);
    }

    public synchronized CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingAssignee(task, currentUserId, "转办");

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        task.status = "TRANSFERRED";
        task.action = "TRANSFER";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.updatedAt = now;
        instance.activeTaskIds.remove(task.taskId);

        DemoTask nextTask = createTask(
                instance,
                task.nodeId,
                task.nodeName,
                Map.of(
                        "mode", "USER",
                        "userIds", List.of(request.targetUserId()),
                        "roleCodes", List.of(),
                        "departmentRef", "",
                        "formFieldKey", ""
                ),
                task.taskId,
                task.originTaskId
        );
        logAction(task, "TRANSFER", currentUserId, request.comment());
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingAssignee(task, currentUserId, "退回");

        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy();
        if (!"PREVIOUS_USER_TASK".equals(targetStrategy)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前仅支持退回上一步人工节点",
                    Map.of("targetStrategy", targetStrategy)
            );
        }

        DemoTask previousTask = task.previousTaskId == null ? null : tasksById.get(task.previousTaskId);
        if (previousTask == null) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不存在可退回的上一步人工节点",
                    Map.of("taskId", taskId)
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        task.status = "RETURNED";
        task.action = "RETURN";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.updatedAt = now;
        instance.activeTaskIds.remove(task.taskId);

        DemoTask nextTask = createTask(
                instance,
                previousTask.nodeId,
                previousTask.nodeName,
                resolveReturnAssignment(previousTask),
                task.taskId,
                previousTask.originTaskId
        );
        logAction(task, "RETURN", currentUserId, request.comment());
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of(nextTask.toView()));
    }

    private List<DemoTaskView> advanceFromNode(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            String nodeId,
            String previousTaskId,
            String originTaskId
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
            case "start", "cc" -> continueAlongOutgoing(definition, graph, instance, node.id(), previousTaskId, originTaskId);
            case "approver" -> List.of(createTask(instance, node, previousTaskId, originTaskId).toView());
            case "condition" -> advanceCondition(definition, graph, instance, node, previousTaskId, originTaskId);
            case "parallel_split" -> advanceParallelSplit(definition, graph, instance, node, previousTaskId, originTaskId);
            case "parallel_join" -> advanceParallelJoin(definition, graph, instance, node, previousTaskId, originTaskId);
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
            String nodeId,
            String previousTaskId,
            String originTaskId
    ) {
        List<DemoTaskView> tasks = new ArrayList<>();
        for (ProcessDslPayload.Edge edge : graph.outgoingEdges.getOrDefault(nodeId, List.of())) {
            tasks.addAll(advanceFromNode(definition, graph, instance, edge.target(), previousTaskId, originTaskId));
        }
        refreshStatus(instance);
        return tasks;
    }

    private List<DemoTaskView> advanceCondition(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId
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
        return advanceFromNode(definition, graph, instance, selected.target(), previousTaskId, originTaskId);
    }

    private List<DemoTaskView> advanceParallelSplit(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId
    ) {
        List<DemoTaskView> tasks = new ArrayList<>();
        for (ProcessDslPayload.Edge edge : graph.outgoingEdges.getOrDefault(node.id(), List.of())) {
            tasks.addAll(advanceFromNode(definition, graph, instance, edge.target(), previousTaskId, originTaskId));
        }
        refreshStatus(instance);
        return tasks;
    }

    private List<DemoTaskView> advanceParallelJoin(
            PublishedProcessDefinition definition,
            Graph graph,
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId
    ) {
        int expectedArrivals = graph.incomingEdges.getOrDefault(node.id(), List.of()).size();
        int currentArrivals = instance.joinArrivals.merge(node.id(), 1, Integer::sum);
        if (currentArrivals < expectedArrivals) {
            refreshStatus(instance);
            return List.of();
        }

        instance.joinArrivals.remove(node.id());
        return continueAlongOutgoing(definition, graph, instance, node.id(), previousTaskId, originTaskId);
    }

    private DemoTask createTask(
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId
    ) {
        return createTask(instance, node.id(), node.name(), mapValue(safeConfig(node).get("assignment")), previousTaskId, originTaskId);
    }

    private DemoTask createTask(
            DemoProcessInstance instance,
            String nodeId,
            String nodeName,
            Map<String, Object> assignment,
            String previousTaskId,
            String originTaskId
    ) {
        String taskId = newId("task");
        OffsetDateTime now = now();
        List<String> candidateUserIds = stringListValue(assignment.get("userIds"));
        String assigneeUserId = candidateUserIds.size() == 1 ? candidateUserIds.get(0) : null;
        String status = candidateUserIds.size() > 1 ? "PENDING_CLAIM" : "PENDING";
        DemoTask task = new DemoTask(
                taskId,
                instance.instanceId,
                nodeId,
                nodeName,
                new HashMap<>(assignment),
                candidateUserIds,
                assigneeUserId,
                previousTaskId,
                originTaskId == null ? taskId : originTaskId,
                now,
                now,
                status
        );
        tasksById.put(taskId, task);
        instance.activeTaskIds.add(taskId);
        refreshStatus(instance);
        return task;
    }

    private void refreshStatus(DemoProcessInstance instance) {
        instance.updatedAt = now();
        if (instance.activeTaskIds.isEmpty() && instance.joinArrivals.isEmpty() && !instance.reachedEndNodeIds.isEmpty()) {
            instance.status = "COMPLETED";
            return;
        }
        instance.status = "RUNNING";
    }

    private boolean matches(DemoTask task, PageRequest request) {
        DemoProcessInstance instance = requireInstance(task.instanceId);
        String keyword = request.keyword();
        if (keyword != null && !keyword.isBlank() && !containsKeyword(task, instance, keyword)) {
            return false;
        }

        for (FilterItem filter : request.filters()) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "不支持的筛选字段",
                        Map.of(
                                "field",
                                filter.field(),
                                "supportedFields",
                                SUPPORTED_FILTER_FIELDS
                        )
                );
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "任务列表目前仅支持等值筛选",
                        Map.of("operator", filter.operator())
                );
            }

            String value = filter.value() == null ? null : filter.value().asText();
            if (!matchesFilter(task, instance, filter.field(), value)) {
                return false;
            }
        }

        return true;
    }

    private boolean containsKeyword(DemoTask task, DemoProcessInstance instance, String keyword) {
        String normalized = keyword.trim().toLowerCase();
        return contains(task.taskId, normalized)
                || contains(task.nodeId, normalized)
                || contains(task.nodeName, normalized)
                || contains(task.status, normalized)
                || contains(task.action, normalized)
                || contains(task.comment, normalized)
                || contains(instance.instanceId, normalized)
                || contains(instance.processDefinitionId, normalized)
                || contains(instance.processKey, normalized)
                || contains(instance.processName, normalized)
                || contains(instance.businessKey, normalized)
                || contains(instance.initiatorUserId, normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private boolean matchesFilter(
            DemoTask task,
            DemoProcessInstance instance,
            String field,
            String value
    ) {
        return switch (field) {
            case "status" -> equalsValue(task.status, value);
            case "processKey" -> equalsValue(instance.processKey, value);
            case "processName" -> equalsValue(instance.processName, value);
            case "nodeName" -> equalsValue(task.nodeName, value);
            case "businessKey" -> equalsValue(instance.businessKey, value);
            case "instanceId" -> equalsValue(instance.instanceId, value);
            case "applicantUserId" -> equalsValue(instance.initiatorUserId, value);
            default -> throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "不支持的筛选字段",
                    Map.of("field", field)
            );
        };
    }

    private boolean equalsValue(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return actual == null || actual.isBlank();
        }
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private Comparator<DemoTask> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(
                            DemoTask::createdAt,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .reversed()
                    .thenComparing(DemoTask::taskId);
        }

        Comparator<DemoTask> comparator = null;
        for (SortItem sort : sorts) {
            Comparator<DemoTask> next = switch (sort.field()) {
                case "createdAt" -> Comparator.comparing(
                        DemoTask::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "updatedAt" -> Comparator.comparing(
                        DemoTask::updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "completedAt" -> Comparator.comparing(
                        DemoTask::completedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "processKey" -> stringComparator(task -> requireInstance(task.instanceId).processKey);
                case "processName" -> stringComparator(task -> requireInstance(task.instanceId).processName);
                case "nodeName" -> stringComparator(task -> task.nodeName);
                case "status" -> stringComparator(task -> task.status);
                case "businessKey" -> stringComparator(task -> requireInstance(task.instanceId).businessKey);
                case "applicantUserId" -> stringComparator(task -> requireInstance(task.instanceId).initiatorUserId);
                default -> throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "不支持的排序字段",
                        Map.of(
                                "field",
                                sort.field(),
                                "supportedFields",
                                SUPPORTED_SORT_FIELDS
                        )
                );
            };
            if ("desc".equalsIgnoreCase(sort.direction())) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        if (comparator == null) {
            comparator = Comparator.comparing(DemoTask::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }

        return comparator.thenComparing(DemoTask::taskId);
    }

    private Comparator<DemoTask> stringComparator(Function<DemoTask, String> extractor) {
        return Comparator.comparing(
                extractor,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        );
    }

    private ProcessTaskListItemResponse toListItem(DemoTask task, DemoProcessInstance instance) {
        return new ProcessTaskListItemResponse(
                task.taskId,
                task.instanceId,
                instance.processDefinitionId,
                instance.processKey,
                instance.processName,
                instance.businessKey,
                instance.initiatorUserId,
                task.nodeId,
                task.nodeName,
                task.status,
                stringValue(task.assignment.get("mode")),
                task.candidateUserIds,
                task.assigneeUserId,
                task.createdAt,
                task.updatedAt,
                task.completedAt
        );
    }

    private ProcessTaskDetailResponse toDetailResponse(DemoTask task, DemoProcessInstance instance) {
        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        ProcessDslPayload payload = definition.dsl();
        Graph graph = Graph.from(payload);
        ProcessDslPayload.Node node = graph.nodeById.get(task.nodeId);
        Map<String, Object> nodeConfig = node == null ? Map.of() : safeConfig(node);
        String processFormKey = payload.processFormKey();
        String processFormVersion = payload.processFormVersion();
        String nodeFormKey = stringValue(nodeConfig.get("nodeFormKey"));
        String nodeFormVersion = stringValue(nodeConfig.get("nodeFormVersion"));
        String effectiveFormKey = nodeFormKey != null ? nodeFormKey : processFormKey;
        String effectiveFormVersion = nodeFormVersion != null ? nodeFormVersion : processFormVersion;

        return new ProcessTaskDetailResponse(
                task.taskId,
                task.instanceId,
                instance.processDefinitionId,
                instance.processKey,
                instance.processName,
                instance.businessKey,
                instance.initiatorUserId,
                task.nodeId,
                task.nodeName,
                task.status,
                stringValue(task.assignment.get("mode")),
                task.candidateUserIds,
                task.assigneeUserId,
                task.action,
                task.operatorUserId,
                task.comment,
                task.createdAt,
                task.updatedAt,
                task.completedAt,
                instance.status,
                processFormKey,
                processFormVersion,
                nodeFormKey,
                nodeFormVersion,
                effectiveFormKey,
                effectiveFormVersion,
                workflowFieldBindings(nodeConfig.get("fieldBindings")),
                instance.formData,
                task.taskFormData,
                instance.activeTaskIds.stream().sorted().toList()
        );
    }

    private TaskActionAvailabilityResponse actionAvailability(DemoTask task, String userId) {
        boolean canClaim = "PENDING_CLAIM".equals(task.status) && task.candidateUserIds.contains(userId);
        boolean canHandle = "PENDING".equals(task.status) && userId.equals(task.assigneeUserId);
        return new TaskActionAvailabilityResponse(
                canClaim,
                canHandle,
                canHandle,
                canHandle,
                canHandle && task.previousTaskId != null
        );
    }

    private Map<String, Object> resolveReturnAssignment(DemoTask previousTask) {
        if (previousTask.assigneeUserId != null && !previousTask.assigneeUserId.isBlank()) {
            return new HashMap<>(Map.of(
                    "mode", "USER",
                    "userIds", List.of(previousTask.assigneeUserId),
                    "roleCodes", List.of(),
                    "departmentRef", "",
                    "formFieldKey", ""
            ));
        }
        return new HashMap<>(previousTask.assignment);
    }

    private void requirePendingAssignee(DemoTask task, String userId, String actionLabel) {
        if (!"PENDING".equals(task.status) || task.assigneeUserId == null || !task.assigneeUserId.equals(userId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许执行" + actionLabel,
                    Map.of("taskId", task.taskId, "status", task.status, "userId", userId)
            );
        }
    }

    private void logAction(DemoTask task, String action, String operatorUserId, String comment) {
        actionLogs.add(new DemoTaskActionLog(
                newId("tal"),
                task.instanceId,
                task.taskId,
                action,
                operatorUserId,
                comment,
                now()
        ));
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private DemoTask requireTask(String taskId) {
        DemoTask task = tasksById.get(taskId);
        if (task == null) {
            throw new ContractException(
                    "PROCESS.TASK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "任务不存在",
                    Map.of("taskId", taskId)
            );
        }
        return task;
    }

    private DemoProcessInstance requireInstance(String instanceId) {
        DemoProcessInstance instance = instancesById.get(instanceId);
        if (instance == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程实例不存在",
                    Map.of("instanceId", instanceId)
            );
        }
        return instance;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(TIME_ZONE);
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

    private List<WorkflowFieldBinding> workflowFieldBindings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<WorkflowFieldBinding> bindings = new ArrayList<>();
        for (Object item : values) {
            Map<String, Object> binding = mapValue(item);
            if (binding.isEmpty()) {
                continue;
            }
            bindings.add(new WorkflowFieldBinding(
                    stringValue(binding.get("source")),
                    stringValue(binding.get("sourceFieldKey")),
                    stringValue(binding.get("targetFieldKey"))
            ));
        }
        return bindings;
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
        private final List<String> candidateUserIds;
        private final String previousTaskId;
        private final String originTaskId;
        private final OffsetDateTime createdAt;
        private Map<String, Object> taskFormData = Map.of();
        private String assigneeUserId;
        private OffsetDateTime updatedAt;
        private OffsetDateTime completedAt;
        private String status;
        private String action;
        private String operatorUserId;
        private String comment;

        private DemoTask(
                String taskId,
                String instanceId,
                String nodeId,
                String nodeName,
                Map<String, Object> assignment,
                List<String> candidateUserIds,
                String assigneeUserId,
                String previousTaskId,
                String originTaskId,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt,
                String status
        ) {
            this.taskId = taskId;
            this.instanceId = instanceId;
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.assignment = assignment;
            this.candidateUserIds = candidateUserIds;
            this.assigneeUserId = assigneeUserId;
            this.previousTaskId = previousTaskId;
            this.originTaskId = originTaskId;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.status = status;
        }

        private String taskId() {
            return taskId;
        }

        private OffsetDateTime createdAt() {
            return createdAt;
        }

        private OffsetDateTime updatedAt() {
            return updatedAt;
        }

        private OffsetDateTime completedAt() {
            return completedAt;
        }

        private DemoTaskView toView() {
            return new DemoTaskView(
                    taskId,
                    nodeId,
                    nodeName,
                    status,
                    stringValue(assignment.get("mode")),
                    candidateUserIds,
                    assigneeUserId
            );
        }
    }

    private record DemoTaskActionLog(
            String actionLogId,
            String instanceId,
            String taskId,
            String action,
            String operatorUserId,
            String comment,
            OffsetDateTime createdAt
    ) {
    }

    private static final class DemoProcessInstance {
        private final String instanceId;
        private final String processDefinitionId;
        private final String processKey;
        private final String processName;
        private final String businessKey;
        private final String initiatorUserId;
        private final Map<String, Object> formData;
        private final OffsetDateTime createdAt;
        private final Set<String> activeTaskIds = new HashSet<>();
        private final Set<String> reachedEndNodeIds = new HashSet<>();
        private final Map<String, Integer> joinArrivals = new HashMap<>();
        private String status = "RUNNING";
        private OffsetDateTime updatedAt;

        private DemoProcessInstance(
                String instanceId,
                String processDefinitionId,
                String processKey,
                String processName,
                String businessKey,
                String initiatorUserId,
                Map<String, Object> formData,
                OffsetDateTime createdAt
        ) {
            this.instanceId = instanceId;
            this.processDefinitionId = processDefinitionId;
            this.processKey = processKey;
            this.processName = processName;
            this.businessKey = businessKey;
            this.initiatorUserId = initiatorUserId;
            this.formData = formData == null ? Map.of() : new HashMap<>(formData);
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }
}
