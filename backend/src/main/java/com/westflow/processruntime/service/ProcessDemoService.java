package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.approval.service.ApprovalSheetQueryService;
import com.westflow.processruntime.api.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.ApprovalSheetListView;
import com.westflow.processruntime.api.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.AddSignTaskRequest;
import com.westflow.processruntime.api.DelegateTaskRequest;
import com.westflow.processruntime.api.JumpTaskRequest;
import com.westflow.processruntime.api.HandoverTaskRequest;
import com.westflow.processruntime.api.RemoveSignTaskRequest;
import com.westflow.processruntime.api.RejectTaskRequest;
import com.westflow.processruntime.api.RevokeTaskRequest;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.ClaimTaskRequest;
import com.westflow.processruntime.api.ClaimTaskResponse;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.ReturnTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.TakeBackTaskRequest;
import com.westflow.processruntime.api.UrgeTaskRequest;
import com.westflow.processruntime.api.WakeUpInstanceRequest;
import com.westflow.processruntime.api.TransferTaskRequest;
import com.westflow.processruntime.api.WorkflowFieldBinding;
import java.time.Duration;
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
    private static final List<String> SUPPORTED_APPROVAL_SHEET_FILTER_FIELDS = List.of(
            "businessType",
            "processName",
            "currentNodeName",
            "instanceStatus",
            "initiatorUserId",
            "businessKey",
            "instanceId",
            "currentTaskId",
            "currentTaskStatus",
            "currentAssigneeUserId",
            "latestAction",
            "latestOperatorUserId",
            "readStatus",
            "createdAt",
            "updatedAt",
            "completedAt"
    );
    private static final List<String> SUPPORTED_APPROVAL_SHEET_SORT_FIELDS = List.of(
            "createdAt",
            "updatedAt",
            "completedAt",
            "processName",
            "businessType",
            "billNo",
            "businessTitle",
            "currentNodeName",
            "instanceStatus",
            "initiatorUserId",
            "businessKey",
            "businessId",
            "instanceId",
            "currentTaskId",
            "currentTaskStatus",
            "currentAssigneeUserId",
            "latestAction",
            "latestOperatorUserId",
            "readStatus"
    );

    private final ProcessDefinitionService processDefinitionService;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final FixtureAuthService fixtureAuthService;
    private final Map<String, DemoProcessInstance> instancesById = new ConcurrentHashMap<>();
    private final Map<String, DemoTask> tasksById = new ConcurrentHashMap<>();
    private final List<ProcessInstanceEventResponse> instanceEvents = new ArrayList<>();

    private enum TaskKind {
        NORMAL,
        CC,
        ADD_SIGN
    }

    public ProcessDemoService(
            ProcessDefinitionService processDefinitionService,
            ApprovalSheetQueryService approvalSheetQueryService,
            FixtureAuthService fixtureAuthService
    ) {
        this.processDefinitionService = processDefinitionService;
        this.approvalSheetQueryService = approvalSheetQueryService;
        this.fixtureAuthService = fixtureAuthService;
    }

    public synchronized void reset() {
        instancesById.clear();
        tasksById.clear();
        instanceEvents.clear();
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
                request.businessType(),
                StpUtil.getLoginIdAsString(),
                request.formData(),
                now
        );
        instancesById.put(instance.instanceId, instance);
        recordInstanceEvent(
                instance.instanceId,
                null,
                null,
                "INSTANCE_STARTED",
                "流程实例已发起",
                StpUtil.getLoginIdAsString(),
                "INSTANCE",
                null,
                null,
                null,
                eventDetails(
                        "processDefinitionId", instance.processDefinitionId,
                        "processKey", instance.processKey,
                        "businessKey", instance.businessKey,
                        "businessType", instance.businessType
                )
        );

        List<DemoTaskView> activeTasks = advanceFromNode(definition, graph, instance, startNode.id(), null, null);
        refreshStatus(instance);
        return new StartProcessResponse(definition.processDefinitionId(), instance.instanceId, instance.status, activeTasks);
    }

    public synchronized PageResponse<ProcessTaskListItemResponse> page(PageRequest request) {
        String currentUserId = currentUserId();
        List<DemoTask> filtered = tasksById.values().stream()
                .filter(task -> matches(task, request))
                .filter(task -> isTaskVisibleToUser(task, currentUserId))
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

    public synchronized PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request) {
        PageRequest pageRequest = request.toPageRequest();
        String userId = currentUserId();
        List<ApprovalSheetProjection> filtered = instancesById.values().stream()
                .map(instance -> buildApprovalSheetProjection(instance, request.view(), userId))
                .filter(projection -> projection != null)
                .filter(projection -> matchesApprovalSheet(projection, request, pageRequest))
                .sorted(resolveApprovalSheetComparator(pageRequest.sorts()))
                .toList();

        long total = filtered.size();
        long pageSize = pageRequest.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (pageRequest.page() - 1) * pageSize;

        List<ApprovalSheetListItemResponse> records = total == 0
                ? List.of()
                : filtered.stream()
                        .skip(offset)
                        .limit(pageSize)
                        .map(this::toApprovalSheetListItem)
                        .toList();

        return new PageResponse<>(pageRequest.page(), pageSize, total, pages, records, List.of());
    }

    public synchronized ProcessTaskDetailResponse detail(String taskId) {
        DemoTask task = requireTask(taskId);
        DemoProcessInstance instance = requireInstance(task.instanceId);
        maybeAutoRead(task, instance);
        return toDetailResponse(task, instance);
    }

    public synchronized ProcessTaskDetailResponse detailByBusiness(String businessType, String businessId) {
        DemoProcessInstance instance = requireInstanceByBusiness(businessType, businessId);
        DemoTask task = resolveDetailTask(instance);
        return detail(task.taskId);
    }

    public synchronized TaskActionAvailabilityResponse actions(String taskId) {
        DemoTask task = requireTask(taskId);
        return actionAvailability(task, currentUserId());
    }

    public synchronized CompleteTaskResponse addSign(String taskId, AddSignTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        if (task.taskKind != TaskKind.NORMAL) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不支持加签",
                    Map.of("taskId", taskId, "taskKind", task.taskKind.name())
            );
        }
        requireSourceAssignee(task, currentUserId, "加签");
        if (hasActiveAddSignChild(task.taskId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务已存在未处理的加签任务",
                    Map.of("taskId", taskId)
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        DemoTask addSignTask = createAddSignTask(instance, task, request.targetUserId());
        OffsetDateTime now = now();
        task.updatedAt = now;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_ADD_SIGN",
                "任务已加签",
                currentUserId,
                "TASK",
                task.taskId,
                addSignTask.taskId,
                request.targetUserId(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", addSignTask.taskId,
                        "targetUserId", request.targetUserId()
                )
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of(addSignTask.toView()));
    }

    public synchronized CompleteTaskResponse removeSign(String taskId, RemoveSignTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        if (task.taskKind != TaskKind.NORMAL) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不支持减签",
                    Map.of("taskId", taskId, "taskKind", task.taskKind.name())
            );
        }
        requireSourceAssignee(task, currentUserId, "减签");
        DemoTask addSignTask = requireTask(request.targetTaskId());
        if (!task.taskId.equals(addSignTask.sourceTaskId)
                || addSignTask.taskKind != TaskKind.ADD_SIGN
                || !"PENDING".equals(addSignTask.status)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不是可减签的加签任务",
                    Map.of(
                            "taskId", taskId,
                            "targetTaskId", request.targetTaskId()
                    )
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        addSignTask.status = "REVOKED";
        addSignTask.action = "REMOVE_SIGN";
        addSignTask.operatorUserId = currentUserId;
        addSignTask.comment = request.comment();
        addSignTask.completedAt = now;
        addSignTask.handleStartTime = addSignTask.handleStartTime == null ? now : addSignTask.handleStartTime;
        addSignTask.handleEndTime = now;
        addSignTask.updatedAt = now;
        instance.activeTaskIds.remove(addSignTask.taskId);
        recordInstanceEvent(
                instance.instanceId,
                addSignTask.taskId,
                addSignTask.nodeId,
                "TASK_REMOVE_SIGN",
                "任务已减签",
                currentUserId,
                "TASK",
                task.taskId,
                addSignTask.taskId,
                addSignTask.targetUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", addSignTask.taskId,
                        "targetUserId", addSignTask.targetUserId
                )
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of());
    }

    public synchronized CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        DemoTask task = requireTask(taskId);
        DemoProcessInstance instance = requireInstance(task.instanceId);
        String currentUserId = currentUserId();
        if (!currentUserId.equals(instance.initiatorUserId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以撤销流程",
                    Map.of("taskId", taskId, "userId", currentUserId)
            );
        }
        if (!hasBlockingActiveTasks(instance)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前流程不存在可撤销的活动任务",
                    Map.of("instanceId", instance.instanceId)
            );
        }

        OffsetDateTime now = now();
        for (DemoTask activeTask : tasksForInstance(instance.instanceId)) {
            if (!isTerminal(activeTask)) {
                activeTask.status = "REVOKED";
                activeTask.action = "REVOKE";
                activeTask.operatorUserId = currentUserId;
                activeTask.comment = request.comment();
                activeTask.completedAt = now;
                activeTask.handleStartTime = activeTask.handleStartTime == null ? now : activeTask.handleStartTime;
                activeTask.handleEndTime = now;
                activeTask.updatedAt = now;
                activeTask.revoked = true;
            }
        }
        instance.activeTaskIds.clear();
        instance.status = "REVOKED";
        instance.updatedAt = now;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_REVOKED",
                "流程已撤销",
                currentUserId,
                "INSTANCE",
                task.taskId,
                task.taskId,
                null,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId
                )
        );
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of());
    }

    public synchronized CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        DemoTask task = requireTask(taskId);
        DemoProcessInstance instance = requireInstance(task.instanceId);
        String currentUserId = currentUserId();
        if (!currentUserId.equals(instance.initiatorUserId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以催办",
                    Map.of("taskId", taskId, "userId", currentUserId)
            );
        }

        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_URGED",
                "任务已催办",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                task.targetUserId != null ? task.targetUserId : task.assigneeUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", task.targetUserId != null ? task.targetUserId : task.assigneeUserId
                )
        );
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of());
    }

    public synchronized CompleteTaskResponse read(String taskId) {
        DemoTask task = requireTask(taskId);
        if (task.taskKind != TaskKind.CC) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不支持已阅",
                    Map.of("taskId", taskId, "taskKind", task.taskKind.name())
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        String currentUserId = currentUserId();
        requireCcRecipient(task, currentUserId);
        if ("CC_READ".equals(task.status)) {
            return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of());
        }

        markCcRead(task, instance, currentUserId);
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of());
    }

    public synchronized CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingHandler(task, currentUserId, "驳回");

        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy();
        String reapproveStrategy = request.reapproveStrategy() == null || request.reapproveStrategy().isBlank()
                ? "CONTINUE"
                : request.reapproveStrategy();

        DemoProcessInstance instance = requireInstance(task.instanceId);
        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        RejectTarget rejectTarget = resolveRejectTarget(instance, task, request, targetStrategy);

        OffsetDateTime now = now();
        task.status = "REJECTED";
        task.action = "REJECT_ROUTE";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.handleEndTime = now;
        task.updatedAt = now;
        task.targetStrategy = targetStrategy;
        task.targetNodeId = rejectTarget.nodeId;
        task.targetNodeName = rejectTarget.nodeName;
        task.reapproveStrategy = reapproveStrategy;
        instance.activeTaskIds.remove(task.taskId);
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_REJECTED",
                "任务已驳回",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                rejectTarget.targetUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", rejectTarget.targetUserId,
                        "targetStrategy", targetStrategy,
                        "targetNodeId", rejectTarget.nodeId,
                        "targetNodeName", rejectTarget.nodeName,
                        "reapproveStrategy", reapproveStrategy
                ),
                targetStrategy,
                rejectTarget.nodeId,
                reapproveStrategy
        );

        DemoTask nextTask = createTask(
                instance,
                rejectTarget.nodeId,
                rejectTarget.nodeName,
                rejectTarget.assignment,
                task.taskId,
                rejectTarget.originTaskId,
                TaskKind.NORMAL,
                rejectTarget.targetUserId
        );
        nextTask.targetStrategy = targetStrategy;
        nextTask.targetNodeId = rejectTarget.nodeId;
        nextTask.targetNodeName = rejectTarget.nodeName;
        nextTask.reapproveStrategy = reapproveStrategy;
        nextTask.rejectedTaskId = task.taskId;
        nextTask.rejectedTaskNodeId = task.nodeId;
        nextTask.rejectedTaskName = task.nodeName;
        nextTask.rejectedOriginTaskId = task.originTaskId;
        nextTask.rejectedTargetUserId = rejectTarget.targetUserId;
        nextTask.rejectedAssignment = new HashMap<>(task.assignment);
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingHandler(task, currentUserId, "跳转");

        DemoProcessInstance instance = requireInstance(task.instanceId);
        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        ProcessDslPayload.Node targetNode = graph.nodeById.get(request.targetNodeId());
        if (targetNode == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "跳转目标节点不存在",
                    Map.of("targetNodeId", request.targetNodeId())
            );
        }

        OffsetDateTime now = now();
        task.status = "JUMPED";
        task.action = "JUMP";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.handleEndTime = now;
        task.updatedAt = now;
        task.targetStrategy = "JUMP";
        task.targetNodeId = targetNode.id();
        task.targetNodeName = targetNode.name();
        instance.activeTaskIds.remove(task.taskId);
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_JUMPED",
                "任务已跳转",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                null,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetNodeId", targetNode.id(),
                        "targetNodeName", targetNode.name()
                ),
                null,
                targetNode.id(),
                null
        );

        List<DemoTaskView> nextTasks = advanceFromNode(definition, graph, instance, targetNode.id(), task.taskId, task.originTaskId);
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, nextTasks);
    }

    public synchronized CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        if (!currentUserCanTakeBack(task, currentUserId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许执行拿回",
                    Map.of("taskId", taskId, "status", task.status, "userId", currentUserId)
            );
        }

        DemoTask previousTask = task.previousTaskId == null ? null : tasksById.get(task.previousTaskId);
        if (previousTask == null) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不存在可拿回的上一步任务",
                    Map.of("taskId", taskId)
            );
        }

        String previousOperator = previousTask.operatorUserId != null ? previousTask.operatorUserId : previousTask.assigneeUserId;
        if (previousOperator == null || !previousOperator.equals(currentUserId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "当前用户不是上一节点提交人",
                    Map.of("taskId", taskId, "userId", currentUserId)
            );
        }
        if (task.readTime != null) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务已阅读，不能拿回",
                    Map.of("taskId", taskId)
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        task.status = "TAKEN_BACK";
        task.action = "TAKE_BACK";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.handleEndTime = now;
        task.updatedAt = now;
        task.targetStrategy = "PREVIOUS_USER_TASK";
        task.targetNodeId = previousTask.nodeId;
        task.targetNodeName = previousTask.nodeName;
        instance.activeTaskIds.remove(task.taskId);
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_TAKEN_BACK",
                "任务已拿回",
                currentUserId,
                "TASK",
                previousTask.taskId,
                task.taskId,
                previousOperator,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", previousTask.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", previousOperator
                ),
                null,
                previousTask.nodeId,
                null
        );

        DemoTask nextTask = createTask(
                instance,
                previousTask.nodeId,
                previousTask.nodeName,
                resolveReturnAssignment(previousTask),
                task.taskId,
                previousTask.originTaskId,
                TaskKind.NORMAL,
                previousOperator
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse wakeUp(String instanceId, WakeUpInstanceRequest request) {
        DemoProcessInstance instance = requireInstance(instanceId);
        if (!List.of("COMPLETED", "REJECTED", "REVOKED").contains(instance.status)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "仅终态流程允许唤醒",
                    Map.of("instanceId", instanceId, "status", instance.status)
            );
        }

        DemoTask sourceTask = requireTask(request.sourceTaskId());
        if (!instance.instanceId.equals(sourceTask.instanceId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "唤醒源任务不属于当前流程实例",
                    Map.of("instanceId", instanceId, "sourceTaskId", request.sourceTaskId())
            );
        }

        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        ProcessDslPayload.Node targetNode = graph.nodeById.get(sourceTask.nodeId);
        if (targetNode == null) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "唤醒源节点不存在",
                    Map.of("sourceTaskId", request.sourceTaskId())
            );
        }

        String currentUserId = currentUserId();
        OffsetDateTime now = now();
        recordInstanceEvent(
                instance.instanceId,
                sourceTask.taskId,
                sourceTask.nodeId,
                "INSTANCE_WAKE_UP",
                "流程已唤醒",
                currentUserId,
                "INSTANCE",
                sourceTask.taskId,
                sourceTask.taskId,
                sourceTask.assigneeUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", sourceTask.taskId,
                        "targetTaskId", sourceTask.taskId,
                        "targetNodeId", sourceTask.nodeId,
                        "targetUserId", sourceTask.assigneeUserId
                ),
                null,
                sourceTask.nodeId,
                null
        );

        instance.status = "RUNNING";
        instance.updatedAt = now;
        sourceTask.targetStrategy = "WAKE_UP";
        sourceTask.targetNodeId = targetNode.id();
        sourceTask.targetNodeName = targetNode.name();
        DemoTask nextTask = createTask(
                instance,
                targetNode.id(),
                targetNode.name(),
                mapValue(sourceTask.assignment),
                sourceTask.taskId,
                sourceTask.originTaskId,
                TaskKind.NORMAL,
                sourceTask.assigneeUserId
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, sourceTask.taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingAssignee(task, currentUserId, "委派");
        if (task.taskKind != TaskKind.NORMAL) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不支持委派",
                    Map.of("taskId", taskId, "taskKind", task.taskKind.name())
            );
        }
        if (request.targetUserId() == null || request.targetUserId().isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserId 不能为空",
                    Map.of("taskId", taskId)
            );
        }

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        task.status = "DELEGATED";
        task.action = "DELEGATE";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.handleEndTime = now;
        task.updatedAt = now;
        task.actingMode = "DELEGATE";
        task.actingForUserId = currentUserId;
        task.delegatedByUserId = currentUserId;
        instance.activeTaskIds.remove(task.taskId);

        DemoTask nextTask = createTask(
                instance,
                task.nodeId,
                task.nodeName,
                delegatedAssignment(task.assignment, request.targetUserId()),
                task.taskId,
                task.originTaskId,
                TaskKind.NORMAL,
                request.targetUserId()
        );
        nextTask.actingMode = "DELEGATE";
        nextTask.actingForUserId = currentUserId;
        nextTask.delegatedByUserId = currentUserId;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_DELEGATED",
                "任务已委派",
                currentUserId,
                "TASK",
                task.taskId,
                nextTask.taskId,
                request.targetUserId(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", nextTask.taskId,
                        "targetUserId", request.targetUserId(),
                        "actingMode", "DELEGATE",
                        "actingForUserId", currentUserId,
                        "delegatedByUserId", currentUserId
                ),
                null,
                null,
                null,
                "DELEGATE",
                currentUserId,
                currentUserId,
                null
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse handover(String sourceUserId, HandoverTaskRequest request) {
        String currentUserId = currentUserId();
        if (!currentUserIsProcessAdmin()) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以执行离职转办",
                    Map.of("userId", currentUserId)
            );
        }
        if (sourceUserId == null || sourceUserId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "sourceUserId 不能为空",
                    Map.of("sourceUserId", sourceUserId)
            );
        }
        if (request.targetUserId() == null || request.targetUserId().isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserId 不能为空",
                    Map.of("sourceUserId", sourceUserId)
            );
        }

        List<DemoTaskView> nextTasks = new ArrayList<>();
        OffsetDateTime now = now();
        String firstInstanceId = null;
        String firstTaskId = null;
        for (DemoTask task : tasksById.values().stream()
                .filter(candidate -> sourceUserId.equals(candidate.assigneeUserId))
                .filter(candidate -> candidate.taskKind == TaskKind.NORMAL)
                .filter(candidate -> "PENDING".equals(candidate.status))
                .sorted(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .toList()) {
            DemoProcessInstance instance = requireInstance(task.instanceId);
            if (firstInstanceId == null) {
                firstInstanceId = instance.instanceId;
                firstTaskId = task.taskId;
            }
            task.status = "HANDOVERED";
            task.action = "HANDOVER";
            task.operatorUserId = currentUserId;
            task.comment = request.comment();
            task.completedAt = now;
            task.handleEndTime = now;
            task.updatedAt = now;
            task.actingMode = "HANDOVER";
            task.actingForUserId = sourceUserId;
            task.handoverFromUserId = sourceUserId;
            instance.activeTaskIds.remove(task.taskId);

            DemoTask nextTask = createTask(
                    instance,
                    task.nodeId,
                    task.nodeName,
                    delegatedAssignment(task.assignment, request.targetUserId()),
                    task.taskId,
                    task.originTaskId,
                    TaskKind.NORMAL,
                    request.targetUserId()
            );
            nextTask.actingMode = "HANDOVER";
            nextTask.actingForUserId = sourceUserId;
            nextTask.handoverFromUserId = sourceUserId;
            nextTasks.add(nextTask.toView());
            recordInstanceEvent(
                    instance.instanceId,
                    task.taskId,
                    task.nodeId,
                    "TASK_HANDOVERED",
                    "任务已离职转办",
                    currentUserId,
                    "TASK",
                    task.taskId,
                    nextTask.taskId,
                    request.targetUserId(),
                    eventDetails(
                            "comment", request.comment(),
                            "sourceTaskId", task.taskId,
                            "targetTaskId", nextTask.taskId,
                            "sourceUserId", sourceUserId,
                            "targetUserId", request.targetUserId(),
                            "actingMode", "HANDOVER",
                            "actingForUserId", sourceUserId,
                            "handoverFromUserId", sourceUserId
                    ),
                    null,
                    null,
                    null,
                    "HANDOVER",
                    sourceUserId,
                    null,
                    sourceUserId
            );
            refreshStatus(instance);
        }

        if (nextTasks.isEmpty()) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "没有可离职转办的活跃人工任务",
                    Map.of("sourceUserId", sourceUserId)
            );
        }

        return new CompleteTaskResponse(
                firstInstanceId,
                firstTaskId,
                "RUNNING",
                nextTasks
        );
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
        if (task.handleStartTime == null) {
            task.handleStartTime = task.updatedAt;
        }
        recordInstanceEvent(
                task.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_CLAIMED",
                "任务已认领",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                currentUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", currentUserId
                )
        );

        return new ClaimTaskResponse(task.taskId, task.instanceId, task.status, task.assigneeUserId);
    }

    public synchronized CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = request.operatorUserId() == null || request.operatorUserId().isBlank()
                ? currentUserId()
                : request.operatorUserId();

        if (task.taskKind == TaskKind.CC) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前抄送任务不支持办理",
                    Map.of("taskId", taskId)
            );
        }
        boolean directAssignee = "PENDING".equals(task.status)
                && task.assigneeUserId != null
                && task.assigneeUserId.equals(currentUserId);
        boolean proxyAssignee = canProxyHandle(task, currentUserId);
        if (!directAssignee && !proxyAssignee) {
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
        if (task.taskKind == TaskKind.NORMAL && hasActiveAddSignChild(task.taskId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务存在未处理的加签任务",
                    Map.of("taskId", taskId)
            );
        }

        OffsetDateTime now = now();
        if (task.actingMode == null || task.actingMode.isBlank()) {
            if (directAssignee) {
                task.actingMode = "DIRECT";
                task.actingForUserId = currentUserId;
            } else {
                task.actingMode = "PROXY";
                task.actingForUserId = task.assigneeUserId;
                task.delegatedByUserId = task.assigneeUserId;
            }
        }
        task.status = "COMPLETED";
        task.action = request.action();
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.taskFormData = request.taskFormData() == null
                ? Map.of()
                : new HashMap<>(request.taskFormData());
        if (task.handleStartTime == null) {
            task.handleStartTime = now;
        }
        task.completedAt = now;
        task.handleEndTime = now;
        task.updatedAt = now;
        instance.activeTaskIds.remove(taskId);
        instance.updatedAt = now;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_COMPLETED",
                "任务已完成",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                task.targetUserId != null ? task.targetUserId : task.assigneeUserId,
                eventDetails(
                        "action", request.action(),
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", task.targetUserId != null ? task.targetUserId : task.assigneeUserId,
                        "actingMode", task.actingMode,
                        "actingForUserId", task.actingForUserId,
                        "delegatedByUserId", task.delegatedByUserId,
                        "handoverFromUserId", task.handoverFromUserId
                ),
                null,
                null,
                null,
                task.actingMode,
                task.actingForUserId,
                task.delegatedByUserId,
                task.handoverFromUserId
        );

        PublishedProcessDefinition definition = processDefinitionService.getById(instance.processDefinitionId);
        Graph graph = Graph.from(definition.dsl());
        List<DemoTaskView> nextTasks = task.taskKind == TaskKind.ADD_SIGN
                ? List.of()
                : continueAlongOutgoing(definition, graph, instance, task.nodeId, task.taskId, task.originTaskId);
        if (task.reapproveStrategy != null && "RETURN_TO_REJECTED_NODE".equals(task.reapproveStrategy) && task.rejectedTaskNodeId != null) {
            nextTasks = List.of(createTask(
                    instance,
                    task.rejectedTaskNodeId,
                    task.rejectedTaskName == null ? task.nodeName : task.rejectedTaskName,
                    task.rejectedAssignment == null ? resolveReturnAssignment(task) : new HashMap<>(task.rejectedAssignment),
                    task.taskId,
                    task.rejectedOriginTaskId == null ? task.originTaskId : task.rejectedOriginTaskId,
                    TaskKind.NORMAL,
                    task.rejectedTargetUserId == null ? task.assigneeUserId : task.rejectedTargetUserId
            ).toView());
        }
        refreshStatus(instance);

        return new CompleteTaskResponse(instance.instanceId, taskId, instance.status, nextTasks);
    }

    public synchronized CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingHandler(task, currentUserId, "转办");

        DemoProcessInstance instance = requireInstance(task.instanceId);
        OffsetDateTime now = now();
        task.status = "TRANSFERRED";
        task.action = "TRANSFER";
        task.operatorUserId = currentUserId;
        task.comment = request.comment();
        task.completedAt = now;
        task.handleEndTime = now;
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
                task.originTaskId,
                TaskKind.NORMAL,
                request.targetUserId()
        );
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_TRANSFERRED",
                "任务已转办",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                request.targetUserId(),
                eventDetails(
                        "targetUserId", request.targetUserId(),
                        "comment", request.comment(),
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId
                )
        );
        refreshStatus(instance);
        return new CompleteTaskResponse(instance.instanceId, task.taskId, instance.status, List.of(nextTask.toView()));
    }

    public synchronized CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        DemoTask task = requireTask(taskId);
        String currentUserId = currentUserId();
        requirePendingHandler(task, currentUserId, "退回");

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
        task.handleEndTime = now;
        task.updatedAt = now;
        instance.activeTaskIds.remove(task.taskId);

        DemoTask nextTask = createTask(
                instance,
                previousTask.nodeId,
                previousTask.nodeName,
                resolveReturnAssignment(previousTask),
                task.taskId,
                previousTask.originTaskId,
                TaskKind.NORMAL,
                previousTask.assigneeUserId
        );
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_RETURNED",
                "任务已退回",
                currentUserId,
                "TASK",
                task.taskId,
                task.taskId,
                previousTask.assigneeUserId,
                eventDetails(
                        "comment", request.comment(),
                        "targetStrategy", targetStrategy,
                        "sourceTaskId", task.taskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", previousTask.assigneeUserId
                )
        );
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
            case "start" -> continueAlongOutgoing(definition, graph, instance, node.id(), previousTaskId, originTaskId);
            case "cc" -> {
                createCcTasks(instance, node, previousTaskId, originTaskId);
                yield continueAlongOutgoing(definition, graph, instance, node.id(), previousTaskId, originTaskId);
            }
            case "approver" -> List.of(createTask(instance, node, previousTaskId, originTaskId, TaskKind.NORMAL).toView());
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
        return createTask(instance, node, previousTaskId, originTaskId, TaskKind.NORMAL);
    }

    private DemoTask createTask(
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId,
            TaskKind taskKind
    ) {
        Map<String, Object> assignment = mapValue(safeConfig(node).get("assignment"));
        if (taskKind == TaskKind.CC) {
            assignment = mapValue(safeConfig(node).get("targets"));
        }
        return createTask(instance, node.id(), node.name(), assignment, previousTaskId, originTaskId, taskKind, null);
    }

    private DemoTask createTask(
            DemoProcessInstance instance,
            String nodeId,
            String nodeName,
            Map<String, Object> assignment,
            String previousTaskId,
            String originTaskId,
            TaskKind taskKind,
            String targetUserId
    ) {
        String taskId = newId("task");
        OffsetDateTime now = now();
        List<String> candidateUserIds = resolveCandidateUserIds(assignment, taskKind, targetUserId);
        String assigneeUserId = resolveAssigneeUserId(candidateUserIds, taskKind, targetUserId);
        String status = resolveTaskStatus(candidateUserIds, taskKind);
        String resolvedTargetUserId = targetUserId != null ? targetUserId : assigneeUserId;
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
                status,
                taskKind,
                previousTaskId,
                taskId,
                resolvedTargetUserId
        );
        if (assigneeUserId != null && taskKind != TaskKind.CC) {
            task.handleStartTime = now;
        }
        tasksById.put(taskId, task);
        if (taskKind != TaskKind.CC) {
            instance.activeTaskIds.add(taskId);
        }
        recordInstanceEvent(
                instance.instanceId,
                taskId,
                nodeId,
                "TASK_CREATED",
                "任务已创建",
                instance.initiatorUserId,
                taskKind == TaskKind.CC ? "CC" : "TASK",
                previousTaskId,
                taskId,
                resolvedTargetUserId,
                eventDetails(
                        "status", status,
                        "assigneeUserId", assigneeUserId,
                        "candidateUserIds", candidateUserIds,
                        "sourceTaskId", previousTaskId,
                        "targetTaskId", taskId,
                        "targetUserId", resolvedTargetUserId
                )
        );
        refreshStatus(instance);
        return task;
    }

    private List<DemoTask> createCcTasks(
            DemoProcessInstance instance,
            ProcessDslPayload.Node node,
            String previousTaskId,
            String originTaskId
    ) {
        Map<String, Object> targets = mapValue(safeConfig(node).get("targets"));
        List<String> targetUserIds = stringListValue(targets.get("userIds"));
        if (targetUserIds.isEmpty()) {
            return List.of();
        }

        List<DemoTask> ccTasks = new ArrayList<>();
        for (String targetUserId : targetUserIds) {
            ccTasks.add(createTask(
                    instance,
                    node.id(),
                    node.name(),
                    targets,
                    previousTaskId,
                    originTaskId,
                    TaskKind.CC,
                    targetUserId
            ));
        }
        return ccTasks;
    }

    private DemoTask createAddSignTask(DemoProcessInstance instance, DemoTask sourceTask, String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserId 不能为空",
                    Map.of("taskId", sourceTask.taskId)
            );
        }
        return createTask(
                instance,
                sourceTask.nodeId,
                sourceTask.nodeName,
                Map.of(
                        "mode", "USER",
                        "userIds", List.of(targetUserId),
                        "roleCodes", List.of(),
                        "departmentRef", "",
                        "formFieldKey", ""
                ),
                sourceTask.taskId,
                sourceTask.originTaskId,
                TaskKind.ADD_SIGN,
                targetUserId
        );
    }

    private List<String> resolveCandidateUserIds(Map<String, Object> assignment, TaskKind taskKind, String targetUserId) {
        if (targetUserId != null && !targetUserId.isBlank()) {
            return List.of(targetUserId);
        }
        return stringListValue(assignment.get("userIds"));
    }

    private String resolveAssigneeUserId(List<String> candidateUserIds, TaskKind taskKind, String targetUserId) {
        if (targetUserId != null && !targetUserId.isBlank()) {
            return targetUserId;
        }
        return candidateUserIds.size() == 1 ? candidateUserIds.get(0) : null;
    }

    private String resolveTaskStatus(List<String> candidateUserIds, TaskKind taskKind) {
        if (taskKind == TaskKind.CC) {
            return "CC_PENDING";
        }
        if (taskKind == TaskKind.ADD_SIGN) {
            return "PENDING";
        }
        return candidateUserIds.size() > 1 ? "PENDING_CLAIM" : "PENDING";
    }

    private void markCcRead(DemoTask task, DemoProcessInstance instance, String currentUserId) {
        OffsetDateTime now = now();
        task.status = "CC_READ";
        task.action = "READ";
        task.operatorUserId = currentUserId;
        task.readTime = now;
        task.handleStartTime = task.handleStartTime == null ? now : task.handleStartTime;
        task.handleEndTime = now;
        task.updatedAt = now;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_READ",
                "抄送已阅",
                currentUserId,
                "CC",
                task.sourceTaskId,
                task.taskId,
                task.targetUserId,
                eventDetails(
                        "status", task.status,
                        "sourceTaskId", task.sourceTaskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", task.targetUserId
                )
        );
    }

    private void refreshStatus(DemoProcessInstance instance) {
        instance.updatedAt = now();
        if ("REVOKED".equals(instance.status)) {
            return;
        }
        if (instance.activeTaskIds.isEmpty() && instance.joinArrivals.isEmpty() && !instance.reachedEndNodeIds.isEmpty()) {
            if (!instance.completedLogged) {
                instance.completedLogged = true;
                recordInstanceEvent(
                        instance.instanceId,
                        null,
                        null,
                        "INSTANCE_COMPLETED",
                        "流程实例已结束",
                        instance.initiatorUserId,
                        eventDetails("processDefinitionId", instance.processDefinitionId)
                );
            }
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

    private ApprovalSheetProjection buildApprovalSheetProjection(
            DemoProcessInstance instance,
            ApprovalSheetListView view,
            String userId
    ) {
        List<DemoTask> instanceTasks = tasksForInstance(instance.instanceId);
        if (instanceTasks.isEmpty()) {
            return null;
        }

        DemoTask latestTask = latestTask(instanceTasks);
        DemoTask currentTask = switch (view) {
            case TODO -> currentVisibleTask(instance, userId);
            case DONE, INITIATED -> currentOrLatestTask(instance, latestTask);
            case CC -> currentCcTask(instance, userId);
        };
        DemoTask latestHandledTask = latestHandledTask(instanceTasks, userId);

        boolean visible = switch (view) {
            case TODO -> currentTask != null;
            case DONE -> latestHandledTask != null;
            case INITIATED -> userId.equals(instance.initiatorUserId);
            case CC -> currentTask != null;
        };
        if (!visible) {
            return null;
        }

        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(instance.businessType, instance.businessKey);
        return new ApprovalSheetProjection(
                instance,
                currentTask == null ? latestTask : currentTask,
                latestTask,
                latestHandledTask,
                businessData,
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(instance.businessType, instance.businessKey, businessData)
        );
    }

    private List<DemoTask> tasksForInstance(String instanceId) {
        return tasksById.values().stream()
                .filter(task -> instanceId.equals(task.instanceId))
                .sorted(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .toList();
    }

    private DemoTask currentVisibleTask(DemoProcessInstance instance, String userId) {
        return instance.activeTaskIds.stream()
                .map(tasksById::get)
                .filter(task -> task != null)
                .filter(task -> isTodoTaskVisibleToUser(task, userId))
                .sorted(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .findFirst()
                .orElse(null);
    }

    private DemoTask currentCcTask(DemoProcessInstance instance, String userId) {
        return tasksForInstance(instance.instanceId).stream()
                .filter(task -> task.taskKind == TaskKind.CC)
                .filter(task -> isTaskVisibleToUser(task, userId))
                .sorted(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .findFirst()
                .orElse(null);
    }

    private boolean isTaskVisibleToUser(DemoTask task, String userId) {
        if (task == null) {
            return false;
        }
        if (task.taskKind == TaskKind.CC) {
            return "CC_PENDING".equals(task.status) || "CC_READ".equals(task.status)
                    ? userId.equals(task.assigneeUserId)
                    : false;
        }
        return switch (task.status) {
            case "PENDING" -> userId.equals(task.assigneeUserId) || isProxyPrincipal(task.assigneeUserId, userId);
            case "PENDING_CLAIM" -> task.candidateUserIds.contains(userId);
            case "DELEGATED", "HANDOVERED", "COMPLETED", "TRANSFERRED", "RETURNED", "REVOKED", "REJECTED", "JUMPED", "TAKEN_BACK" ->
                    userId.equals(task.assigneeUserId)
                            || userId.equals(task.operatorUserId)
                            || userId.equals(task.actingForUserId)
                            || userId.equals(task.delegatedByUserId)
                            || userId.equals(task.handoverFromUserId);
            default -> false;
        };
    }

    private boolean isTodoTaskVisibleToUser(DemoTask task, String userId) {
        if (task == null) {
            return false;
        }
        if (task.taskKind == TaskKind.CC) {
            return false;
        }
        return switch (task.status) {
            case "PENDING" -> userId.equals(task.assigneeUserId) || isProxyPrincipal(task.assigneeUserId, userId);
            case "PENDING_CLAIM" -> task.candidateUserIds.contains(userId);
            default -> false;
        };
    }

    private boolean isProxyPrincipal(String principalUserId, String delegateUserId) {
        if (principalUserId == null || delegateUserId == null || !delegateUserId.equals(currentUserId())) {
            return false;
        }
        return currentUser().delegations().stream()
                .filter(delegation -> "ACTIVE".equalsIgnoreCase(delegation.status()))
                .anyMatch(delegation -> principalUserId.equals(delegation.principalUserId())
                        && delegateUserId.equals(delegation.delegateUserId()));
    }

    private DemoTask currentOrLatestTask(DemoProcessInstance instance, DemoTask latestTask) {
        if (!instance.activeTaskIds.isEmpty()) {
            return resolveDetailTask(instance);
        }
        return latestTask;
    }

    private DemoTask latestTask(List<DemoTask> tasks) {
        return tasks.stream()
                .max(Comparator.comparing(DemoTask::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DemoTask::createdAt)
                        .thenComparing(DemoTask::taskId))
                .orElse(null);
    }

    private DemoTask latestHandledTask(List<DemoTask> tasks, String userId) {
        return tasks.stream()
                .filter(task -> task.action != null && !"CLAIM".equals(task.action))
                .filter(task -> userId.equals(task.operatorUserId)
                        || userId.equals(task.actingForUserId)
                        || userId.equals(task.assigneeUserId)
                        || userId.equals(task.delegatedByUserId)
                        || userId.equals(task.handoverFromUserId))
                .max(Comparator.comparing(DemoTask::completedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DemoTask::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DemoTask::taskId))
                .orElse(null);
    }

    private boolean matchesApprovalSheet(
            ApprovalSheetProjection projection,
            ApprovalSheetPageRequest request,
            PageRequest pageRequest
    ) {
        if (!request.businessTypes().isEmpty()
                && (projection.instance.businessType == null || !request.businessTypes().contains(projection.instance.businessType))) {
            return false;
        }

        String keyword = pageRequest.keyword();
        if (keyword != null && !keyword.isBlank() && !containsApprovalSheetKeyword(projection, keyword)) {
            return false;
        }

        for (FilterItem filter : pageRequest.filters()) {
            if (!SUPPORTED_APPROVAL_SHEET_FILTER_FIELDS.contains(filter.field())) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "不支持的审批单筛选字段",
                        Map.of(
                                "field",
                                filter.field(),
                                "supportedFields",
                                SUPPORTED_APPROVAL_SHEET_FILTER_FIELDS
                            )
                );
            }
            if ("between".equalsIgnoreCase(filter.operator())) {
                if (!matchesApprovalSheetBetweenFilter(projection, filter.field(), filter.value())) {
                    return false;
                }
                continue;
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "审批单列表目前仅支持等值和区间筛选",
                        Map.of("operator", filter.operator())
                );
            }

            String value = filter.value() == null ? null : filter.value().asText();
            if (!matchesApprovalSheetFilter(projection, filter.field(), value)) {
                return false;
            }
        }

        return true;
    }

    private boolean containsApprovalSheetKeyword(ApprovalSheetProjection projection, String keyword) {
        String normalized = keyword.trim().toLowerCase();
        return contains(projection.instance.instanceId, normalized)
                || contains(projection.instance.processKey, normalized)
                || contains(projection.instance.processName, normalized)
                || contains(projection.instance.businessKey, normalized)
                || contains(projection.instance.businessType, normalized)
                || contains(projection.billNo, normalized)
                || contains(projection.businessTitle, normalized)
                || contains(projection.currentTask == null ? null : projection.currentTask.nodeName, normalized)
                || contains(projection.currentTask == null ? null : projection.currentTask.status, normalized)
                || contains(projection.latestAction(), normalized)
                || contains(projection.latestOperatorUserId(), normalized)
                || contains(projection.instance.initiatorUserId, normalized);
    }

    private boolean matchesApprovalSheetFilter(
            ApprovalSheetProjection projection,
            String field,
            String value
    ) {
        return switch (field) {
            case "businessType" -> equalsValue(projection.instance.businessType, value);
            case "processName" -> equalsValue(projection.instance.processName, value);
            case "currentNodeName" -> equalsValue(projection.currentTask == null ? null : projection.currentTask.nodeName, value);
            case "instanceStatus" -> equalsValue(projection.instance.status, value);
            case "initiatorUserId" -> equalsValue(projection.instance.initiatorUserId, value);
            case "businessKey", "businessId" -> equalsValue(projection.instance.businessKey, value);
            case "instanceId" -> equalsValue(projection.instance.instanceId, value);
            case "currentTaskId" -> equalsValue(projection.currentTask == null ? null : projection.currentTask.taskId, value);
            case "currentTaskStatus" -> equalsValue(projection.currentTask == null ? null : projection.currentTask.status, value);
            case "currentAssigneeUserId" -> equalsValue(projection.currentTask == null ? null : projection.currentTask.assigneeUserId, value);
            case "latestAction" -> equalsValue(projection.latestAction(), value);
            case "latestOperatorUserId" -> equalsValue(projection.latestOperatorUserId(), value);
            case "readStatus" -> matchesApprovalSheetReadStatus(projection, value);
            case "createdAt" -> matchesDateFilter(projection.instance.createdAt, value);
            case "updatedAt" -> matchesDateFilter(projection.instance.updatedAt, value);
            case "completedAt" -> matchesDateFilter(projection.completedAt(), value);
            default -> throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "不支持的审批单筛选字段",
                    Map.of("field", field)
            );
        };
    }

    private boolean matchesApprovalSheetBetweenFilter(
            ApprovalSheetProjection projection,
            String field,
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (value == null || !value.isArray() || value.size() != 2) {
            return false;
        }
        OffsetDateTime start = parseOffsetDateTime(value.get(0));
        OffsetDateTime end = parseOffsetDateTime(value.get(1));
        OffsetDateTime actual = switch (field) {
            case "createdAt" -> projection.instance.createdAt;
            case "updatedAt" -> projection.instance.updatedAt;
            case "completedAt" -> projection.completedAt();
            default -> throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "当前字段不支持区间筛选",
                    Map.of("field", field)
            );
        };
        if (actual == null) {
            return false;
        }
        if (start != null && actual.isBefore(start)) {
            return false;
        }
        if (end != null && actual.isAfter(end)) {
            return false;
        }
        return true;
    }

    private boolean matchesDateFilter(OffsetDateTime actual, String value) {
        return equalsValue(actual == null ? null : actual.toString(), value);
    }

    private OffsetDateTime parseOffsetDateTime(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(text);
    }

    private String resolveApprovalSheetReadStatus(ApprovalSheetProjection projection) {
        if (projection.currentTask == null || projection.currentTask.taskKind != TaskKind.CC) {
            return null;
        }
        return "CC_READ".equals(projection.currentTask.status) ? "READ" : "UNREAD";
    }

    private boolean matchesApprovalSheetReadStatus(ApprovalSheetProjection projection, String value) {
        String actual = resolveApprovalSheetReadStatus(projection);
        if (actual == null) {
            return false;
        }
        String normalized = value == null ? null : value.trim().toUpperCase();
        return switch (normalized == null ? "" : normalized) {
            case "READ", "CC_READ" -> "READ".equals(actual);
            case "UNREAD", "CC_PENDING" -> "UNREAD".equals(actual);
            default -> equalsValue(actual, value);
        };
    }

    private Comparator<ApprovalSheetProjection> resolveApprovalSheetComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(
                            (ApprovalSheetProjection projection) -> projection.instance.updatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .reversed()
                    .thenComparing(projection -> projection.instance.instanceId);
        }

        Comparator<ApprovalSheetProjection> comparator = null;
        for (SortItem sort : sorts) {
            Comparator<ApprovalSheetProjection> next = switch (sort.field()) {
                case "createdAt" -> Comparator.comparing(
                        (ApprovalSheetProjection projection) -> projection.instance.createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "updatedAt" -> Comparator.comparing(
                        (ApprovalSheetProjection projection) -> projection.instance.updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "completedAt" -> Comparator.comparing(
                        ApprovalSheetProjection::completedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "processName" -> approvalSheetStringComparator(projection -> projection.instance.processName);
                case "businessType" -> approvalSheetStringComparator(projection -> projection.instance.businessType);
                case "billNo" -> approvalSheetStringComparator(ApprovalSheetProjection::billNo);
                case "businessTitle" -> approvalSheetStringComparator(ApprovalSheetProjection::businessTitle);
                case "currentNodeName" -> approvalSheetStringComparator(
                        projection -> projection.currentTask == null ? null : projection.currentTask.nodeName
                );
                case "instanceStatus" -> approvalSheetStringComparator(projection -> projection.instance.status);
                case "initiatorUserId" -> approvalSheetStringComparator(projection -> projection.instance.initiatorUserId);
                case "businessKey", "businessId" -> approvalSheetStringComparator(projection -> projection.instance.businessKey);
                case "instanceId" -> approvalSheetStringComparator(projection -> projection.instance.instanceId);
                case "currentTaskId" -> approvalSheetStringComparator(projection -> projection.currentTask == null ? null : projection.currentTask.taskId);
                case "currentTaskStatus" -> approvalSheetStringComparator(projection -> projection.currentTask == null ? null : projection.currentTask.status);
                case "currentAssigneeUserId" -> approvalSheetStringComparator(projection -> projection.currentTask == null ? null : projection.currentTask.assigneeUserId);
                case "latestAction" -> approvalSheetStringComparator(ApprovalSheetProjection::latestAction);
                case "latestOperatorUserId" -> approvalSheetStringComparator(ApprovalSheetProjection::latestOperatorUserId);
                case "readStatus" -> approvalSheetStringComparator(this::resolveApprovalSheetReadStatus);
                default -> throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "不支持的审批单排序字段",
                        Map.of(
                                "field",
                                sort.field(),
                                "supportedFields",
                                SUPPORTED_APPROVAL_SHEET_SORT_FIELDS
                        )
                );
            };
            if ("desc".equalsIgnoreCase(sort.direction())) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        if (comparator == null) {
            comparator = Comparator.comparing(
                    (ApprovalSheetProjection projection) -> projection.instance.updatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
        return comparator.thenComparing(projection -> projection.instance.instanceId);
    }

    private Comparator<ApprovalSheetProjection> approvalSheetStringComparator(
            Function<ApprovalSheetProjection, String> extractor
    ) {
        return Comparator.comparing(extractor, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private ApprovalSheetListItemResponse toApprovalSheetListItem(ApprovalSheetProjection projection) {
        DemoTask currentTask = projection.currentTask;
        DemoTask latestHandledTask = projection.latestHandledTask;
        DemoTask latestRelevantTask = latestHandledTask != null ? latestHandledTask : projection.latestTask;
        return new ApprovalSheetListItemResponse(
                projection.instance.instanceId,
                projection.instance.processDefinitionId,
                projection.instance.processKey,
                projection.instance.processName,
                projection.instance.businessKey,
                projection.instance.businessType,
                projection.billNo,
                projection.businessTitle,
                projection.instance.initiatorUserId,
                currentTask == null ? null : currentTask.nodeName,
                currentTask == null ? null : currentTask.taskId,
                currentTask == null ? null : currentTask.status,
                currentTask == null ? null : currentTask.assigneeUserId,
                projection.instance.status,
                latestRelevantTask.action,
                latestRelevantTask.operatorUserId,
                projection.instance.createdAt,
                projection.instance.updatedAt,
                projection.completedAt()
        );
    }

    private String resolveBusinessTitle(String businessType, String businessKey, Map<String, Object> businessData) {
        return switch (businessType == null ? "" : businessType) {
            case "OA_LEAVE" -> titleWithSuffix("请假申请", stringValue(businessData.get("reason")));
            case "OA_EXPENSE" -> titleWithSuffix("报销申请", stringValue(businessData.get("reason")));
            case "OA_COMMON" -> titleWithSuffix("通用申请", stringValue(businessData.get("title")));
            default -> stringValue(businessData.get("title")) != null
                    ? stringValue(businessData.get("title"))
                    : businessKey;
        };
    }

    private String titleWithSuffix(String prefix, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return prefix;
        }
        return prefix + " · " + suffix;
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
                instance.businessType,
                instance.initiatorUserId,
                task.nodeId,
                task.nodeName,
                task.taskKind.name(),
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
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(instance.businessType, instance.businessKey);

        return new ProcessTaskDetailResponse(
                task.taskId,
                task.instanceId,
                instance.processDefinitionId,
                instance.processKey,
                instance.processName,
                instance.businessKey,
                instance.businessType,
                instance.initiatorUserId,
                businessData,
                payload.nodes(),
                payload.edges(),
                instanceEventsFor(instance.instanceId),
                taskTraceFor(instance.instanceId),
                task.nodeId,
                task.nodeName,
                task.taskKind.name(),
                task.status,
                stringValue(task.assignment.get("mode")),
                task.candidateUserIds,
                task.assigneeUserId,
                task.action,
                task.operatorUserId,
                task.comment,
                task.createdAt,
                task.readTime,
                task.handleStartTime,
                task.handleEndTime,
                handleDurationSeconds(task.handleStartTime, task.handleEndTime),
                task.targetStrategy,
                task.targetNodeId,
                task.targetNodeName,
                task.reapproveStrategy,
                task.actingMode,
                task.actingForUserId,
                task.delegatedByUserId,
                task.handoverFromUserId,
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

    private List<ProcessInstanceEventResponse> instanceEventsFor(String instanceId) {
        return instanceEvents.stream()
                .filter(event -> instanceId.equals(event.instanceId()))
                .toList();
    }

    private List<ProcessTaskTraceItemResponse> taskTraceFor(String instanceId) {
        return tasksById.values().stream()
                .filter(task -> instanceId.equals(task.instanceId))
                .sorted(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .map(this::toTraceItem)
                .toList();
    }

    private ProcessTaskTraceItemResponse toTraceItem(DemoTask task) {
        return new ProcessTaskTraceItemResponse(
                task.taskId,
                task.nodeId,
                task.nodeName,
                task.taskKind.name(),
                task.status,
                task.assigneeUserId,
                task.candidateUserIds,
                task.action,
                task.operatorUserId,
                task.comment,
                task.createdAt,
                task.readTime,
                task.handleStartTime,
                task.handleEndTime,
                handleDurationSeconds(task.handleStartTime, task.handleEndTime),
                task.sourceTaskId,
                task.targetTaskId,
                task.targetUserId,
                task.taskKind == TaskKind.CC,
                task.taskKind == TaskKind.ADD_SIGN,
                task.revoked,
                "REJECTED".equals(task.status),
                "JUMPED".equals(task.status),
                "TAKEN_BACK".equals(task.status),
                task.targetStrategy,
                task.targetNodeId,
                task.reapproveStrategy,
                task.actingMode,
                task.actingForUserId,
                task.delegatedByUserId,
                task.handoverFromUserId
        );
    }

    private Long handleDurationSeconds(OffsetDateTime handleStartTime, OffsetDateTime handleEndTime) {
        if (handleStartTime == null || handleEndTime == null) {
            return null;
        }
        return Math.max(0L, Duration.between(handleStartTime, handleEndTime).toSeconds());
    }

    private TaskActionAvailabilityResponse actionAvailability(DemoTask task, String userId) {
        boolean canClaim = task.taskKind == TaskKind.NORMAL
                && "PENDING_CLAIM".equals(task.status)
                && task.candidateUserIds.contains(userId);
        boolean blockedByAddSign = task.taskKind == TaskKind.NORMAL && hasActiveAddSignChild(task.taskId);
        boolean canSourceHandle = task.taskKind != TaskKind.CC
                && "PENDING".equals(task.status)
                && userId.equals(task.assigneeUserId);
        boolean canProxyHandle = canProxyHandle(task, userId);
        boolean canHandle = (canSourceHandle || canProxyHandle) && !blockedByAddSign;
        boolean hasActiveAddSign = hasActiveAddSignChild(task.taskId);
        boolean canAddSign = task.taskKind == TaskKind.NORMAL && canSourceHandle && !hasActiveAddSign;
        boolean canRemoveSign = task.taskKind == TaskKind.NORMAL && canSourceHandle && hasActiveAddSign;
        boolean canRevoke = currentUserCanRevoke(task, userId);
        boolean canUrge = currentUserCanUrge(task, userId);
        boolean canRead = task.taskKind == TaskKind.CC && "CC_PENDING".equals(task.status) && userId.equals(task.assigneeUserId);
        boolean canRejectRoute = canHandle;
        boolean canJump = canHandle;
        boolean canTakeBack = currentUserCanTakeBack(task, userId);
        boolean canWakeUp = currentUserCanWakeUp(task, userId);
        boolean canDelegate = canSourceHandle;
        boolean canHandover = currentUserIsProcessAdmin() && task.taskKind == TaskKind.NORMAL && "PENDING".equals(task.status);
        return new TaskActionAvailabilityResponse(
                canClaim,
                canHandle,
                canHandle,
                canHandle && task.taskKind == TaskKind.NORMAL,
                canHandle && task.taskKind == TaskKind.NORMAL && task.previousTaskId != null,
                canAddSign,
                canRemoveSign,
                canRevoke,
                canUrge,
                canRead,
                canRejectRoute,
                canJump,
                canTakeBack,
                canWakeUp,
                canDelegate,
                canHandover
        );
    }

    private boolean currentUserCanRevoke(DemoTask task, String userId) {
        DemoProcessInstance instance = requireInstance(task.instanceId);
        return userId.equals(instance.initiatorUserId) && hasBlockingActiveTasks(instance);
    }

    private boolean currentUserCanUrge(DemoTask task, String userId) {
        DemoProcessInstance instance = requireInstance(task.instanceId);
        return userId.equals(instance.initiatorUserId) && hasBlockingActiveTasks(instance);
    }

    private boolean currentUserCanTakeBack(DemoTask task, String userId) {
        DemoTask previousTask = task.previousTaskId == null ? null : tasksById.get(task.previousTaskId);
        if (previousTask == null || !"PENDING".equals(task.status) || task.readTime != null || task.operatorUserId != null) {
            return false;
        }
        String previousOperator = previousTask.operatorUserId != null ? previousTask.operatorUserId : previousTask.assigneeUserId;
        return previousOperator != null && previousOperator.equals(userId);
    }

    private boolean currentUserCanWakeUp(DemoTask task, String userId) {
        DemoProcessInstance instance = requireInstance(task.instanceId);
        return List.of("COMPLETED", "REJECTED", "REVOKED").contains(instance.status)
                && userId.equals(instance.initiatorUserId)
                && sourceTaskExistsForWakeUp(instance, task.taskId);
    }

    private boolean canProxyHandle(DemoTask task, String userId) {
        if (task.taskKind != TaskKind.NORMAL || !"PENDING".equals(task.status) || task.assigneeUserId == null) {
            return false;
        }
        return fixtureAuthService.isActiveDelegate(task.assigneeUserId, userId);
    }

    private boolean currentUserIsProcessAdmin() {
        return fixtureAuthService.isProcessAdmin(currentUserId());
    }

    private CurrentUserResponse currentUser() {
        return fixtureAuthService.currentUser();
    }

    private Set<String> activeDelegationPrincipalIds() {
        return currentUser().delegations().stream()
                .filter(delegation -> "ACTIVE".equalsIgnoreCase(delegation.status()))
                .map(CurrentUserResponse.Delegation::principalUserId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean sourceTaskExistsForWakeUp(DemoProcessInstance instance, String taskId) {
        return tasksById.values().stream().anyMatch(task -> instance.instanceId.equals(task.instanceId) && task.taskId.equals(taskId));
    }

    private boolean hasBlockingActiveTasks(DemoProcessInstance instance) {
        return instance.activeTaskIds.stream()
                .map(tasksById::get)
                .anyMatch(task -> task != null && !isTerminal(task) && task.taskKind != TaskKind.CC);
    }

    private boolean hasActiveAddSignChild(String sourceTaskId) {
        return tasksById.values().stream()
                .filter(task -> task.taskKind == TaskKind.ADD_SIGN)
                .filter(task -> sourceTaskId.equals(task.sourceTaskId))
                .anyMatch(task -> !isTerminal(task));
    }

    private boolean isTerminal(DemoTask task) {
        return switch (task.status) {
            case "COMPLETED", "TRANSFERRED", "RETURNED", "REVOKED", "CC_READ", "REJECTED", "JUMPED", "TAKEN_BACK", "DELEGATED", "HANDOVERED" -> true;
            default -> false;
        };
    }

    private void requireSourceAssignee(DemoTask task, String userId, String actionLabel) {
        if (!"PENDING".equals(task.status) || task.assigneeUserId == null || !task.assigneeUserId.equals(userId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许执行" + actionLabel,
                    Map.of("taskId", task.taskId, "status", task.status, "userId", userId)
            );
        }
    }

    private void requireCcRecipient(DemoTask task, String userId) {
        if (task.taskKind != TaskKind.CC || task.assigneeUserId == null || !task.assigneeUserId.equals(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "当前用户不在抄送目标范围内",
                    Map.of("taskId", task.taskId, "userId", userId)
            );
        }
    }

    private void maybeAutoRead(DemoTask task, DemoProcessInstance instance) {
        if (task.taskKind == TaskKind.CC || task.readTime != null) {
            return;
        }
        if ("PENDING".equals(task.status) || "PENDING_CLAIM".equals(task.status)) {
            markDetailRead(task, instance, currentUserId());
        }
    }

    private void markDetailRead(DemoTask task, DemoProcessInstance instance, String currentUserId) {
        if (task.readTime == null) {
            task.readTime = now();
        }
        task.handleStartTime = task.handleStartTime == null ? task.readTime : task.handleStartTime;
        task.updatedAt = task.readTime;
        recordInstanceEvent(
                instance.instanceId,
                task.taskId,
                task.nodeId,
                "TASK_READ",
                "任务已阅读",
                currentUserId,
                "TASK",
                task.sourceTaskId,
                task.taskId,
                task.targetUserId != null ? task.targetUserId : task.assigneeUserId,
                eventDetails(
                        "status", task.status,
                        "sourceTaskId", task.sourceTaskId,
                        "targetTaskId", task.taskId,
                        "targetUserId", task.targetUserId != null ? task.targetUserId : task.assigneeUserId
                )
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

    private Map<String, Object> delegatedAssignment(Map<String, Object> sourceAssignment, String targetUserId) {
        Map<String, Object> assignment = new HashMap<>(sourceAssignment);
        assignment.put("mode", "USER");
        assignment.put("userIds", List.of(targetUserId));
        assignment.put("roleCodes", List.of());
        assignment.put("departmentRef", "");
        assignment.put("formFieldKey", "");
        return assignment;
    }

    private RejectTarget resolveRejectTarget(
            DemoProcessInstance instance,
            DemoTask task,
            RejectTaskRequest request,
            String targetStrategy
    ) {
        return switch (targetStrategy) {
            case "PREVIOUS_USER_TASK" -> {
                DemoTask previousTask = task.previousTaskId == null ? null : tasksById.get(task.previousTaskId);
                if (previousTask == null) {
                    throw new ContractException(
                            "PROCESS.ACTION_NOT_ALLOWED",
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "当前任务不存在可驳回的上一步人工节点",
                            Map.of("taskId", task.taskId, "targetStrategy", targetStrategy)
                    );
                }
                yield buildRejectTarget(previousTask);
            }
            case "INITIATOR" -> {
                DemoTask initiatorTask = tasksForInstance(instance.instanceId).stream()
                        .filter(candidate -> candidate.taskKind != TaskKind.CC)
                        .filter(candidate -> instance.initiatorUserId.equals(candidate.operatorUserId)
                                || instance.initiatorUserId.equals(candidate.assigneeUserId))
                        .max(Comparator.comparing(DemoTask::completedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(DemoTask::createdAt)
                                .thenComparing(DemoTask::taskId))
                        .orElse(null);
                if (initiatorTask == null) {
                    throw new ContractException(
                            "PROCESS.ACTION_NOT_ALLOWED",
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "当前流程不存在可驳回到发起人的任务",
                            Map.of("taskId", task.taskId, "targetStrategy", targetStrategy)
                    );
                }
                yield buildRejectTarget(initiatorTask);
            }
            case "ANY_USER_TASK" -> {
                DemoTask targetTask = null;
                if (request.targetTaskId() != null && !request.targetTaskId().isBlank()) {
                    targetTask = tasksById.get(request.targetTaskId());
                }
                if (targetTask == null && request.targetNodeId() != null && !request.targetNodeId().isBlank()) {
                    targetTask = tasksForInstance(instance.instanceId).stream()
                            .filter(candidate -> request.targetNodeId().equals(candidate.nodeId))
                            .filter(candidate -> candidate.taskKind != TaskKind.CC)
                            .max(Comparator.comparing(DemoTask::completedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(DemoTask::createdAt)
                                    .thenComparing(DemoTask::taskId))
                            .orElse(null);
                }
                if (targetTask == null) {
                    throw new ContractException(
                            "VALIDATION.REQUEST_INVALID",
                            HttpStatus.BAD_REQUEST,
                            "驳回到任意节点时必须指定 targetTaskId 或 targetNodeId",
                            Map.of("taskId", task.taskId)
                    );
                }
                yield buildRejectTarget(targetTask);
            }
            default -> throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "不支持的驳回目标策略",
                    Map.of("taskId", task.taskId, "targetStrategy", targetStrategy)
            );
        };
    }

    private RejectTarget buildRejectTarget(DemoTask targetTask) {
        String targetUserId = targetTask.assigneeUserId != null ? targetTask.assigneeUserId : targetTask.operatorUserId;
        return new RejectTarget(
                targetTask.nodeId,
                targetTask.nodeName,
                new HashMap<>(targetTask.assignment),
                targetTask.originTaskId,
                targetUserId
        );
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

    private void requirePendingHandler(DemoTask task, String userId, String actionLabel) {
        boolean directAssignee = "PENDING".equals(task.status)
                && task.assigneeUserId != null
                && task.assigneeUserId.equals(userId);
        if (!directAssignee && !canProxyHandle(task, userId)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前任务不允许执行" + actionLabel,
                    Map.of("taskId", task.taskId, "status", task.status, "userId", userId)
            );
        }
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private void recordInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String operatorUserId,
            Map<String, Object> details
    ) {
        recordInstanceEvent(instanceId, taskId, nodeId, eventType, eventName, operatorUserId, null, null, null, null, details, null, null, null, null, null, null, null);
    }

    private void recordInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String operatorUserId,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details
    ) {
        recordInstanceEvent(instanceId, taskId, nodeId, eventType, eventName, operatorUserId, actionCategory, sourceTaskId, targetTaskId, targetUserId, details, null, null, null, null, null, null, null);
    }

    private void recordInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String operatorUserId,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy
    ) {
        recordInstanceEvent(
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                operatorUserId,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                null,
                null,
                null,
                null
        );
    }

    private void recordInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String operatorUserId,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId
    ) {
        instanceEvents.add(new ProcessInstanceEventResponse(
                newId("evt"),
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                operatorUserId,
                now(),
                details == null ? Map.of() : new HashMap<>(details),
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId
        ));
    }

    private Map<String, Object> eventDetails(Object... keyValues) {
        Map<String, Object> details = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key != null && value != null) {
                details.put(String.valueOf(key), value);
            }
        }
        return details;
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

    private DemoTask resolveDetailTask(DemoProcessInstance instance) {
        if (!instance.activeTaskIds.isEmpty()) {
            return instance.activeTaskIds.stream()
                    .sorted()
                    .map(tasksById::get)
                    .filter(task -> task != null)
                    .findFirst()
                    .orElseThrow(() -> new ContractException(
                            "PROCESS.INSTANCE_NOT_FOUND",
                            HttpStatus.NOT_FOUND,
                            "审批单不存在或未关联流程实例",
                            Map.of("businessType", instance.businessType, "businessId", instance.businessKey)
                    ));
        }

        return tasksById.values().stream()
                .filter(task -> instance.instanceId.equals(task.instanceId))
                .max(Comparator.comparing(DemoTask::createdAt).thenComparing(DemoTask::taskId))
                .orElseThrow(() -> new ContractException(
                        "PROCESS.INSTANCE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "审批单不存在或未关联流程实例",
                        Map.of("businessType", instance.businessType, "businessId", instance.businessKey)
                ));
    }

    private DemoProcessInstance requireInstanceByBusiness(String businessType, String businessId) {
        if (businessType == null || businessType.isBlank() || businessId == null || businessId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "businessType 和 businessId 不能为空",
                    Map.of("businessType", businessType, "businessId", businessId)
            );
        }

        return instancesById.values().stream()
                .filter(instance -> businessId.equals(instance.businessKey))
                .filter(instance -> instance.businessType == null || businessType.equals(instance.businessType))
                .max(Comparator.comparing((DemoProcessInstance instance) -> instance.createdAt)
                        .thenComparing(instance -> instance.instanceId))
                .orElseThrow(() -> new ContractException(
                        "PROCESS.INSTANCE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "审批单不存在或未关联流程实例",
                        Map.of("businessType", businessType, "businessId", businessId)
                ));
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

    private record ApprovalSheetProjection(
            DemoProcessInstance instance,
            DemoTask currentTask,
            DemoTask latestTask,
            DemoTask latestHandledTask,
            Map<String, Object> businessData,
            String billNo,
            String businessTitle
    ) {
        private OffsetDateTime completedAt() {
            return "COMPLETED".equals(instance.status) ? instance.updatedAt : latestTask.completedAt;
        }

        private String latestAction() {
            return latestHandledTask != null ? latestHandledTask.action : latestTask.action;
        }

        private String latestOperatorUserId() {
            return latestHandledTask != null ? latestHandledTask.operatorUserId : latestTask.operatorUserId;
        }
    }

    private record RejectTarget(
            String nodeId,
            String nodeName,
            Map<String, Object> assignment,
            String originTaskId,
            String targetUserId
    ) {
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
        private final TaskKind taskKind;
        private final String sourceTaskId;
        private final String targetTaskId;
        private String targetUserId;
        private Map<String, Object> taskFormData = Map.of();
        private OffsetDateTime readTime;
        private OffsetDateTime handleStartTime;
        private OffsetDateTime handleEndTime;
        private String assigneeUserId;
        private OffsetDateTime updatedAt;
        private OffsetDateTime completedAt;
        private String status;
        private String action;
        private String operatorUserId;
        private String comment;
        private boolean revoked;
        private String targetStrategy;
        private String targetNodeId;
        private String targetNodeName;
        private String reapproveStrategy;
        private String actingMode;
        private String actingForUserId;
        private String delegatedByUserId;
        private String handoverFromUserId;
        private String rejectedTaskId;
        private String rejectedTaskNodeId;
        private String rejectedTaskName;
        private String rejectedOriginTaskId;
        private String rejectedTargetUserId;
        private Map<String, Object> rejectedAssignment = Map.of();

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
                String status,
                TaskKind taskKind,
                String sourceTaskId,
                String targetTaskId,
                String targetUserId
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
            this.taskKind = taskKind;
            this.sourceTaskId = sourceTaskId;
            this.targetTaskId = targetTaskId;
            this.targetUserId = targetUserId;
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
                    taskKind.name(),
                    status,
                    stringValue(assignment.get("mode")),
                    candidateUserIds,
                    assigneeUserId,
                    actingMode,
                    actingForUserId,
                    delegatedByUserId,
                    handoverFromUserId
            );
        }
    }

    private static final class DemoProcessInstance {
        private final String instanceId;
        private final String processDefinitionId;
        private final String processKey;
        private final String processName;
        private final String businessKey;
        private final String businessType;
        private final String initiatorUserId;
        private final Map<String, Object> formData;
        private final OffsetDateTime createdAt;
        private final Set<String> activeTaskIds = new HashSet<>();
        private final Set<String> reachedEndNodeIds = new HashSet<>();
        private final Map<String, Integer> joinArrivals = new HashMap<>();
        private String status = "RUNNING";
        private OffsetDateTime updatedAt;
        private boolean completedLogged;

        private DemoProcessInstance(
                String instanceId,
                String processDefinitionId,
                String processKey,
                String processName,
                String businessKey,
                String businessType,
                String initiatorUserId,
                Map<String, Object> formData,
                OffsetDateTime createdAt
        ) {
            this.instanceId = instanceId;
            this.processDefinitionId = processDefinitionId;
            this.processKey = processKey;
            this.processName = processName;
            this.businessKey = businessKey;
            this.businessType = businessType;
            this.initiatorUserId = initiatorUserId;
            this.formData = formData == null ? Map.of() : new HashMap<>(formData);
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }
}
