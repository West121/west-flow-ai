package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.approval.service.ApprovalSheetQueryService;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.ApprovalSheetListView;
import com.westflow.processruntime.api.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.AddSignTaskRequest;
import com.westflow.processruntime.api.ClaimTaskRequest;
import com.westflow.processruntime.api.ClaimTaskResponse;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.DelegateTaskRequest;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.HandoverExecutionResponse;
import com.westflow.processruntime.api.HandoverExecutionTaskItemResponse;
import com.westflow.processruntime.api.HandoverPreviewResponse;
import com.westflow.processruntime.api.HandoverPreviewTaskItemResponse;
import com.westflow.processruntime.api.HandoverTaskRequest;
import com.westflow.processruntime.api.JumpTaskRequest;
import com.westflow.processruntime.api.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessNotificationSendRecordResponse;
import com.westflow.processruntime.api.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.RejectTaskRequest;
import com.westflow.processruntime.api.RevokeTaskRequest;
import com.westflow.processruntime.api.RemoveSignTaskRequest;
import com.westflow.processruntime.api.ReturnTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.TakeBackTaskRequest;
import com.westflow.processruntime.api.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.UrgeTaskRequest;
import com.westflow.processruntime.api.TransferTaskRequest;
import com.westflow.processruntime.api.WakeUpInstanceRequest;
import com.westflow.processruntime.api.WorkflowFieldBinding;
import com.westflow.workflowadmin.service.WorkflowOperationLogService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于真实 Flowable 的最小运行态服务。
 * 当前承接真实发起、待办、审批单详情和已迁移到 Flowable 的任务动作。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableProcessRuntimeService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final FlowableCountersignService flowableCountersignService;
    private final ProcessDefinitionService processDefinitionService;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final ProcessRuntimeTraceStore traceStore;
    private final JdbcTemplate jdbcTemplate;
    private final WorkflowOperationLogService workflowOperationLogService;

    /**
     * 发起真实 Flowable 流程实例。
     */
    public StartProcessResponse start(StartProcessRequest request) {
        return flowableRuntimeStartService.start(request);
    }

    /**
     * 查询当前登录人可见的真实待办。
     */
    public PageResponse<ProcessTaskListItemResponse> page(PageRequest request) {
        String currentUserId = currentUserId();
        List<ProcessTaskListItemResponse> allRecords = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskCandidateOrAssigned(currentUserId)
                .active()
                .list()
                .stream()
                .filter(task -> !"CC".equals(resolveTaskKind(task)))
                .map(this::toTaskListItem)
                .filter(Objects::nonNull)
                .filter(item -> matchesTaskKeyword(item, request.keyword()))
                .sorted(Comparator.comparing(ProcessTaskListItemResponse::createdAt).reversed())
                .toList();
        return page(allRecords, request.page(), request.pageSize());
    }

    /**
     * 查询审批单列表，先覆盖待办、已办、我发起三类主视图。
     */
    public PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request) {
        String currentUserId = currentUserId();
        List<ApprovalSheetListItemResponse> records = switch (request.view()) {
            case TODO -> buildTodoApprovalSheets(currentUserId);
            case INITIATED -> buildInitiatedApprovalSheets(currentUserId, request.businessTypes());
            case DONE -> buildDoneApprovalSheets(currentUserId, request.businessTypes());
            case CC -> buildCopiedApprovalSheets(currentUserId, request.businessTypes());
        };
        List<ApprovalSheetListItemResponse> filtered = records.stream()
                .filter(item -> matchesApprovalKeyword(item, request.keyword()))
                .sorted(Comparator.comparing(ApprovalSheetListItemResponse::updatedAt).reversed())
                .toList();
        return page(filtered, request.page(), request.pageSize());
    }

    /**
     * 按任务主键查询真实详情。
     */
    public ProcessTaskDetailResponse detail(String taskId) {
        Task activeTask = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
        if (activeTask != null) {
            return buildDetail(activeTask.getProcessInstanceId(), activeTask, null, null, true);
        }
        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        if (historicTask == null) {
            throw taskNotFound(taskId);
        }
        return buildDetail(historicTask.getProcessInstanceId(), null, historicTask, null, false);
    }

    /**
     * 按业务单定位审批单详情。
     */
    public ProcessTaskDetailResponse detailByBusiness(String businessType, String businessId) {
        Optional<BusinessLinkSnapshot> link = findBusinessLink(businessType, businessId);
        String processInstanceId = link.map(BusinessLinkSnapshot::processInstanceId)
                .orElseGet(() -> resolveProcessInstanceIdByBusinessKey(businessId));
        Task activeTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        HistoricTaskInstance latestHistoricTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        return buildDetail(
                processInstanceId,
                activeTask,
                latestHistoricTask,
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(null),
                activeTask != null
        );
    }

    /**
     * 查询实例维度的会签任务组快照。
     */
    public List<CountersignTaskGroupResponse> taskGroups(String instanceId) {
        requireHistoricProcessInstance(instanceId);
        return flowableCountersignService.queryTaskGroups(instanceId);
    }

    /**
     * 查询任务可执行动作。
     */
    public TaskActionAvailabilityResponse actions(String taskId) {
        Task task = requireActiveTask(taskId);
        String taskKind = resolveTaskKind(task);
        List<String> candidateUserIds = candidateUsers(task.getId());
        boolean isCandidate = task.getAssignee() == null && candidateUserIds.contains(currentUserId());
        boolean isAssignee = currentUserId().equals(task.getAssignee());
        boolean canHandle = isAssignee || isCandidate;
        boolean blockedByAddSign = "NORMAL".equals(taskKind) && hasActiveAddSignChild(task.getId());
        boolean canTakeBack = "NORMAL".equals(taskKind) && canTakeBack(task);
        boolean canAddSign = "NORMAL".equals(taskKind) && canHandle && !blockedByAddSign;
        boolean canRemoveSign = "NORMAL".equals(taskKind) && canHandle && blockedByAddSign;
        boolean canRead = "CC".equals(taskKind) && canHandle && "CC_PENDING".equals(resolveTaskStatus(task));
        return new TaskActionAvailabilityResponse(
                "NORMAL".equals(taskKind) && task.getAssignee() == null && isCandidate,
                "NORMAL".equals(taskKind) && canHandle && !blockedByAddSign,
                "NORMAL".equals(taskKind) && canHandle && !blockedByAddSign,
                "NORMAL".equals(taskKind) && canHandle,
                "NORMAL".equals(taskKind) && canHandle && !blockedByAddSign,
                canAddSign,
                canRemoveSign,
                canRevoke(task),
                canUrge(task),
                canRead,
                "NORMAL".equals(taskKind) && canHandle && !blockedByAddSign,
                "NORMAL".equals(taskKind) && canHandle,
                canTakeBack,
                false,
                "NORMAL".equals(taskKind) && canHandle,
                false
        );
    }

    /**
     * 给当前人工任务增加一个临时加签子任务。
     */
    public CompleteTaskResponse addSign(String taskId, AddSignTaskRequest request) {
        Task task = requireTaskForAction(taskId, "加签");
        if (!"NORMAL".equals(resolveTaskKind(task))) {
            throw actionNotAllowed("当前任务不支持加签", Map.of("taskId", taskId));
        }
        if (hasActiveAddSignChild(taskId)) {
            throw actionNotAllowed("当前任务存在未完成的加签任务", Map.of("taskId", taskId));
        }

        String targetUserId = requireTargetUserId(request.targetUserId(), taskId);
        appendComment(task, request.comment());
        Task addSignTask = flowableTaskActionService.createAdhocTask(
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                "ADD_SIGN",
                targetUserId,
                List.of(targetUserId),
                task.getId(),
                eventDetails(
                        "westflowTaskKind", "ADD_SIGN",
                        "westflowSourceTaskId", task.getId(),
                        "westflowTargetUserId", targetUserId
                )
        );
        appendInstanceEvent(
                task.getProcessInstanceId(),
                addSignTask.getId(),
                addSignTask.getTaskDefinitionKey(),
                "TASK_ADD_SIGN",
                "任务已加签",
                "TASK",
                task.getId(),
                addSignTask.getId(),
                targetUserId,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.getId(),
                        "targetTaskId", addSignTask.getId(),
                        "targetUserId", targetUserId
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new CompleteTaskResponse(task.getProcessInstanceId(), taskId, "RUNNING", List.of(toTaskView(addSignTask)));
    }

    /**
     * 移除尚未处理的加签任务。
     */
    public CompleteTaskResponse removeSign(String taskId, RemoveSignTaskRequest request) {
        Task sourceTask = requireTaskForAction(taskId, "减签");
        Task addSignTask = requireActiveTask(request.targetTaskId());
        if (!"ADD_SIGN".equals(resolveTaskKind(addSignTask)) || !taskId.equals(addSignTask.getParentTaskId())) {
            throw actionNotAllowed("目标任务不是当前节点的加签任务", Map.of("taskId", taskId, "targetTaskId", request.targetTaskId()));
        }

        appendComment(sourceTask, request.comment());
        flowableTaskActionService.deleteTask(addSignTask.getId(), "WESTFLOW_REMOVE_SIGN");
        appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                addSignTask.getId(),
                addSignTask.getTaskDefinitionKey(),
                "TASK_REMOVE_SIGN",
                "加签任务已移除",
                "TASK",
                taskId,
                addSignTask.getId(),
                addSignTask.getAssignee(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", taskId,
                        "targetTaskId", addSignTask.getId(),
                        "targetUserId", addSignTask.getAssignee()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(sourceTask.getProcessInstanceId(), taskId);
    }

    /**
     * 将抄送任务标记为已阅。
     */
    public CompleteTaskResponse read(String taskId) {
        Task task = requireActiveTask(taskId);
        if (!"CC".equals(resolveTaskKind(task))) {
            throw actionNotAllowed("当前任务不支持已阅", Map.of("taskId", taskId));
        }
        List<String> candidateUserIds = candidateUsers(taskId);
        boolean canRead = currentUserId().equals(task.getAssignee())
                || (task.getAssignee() == null && candidateUserIds.contains(currentUserId()));
        if (!canRead) {
            throw actionNotAllowed("当前用户不能操作该抄送任务", Map.of("taskId", taskId, "userId", currentUserId()));
        }

        Task activeTask = claimTaskIfNeeded(task, currentUserId());
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowTaskKind", "CC");
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowReadTime", Timestamp.from(Instant.now()));
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowAction", "READ");
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowTargetUserId", currentUserId());
        flowableTaskActionService.complete(activeTask.getId(), Map.of());
        appendInstanceEvent(
                activeTask.getProcessInstanceId(),
                activeTask.getId(),
                activeTask.getTaskDefinitionKey(),
                "TASK_READ",
                "抄送已阅",
                "CC",
                activeTask.getParentTaskId(),
                activeTask.getId(),
                currentUserId(),
                eventDetails(
                        "sourceTaskId", activeTask.getParentTaskId(),
                        "targetTaskId", activeTask.getId(),
                        "targetUserId", currentUserId()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(activeTask.getProcessInstanceId(), taskId);
    }

    /**
     * 认领 Flowable 待办。
     */
    public ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        String assigneeUserId = currentUserId();
        if (request != null && request.comment() != null && !request.comment().isBlank()) {
            Task task = requireActiveTask(taskId);
            flowableEngineFacade.taskService().addComment(taskId, task.getProcessInstanceId(), request.comment().trim());
        }
        flowableTaskActionService.claim(taskId, assigneeUserId);
        Task task = requireActiveTask(taskId);
        return new ClaimTaskResponse(task.getId(), task.getProcessInstanceId(), "PENDING", task.getAssignee());
    }

    /**
     * 完成 Flowable 待办，并返回下一批活动任务。
     */
    public CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        Task task = requireActiveTask(taskId);
        String taskKind = resolveTaskKind(task);
        String operatorUserId = normalizeUserId(request.operatorUserId());
        task = claimTaskIfNeeded(task, operatorUserId);
        if ("CC".equals(taskKind)) {
            throw actionNotAllowed("当前抄送任务不支持办理", Map.of("taskId", taskId));
        }
        if ("NORMAL".equals(taskKind) && hasActiveAddSignChild(taskId)) {
            throw actionNotAllowed("当前任务存在未处理的加签任务", Map.of("taskId", taskId));
        }
        Map<String, Object> variables = actionVariables(
                request.action(),
                operatorUserId,
                request.comment(),
                request.taskFormData()
        );
        variables.putAll(flowableCountersignService.prepareCompletionVariables(
                task.getProcessDefinitionId(),
                task,
                request.action()
        ));
        if (request.taskFormData() != null && !request.taskFormData().isEmpty()) {
            variables.putAll(request.taskFormData());
        }
        appendComment(task, request.comment());
        flowableEngineFacade.taskService().setVariableLocal(taskId, "westflowTaskKind", taskKind);
        flowableTaskActionService.complete(taskId, variables);
        flowableCountersignService.syncAfterTaskCompleted(
                task.getProcessDefinitionId(),
                task.getProcessInstanceId(),
                taskId
        );
        List<DemoTaskView> nextTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(this::isVisibleTask)
                .map(this::toTaskView)
                .toList();
        String status = blockingTaskViews(nextTasks).isEmpty() ? "COMPLETED" : "RUNNING";
        return new CompleteTaskResponse(
                task.getProcessInstanceId(),
                taskId,
                status,
                nextTasks
        );
    }

    /**
     * 把当前任务转办给其他用户。
     */
    public CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        Task task = requireTaskForAction(taskId, "转办");
        appendComment(task, request.comment());
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                actionVariables("TRANSFER", currentUserId(), request.comment(), Map.of("targetUserId", request.targetUserId()))
        );
        flowableTaskActionService.transfer(taskId, request.targetUserId());
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_TRANSFERRED",
                "任务已转办",
                "TASK",
                task.getId(),
                task.getId(),
                request.targetUserId(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", request.targetUserId()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        Task updatedTask = requireActiveTask(taskId);
        return new CompleteTaskResponse(
                updatedTask.getProcessInstanceId(),
                taskId,
                "RUNNING",
                List.of(toTaskView(updatedTask))
        );
    }

    /**
     * 委派任务给其他处理人。
     */
    public CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        Task task = requireTaskForAction(taskId, "委派");
        String targetUserId = requireTargetUserId(request == null ? null : request.targetUserId(), taskId);
        String comment = request == null ? null : request.comment();

        appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                actionVariables("DELEGATE", currentUserId(), comment, Map.of("targetUserId", targetUserId))
        );
        flowableTaskActionService.delegate(taskId, targetUserId);

        Task delegatedTask = requireActiveTask(taskId);
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_DELEGATED",
                "任务已委派",
                "TASK",
                task.getId(),
                delegatedTask.getId(),
                targetUserId,
                eventDetails(
                        "comment", comment,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", delegatedTask.getId(),
                        "targetUserId", targetUserId,
                        "actingMode", "DELEGATE",
                        "actingForUserId", currentUserId(),
                        "delegatedByUserId", currentUserId()
                ),
                null,
                null,
                null,
                "DELEGATE",
                currentUserId(),
                currentUserId(),
                null
        );

        return new CompleteTaskResponse(
                delegatedTask.getProcessInstanceId(),
                task.getId(),
                "RUNNING",
                List.of(toTaskView(delegatedTask))
        );
    }

    /**
     * 将当前节点拿回到上一位办理人手里。
     */
    public CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        Task task = requireActiveTask(taskId);
        if (!canTakeBack(task)) {
            throw actionNotAllowed(
                    "当前任务不满足拿回条件",
                    Map.of("taskId", taskId, "userId", currentUserId())
            );
        }
        HistoricTaskInstance previousTask = requirePreviousUserTask(task);
        String comment = request == null ? null : request.comment();
        appendComment(task, comment);
        flowableEngineFacade.taskService().setVariableLocal(taskId, "westflowAction", "TAKE_BACK");
        flowableTaskActionService.moveToActivity(
                taskId,
                previousTask.getTaskDefinitionKey(),
                actionVariables(
                        "TAKE_BACK",
                        currentUserId(),
                        comment,
                        Map.of(
                                "targetNodeId", previousTask.getTaskDefinitionKey(),
                                "sourceTaskId", taskId,
                                "targetUserId", previousTask.getAssignee()
                        )
                )
        );
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_TAKEN_BACK",
                "任务已拿回",
                "TASK",
                task.getId(),
                task.getId(),
                previousTask.getAssignee(),
                eventDetails(
                        "comment", comment,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetNodeId", previousTask.getTaskDefinitionKey(),
                        "targetUserId", previousTask.getAssignee()
                ),
                "PREVIOUS_USER_TASK",
                previousTask.getTaskDefinitionKey(),
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    /**
     * 由发起人撤销流程实例。
     */
    public CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        Task task = requireActiveTask(taskId);
        String comment = request == null ? null : request.comment();
        String initiatorUserId = stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId"));

        if (initiatorUserId == null || !initiatorUserId.equals(currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以撤销流程",
                    Map.of("taskId", taskId, "userId", currentUserId())
            );
        }

        appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                actionVariables("REVOKE", currentUserId(), comment, Map.of())
        );
        flowableEngineFacade.runtimeService().deleteProcessInstance(
                task.getProcessInstanceId(),
                comment == null || comment.isBlank() ? "流程已撤销" : comment
        );

        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_REVOKED",
                "流程已撤销",
                "INSTANCE",
                task.getId(),
                task.getId(),
                null,
                eventDetails(
                        "comment", comment,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new CompleteTaskResponse(
                task.getProcessInstanceId(),
                task.getId(),
                "REVOKED",
                List.of()
        );
    }

    /**
     * 通过重新启动实例并定位到来源节点，实现历史实例唤醒。
     */
    @Transactional
    public CompleteTaskResponse wakeUp(String instanceId, WakeUpInstanceRequest request) {
        HistoricProcessInstance historicInstance = requireHistoricProcessInstance(instanceId);
        if (historicInstance.getEndTime() == null && historicInstance.getDeleteReason() == null) {
            throw actionNotAllowed("仅已结束的流程实例支持唤醒", Map.of("instanceId", instanceId));
        }

        HistoricTaskInstance sourceTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(request.sourceTaskId())
                .singleResult();
        if (sourceTask == null || !instanceId.equals(sourceTask.getProcessInstanceId())) {
            throw actionNotAllowed(
                    "唤醒来源任务不存在",
                    Map.of("instanceId", instanceId, "sourceTaskId", request.sourceTaskId())
            );
        }

        Map<String, Object> variables = new LinkedHashMap<>(historicVariables(instanceId));
        String processKey = stringValue(variables.get("westflowProcessKey"));
        String platformProcessDefinitionId = stringValue(variables.get("westflowProcessDefinitionId"));
        if (processKey == null || processKey.isBlank()) {
            throw resourceNotFound("流程定义不存在", Map.of("instanceId", instanceId));
        }
        String businessKey = historicInstance.getBusinessKey();
        ProcessInstance newInstance = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey(processKey, businessKey, variables);

        Task firstActiveTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(newInstance.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        if (firstActiveTask != null && !Objects.equals(firstActiveTask.getTaskDefinitionKey(), sourceTask.getTaskDefinitionKey())) {
            flowableTaskActionService.moveToActivity(firstActiveTask.getId(), sourceTask.getTaskDefinitionKey(), Map.of());
        }

        List<Task> awakenedTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(newInstance.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        awakenedTasks.stream()
                .filter(task -> sourceTask.getTaskDefinitionKey().equals(task.getTaskDefinitionKey()))
                .filter(task -> sourceTask.getAssignee() != null && !sourceTask.getAssignee().isBlank())
                .forEach(task -> flowableTaskActionService.transfer(task.getId(), sourceTask.getAssignee()));

        findBusinessLinkByInstanceId(instanceId).ifPresent(link -> {
            insertBusinessLink(
                    link.businessType(),
                    link.businessId(),
                    newInstance.getProcessInstanceId(),
                    platformProcessDefinitionId == null ? link.processDefinitionId() : platformProcessDefinitionId,
                    link.startUserId(),
                    "RUNNING"
            );
            updateBusinessProcessLink(link.businessType(), link.businessId(), newInstance.getProcessInstanceId(), "RUNNING");
        });

        appendInstanceEvent(
                newInstance.getProcessInstanceId(),
                null,
                sourceTask.getTaskDefinitionKey(),
                "INSTANCE_WOKEN_UP",
                "流程实例已唤醒",
                "INSTANCE",
                request.sourceTaskId(),
                null,
                sourceTask.getAssignee(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceInstanceId", instanceId,
                        "sourceTaskId", request.sourceTaskId(),
                        "targetNodeId", sourceTask.getTaskDefinitionKey(),
                        "targetUserId", sourceTask.getAssignee()
                ),
                null,
                sourceTask.getTaskDefinitionKey(),
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(newInstance.getProcessInstanceId(), request.sourceTaskId());
    }

    /**
     * 发起人催办当前任务。
     */
    public CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        Task task = requireActiveTask(taskId);
        String comment = request == null ? null : request.comment();
        String initiatorUserId = stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId"));

        if (initiatorUserId == null || !initiatorUserId.equals(currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以催办",
                    Map.of("taskId", taskId, "userId", currentUserId())
            );
        }

        appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                actionVariables("URGE", currentUserId(), comment, Map.of())
        );

        String targetUserId = resolveCurrentTaskOwner(task);
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_URGED",
                "任务已催办",
                "TASK",
                task.getId(),
                task.getId(),
                targetUserId,
                eventDetails(
                        "comment", comment,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", targetUserId
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new CompleteTaskResponse(
                task.getProcessInstanceId(),
                task.getId(),
                "RUNNING",
                List.of(toTaskView(task))
        );
    }

    /**
     * 直接执行离职转办，并返回平台侧摘要。
     */
    @Transactional
    public CompleteTaskResponse handover(String sourceUserId, HandoverTaskRequest request) {
        HandoverExecutionResponse execution = executeHandover(sourceUserId, request);
        return new CompleteTaskResponse(
                execution.instanceId(),
                execution.completedTaskId(),
                execution.status(),
                execution.nextTasks()
        );
    }

    /**
     * 预览离职转办会影响的在办任务。
     */
    public HandoverPreviewResponse previewHandover(String sourceUserId, HandoverTaskRequest request) {
        String normalizedSourceUserId = normalizeTargetUserId(sourceUserId, "sourceUserId");
        String targetUserId = requireTargetUserId(request.targetUserId(), normalizedSourceUserId);
        List<HandoverPreviewTaskItemResponse> previewTasks = activeTasksAssignedTo(normalizedSourceUserId).stream()
                .map(task -> toHandoverPreviewTask(task, normalizedSourceUserId))
                .toList();
        return new HandoverPreviewResponse(
                normalizedSourceUserId,
                resolveUserDisplayName(normalizedSourceUserId),
                targetUserId,
                resolveUserDisplayName(targetUserId),
                previewTasks.size(),
                previewTasks
        );
    }

    /**
     * 执行离职转办，把来源人的在办任务整体改派给目标人。
     */
    @Transactional
    public HandoverExecutionResponse executeHandover(String sourceUserId, HandoverTaskRequest request) {
        String normalizedSourceUserId = normalizeTargetUserId(sourceUserId, "sourceUserId");
        String targetUserId = requireTargetUserId(request.targetUserId(), normalizedSourceUserId);
        String comment = request.comment();
        List<Task> sourceTasks = activeTasksAssignedTo(normalizedSourceUserId);

        List<HandoverExecutionTaskItemResponse> executionTasks = new ArrayList<>();
        List<DemoTaskView> nextTasks = new ArrayList<>();
        for (Task task : sourceTasks) {
            appendComment(task, comment);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowAction", "HANDOVER");
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowActingMode", "HANDOVER");
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowActingForUserId", normalizedSourceUserId);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowHandoverFromUserId", normalizedSourceUserId);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowTargetUserId", targetUserId);
            flowableTaskActionService.transfer(task.getId(), targetUserId);

            Task updatedTask = requireActiveTask(task.getId());
            appendInstanceEvent(
                    updatedTask.getProcessInstanceId(),
                    updatedTask.getId(),
                    updatedTask.getTaskDefinitionKey(),
                    "TASK_HANDOVERED",
                    "任务已离职转办",
                    "TASK",
                    task.getId(),
                    updatedTask.getId(),
                    targetUserId,
                    eventDetails(
                            "comment", comment,
                            "sourceTaskId", task.getId(),
                            "targetTaskId", updatedTask.getId(),
                            "targetUserId", targetUserId
                    ),
                    null,
                    null,
                    null,
                    "HANDOVER",
                    normalizedSourceUserId,
                    null,
                    normalizedSourceUserId
            );

            nextTasks.add(toTaskView(updatedTask));
            executionTasks.add(toHandoverExecutionTask(task, updatedTask, normalizedSourceUserId, targetUserId, comment));
        }

        Task firstTask = sourceTasks.stream().findFirst().orElse(null);
        return new HandoverExecutionResponse(
                normalizedSourceUserId,
                resolveUserDisplayName(normalizedSourceUserId),
                targetUserId,
                resolveUserDisplayName(targetUserId),
                executionTasks.size(),
                firstTask == null ? null : firstTask.getProcessInstanceId(),
                firstTask == null ? null : firstTask.getId(),
                executionTasks.isEmpty() ? "COMPLETED" : "RUNNING",
                nextTasks,
                executionTasks
        );
    }

    /**
     * 跳转当前任务到指定节点。
     */
    public CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        Task task = requireTaskForAction(taskId, "跳转");
        String targetNodeId = requireTargetNode(task.getProcessDefinitionId(), request.targetNodeId());
        String targetNodeName = resolveNodeName(task.getProcessDefinitionId(), targetNodeId);
        appendComment(task, request.comment());
        flowableTaskActionService.moveToActivity(
                taskId,
                targetNodeId,
                actionVariables("JUMP", currentUserId(), request.comment(), Map.of("targetNodeId", targetNodeId))
        );
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_JUMPED",
                "任务已跳转",
                "TASK",
                task.getId(),
                task.getId(),
                null,
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetNodeId", targetNodeId,
                        "targetNodeName", targetNodeName
                ),
                "JUMP",
                targetNodeId,
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    /**
     * 退回到上一人工节点。
     */
    public CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        Task task = requireTaskForAction(taskId, "退回");
        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy().trim();
        if (!"PREVIOUS_USER_TASK".equals(targetStrategy)) {
            throw actionNotAllowed("当前仅支持退回上一步人工节点", Map.of("targetStrategy", targetStrategy));
        }
        HistoricTaskInstance previousTask = requirePreviousUserTask(task);
        appendComment(task, request.comment());
        flowableTaskActionService.moveToActivity(
                taskId,
                previousTask.getTaskDefinitionKey(),
                actionVariables("RETURN", currentUserId(), request.comment(), Map.of("targetStrategy", targetStrategy))
        );
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_RETURNED",
                "任务已退回",
                "TASK",
                task.getId(),
                task.getId(),
                previousTask.getAssignee(),
                eventDetails(
                        "comment", request.comment(),
                        "targetStrategy", targetStrategy,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", previousTask.getAssignee()
                ),
                targetStrategy,
                previousTask.getTaskDefinitionKey(),
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    /**
     * 按轻量策略驳回当前任务。
     */
    public CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        Task task = requireTaskForAction(taskId, "驳回");
        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy().trim();
        RejectTarget target = resolveRejectTarget(task, request, targetStrategy);
        appendComment(task, request.comment());
        flowableTaskActionService.moveToActivity(
                taskId,
                target.nodeId(),
                actionVariables(
                        "REJECT_ROUTE",
                        currentUserId(),
                        request.comment(),
                        Map.of(
                                "targetStrategy", targetStrategy,
                                "targetNodeId", target.nodeId(),
                                "targetNodeName", target.nodeName(),
                                "reapproveStrategy", normalizeReapproveStrategy(request.reapproveStrategy())
                        )
                )
        );
        appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_REJECTED",
                "任务已驳回",
                "TASK",
                task.getId(),
                task.getId(),
                target.targetUserId(),
                eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", target.targetUserId(),
                        "targetStrategy", targetStrategy,
                        "targetNodeId", target.nodeId(),
                        "targetNodeName", target.nodeName(),
                        "reapproveStrategy", normalizeReapproveStrategy(request.reapproveStrategy())
                ),
                targetStrategy,
                target.nodeId(),
                normalizeReapproveStrategy(request.reapproveStrategy()),
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    private ProcessTaskDetailResponse buildDetail(
            String processInstanceId,
            Task activeTask,
            HistoricTaskInstance fallbackHistoricTask,
            String platformProcessDefinitionIdOverride,
            boolean active
    ) {
        Map<String, Object> variables = active
                ? runtimeVariables(processInstanceId)
                : historicVariables(processInstanceId);
        HistoricProcessInstance historicProcessInstance = requireHistoricProcessInstance(processInstanceId);
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                platformProcessDefinitionIdOverride,
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessKey")),
                processInstanceId
        );
        ProcessDslPayload payload = definition.dsl();
        String businessType = stringValue(variables.get("westflowBusinessType"));
        String businessKey = stringValue(variables.get("westflowBusinessKey"));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        List<Task> blockingActiveTasks = activeTasks.stream()
                .filter(this::isBlockingTask)
                .toList();
        List<HistoricTaskInstance> historicTasks = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .asc()
                .list();
        Task referenceActiveTask = activeTask != null
                ? activeTask
                : blockingActiveTasks.stream().findFirst().orElse(activeTasks.stream().findFirst().orElse(null));
        HistoricTaskInstance referenceHistoricTask = fallbackHistoricTask != null
                ? fallbackHistoricTask
                : historicTasks.stream().max(Comparator.comparing(HistoricTaskInstance::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))).orElse(null);
        String nodeId = referenceActiveTask != null
                ? referenceActiveTask.getTaskDefinitionKey()
                : referenceHistoricTask == null ? null : referenceHistoricTask.getTaskDefinitionKey();
        String nodeName = referenceActiveTask != null
                ? referenceActiveTask.getName()
                : referenceHistoricTask == null ? null : referenceHistoricTask.getName();
        Map<String, Object> nodeConfig = nodeConfig(payload, nodeId);
        String nodeFormKey = stringValue(nodeConfig.get("nodeFormKey"));
        String nodeFormVersion = stringValue(nodeConfig.get("nodeFormVersion"));
        List<WorkflowFieldBinding> fieldBindings = workflowFieldBindings(nodeConfig.get("fieldBindings"));
        List<ProcessTaskTraceItemResponse> taskTrace = buildTaskTrace(historicTasks, activeTasks);
        List<ProcessInstanceEventResponse> instanceEvents = traceStore.queryInstanceEvents(processInstanceId);
        if (instanceEvents.isEmpty()) {
            instanceEvents = buildSyntheticEvents(historicProcessInstance, taskTrace);
        }
        List<ProcessAutomationTraceItemResponse> automationTrace = traceStore.queryAutomationTraces(
                processInstanceId,
                blockingActiveTasks.isEmpty() ? "SUCCESS" : "PENDING",
                stringValue(variables.get("westflowInitiatorUserId")),
                payload,
                toOffsetDateTime(historicProcessInstance.getStartTime())
        );
        List<ProcessNotificationSendRecordResponse> notificationRecords = traceStore.queryNotificationSendRecords(
                processInstanceId,
                blockingActiveTasks.isEmpty() ? "SUCCESS" : "PENDING",
                stringValue(variables.get("westflowInitiatorUserId")),
                payload,
                toOffsetDateTime(historicProcessInstance.getStartTime())
        );
        List<CountersignTaskGroupResponse> countersignGroups = flowableCountersignService.queryTaskGroups(processInstanceId);
        OffsetDateTime createdAt = referenceActiveTask != null
                ? toOffsetDateTime(referenceActiveTask.getCreateTime())
                : referenceHistoricTask == null ? toOffsetDateTime(historicProcessInstance.getStartTime()) : toOffsetDateTime(referenceHistoricTask.getCreateTime());
        OffsetDateTime completedAt = referenceHistoricTask == null ? null : toOffsetDateTime(referenceHistoricTask.getEndTime());
        Long handleDurationSeconds = durationSeconds(createdAt, completedAt);
        String instanceStatus = resolveInstanceStatus(historicProcessInstance, activeTasks);
        return new ProcessTaskDetailResponse(
                referenceActiveTask != null ? referenceActiveTask.getId() : referenceHistoricTask == null ? null : referenceHistoricTask.getId(),
                processInstanceId,
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                stringValue(variables.get("westflowInitiatorUserId")),
                businessData,
                blockingActiveTasks.isEmpty() ? "SUCCESS" : "PENDING",
                payload.nodes(),
                payload.edges(),
                instanceEvents,
                taskTrace,
                automationTrace,
                notificationRecords,
                nodeId,
                nodeName,
                resolveTaskKind(referenceActiveTask != null ? referenceActiveTask.getProcessDefinitionId() : historicProcessInstance.getProcessDefinitionId(), nodeId),
                activeTasks.isEmpty() ? resolveHistoricTaskStatus(referenceHistoricTask, historicProcessInstance) : resolveTaskStatus(referenceActiveTask),
                "USER",
                referenceActiveTask != null ? candidateUsers(referenceActiveTask.getId()) : List.of(),
                referenceActiveTask != null ? referenceActiveTask.getAssignee() : referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee(),
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                stringValue(variables.get("westflowLastComment")),
                createdAt,
                createdAt,
                createdAt,
                completedAt,
                handleDurationSeconds,
                null,
                null,
                null,
                null,
                resolveActingMode(referenceActiveTask, referenceHistoricTask),
                resolveActingForUserId(referenceActiveTask, referenceHistoricTask),
                resolveDelegatedByUserId(referenceActiveTask, referenceHistoricTask),
                null,
                createdAt,
                completedAt == null ? createdAt : completedAt,
                completedAt,
                instanceStatus,
                payload.processFormKey(),
                payload.processFormVersion(),
                nodeFormKey,
                nodeFormVersion,
                nodeFormKey != null ? nodeFormKey : payload.processFormKey(),
                nodeFormVersion != null ? nodeFormVersion : payload.processFormVersion(),
                fieldBindings,
                mapValue(variables.get("westflowFormData")),
                mapValue(variables.get("westflowTaskFormData")),
                countersignGroups,
                blockingActiveTasks.stream().map(Task::getId).toList()
        );
    }

    private List<ProcessTaskTraceItemResponse> buildTaskTrace(List<HistoricTaskInstance> historicTasks, List<Task> activeTasks) {
        List<ProcessTaskTraceItemResponse> items = new ArrayList<>();
        Set<String> knownTaskIds = new LinkedHashSet<>();
        for (HistoricTaskInstance task : historicTasks) {
            knownTaskIds.add(task.getId());
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            OffsetDateTime endedAt = toOffsetDateTime(task.getEndTime());
            String taskKind = resolveHistoricTaskKind(task);
            Map<String, Object> localVariables = historicTaskLocalVariables(task.getId());
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    taskKind,
                    resolveHistoricTaskStatus(task, null),
                    task.getAssignee(),
                    List.of(),
                    stringValue(localVariables.get("westflowAction")),
                    null,
                    null,
                    createdAt,
                    readTimeValue(localVariables),
                    createdAt,
                    endedAt,
                    durationSeconds(createdAt, endedAt),
                    stringValue(localVariables.get("westflowSourceTaskId")),
                    null,
                    stringValue(localVariables.get("westflowTargetUserId")),
                    "CC".equals(taskKind),
                    "ADD_SIGN".equals(taskKind),
                    isHistoricTaskRevoked(task),
                    false,
                    false,
                    "WESTFLOW_TAKEN_BACK".equals(task.getDeleteReason()),
                    null,
                    null,
                    null,
                    resolveActingMode(null, task),
                    resolveActingForUserId(null, task),
                    resolveDelegatedByUserId(null, task),
                    stringValue(localVariables.get("westflowHandoverFromUserId"))
            ));
        }
        for (Task task : activeTasks) {
            if (knownTaskIds.contains(task.getId())) {
                continue;
            }
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            String taskKind = resolveTaskKind(task);
            Map<String, Object> localVariables = taskLocalVariables(task.getId());
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    taskKind,
                    resolveTraceTaskStatus(task, localVariables),
                    task.getAssignee(),
                    candidateUsers(task.getId()),
                    null,
                    null,
                    null,
                    createdAt,
                    readTimeValue(localVariables),
                    createdAt,
                    null,
                    null,
                    stringValue(localVariables.get("westflowSourceTaskId")),
                    null,
                    stringValue(localVariables.get("westflowTargetUserId")),
                    "CC".equals(taskKind),
                    "ADD_SIGN".equals(taskKind),
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    resolveActingMode(task, null),
                    resolveActingForUserId(task, null),
                    resolveDelegatedByUserId(task, null),
                    stringValue(localVariables.get("westflowHandoverFromUserId"))
            ));
        }
        return items;
    }

    private List<ProcessInstanceEventResponse> buildSyntheticEvents(
            HistoricProcessInstance processInstance,
            List<ProcessTaskTraceItemResponse> taskTrace
    ) {
        List<ProcessInstanceEventResponse> events = new ArrayList<>();
        events.add(new ProcessInstanceEventResponse(
                processInstance.getId() + "::start",
                processInstance.getId(),
                null,
                null,
                "INSTANCE_STARTED",
                "流程实例已发起",
                "INSTANCE",
                null,
                null,
                null,
                processInstance.getStartUserId(),
                toOffsetDateTime(processInstance.getStartTime()),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        for (ProcessTaskTraceItemResponse item : taskTrace) {
            OffsetDateTime occurredAt = item.handleEndTime() != null ? item.handleEndTime() : item.receiveTime();
            events.add(new ProcessInstanceEventResponse(
                    processInstance.getId() + "::" + item.taskId(),
                    processInstance.getId(),
                    item.taskId(),
                    item.nodeId(),
                    item.handleEndTime() == null ? "TASK_CREATED" : "TASK_COMPLETED",
                    item.handleEndTime() == null ? "任务已创建" : "任务已完成",
                    "TASK",
                    null,
                    null,
                null,
                item.assigneeUserId(),
                occurredAt,
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ));
        }
        return events;
    }

    private List<ApprovalSheetListItemResponse> buildTodoApprovalSheets(String currentUserId) {
        Map<String, ApprovalSheetListItemResponse> projections = new LinkedHashMap<>();
        for (ProcessTaskListItemResponse task : page(new PageRequest(1, Integer.MAX_VALUE, null, List.of(), List.of(), List.of())).records()) {
            projections.putIfAbsent(task.instanceId(), toApprovalSheetFromTask(task));
        }
        return List.copyOf(projections.values());
    }

    private List<ApprovalSheetListItemResponse> buildInitiatedApprovalSheets(String currentUserId, List<String> businessTypes) {
        return queryBusinessLinksByStartUser(currentUserId).stream()
                .filter(link -> businessTypes.isEmpty() || businessTypes.contains(link.businessType()))
                .map(this::toApprovalSheetFromLink)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ApprovalSheetListItemResponse> buildDoneApprovalSheets(String currentUserId, List<String> businessTypes) {
        Set<String> instanceIds = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskAssignee(currentUserId)
                .finished()
                .list()
                .stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ApprovalSheetListItemResponse> items = new ArrayList<>();
        for (String instanceId : instanceIds) {
            findBusinessLinkByInstanceId(instanceId)
                    .filter(link -> businessTypes.isEmpty() || businessTypes.contains(link.businessType()))
                    .map(this::toApprovalSheetFromLink)
                    .ifPresent(items::add);
        }
        return items;
    }

    private List<ApprovalSheetListItemResponse> buildCopiedApprovalSheets(String currentUserId, List<String> businessTypes) {
        Map<String, ApprovalSheetListItemResponse> items = new LinkedHashMap<>();
        flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskCandidateOrAssigned(currentUserId)
                .active()
                .list()
                .stream()
                .filter(task -> "CC".equals(resolveTaskKind(task)))
                .filter(task -> businessTypes.isEmpty() || businessTypes.contains(stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowBusinessType"))))
                .forEach(task -> items.putIfAbsent(task.getProcessInstanceId(), toApprovalSheetFromCopiedTask(task)));
        flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskAssignee(currentUserId)
                .finished()
                .list()
                .stream()
                .filter(task -> "CC".equals(resolveHistoricTaskKind(task)))
                .filter(task -> businessTypes.isEmpty() || businessTypes.contains(stringValue(historicVariables(task.getProcessInstanceId()).get("westflowBusinessType"))))
                .forEach(task -> items.putIfAbsent(task.getProcessInstanceId(), toApprovalSheetFromCopiedHistoricTask(task)));
        return List.copyOf(items.values());
    }

    private ApprovalSheetListItemResponse toApprovalSheetFromTask(ProcessTaskListItemResponse task) {
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(task.businessType(), task.businessKey());
        return new ApprovalSheetListItemResponse(
                task.instanceId(),
                task.processDefinitionId(),
                task.processKey(),
                task.processName(),
                task.businessKey(),
                task.businessType(),
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(task.businessType(), businessData),
                task.applicantUserId(),
                task.nodeName(),
                task.taskId(),
                task.status(),
                task.assigneeUserId(),
                "RUNNING",
                null,
                null,
                "PENDING",
                task.createdAt(),
                task.updatedAt(),
                task.completedAt()
        );
    }

    private ApprovalSheetListItemResponse toApprovalSheetFromCopiedTask(Task task) {
        Map<String, Object> variables = runtimeVariables(task.getProcessInstanceId());
        String processKey = stringValue(variables.get("westflowProcessKey"));
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessDefinitionId")),
                processKey,
                task.getProcessInstanceId()
        );
        String businessType = stringValue(variables.get("westflowBusinessType"));
        String businessKey = stringValue(variables.get("westflowBusinessKey"));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new ApprovalSheetListItemResponse(
                task.getProcessInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(businessType, businessData),
                stringValue(variables.get("westflowInitiatorUserId")),
                task.getName(),
                task.getId(),
                resolveTaskStatus(task),
                task.getAssignee(),
                "COMPLETED",
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                "READ".equals(resolveTaskStatus(task)) || "CC_READ".equals(resolveTaskStatus(task)) ? "READ" : "UNREAD",
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getCreateTime()),
                null
        );
    }

    private ApprovalSheetListItemResponse toApprovalSheetFromCopiedHistoricTask(HistoricTaskInstance task) {
        Map<String, Object> variables = historicVariables(task.getProcessInstanceId());
        String processKey = stringValue(variables.get("westflowProcessKey"));
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessDefinitionId")),
                processKey,
                task.getProcessInstanceId()
        );
        String businessType = stringValue(variables.get("westflowBusinessType"));
        String businessKey = stringValue(variables.get("westflowBusinessKey"));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new ApprovalSheetListItemResponse(
                task.getProcessInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(businessType, businessData),
                stringValue(variables.get("westflowInitiatorUserId")),
                task.getName(),
                task.getId(),
                resolveHistoricTaskStatus(task, requireHistoricProcessInstance(task.getProcessInstanceId())),
                task.getAssignee(),
                "COMPLETED",
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                "CC_READ",
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getEndTime()),
                toOffsetDateTime(task.getEndTime())
        );
    }

    private ApprovalSheetListItemResponse toApprovalSheetFromLink(BusinessLinkSnapshot link) {
        HistoricProcessInstance historicProcessInstance = requireHistoricProcessInstance(link.processInstanceId());
        Map<String, Object> variables = runtimeVariables(link.processInstanceId());
        if (variables.isEmpty()) {
            variables = historicVariables(link.processInstanceId());
        }
        String processKey = stringValue(variables.get("westflowProcessKey"));
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                link.processDefinitionId(),
                stringValue(variables.get("westflowProcessDefinitionId")),
                processKey,
                link.processInstanceId()
        );
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(link.processInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        HistoricTaskInstance latestHistoricTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(link.processInstanceId())
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(link.businessType(), link.businessId());
        Task currentTask = activeTasks.stream().findFirst().orElse(null);
        OffsetDateTime updatedAt = currentTask != null
                ? toOffsetDateTime(currentTask.getCreateTime())
                : latestHistoricTask == null ? toOffsetDateTime(historicProcessInstance.getStartTime()) : toOffsetDateTime(latestHistoricTask.getEndTime());
        return new ApprovalSheetListItemResponse(
                link.processInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                link.businessId(),
                link.businessType(),
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(link.businessType(), businessData),
                link.startUserId(),
                currentTask != null ? currentTask.getName() : latestHistoricTask == null ? null : latestHistoricTask.getName(),
                currentTask != null ? currentTask.getId() : latestHistoricTask == null ? null : latestHistoricTask.getId(),
                currentTask != null ? resolveTaskStatus(currentTask) : latestHistoricTask == null ? null : "COMPLETED",
                currentTask != null ? currentTask.getAssignee() : latestHistoricTask == null ? null : latestHistoricTask.getAssignee(),
                currentTask == null ? "COMPLETED" : "RUNNING",
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                currentTask == null ? "SUCCESS" : "PENDING",
                toOffsetDateTime(historicProcessInstance.getStartTime()),
                updatedAt,
                toOffsetDateTime(historicProcessInstance.getEndTime())
        );
    }

    private Task requireTaskForAction(String taskId, String actionLabel) {
        Task task = requireActiveTask(taskId);
        List<String> candidateUserIds = candidateUsers(taskId);
        boolean canHandle = currentUserId().equals(task.getAssignee())
                || (task.getAssignee() == null && candidateUserIds.contains(currentUserId()));
        if (!canHandle) {
            throw actionNotAllowed(
                    "当前任务不允许执行" + actionLabel,
                    eventDetails("taskId", taskId, "userId", currentUserId(), "assigneeUserId", task.getAssignee())
            );
        }
        return claimTaskIfNeeded(task, currentUserId());
    }

    // 在委派、催办场景下校验目标用户。
    private String requireTargetUserId(String targetUserId, String taskId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserId 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return targetUserId.trim();
    }

    private String normalizeTargetUserId(String userId, String fieldName) {
        if (userId == null || userId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    fieldName + " 不能为空",
                    Map.of("field", fieldName)
            );
        }
        return userId.trim();
    }

    // 获取当前任务当前处理人；无处理人时回退到候选人。
    private String resolveCurrentTaskOwner(Task task) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            return task.getAssignee();
        }
        List<String> candidates = candidateUsers(task.getId());
        return candidates.isEmpty() ? null : candidates.get(0);
    }


    private boolean hasInitiatorPermission(Task task) {
        if (task == null) {
            return false;
        }
        String processInstanceId = task.getProcessInstanceId();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        String initiatorUserId = stringValue(runtimeVariables(processInstanceId).get("westflowInitiatorUserId"));
        return currentUserId().equals(initiatorUserId);
    }

    private Task claimTaskIfNeeded(Task task, String assigneeUserId) {
        if (task.getAssignee() == null) {
            flowableTaskActionService.claim(task.getId(), assigneeUserId);
            return requireActiveTask(task.getId());
        }
        return task;
    }

    private List<Task> activeTasksAssignedTo(String assigneeUserId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskAssignee(assigneeUserId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(task -> "NORMAL".equals(resolveTaskKind(task)))
                .toList();
    }

    private Map<String, Object> actionVariables(
            String action,
            String operatorUserId,
            String comment,
            Map<String, Object> taskFormData
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("westflowLastAction", action);
        variables.put("westflowLastOperatorUserId", operatorUserId);
        variables.put("westflowLastComment", comment);
        variables.put("westflowTaskFormData", taskFormData == null || taskFormData.isEmpty() ? Map.of() : taskFormData);
        return variables;
    }

    private void appendComment(Task task, String comment) {
        if (comment != null && !comment.isBlank()) {
            flowableEngineFacade.taskService().addComment(task.getId(), task.getProcessInstanceId(), comment.trim());
        }
    }

    private String requireTargetNode(String processDefinitionId, String targetNodeId) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "目标节点不能为空",
                    eventDetails("targetNodeId", targetNodeId)
            );
        }
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || model.getFlowElement(targetNodeId) == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "目标节点不存在",
                    eventDetails("targetNodeId", targetNodeId)
            );
        }
        return targetNodeId;
    }

    private String resolveNodeName(String processDefinitionId, String targetNodeId) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || model.getFlowElement(targetNodeId) == null) {
            return targetNodeId;
        }
        String name = model.getFlowElement(targetNodeId).getName();
        return name == null || name.isBlank() ? targetNodeId : name;
    }

    private HistoricTaskInstance requirePreviousUserTask(Task task) {
        return flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list()
                .stream()
                .filter(candidate -> !candidate.getId().equals(task.getId()))
                .findFirst()
                .orElseThrow(() -> actionNotAllowed(
                        "当前任务不存在可退回的上一步人工节点",
                        Map.of("taskId", task.getId(), "processInstanceId", task.getProcessInstanceId())
                ));
    }

    private RejectTarget resolveRejectTarget(Task task, RejectTaskRequest request, String targetStrategy) {
        return switch (targetStrategy) {
            case "PREVIOUS_USER_TASK" -> {
                HistoricTaskInstance previousTask = requirePreviousUserTask(task);
                yield new RejectTarget(
                        previousTask.getTaskDefinitionKey(),
                        previousTask.getName(),
                        previousTask.getAssignee()
                );
            }
            case "ANY_USER_TASK" -> {
                String targetNodeId = requireTargetNode(task.getProcessDefinitionId(), request.targetNodeId());
                yield new RejectTarget(
                        targetNodeId,
                        resolveNodeName(task.getProcessDefinitionId(), targetNodeId),
                        null
                );
            }
            default -> throw actionNotAllowed(
                    "当前真实运行态仅支持驳回到上一步或指定节点",
                    Map.of("targetStrategy", targetStrategy)
            );
        };
    }

    private String normalizeReapproveStrategy(String reapproveStrategy) {
        return reapproveStrategy == null || reapproveStrategy.isBlank()
                ? "CONTINUE"
                : reapproveStrategy.trim();
    }

    private CompleteTaskResponse nextTaskResponse(String processInstanceId, String completedTaskId) {
        List<DemoTaskView> nextTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(this::isVisibleTask)
                .map(this::toTaskView)
                .toList();
        return new CompleteTaskResponse(
                processInstanceId,
                completedTaskId,
                blockingTaskViews(nextTasks).isEmpty() ? "COMPLETED" : "RUNNING",
                nextTasks
        );
    }

    private void appendInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
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
        Map<String, Object> processVariables = runtimeOrHistoricVariables(instanceId);
        ProcessInstanceEventResponse event = new ProcessInstanceEventResponse(
                instanceId + "::" + eventType + "::" + UUID.randomUUID(),
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                currentUserId(),
                OffsetDateTime.now(TIME_ZONE),
                details == null ? Map.of() : details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId
        );
        traceStore.appendInstanceEvent(event);
        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                instanceId,
                stringValue(processVariables.get("westflowProcessDefinitionId")),
                activeFlowableDefinitionId(instanceId),
                stringValue(processVariables.get("westflowBusinessType")),
                stringValue(processVariables.get("westflowBusinessKey")),
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                currentUserId(),
                targetUserId,
                sourceTaskId,
                targetTaskId,
                details == null ? null : stringValue(details.get("comment")),
                event.details(),
                java.time.Instant.now()
        ));
    }

    private Map<String, Object> eventDetails(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = keyValues[index + 1];
            if (value != null) {
                details.put(key, value);
            }
        }
        return Map.copyOf(details);
    }

    private ProcessTaskListItemResponse toTaskListItem(Task task) {
        Map<String, Object> variables = runtimeVariables(task.getProcessInstanceId());
        String processKey = stringValue(variables.get("westflowProcessKey"));
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessDefinitionId")),
                processKey,
                task.getProcessInstanceId()
        );
        return new ProcessTaskListItemResponse(
                task.getId(),
                task.getProcessInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                stringValue(variables.get("westflowBusinessKey")),
                stringValue(variables.get("westflowBusinessType")),
                stringValue(variables.get("westflowInitiatorUserId")),
                task.getTaskDefinitionKey(),
                task.getName(),
                resolveTaskKind(task),
                resolveTaskStatus(task),
                "USER",
                candidateUsers(task.getId()),
                task.getAssignee(),
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getCreateTime()),
                null
        );
    }

    private DemoTaskView toTaskView(Task task) {
        return new DemoTaskView(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                resolveTaskKind(task),
                resolveTaskStatus(task),
                "USER",
                candidateUsers(task.getId()),
                task.getAssignee(),
                resolveActingMode(task, null),
                resolveActingForUserId(task, null),
                resolveDelegatedByUserId(task, null),
                null
        );
    }

    private PublishedProcessDefinition resolvePublishedDefinition(
            String preferredPlatformDefinitionId,
            String variablePlatformDefinitionId,
            String processKey,
            String processInstanceId
    ) {
        String platformDefinitionId = preferredPlatformDefinitionId != null && !preferredPlatformDefinitionId.isBlank()
                ? preferredPlatformDefinitionId
                : variablePlatformDefinitionId;
        if (platformDefinitionId != null && !platformDefinitionId.isBlank()) {
            return processDefinitionService.getById(platformDefinitionId);
        }
        if (processKey == null || processKey.isBlank()) {
            throw resourceNotFound("流程定义不存在", Map.of("processInstanceId", processInstanceId));
        }
        return processDefinitionService.getLatestByProcessKey(processKey);
    }

    private HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
        HistoricProcessInstance instance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw resourceNotFound("流程实例不存在", Map.of("instanceId", processInstanceId));
        }
        return instance;
    }

    private Task requireActiveTask(String taskId) {
        Task task = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw taskNotFound(taskId);
        }
        return task;
    }

    private List<String> candidateUsers(String taskId) {
        return flowableEngineFacade.taskService()
                .getIdentityLinksForTask(taskId)
                .stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(link -> link.getUserId())
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> taskLocalVariables(String taskId) {
        Map<String, Object> variables = flowableEngineFacade.taskService().getVariablesLocal(taskId);
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    private Map<String, Object> historicTaskLocalVariables(String taskId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .taskId(taskId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private String resolveTaskKind(Task task) {
        String runtimeTaskKind = stringValue(taskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (runtimeTaskKind != null) {
            return runtimeTaskKind;
        }
        return resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
    }

    private String resolveHistoricTaskKind(HistoricTaskInstance task) {
        String historicTaskKind = stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (historicTaskKind != null) {
            return historicTaskKind;
        }
        return resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
    }

    private String resolveTaskKind(String engineProcessDefinitionId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "NORMAL";
        }
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(engineProcessDefinitionId);
        if (model == null) {
            return "NORMAL";
        }
        BaseElement element = model.getFlowElement(nodeId);
        if (element == null) {
            return "NORMAL";
        }
        List<org.flowable.bpmn.model.ExtensionAttribute> attrs = element.getAttributes().get("taskKind");
        if (attrs == null || attrs.isEmpty() || attrs.get(0).getValue() == null || attrs.get(0).getValue().isBlank()) {
            return "NORMAL";
        }
        return attrs.get(0).getValue();
    }

    private String resolveTaskStatus(Task task) {
        String taskKind = resolveTaskKind(task);
        if ("CC".equals(taskKind)) {
            return readTimeValue(taskLocalVariables(task.getId())) == null ? "CC_PENDING" : "CC_READ";
        }
        if ("DELEGATED".equals(resolveActingMode(task, null))) {
            return "DELEGATED";
        }
        if ("ADD_SIGN".equals(taskKind)) {
            return "PENDING";
        }
        if (task.getOwner() != null && task.getAssignee() != null && !task.getOwner().equals(task.getAssignee())) {
            return "DELEGATED";
        }
        return task.getAssignee() == null && !candidateUsers(task.getId()).isEmpty() ? "PENDING_CLAIM" : "PENDING";
    }

    private String resolveTraceTaskStatus(Task task, Map<String, Object> localVariables) {
        String action = stringValue(localVariables.get("westflowAction"));
        if ("HANDOVER".equals(action)) {
            return "HANDOVERED";
        }
        return resolveTaskStatus(task);
    }

    private boolean canRevoke(Task task) {
        return hasInitiatorPermission(task);
    }

    private boolean canUrge(Task task) {
        return hasInitiatorPermission(task);
    }

    private boolean canTakeBack(Task task) {
        if (task == null || !"NORMAL".equals(resolveTaskKind(task)) || hasActiveAddSignChild(task.getId())) {
            return false;
        }
        HistoricTaskInstance previousTask;
        try {
            previousTask = requirePreviousUserTask(task);
        } catch (ContractException exception) {
            return false;
        }
        if (!currentUserId().equals(previousTask.getAssignee())) {
            return false;
        }
        Map<String, Object> localVariables = taskLocalVariables(task.getId());
        if (stringValue(localVariables.get("westflowAction")) != null || readTimeValue(localVariables) != null) {
            return false;
        }
        return flowableEngineFacade.taskService().getTaskComments(task.getId()).isEmpty();
    }

    private String resolveInstanceStatus(HistoricProcessInstance processInstance, List<Task> activeTasks) {
        if (processInstance.getDeleteReason() != null) {
            return "WESTFLOW_REVOKED".equals(processInstance.getDeleteReason()) ? "REVOKED" : "COMPLETED";
        }
        return activeTasks.stream().anyMatch(this::isBlockingTask) ? "RUNNING" : "COMPLETED";
    }

    private boolean hasActiveAddSignChild(String taskId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .active()
                .list()
                .stream()
                .anyMatch(task -> taskId.equals(task.getParentTaskId()) && "ADD_SIGN".equals(resolveTaskKind(task)));
    }

    private boolean isBlockingTask(Task task) {
        return !"CC".equals(resolveTaskKind(task));
    }

    private boolean isVisibleTask(Task task) {
        return true;
    }

    private List<DemoTaskView> blockingTaskViews(List<DemoTaskView> nextTasks) {
        return nextTasks.stream()
                .filter(task -> !"CC".equals(task.taskKind()))
                .toList();
    }

    private OffsetDateTime readTimeValue(Map<String, Object> localVariables) {
        if (localVariables == null || localVariables.isEmpty()) {
            return null;
        }
        Object value = localVariables.get("westflowReadTime");
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), TIME_ZONE);
        }
        if (value instanceof java.util.Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
        }
        return null;
    }

    private String resolveHistoricTaskStatus(HistoricTaskInstance task, HistoricProcessInstance processInstance) {
        String taskKind = task == null ? null : resolveHistoricTaskKind(task);
        if (task != null) {
            Map<String, Object> localVariables = historicTaskLocalVariables(task.getId());
            String action = stringValue(localVariables.get("westflowAction"));
            if ("TAKE_BACK".equals(action)) {
                return "TAKEN_BACK";
            }
            if ("HANDOVER".equals(action)) {
                return "HANDOVERED";
            }
            if ("CC".equals(taskKind)) {
                return readTimeValue(localVariables) == null ? "CC_PENDING" : "CC_READ";
            }
            if (isHistoricTaskAutoFinished(task)) {
                return "AUTO_FINISHED";
            }
            return isHistoricTaskRevoked(task) ? "REVOKED" : "COMPLETED";
        }
        if (processInstance != null && "WESTFLOW_REVOKED".equals(processInstance.getDeleteReason())) {
            return "REVOKED";
        }
        return "COMPLETED";
    }

    private boolean isHistoricTaskRevoked(HistoricTaskInstance task) {
        return task.getDeleteReason() != null && !task.getDeleteReason().isBlank();
    }

    private boolean isHistoricTaskAutoFinished(HistoricTaskInstance task) {
        if (task == null || task.getDeleteReason() == null || task.getDeleteReason().isBlank()) {
            return false;
        }
        if (!"MI_END".equals(task.getDeleteReason())) {
            return false;
        }
        String approvalMode = countersignApprovalMode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        return "OR_SIGN".equals(approvalMode) || "VOTE".equals(approvalMode);
    }

    private String countersignApprovalMode(String processDefinitionId, String nodeId) {
        if (processDefinitionId == null || nodeId == null) {
            return null;
        }
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || model.getFlowElement(nodeId) == null || model.getFlowElement(nodeId).getAttributes() == null) {
            return null;
        }
        List<org.flowable.bpmn.model.ExtensionAttribute> attrs = model.getFlowElement(nodeId).getAttributes().get("approvalMode");
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        String value = attrs.get(0).getValue();
        return value == null || value.isBlank() ? null : value;
    }

    private String resolveActingMode(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitMode = stringValue(localVariables.get("westflowActingMode"));
        if (explicitMode != null) {
            return explicitMode;
        }
        String ownerUserId = activeTask != null ? activeTask.getOwner() : historicTask == null ? null : historicTask.getOwner();
        String assigneeUserId = activeTask != null ? activeTask.getAssignee() : historicTask == null ? null : historicTask.getAssignee();
        if (ownerUserId != null && assigneeUserId != null && !ownerUserId.equals(assigneeUserId)) {
            return "DELEGATE";
        }
        return null;
    }

    private String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowActingForUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        String ownerUserId = activeTask != null ? activeTask.getOwner() : historicTask == null ? null : historicTask.getOwner();
        String assigneeUserId = activeTask != null ? activeTask.getAssignee() : historicTask == null ? null : historicTask.getAssignee();
        if (ownerUserId != null && assigneeUserId != null && !ownerUserId.equals(assigneeUserId)) {
            return ownerUserId;
        }
        return null;
    }

    private String resolveDelegatedByUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowDelegatedByUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        return resolveActingForUserId(activeTask, historicTask);
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    /**
     * 优先读取运行时变量；流程已经结束或被删除时回退到历史变量，避免动作收尾阶段再抛找不到实例。
     */
    private Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        try {
            Map<String, Object> runtimeValues = runtimeVariables(processInstanceId);
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
        } catch (FlowableObjectNotFoundException exception) {
            // 流程实例已结束或已删除时，继续走历史变量兜底。
        }
        return historicVariables(processInstanceId);
    }

    private String activeFlowableDefinitionId(String processInstanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionId();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return historicInstance == null ? null : historicInstance.getProcessDefinitionId();
    }

    private Map<String, Object> historicVariables(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private Optional<BusinessLinkSnapshot> findBusinessLink(String businessType, String businessId) {
        List<BusinessLinkSnapshot> links = jdbcTemplate.query(
                """
                SELECT business_type, business_id, process_instance_id, process_definition_id, start_user_id, status
                FROM wf_business_process_link
                WHERE business_type = ? AND business_id = ?
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> new BusinessLinkSnapshot(
                        rs.getString("business_type"),
                        rs.getString("business_id"),
                        rs.getString("process_instance_id"),
                        rs.getString("process_definition_id"),
                        rs.getString("start_user_id"),
                        rs.getString("status")
                ),
                businessType,
                businessId
        );
        return links.stream().findFirst();
    }

    private String resolveProcessInstanceIdByBusinessKey(String businessId) {
        org.flowable.engine.runtime.ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessInstanceId();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .singleResult();
        if (historicInstance != null) {
            return historicInstance.getId();
        }
        throw resourceNotFound("审批单不存在", Map.of("businessId", businessId));
    }

    private Optional<BusinessLinkSnapshot> findBusinessLinkByInstanceId(String processInstanceId) {
        List<BusinessLinkSnapshot> links = jdbcTemplate.query(
                """
                SELECT business_type, business_id, process_instance_id, process_definition_id, start_user_id, status
                FROM wf_business_process_link
                WHERE process_instance_id = ?
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> new BusinessLinkSnapshot(
                        rs.getString("business_type"),
                        rs.getString("business_id"),
                        rs.getString("process_instance_id"),
                        rs.getString("process_definition_id"),
                        rs.getString("start_user_id"),
                        rs.getString("status")
                ),
                processInstanceId
        );
        return links.stream().findFirst();
    }

    private List<BusinessLinkSnapshot> queryBusinessLinksByStartUser(String startUserId) {
        return jdbcTemplate.query(
                """
                SELECT business_type, business_id, process_instance_id, process_definition_id, start_user_id, status
                FROM wf_business_process_link
                WHERE start_user_id = ?
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> new BusinessLinkSnapshot(
                        rs.getString("business_type"),
                        rs.getString("business_id"),
                        rs.getString("process_instance_id"),
                        rs.getString("process_definition_id"),
                        rs.getString("start_user_id"),
                        rs.getString("status")
                ),
                startUserId
        );
    }

    private boolean matchesTaskKeyword(ProcessTaskListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.processName(), normalized)
                || contains(item.processKey(), normalized)
                || contains(item.nodeName(), normalized)
                || contains(item.businessKey(), normalized)
                || contains(item.businessType(), normalized);
    }

    private boolean matchesApprovalKeyword(ApprovalSheetListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.processName(), normalized)
                || contains(item.billNo(), normalized)
                || contains(item.businessTitle(), normalized)
                || contains(item.currentNodeName(), normalized)
                || contains(item.businessType(), normalized);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private String resolveBusinessTitle(String businessType, Map<String, Object> businessData) {
        if (businessData == null || businessData.isEmpty()) {
            return null;
        }
        return switch (businessType) {
            case "OA_LEAVE" -> stringValue(businessData.get("reason"));
            case "OA_EXPENSE" -> stringValue(businessData.get("reason"));
            case "OA_COMMON" -> stringValue(businessData.get("title"));
            default -> stringValue(businessData.get("billNo"));
        };
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    private Long durationSeconds(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? currentUserId() : userId.trim();
    }

    private Map<String, Object> nodeConfig(ProcessDslPayload payload, String nodeId) {
        if (payload == null || nodeId == null) {
            return Map.of();
        }
        return payload.nodes().stream()
                .filter(node -> nodeId.equals(node.id()))
                .findFirst()
                .map(node -> node.config() == null ? Map.<String, Object>of() : node.config())
                .orElse(Map.of());
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
        return List.copyOf(bindings);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private HandoverPreviewTaskItemResponse toHandoverPreviewTask(Task task, String sourceUserId) {
        Optional<BusinessLinkSnapshot> link = findBusinessLinkByInstanceId(task.getProcessInstanceId());
        String businessType = link.map(BusinessLinkSnapshot::businessType)
                .orElseGet(() -> stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowBusinessType")));
        String businessKey = link.map(BusinessLinkSnapshot::businessId)
                .orElseGet(() -> stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowBusinessKey")));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new HandoverPreviewTaskItemResponse(
                task.getId(),
                task.getProcessInstanceId(),
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(task.getProcessDefinitionId()),
                stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowProcessKey")),
                stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowProcessName")),
                businessKey,
                businessType,
                resolveBusinessTitle(businessType, businessData),
                stringValue(businessData.get("billNo")),
                task.getTaskDefinitionKey(),
                task.getName(),
                sourceUserId,
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getCreateTime()),
                true,
                null
        );
    }

    private HandoverExecutionTaskItemResponse toHandoverExecutionTask(
            Task sourceTask,
            Task updatedTask,
            String sourceUserId,
            String targetUserId,
            String comment
    ) {
        Optional<BusinessLinkSnapshot> link = findBusinessLinkByInstanceId(updatedTask.getProcessInstanceId());
        String businessType = link.map(BusinessLinkSnapshot::businessType)
                .orElseGet(() -> stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowBusinessType")));
        String businessKey = link.map(BusinessLinkSnapshot::businessId)
                .orElseGet(() -> stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowBusinessKey")));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new HandoverExecutionTaskItemResponse(
                sourceTask.getId(),
                updatedTask.getId(),
                updatedTask.getProcessInstanceId(),
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(updatedTask.getProcessDefinitionId()),
                stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowProcessKey")),
                stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowProcessName")),
                businessKey,
                businessType,
                resolveBusinessTitle(businessType, businessData),
                stringValue(businessData.get("billNo")),
                updatedTask.getTaskDefinitionKey(),
                updatedTask.getName(),
                targetUserId,
                "HANDOVERED",
                updatedTask.getAssignee(),
                sourceUserId,
                comment,
                OffsetDateTime.now(TIME_ZONE),
                true,
                null
        );
    }

    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }
        List<String> results = jdbcTemplate.query(
                "SELECT display_name FROM wf_user WHERE id = ?",
                (rs, rowNum) -> rs.getString("display_name"),
                userId
        );
        return results.isEmpty() || results.get(0) == null || results.get(0).isBlank() ? userId : results.get(0);
    }

    private void insertBusinessLink(
            String businessType,
            String businessId,
            String processInstanceId,
            String processDefinitionId,
            String startUserId,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO wf_business_process_link (
                    id,
                    business_type,
                    business_id,
                    process_instance_id,
                    process_definition_id,
                    start_user_id,
                    status,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                "bpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                businessType,
                businessId,
                processInstanceId,
                processDefinitionId,
                startUserId,
                status
        );
    }

    private void updateBusinessProcessLink(String businessType, String businessId, String processInstanceId, String status) {
        switch (businessType) {
            case "OA_LEAVE" -> jdbcTemplate.update(
                    "UPDATE oa_leave_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            case "OA_EXPENSE" -> jdbcTemplate.update(
                    "UPDATE oa_expense_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            case "OA_COMMON" -> jdbcTemplate.update(
                    "UPDATE oa_common_request_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            default -> {
                // 其他业务类型后续再按业务表扩展更新逻辑。
            }
        }
    }

    private ContractException taskNotFound(String taskId) {
        return new ContractException(
                "PROCESS.TASK_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "任务不存在",
                Map.of("taskId", taskId)
        );
    }

    private ContractException resourceNotFound(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                details
        );
    }

    private ContractException actionNotAllowed(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.ACTION_NOT_ALLOWED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                message,
                details
        );
    }

    private <T> PageResponse<T> page(List<T> records, int page, int pageSize) {
        long total = records.size();
        long pages = total == 0 ? 0 : (total + pageSize - 1L) / pageSize;
        long offset = Math.max(0L, (long) (page - 1) * pageSize);
        List<T> currentPage = total == 0
                ? List.of()
                : records.stream().skip(offset).limit(pageSize).toList();
        return new PageResponse<>(page, pageSize, total, pages, currentPage, List.of());
    }

    /**
     * 业务单与流程实例关联快照。
     */
    private record BusinessLinkSnapshot(
            String businessType,
            String businessId,
            String processInstanceId,
            String processDefinitionId,
            String startUserId,
            String status
    ) {
    }

    /**
     * 真实驳回动作解析后的目标节点。
     */
    private record RejectTarget(
            String nodeId,
            String nodeName,
            String targetUserId
    ) {
    }
}
