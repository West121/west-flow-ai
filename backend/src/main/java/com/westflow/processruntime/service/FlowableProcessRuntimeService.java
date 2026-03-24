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
import com.westflow.processruntime.api.AppendTaskRequest;
import com.westflow.processruntime.api.AppendTaskResponse;
import com.westflow.processruntime.api.ClaimTaskRequest;
import com.westflow.processruntime.api.ClaimTaskResponse;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.DelegateTaskRequest;
import com.westflow.processruntime.api.HandoverExecutionResponse;
import com.westflow.processruntime.api.HandoverExecutionTaskItemResponse;
import com.westflow.processruntime.api.HandoverPreviewResponse;
import com.westflow.processruntime.api.HandoverPreviewTaskItemResponse;
import com.westflow.processruntime.api.InclusiveGatewayHitResponse;
import com.westflow.processruntime.api.HandoverTaskRequest;
import com.westflow.processruntime.api.JumpTaskRequest;
import com.westflow.processruntime.api.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.ProcessNotificationSendRecordResponse;
import com.westflow.processruntime.api.ProcessTaskSnapshot;
import com.westflow.processruntime.api.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.RuntimeAppendLinkResponse;
import com.westflow.processruntime.api.RejectTaskRequest;
import com.westflow.processruntime.api.RevokeTaskRequest;
import com.westflow.processruntime.api.RemoveSignTaskRequest;
import com.westflow.processruntime.api.ReturnTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.TakeBackTaskRequest;
import com.westflow.processruntime.api.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.TerminateProcessInstanceRequest;
import com.westflow.processruntime.api.UrgeTaskRequest;
import com.westflow.processruntime.api.TransferTaskRequest;
import com.westflow.processruntime.api.WakeUpInstanceRequest;
import com.westflow.processruntime.api.WorkflowFieldBinding;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
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
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于真实 Flowable 的最小运行态服务。
 * 当前承接真实发起、待办、审批单详情和已迁移到 Flowable 的任务动作。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableProcessRuntimeService {

    private static final String INCLUSIVE_SELECTION_SUMMARY_PREFIX = "westflowInclusiveSelectionSummary_";

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SIMPLE_COMPARISON_PATTERN = Pattern.compile("^([A-Za-z0-9_\\.]+)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");

    private record NodeMetadata(String nodeName, String nodeType) {
    }

    private record DefinitionMetadata(String processName, Integer version) {
    }

    private record SubprocessStructureMetadata(
            String callScope,
            String joinMode,
            String childStartStrategy,
            String parentResumeStrategy
    ) {
    }

    private record InclusiveSelectionSummary(
            Integer eligibleTargetCount,
            List<String> selectedEdgeIds,
            List<String> selectedBranchLabels,
            List<Integer> selectedBranchPriorities,
            boolean defaultBranchSelected
    ) {
        private static InclusiveSelectionSummary empty() {
            return new InclusiveSelectionSummary(null, List.of(), List.of(), List.of(), false);
        }
    }

    private final FlowableEngineFacade flowableEngineFacade;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final FlowableCountersignService flowableCountersignService;
    private final ProcessDefinitionService processDefinitionService;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final ProcessLinkService processLinkService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
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
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        Task activeTask = activeTasks.stream()
                .filter(this::isBlockingTask)
                .filter(task -> !"APPEND".equals(resolveTaskKind(task)))
                .findFirst()
                .orElse(activeTasks.stream()
                        .filter(this::isBlockingTask)
                        .findFirst()
                        .orElse(null));
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
     * 查询实例维度的包容分支命中摘要。
     */
    public List<InclusiveGatewayHitResponse> inclusiveGatewayHits(String instanceId) {
        HistoricProcessInstance historicProcessInstance = requireHistoricProcessInstance(instanceId);
        Map<String, Object> processVariables = runtimeOrHistoricVariables(instanceId);
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                null,
                stringValue(processVariables.get("westflowProcessDefinitionId")),
                stringValue(processVariables.get("westflowProcessKey")),
                instanceId
        );
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instanceId)
                .active()
                .list();
        return buildInclusiveGatewayHits(instanceId, definition.dsl(), historicProcessInstance, activeTasks);
    }

    /**
     * 查询实例与其子流程之间的关联记录。
     */
    public List<ProcessInstanceLinkResponse> links(String instanceId) {
        requireHistoricProcessInstance(instanceId);
        return subprocessLinks(instanceId);
    }

    /**
     * 父流程确认子流程完成并恢复后续状态。
     */
    @Transactional
    public ProcessInstanceLinkResponse confirmParentResume(String instanceId, String linkId) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(instanceId);
        synchronizeProcessLinks(rootInstanceId);
        var link = processLinkService.getById(linkId);
        if (link == null) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "主子流程关联不存在",
                    Map.of("linkId", linkId)
            );
        }
        if (!Objects.equals(link.rootInstanceId(), rootInstanceId) && !Objects.equals(link.parentInstanceId(), instanceId)) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_FORBIDDEN",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前实例无权确认该子流程关联",
                    Map.of("instanceId", instanceId, "linkId", linkId)
            );
        }
        if (!"WAIT_PARENT_CONFIRM".equals(link.status())) {
            throw actionNotAllowed(
                    "当前子流程不处于等待父流程确认状态",
                    Map.of("linkId", linkId, "status", link.status())
            );
        }
        Instant confirmedAt = link.finishedAt() != null ? link.finishedAt() : Instant.now();
        processLinkService.updateStatus(link.childInstanceId(), "FINISHED", confirmedAt);
        appendInstanceEvent(
                link.parentInstanceId(),
                null,
                link.parentNodeId(),
                "SUBPROCESS_PARENT_CONFIRMED",
                "父流程已确认子流程完成",
                "INSTANCE",
                null,
                link.childInstanceId(),
                currentUserId(),
                eventDetails(
                        "linkId", link.id(),
                        "childInstanceId", link.childInstanceId(),
                        "parentNodeId", link.parentNodeId(),
                        "joinMode", link.joinMode(),
                        "parentResumeStrategy", link.parentResumeStrategy()
                ),
                null,
                link.parentNodeId(),
                null,
                null,
                null,
                null,
                null
        );
        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                link.parentInstanceId(),
                null,
                null,
                null,
                null,
                null,
                link.parentNodeId(),
                "CONFIRM_SUBPROCESS_RESUME",
                "父流程确认子流程完成",
                "INSTANCE",
                currentUserId(),
                null,
                null,
                null,
                null,
                Map.of(
                        "linkId", link.id(),
                        "childInstanceId", link.childInstanceId(),
                        "rootInstanceId", link.rootInstanceId()
                ),
                Instant.now()
        ));
        synchronizeProcessLinks(rootInstanceId);
        return toProcessInstanceLinkResponse(requireProcessLink(linkId));
    }

    /**
     * 查询实例维度的追加与动态构建附属结构。
     */
    public List<RuntimeAppendLinkResponse> appendLinks(String instanceId) {
        requireHistoricProcessInstance(instanceId);
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(instanceId);
        synchronizeAppendLinks(rootInstanceId);
        return runtimeAppendLinkService.listByRootInstanceId(rootInstanceId).stream()
                .map(this::toRuntimeAppendLinkResponse)
                .toList();
    }

    /**
     * 终止流程实例。当前先收口根流程级联终止，后续再扩子流程单独终止。
     */
    @Transactional
    public CompleteTaskResponse terminate(String instanceId, TerminateProcessInstanceRequest request) {
        String terminateScope = normalizeTerminateScope(request.terminateScope());
        if ("ROOT".equals(terminateScope)) {
            return terminateRootProcess(instanceId, request.reason());
        }
        if ("CHILD".equals(terminateScope) || "SUBPROCESS".equals(terminateScope)) {
            return terminateChildProcess(instanceId, request.childInstanceId(), request.reason());
        }
        throw actionNotAllowed("当前仅支持终止根流程或子流程", Map.of("terminateScope", terminateScope));
    }

    private CompleteTaskResponse terminateRootProcess(String instanceId, String reason) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (runtimeInstance == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_RUNNING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "流程实例未运行，不能终止",
                    Map.of("instanceId", instanceId)
            );
        }
        Instant terminatedAt = Instant.now();
        List<com.westflow.processruntime.model.ProcessLinkRecord> childLinks = processLinkService.listByRootInstanceId(instanceId);
        runtimeAppendLinkService.markTerminatedByRootInstanceId(instanceId, terminatedAt);
        flowableTaskActionService.revokeProcessInstance(instanceId, "WESTFLOW_TERMINATE:" + reason);
        childLinks.forEach(link -> processLinkService.updateStatus(link.childInstanceId(), "TERMINATED", terminatedAt));
        appendInstanceEvent(
                instanceId,
                null,
                null,
                "INSTANCE_TERMINATED",
                "流程已终止",
                "INSTANCE",
                null,
                null,
                null,
                eventDetails(
                        "terminateScope", "ROOT",
                        "reason", reason,
                        "terminatedChildCount", childLinks.size()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new CompleteTaskResponse(instanceId, null, "TERMINATED", List.of());
    }

    private CompleteTaskResponse terminateChildProcess(String instanceId, String childInstanceId, String reason) {
        String resolvedChildInstanceId = childInstanceId == null || childInstanceId.isBlank()
                ? instanceId
                : childInstanceId.trim();
        var link = processLinkService.getByChildInstanceId(resolvedChildInstanceId);
        RuntimeAppendLinkRecord appendLink = null;
        if (link == null) {
            appendLink = runtimeAppendLinkService.getByTargetInstanceId(resolvedChildInstanceId);
            if (appendLink == null) {
                throw actionNotAllowed(
                        "目标子流程实例不存在",
                        Map.of("instanceId", instanceId, "childInstanceId", resolvedChildInstanceId)
                );
            }
        }
        ProcessInstance runtimeChildInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(resolvedChildInstanceId)
                .singleResult();
        if (runtimeChildInstance == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_RUNNING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "子流程实例未运行，不能终止",
                    Map.of("childInstanceId", resolvedChildInstanceId)
            );
        }
        Instant terminatedAt = Instant.now();
        runtimeAppendLinkService.markTerminatedByParentInstanceId(resolvedChildInstanceId, terminatedAt);
        flowableTaskActionService.revokeProcessInstance(resolvedChildInstanceId, "WESTFLOW_TERMINATE:" + reason);
        if (link != null) {
            processLinkService.updateStatus(resolvedChildInstanceId, "TERMINATED", terminatedAt);
            appendInstanceEvent(
                    link.parentInstanceId(),
                    null,
                    link.parentNodeId(),
                    "SUBPROCESS_TERMINATED",
                    "子流程已终止",
                    "INSTANCE",
                    null,
                    resolvedChildInstanceId,
                    null,
                    eventDetails(
                            "terminateScope", "CHILD",
                            "reason", reason,
                            "childInstanceId", resolvedChildInstanceId,
                            "parentNodeId", link.parentNodeId()
                    ),
                    null,
                    link.parentNodeId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } else {
            runtimeAppendLinkService.updateStatusByTargetInstanceId(resolvedChildInstanceId, "TERMINATED", terminatedAt);
            appendInstanceEvent(
                    appendLink.parentInstanceId(),
                    null,
                    appendLink.sourceNodeId(),
                    "APPEND_TERMINATED",
                    "追加子流程已终止",
                    "INSTANCE",
                    appendLink.sourceTaskId(),
                    resolvedChildInstanceId,
                    null,
                    eventDetails(
                            "terminateScope", "CHILD",
                            "reason", reason,
                            "childInstanceId", resolvedChildInstanceId,
                            "sourceTaskId", appendLink.sourceTaskId(),
                            "sourceNodeId", appendLink.sourceNodeId(),
                            "appendLinkId", appendLink.id()
                    ),
                    null,
                    appendLink.sourceNodeId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        appendInstanceEvent(
                resolvedChildInstanceId,
                null,
                null,
                "INSTANCE_TERMINATED",
                "流程已终止",
                "INSTANCE",
                null,
                null,
                null,
                eventDetails(
                        "terminateScope", "CHILD",
                        "reason", reason,
                        "rootInstanceId", link != null ? link.rootInstanceId() : appendLink.rootInstanceId(),
                        "parentInstanceId", link != null ? link.parentInstanceId() : appendLink.parentInstanceId()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return nextTaskResponse(link != null ? link.parentInstanceId() : appendLink.parentInstanceId(), null);
    }

    /**
     * 查询任务可执行动作。
     */
    public TaskActionAvailabilityResponse actions(String taskId) {
        Task task = requireActiveTask(taskId);
        String taskKind = resolveTaskKind(task);
        boolean isNormalTask = "NORMAL".equals(taskKind);
        boolean isAppendTask = "APPEND".equals(taskKind);
        boolean isFlowHandleTask = isNormalTask || isAppendTask;
        List<String> candidateUserIds = candidateUsers(task.getId());
        boolean isCandidate = task.getAssignee() == null && candidateUserIds.contains(currentUserId());
        boolean isAssignee = currentUserId().equals(task.getAssignee());
        boolean canHandle = isAssignee || isCandidate;
        boolean blockedByAddSign = isNormalTask && hasActiveAddSignChild(task.getId());
        boolean blockedByAppendPolicy = isNormalTask && isBlockedByPendingAppendStructures(task);
        boolean canTakeBack = isNormalTask && canTakeBack(task);
        boolean canAddSign = isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy;
        boolean canRemoveSign = isNormalTask && canHandle && blockedByAddSign && !blockedByAppendPolicy;
        boolean canRead = "CC".equals(taskKind) && canHandle && "CC_PENDING".equals(resolveTaskStatus(task));
        return new TaskActionAvailabilityResponse(
                isFlowHandleTask && task.getAssignee() == null && isCandidate,
                isFlowHandleTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                isFlowHandleTask && canHandle,
                isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                canAddSign,
                canRemoveSign,
                canRevoke(task),
                canUrge(task),
                canRead,
                isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                isNormalTask && canHandle,
                canTakeBack,
                false,
                isNormalTask && canHandle && !blockedByAppendPolicy,
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
     * 为当前任务追加一段人工处理链路。
     */
    @Transactional
    public AppendTaskResponse appendTask(String taskId, AppendTaskRequest request) {
        Task sourceTask = requireTaskForAppend(taskId);
        if (!"NORMAL".equals(resolveTaskKind(sourceTask))) {
            throw actionNotAllowed("当前任务不支持追加", Map.of("taskId", taskId));
        }
        List<String> targetUserIds = normalizeTargetUserIds(request.targetUserIds(), taskId);
        String appendPolicy = normalizeAppendPolicy(request.appendPolicy());
        String sourceNodeId = sourceTask.getTaskDefinitionKey();
        String sourceNodeName = sourceTask.getName();
        String targetUserId = targetUserIds.get(0);
        String comment = request.comment();
        appendComment(sourceTask, comment);
        String appendLinkId = UUID.randomUUID().toString();
        Map<String, Object> localVariables = new LinkedHashMap<>();
        if (request.appendVariables() != null && !request.appendVariables().isEmpty()) {
            localVariables.putAll(request.appendVariables());
        }
        localVariables.putAll(eventDetails(
                "westflowTaskKind", "APPEND",
                "westflowAppendType", "TASK",
                "westflowSourceTaskId", sourceTask.getId(),
                "westflowSourceNodeId", sourceNodeId,
                "westflowAppendPolicy", appendPolicy,
                "westflowTriggerMode", "APPEND",
                "westflowTargetUserId", targetUserId,
                "westflowOperatorUserId", currentUserId()
        ));
        Task appendedTask = flowableTaskActionService.createAdhocTask(
                sourceTask.getProcessInstanceId(),
                sourceTask.getProcessDefinitionId(),
                sourceNodeId,
                sourceNodeName + "（追加）",
                "APPEND",
                targetUserId,
                targetUserIds,
                sourceTask.getId(),
                localVariables
        );
        RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                appendLinkId,
                resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()),
                sourceTask.getProcessInstanceId(),
                sourceTask.getId(),
                sourceNodeId,
                "TASK",
                "ADHOC_TASK",
                appendPolicy,
                appendedTask.getId(),
                null,
                targetUserId,
                null,
                null,
                "RUNNING",
                "APPEND",
                currentUserId(),
                comment,
                Instant.now(),
                null
        );
        runtimeAppendLinkService.createLink(appendLink);
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendLinkId", appendLink.id());
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendType", "TASK");
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendPolicy", appendPolicy);
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowTaskKind", "APPEND");
        appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                appendedTask.getId(),
                appendedTask.getTaskDefinitionKey(),
                "TASK_APPENDED",
                "任务已追加",
                "TASK",
                sourceTask.getId(),
                appendedTask.getId(),
                targetUserId,
                eventDetails(
                        "comment", comment,
                        "appendType", "TASK",
                        "appendPolicy", appendPolicy,
                        "sourceTaskId", sourceTask.getId(),
                        "sourceNodeId", sourceNodeId,
                        "targetTaskId", appendedTask.getId(),
                        "targetUserId", targetUserId,
                        "appendLinkId", appendLink.id()
                ),
                null,
                sourceNodeId,
                null,
                null,
                null,
                null,
                null
        );
        return new AppendTaskResponse(
                sourceTask.getProcessInstanceId(),
                sourceTask.getId(),
                "TASK",
                "RUNNING",
                appendedTask.getId(),
                null,
                activeAppendTasks(sourceTask.getProcessInstanceId()),
                appendLinks(sourceTask.getProcessInstanceId())
        );
    }

    /**
     * 为当前任务追加一个附属子流程。
     */
    @Transactional
    public AppendTaskResponse appendSubprocess(String taskId, AppendTaskRequest request) {
        Task sourceTask = requireTaskForAppend(taskId);
        if (!"NORMAL".equals(resolveTaskKind(sourceTask))) {
            throw actionNotAllowed("当前任务不支持追加", Map.of("taskId", taskId));
        }
        String calledProcessKey = normalizeCalledProcessKey(request.calledProcessKey(), taskId);
        String appendPolicy = normalizeAppendPolicy(request.appendPolicy());
        String versionPolicy = normalizeVersionPolicy(request.calledVersionPolicy());
        PublishedProcessDefinition definition = "FIXED_VERSION".equals(versionPolicy)
                ? processDefinitionService.getPublishedByProcessKeyAndVersion(calledProcessKey, request.calledVersion())
                : processDefinitionService.getLatestByProcessKey(calledProcessKey);
        String sourceNodeId = sourceTask.getTaskDefinitionKey();
        String comment = request.comment();
        appendComment(sourceTask, comment);
        Map<String, Object> parentVariables = runtimeVariables(sourceTask.getProcessInstanceId());

        Map<String, Object> variables = new LinkedHashMap<>();
        String businessKey = stringValue(parentVariables.get("westflowBusinessKey"));
        variables.put("westflowProcessDefinitionId", definition.processDefinitionId());
        variables.put("westflowProcessKey", definition.processKey());
        variables.put("westflowProcessName", definition.processName());
        variables.put("westflowBusinessType", stringValue(parentVariables.get("westflowBusinessType")));
        variables.put("westflowBusinessKey", businessKey);
        variables.put("westflowInitiatorUserId", stringValue(parentVariables.get("westflowInitiatorUserId")));
        variables.put("westflowParentInstanceId", sourceTask.getProcessInstanceId());
        variables.put("westflowRootInstanceId", resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()));
        variables.put("westflowAppendType", "SUBPROCESS");
        variables.put("westflowAppendPolicy", appendPolicy);
        variables.put("westflowAppendTriggerMode", "APPEND");
        variables.put("westflowAppendSourceTaskId", sourceTask.getId());
        variables.put("westflowAppendSourceNodeId", sourceNodeId);
        variables.put("westflowAppendOperatorUserId", currentUserId());
        if (request.appendVariables() != null && !request.appendVariables().isEmpty()) {
            variables.putAll(request.appendVariables());
        }
        String childRuntimeBusinessKey = buildAppendSubprocessRuntimeBusinessKey(businessKey, sourceTask.getId());
        ProcessInstance childInstance = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey(
                        definition.processKey(),
                        childRuntimeBusinessKey,
                        variables
                );
        RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                UUID.randomUUID().toString(),
                resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()),
                sourceTask.getProcessInstanceId(),
                sourceTask.getId(),
                sourceNodeId,
                "SUBPROCESS",
                "ADHOC_SUBPROCESS",
                appendPolicy,
                null,
                childInstance.getProcessInstanceId(),
                null,
                definition.processKey(),
                definition.processDefinitionId(),
                "RUNNING",
                "APPEND",
                currentUserId(),
                comment,
                Instant.now(),
                null
        );
        runtimeAppendLinkService.createLink(appendLink);
        appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                null,
                sourceNodeId,
                "SUBPROCESS_APPENDED",
                "子流程已追加",
                "INSTANCE",
                sourceTask.getId(),
                null,
                null,
                eventDetails(
                        "comment", comment,
                        "appendType", "SUBPROCESS",
                        "appendPolicy", appendPolicy,
                        "sourceTaskId", sourceTask.getId(),
                        "sourceNodeId", sourceNodeId,
                        "childInstanceId", childInstance.getProcessInstanceId(),
                        "calledProcessKey", definition.processKey(),
                        "appendLinkId", appendLink.id()
                ),
                null,
                sourceNodeId,
                null,
                null,
                null,
                null,
                null
        );
        return new AppendTaskResponse(
                sourceTask.getProcessInstanceId(),
                sourceTask.getId(),
                "SUBPROCESS",
                "RUNNING",
                null,
                childInstance.getProcessInstanceId(),
                activeAppendTasks(sourceTask.getProcessInstanceId()),
                appendLinks(sourceTask.getProcessInstanceId())
        );
    }

    /**
     * 在 Flowable 动态构建节点触发时生成附属任务或附属子流程。
     */
    @Transactional
    public void executeDynamicBuilder(String processInstanceId, String sourceNodeId) {
        Map<String, Object> processVariables = runtimeOrHistoricVariables(processInstanceId);
        // 先做一次显式流程实例/执行查询，确保当前命令中的运行时实体已刷新到数据库，
        // 避免附属任务在同一事务里插入时碰到 proc_inst_id 外键约束。
        flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        flowableEngineFacade.runtimeService()
                .createExecutionQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(processVariables.get("westflowProcessDefinitionId")),
                stringValue(processVariables.get("westflowProcessDefinitionId")),
                stringValue(processVariables.get("westflowProcessKey")),
                processInstanceId
        );
        ProcessDslPayload payload = definition.dsl();
        Map<String, Object> config = nodeConfig(payload, sourceNodeId);
        if (config.isEmpty()) {
            return;
        }
        if (runtimeAppendLinkService.listByParentInstanceId(processInstanceId).stream()
                .anyMatch(record -> "DYNAMIC_BUILD".equals(record.triggerMode()) && sourceNodeId.equals(record.sourceNodeId()))) {
            return;
        }
        String buildMode = stringValue(config.get("buildMode"));
        String appendPolicy = normalizeAppendPolicy(stringValue(config.get("appendPolicy")));
        Integer maxGeneratedCount = Optional.ofNullable((Integer) config.get("maxGeneratedCount")).orElse(1);
        String operatorUserId = resolveRuntimeOperatorUserId(processVariables);
        String nodeName = payload.nodes().stream()
                .filter(node -> sourceNodeId.equals(node.id()))
                .findFirst()
                .map(ProcessDslPayload.Node::name)
                .orElse(sourceNodeId);
        List<Map<String, Object>> generatedItems = resolveDynamicBuilderItems(buildMode, config, processVariables, maxGeneratedCount);
        if (generatedItems.isEmpty()) {
            appendInstanceEvent(
                    processInstanceId,
                    null,
                    sourceNodeId,
                    "DYNAMIC_BUILD_TRIGGERED",
                    "动态构建已触发",
                    "INSTANCE",
                    null,
                    null,
                    null,
                    eventDetails(
                            "sourceNodeId", sourceNodeId,
                            "buildMode", buildMode,
                            "appendPolicy", appendPolicy,
                            "generatedCount", 0
                    ),
                    null,
                    sourceNodeId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    operatorUserId
            );
            return;
        }
        appendInstanceEvent(
                processInstanceId,
                null,
                sourceNodeId,
                "DYNAMIC_BUILD_TRIGGERED",
                "动态构建已触发",
                "INSTANCE",
                null,
                null,
                null,
                eventDetails(
                        "sourceNodeId", sourceNodeId,
                        "buildMode", buildMode,
                        "appendPolicy", appendPolicy,
                        "generatedCount", generatedItems.size()
                ),
                null,
                sourceNodeId,
                null,
                null,
                null,
                null,
                null,
                operatorUserId
        );
        if ("SUBPROCESS_CALLS".equals(buildMode)) {
            createDynamicBuildSubprocesses(processInstanceId, sourceNodeId, nodeName, definition, appendPolicy, processVariables, generatedItems, operatorUserId);
            return;
        }
        createDynamicBuildTasks(processInstanceId, sourceNodeId, nodeName, appendPolicy, generatedItems, operatorUserId);
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
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetTaskId(taskId);
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
        if (appendLink != null) {
            runtimeAppendLinkService.updateStatusByTargetTaskId(taskId, "COMPLETED", Instant.now());
            appendInstanceEvent(
                    task.getProcessInstanceId(),
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    "TASK_APPEND_COMPLETED",
                    "追加任务已完成",
                    "TASK",
                    task.getId(),
                    task.getId(),
                    currentUserId(),
                    eventDetails(
                            "appendType", appendLink.appendType(),
                            "appendLinkId", appendLink.id(),
                            "sourceTaskId", appendLink.sourceTaskId(),
                            "sourceNodeId", appendLink.sourceNodeId(),
                            "targetUserId", appendLink.targetUserId()
                    ),
                    null,
                    task.getTaskDefinitionKey(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        com.westflow.processruntime.model.ProcessLinkRecord subprocessLink =
                processLinkService.getByChildInstanceId(task.getProcessInstanceId());
        if (subprocessLink != null) {
            synchronizeProcessLinks(subprocessLink.rootInstanceId());
        }
        flowableCountersignService.syncAfterTaskCompleted(
                task.getProcessDefinitionId(),
                task.getProcessInstanceId(),
                taskId
        );
        List<ProcessTaskSnapshot> nextTasks = flowableEngineFacade.taskService()
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
        List<ProcessTaskSnapshot> nextTasks = new ArrayList<>();
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
                : blockingActiveTasks.stream()
                        .filter(task -> !"APPEND".equals(resolveTaskKind(task)))
                        .findFirst()
                        .orElse(blockingActiveTasks.stream().findFirst().orElse(activeTasks.stream().findFirst().orElse(null)));
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
        List<InclusiveGatewayHitResponse> inclusiveGatewayHits = buildInclusiveGatewayHits(
                processInstanceId,
                payload,
                historicProcessInstance,
                activeTasks
        );
        instanceEvents = mergeInstanceEvents(
                instanceEvents,
                buildInclusiveGatewayEvents(processInstanceId, inclusiveGatewayHits)
        );
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
        List<ProcessInstanceLinkResponse> processLinks = subprocessLinks(processInstanceId);
        List<RuntimeAppendLinkResponse> runtimeAppendLinks = appendLinks(processInstanceId);
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
                inclusiveGatewayHits,
                processLinks,
                runtimeAppendLinks,
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

    private List<InclusiveGatewayHitResponse> buildInclusiveGatewayHits(
            String processInstanceId,
            ProcessDslPayload payload,
            HistoricProcessInstance historicProcessInstance,
            List<Task> activeTasks
    ) {
        if (payload == null || payload.nodes() == null || payload.edges() == null) {
            return List.of();
        }

        Map<String, ProcessDslPayload.Node> nodeById = payload.nodes().stream()
                .collect(LinkedHashMap::new, (map, node) -> map.put(node.id(), node), Map::putAll);
        List<ProcessDslPayload.Node> inclusiveSplits = payload.nodes().stream()
                .filter(node -> "inclusive_split".equals(node.type()))
                .toList();
        if (inclusiveSplits.isEmpty()) {
            return List.of();
        }

        Map<String, List<ProcessDslPayload.Edge>> outgoingEdges = new LinkedHashMap<>();
        for (ProcessDslPayload.Edge edge : payload.edges()) {
            outgoingEdges.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
        }
        Map<String, Object> processVariables = runtimeOrHistoricVariables(processInstanceId);

        Map<String, OffsetDateTime> firstOccurredAtByNodeId = new HashMap<>();
        Set<String> reachedNodeIds = new LinkedHashSet<>();
        List<HistoricActivityInstance> historicActivities = flowableEngineFacade.historyService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();
        for (HistoricActivityInstance activity : historicActivities) {
            if (activity.getActivityId() == null || activity.getActivityId().isBlank()) {
                continue;
            }
            reachedNodeIds.add(activity.getActivityId());
            firstOccurredAtByNodeId.putIfAbsent(
                    activity.getActivityId(),
                    toOffsetDateTime(activity.getStartTime())
            );
        }
        for (Task activeTask : activeTasks) {
            if (activeTask.getTaskDefinitionKey() == null || activeTask.getTaskDefinitionKey().isBlank()) {
                continue;
            }
            reachedNodeIds.add(activeTask.getTaskDefinitionKey());
            firstOccurredAtByNodeId.putIfAbsent(
                    activeTask.getTaskDefinitionKey(),
                    toOffsetDateTime(activeTask.getCreateTime())
            );
        }

        List<InclusiveGatewayHitResponse> hits = new ArrayList<>();
        for (ProcessDslPayload.Node split : inclusiveSplits) {
            ProcessDslPayload.Node join = resolveNearestInclusiveJoin(split.id(), nodeById, outgoingEdges);
            List<ProcessDslPayload.Edge> branchEdges = outgoingEdges.getOrDefault(split.id(), List.of()).stream()
                    .sorted(Comparator
                            .comparing(ProcessDslPayload.Edge::priority, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ProcessDslPayload.Edge::id))
                    .toList();
            if (branchEdges.isEmpty()) {
                continue;
            }

            Map<String, Object> splitConfig = mapValue(split.config());
            String defaultBranchId = stringValue(splitConfig.get("defaultBranchId"));
            Integer requiredBranchCount = integerValue(splitConfig.get("requiredBranchCount"));
            String branchMergePolicy = stringValueOrDefault(splitConfig.get("branchMergePolicy"), "ALL_SELECTED");
            InclusiveSelectionSummary selectionSummary = inclusiveSelectionSummary(processVariables, split.id(), defaultBranchId);
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
            boolean defaultBranchSelected = false;
            OffsetDateTime firstActivatedAt = null;
            int branchIndex = 1;

            for (ProcessDslPayload.Edge edge : branchEdges) {
                String targetNodeId = edge.target();
                if (targetNodeId == null || targetNodeId.isBlank()) {
                    branchIndex++;
                    continue;
                }
                branchPriorities.add(edge.priority() == null ? branchIndex : edge.priority());
                branchLabels.add(edge.label() == null || edge.label().isBlank()
                        ? targetNodeId
                        : edge.label());
                String expression = edgeConditionExpression(edge);
                if (expression != null) {
                    branchExpressions.add(expression);
                }
                ProcessDslPayload.Node targetNode = nodeById.get(targetNodeId);
                boolean activated = selectionSummary.selectedEdgeIds().isEmpty()
                        ? hasReachedInclusiveBranchTarget(
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
                    if (defaultBranchId != null && defaultBranchId.equals(edge.id())) {
                        defaultBranchSelected = true;
                    }
                    activatedTargetNodeIds.add(targetNodeId);
                    activatedTargetNodeNames.add(targetNode == null ? targetNodeId : targetNode.name());
                    OffsetDateTime occurredAt = resolveInclusiveBranchOccurredAt(
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

            OffsetDateTime finishedAt = join == null ? null : firstOccurredAtByNodeId.get(join.id());
            String gatewayStatus = resolveInclusiveGatewayStatus(
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
                    selectionSummary.selectedEdgeIds().isEmpty()
                            ? List.copyOf(selectedEdgeIds)
                            : selectionSummary.selectedEdgeIds(),
                    selectionSummary.selectedBranchLabels().isEmpty()
                            ? List.copyOf(selectedBranchLabels)
                            : selectionSummary.selectedBranchLabels(),
                    selectionSummary.selectedBranchPriorities().isEmpty()
                            ? List.copyOf(selectedBranchPriorities)
                            : selectionSummary.selectedBranchPriorities(),
                    selectionSummary.selectedEdgeIds().isEmpty()
                            ? defaultBranchSelected
                            : selectionSummary.defaultBranchSelected(),
                    buildInclusiveDecisionSummary(
                            branchEdges.size(),
                            selectionSummary.eligibleTargetCount() == null
                                    ? activatedTargetNodeIds.size()
                                    : selectionSummary.eligibleTargetCount(),
                            activatedTargetNodeIds.size(),
                            branchMergePolicy,
                            selectionSummary.selectedEdgeIds().isEmpty()
                                    ? defaultBranchSelected
                                    : selectionSummary.defaultBranchSelected(),
                            join != null
                    ),
                    firstActivatedAt,
                    finishedAt
            ));
        }
        return hits;
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
                stringListValue(summary.get("selectedLabels")),
                integerListValue(summary.get("selectedPriorities")),
                resolvedDefaultBranchId != null && selectedEdgeIds.contains(resolvedDefaultBranchId)
        );
    }

    private List<ProcessInstanceEventResponse> buildInclusiveGatewayEvents(
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
                        inclusiveGatewayEventDetails(hit, false),
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
                        inclusiveGatewayEventDetails(hit, true),
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

    private Map<String, Object> inclusiveGatewayEventDetails(
            InclusiveGatewayHitResponse hit,
            boolean joined
    ) {
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
        details.put("defaultBranchSelected", hit.defaultBranchSelected());
        details.put("activatedTargetNodeIds", hit.activatedTargetNodeIds());
        details.put("activatedTargetNodeNames", hit.activatedTargetNodeNames());
        return details;
    }

    private List<ProcessInstanceEventResponse> mergeInstanceEvents(
            List<ProcessInstanceEventResponse> instanceEvents,
            List<ProcessInstanceEventResponse> derivedEvents
    ) {
        if (derivedEvents.isEmpty()) {
            return instanceEvents;
        }
        Map<String, ProcessInstanceEventResponse> deduped = new LinkedHashMap<>();
        for (ProcessInstanceEventResponse event : instanceEvents) {
            deduped.put(event.eventId(), event);
        }
        for (ProcessInstanceEventResponse event : derivedEvents) {
            deduped.put(event.eventId(), event);
        }
        return deduped.values().stream()
                .sorted(Comparator.comparing(ProcessInstanceEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProcessInstanceEventResponse::eventId))
                .toList();
    }

    private ProcessDslPayload.Node resolveNearestInclusiveJoin(
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

    private boolean hasReachedInclusiveBranchTarget(
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

    private OffsetDateTime resolveInclusiveBranchOccurredAt(
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

    private String resolveInclusiveGatewayStatus(
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

    private boolean isNodeCurrentlyActive(String processInstanceId, String nodeId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(nodeId)
                .active()
                .count() > 0;
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

    private Task requireTaskForAppend(String taskId) {
        Task task = requireActiveTask(taskId);
        List<String> candidateUserIds = candidateUsers(taskId);
        String initiatorUserId = stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId"));
        boolean canAppend = currentUserId().equals(task.getAssignee())
                || (task.getAssignee() == null && candidateUserIds.contains(currentUserId()))
                || currentUserId().equals(initiatorUserId);
        if (!canAppend) {
            throw actionNotAllowed(
                    "当前任务不允许执行追加",
                    eventDetails(
                            "taskId", taskId,
                            "userId", currentUserId(),
                            "assigneeUserId", task.getAssignee(),
                            "initiatorUserId", initiatorUserId
                    )
            );
        }
        if (currentUserId().equals(initiatorUserId)) {
            return task;
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

    private List<String> normalizeTargetUserIds(List<String> targetUserIds, String taskId) {
        List<String> normalized = targetUserIds == null ? List.of() : targetUserIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "targetUserIds 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return normalized;
    }

    private String normalizeAppendPolicy(String appendPolicy) {
        if (appendPolicy == null || appendPolicy.isBlank()) {
            return "SERIAL_AFTER_CURRENT";
        }
        String normalized = appendPolicy.trim().toUpperCase();
        return List.of("SERIAL_AFTER_CURRENT", "PARALLEL_WITH_CURRENT", "SERIAL_BEFORE_NEXT").contains(normalized)
                ? normalized
                : "SERIAL_AFTER_CURRENT";
    }

    private String normalizeVersionPolicy(String versionPolicy) {
        if (versionPolicy == null || versionPolicy.isBlank()) {
            return "LATEST_PUBLISHED";
        }
        String normalized = versionPolicy.trim().toUpperCase();
        return List.of("LATEST_PUBLISHED", "FIXED_VERSION").contains(normalized)
                ? normalized
                : "LATEST_PUBLISHED";
    }

    private String normalizeCalledProcessKey(String calledProcessKey, String taskId) {
        if (calledProcessKey == null || calledProcessKey.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "calledProcessKey 不能为空",
                    Map.of("taskId", taskId)
            );
        }
        return calledProcessKey.trim();
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

    private String normalizeTerminateScope(String terminateScope) {
        return terminateScope == null || terminateScope.isBlank()
                ? "ROOT"
                : terminateScope.trim().toUpperCase();
    }

    private List<ProcessInstanceLinkResponse> subprocessLinks(String instanceId) {
        String rootInstanceId = processLinkService.resolveRootInstanceId(instanceId);
        synchronizeProcessLinks(rootInstanceId);
        List<com.westflow.processruntime.model.ProcessLinkRecord> rootLinks =
                processLinkService.listByRootInstanceId(rootInstanceId);
        if (rootLinks.isEmpty()) {
            return List.of();
        }
        Map<String, List<com.westflow.processruntime.model.ProcessLinkRecord>> linksByParentInstanceId = rootLinks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.westflow.processruntime.model.ProcessLinkRecord::parentInstanceId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<com.westflow.processruntime.model.ProcessLinkRecord> visibleLinks = new ArrayList<>();
        collectVisibleSubprocessLinks(rootInstanceId, linksByParentInstanceId, visibleLinks);
        return visibleLinks.stream()
                .map(this::toProcessInstanceLinkResponse)
                .toList();
    }

    private com.westflow.processruntime.model.ProcessLinkRecord requireProcessLink(String linkId) {
        com.westflow.processruntime.model.ProcessLinkRecord link = processLinkService.getById(linkId);
        if (link == null) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "主子流程关联不存在",
                    Map.of("linkId", linkId)
            );
        }
        return link;
    }

    private void collectVisibleSubprocessLinks(
            String parentInstanceId,
            Map<String, List<com.westflow.processruntime.model.ProcessLinkRecord>> linksByParentInstanceId,
            List<com.westflow.processruntime.model.ProcessLinkRecord> visibleLinks
    ) {
        for (com.westflow.processruntime.model.ProcessLinkRecord link : linksByParentInstanceId.getOrDefault(parentInstanceId, List.of())) {
            visibleLinks.add(link);
            if ("CHILD_AND_DESCENDANTS".equals(resolveSubprocessStructureMetadata(link).callScope())) {
                collectVisibleSubprocessLinks(link.childInstanceId(), linksByParentInstanceId, visibleLinks);
            }
        }
    }

    private void synchronizeProcessLinks(String rootInstanceId) {
        List<com.westflow.processruntime.model.ProcessLinkRecord> rootLinks = processLinkService.listByRootInstanceId(rootInstanceId);
        rootLinks.stream()
                .filter(record -> "RUNNING".equals(record.status()))
                .forEach(record -> {
                    ProcessInstance runtimeChild = flowableEngineFacade.runtimeService()
                            .createProcessInstanceQuery()
                            .processInstanceId(record.childInstanceId())
                            .singleResult();
                    if (runtimeChild != null) {
                        return;
                    }
                    HistoricProcessInstance historicChild = flowableEngineFacade.historyService()
                            .createHistoricProcessInstanceQuery()
                            .processInstanceId(record.childInstanceId())
                            .singleResult();
                    if (historicChild == null || historicChild.getEndTime() == null) {
                        return;
                    }
                    SubprocessStructureMetadata structureMetadata = resolveSubprocessStructureMetadata(record);
                    boolean terminateParentAfterFinish = "TERMINATE_PARENT".equals(record.childFinishPolicy())
                            && !isTerminatedProcess(historicChild);
                    String resolvedStatus = isTerminatedProcess(historicChild)
                            ? "TERMINATED"
                            : terminateParentAfterFinish
                            ? "FINISHED"
                            : requiresParentConfirmation(structureMetadata)
                            ? "WAIT_PARENT_CONFIRM"
                            : "FINISHED";
                    processLinkService.updateStatus(record.childInstanceId(), resolvedStatus, historicChild.getEndTime().toInstant());
                    appendInstanceEvent(
                            record.parentInstanceId(),
                            null,
                            record.parentNodeId(),
                            switch (resolvedStatus) {
                                case "TERMINATED" -> "SUBPROCESS_TERMINATED";
                                case "WAIT_PARENT_CONFIRM" -> "SUBPROCESS_WAIT_PARENT_CONFIRM";
                                default -> "SUBPROCESS_FINISHED";
                            },
                            switch (resolvedStatus) {
                                case "TERMINATED" -> "子流程已终止";
                                case "WAIT_PARENT_CONFIRM" -> "子流程已完成，等待父流程确认";
                                default -> "子流程已完成";
                            },
                            "INSTANCE",
                            null,
                            record.childInstanceId(),
                            null,
                            eventDetails(
                                    "childInstanceId", record.childInstanceId(),
                                    "parentNodeId", record.parentNodeId(),
                                    "calledProcessKey", record.calledProcessKey(),
                                    "resolvedStatus", resolvedStatus
                            ),
                            null,
                            record.parentNodeId(),
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    if (terminateParentAfterFinish) {
                        terminateParentProcessOnChildFinish(record, historicChild.getEndTime().toInstant(), rootLinks);
                    }
                });
    }

    private void terminateParentProcessOnChildFinish(
            com.westflow.processruntime.model.ProcessLinkRecord record,
            Instant finishedAt,
            List<com.westflow.processruntime.model.ProcessLinkRecord> rootLinks
    ) {
        ProcessInstance runtimeParent = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(record.parentInstanceId())
                .singleResult();
        if (runtimeParent == null) {
            return;
        }
        flowableTaskActionService.revokeProcessInstance(
                record.parentInstanceId(),
                "WESTFLOW_SUBPROCESS_FINISH_POLICY:" + record.parentNodeId()
        );
        Map<String, List<com.westflow.processruntime.model.ProcessLinkRecord>> linksByParentInstanceId = rootLinks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.westflow.processruntime.model.ProcessLinkRecord::parentInstanceId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<com.westflow.processruntime.model.ProcessLinkRecord> subtreeLinks = new ArrayList<>();
        collectVisibleSubprocessLinks(record.parentInstanceId(), linksByParentInstanceId, subtreeLinks);
        for (com.westflow.processruntime.model.ProcessLinkRecord subtreeLink : subtreeLinks) {
            if (record.childInstanceId().equals(subtreeLink.childInstanceId())) {
                continue;
            }
            if (!"TERMINATED".equals(subtreeLink.status())) {
                processLinkService.updateStatus(subtreeLink.childInstanceId(), "TERMINATED", finishedAt);
            }
        }
        LinkedHashSet<String> subtreeInstanceIds = new LinkedHashSet<>();
        subtreeInstanceIds.add(record.parentInstanceId());
        subtreeLinks.stream()
                .map(com.westflow.processruntime.model.ProcessLinkRecord::childInstanceId)
                .filter(childInstanceId -> !record.childInstanceId().equals(childInstanceId))
                .forEach(subtreeInstanceIds::add);
        subtreeInstanceIds.forEach(instanceId -> runtimeAppendLinkService.markTerminatedByParentInstanceId(instanceId, finishedAt));
        appendInstanceEvent(
                record.parentInstanceId(),
                null,
                record.parentNodeId(),
                "SUBPROCESS_FINISH_TERMINATE_PARENT",
                "子流程完成后终止父流程",
                "INSTANCE",
                null,
                record.childInstanceId(),
                null,
                eventDetails(
                        "childInstanceId", record.childInstanceId(),
                        "parentNodeId", record.parentNodeId(),
                        "childFinishPolicy", record.childFinishPolicy()
                ),
                null,
                record.parentNodeId(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private String resolveRuntimeTreeRootInstanceId(String instanceId) {
        String processLinkRootInstanceId = processLinkService.resolveRootInstanceId(instanceId);
        if (!instanceId.equals(processLinkRootInstanceId)) {
            return processLinkRootInstanceId;
        }
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetInstanceId(instanceId);
        if (appendLink != null && appendLink.rootInstanceId() != null && !appendLink.rootInstanceId().isBlank()) {
            return appendLink.rootInstanceId();
        }
        return instanceId;
    }

    private String buildAppendSubprocessRuntimeBusinessKey(String parentBusinessKey, String sourceTaskId) {
        String normalizedParentBusinessKey = parentBusinessKey == null || parentBusinessKey.isBlank()
                ? "instance"
                : parentBusinessKey;
        return normalizedParentBusinessKey + "::append-subprocess::" + sourceTaskId;
    }

    private void createDynamicBuildTasks(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            String appendPolicy,
            List<Map<String, Object>> generatedItems,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String processDefinitionId = activeFlowableDefinitionId(processInstanceId);
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < generatedItems.size(); index++) {
            Map<String, Object> item = generatedItems.get(index);
            String targetUserId = stringValue(item.get("userId"));
            if (targetUserId == null) {
                targetUserId = stringValue(item.get("targetUserId"));
            }
            if (targetUserId == null || targetUserId.isBlank()) {
                continue;
            }
            String generatedNodeId = sourceNodeId + "__dynamic_task_" + (index + 1);
            Task generatedTask = flowableTaskActionService.createAdhocTask(
                    processInstanceId,
                    processDefinitionId,
                    generatedNodeId,
                    nodeName + " / 动态生成审批",
                    "APPEND",
                    targetUserId,
                    List.of(),
                    null,
                    new LinkedHashMap<>(Map.of(
                            "westflowTaskKind", "APPEND",
                            "westflowAppendType", "TASK",
                            "westflowAppendPolicy", appendPolicy,
                            "westflowTriggerMode", "DYNAMIC_BUILD",
                            "westflowSourceTaskId", sourceStructureId,
                            "westflowSourceNodeId", sourceNodeId,
                            "westflowOperatorUserId", operatorUserId
                    ))
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "TASK",
                    "ADHOC_TASK",
                    appendPolicy,
                    generatedTask.getId(),
                    null,
                    targetUserId,
                    null,
                    null,
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
            appendInstanceEvent(
                    processInstanceId,
                    generatedTask.getId(),
                    sourceNodeId,
                    "DYNAMIC_BUILD_TASK_CREATED",
                    "动态构建已生成附属任务",
                    "TASK",
                    sourceNodeId,
                    generatedTask.getId(),
                    targetUserId,
                    eventDetails(
                            "sourceNodeId", sourceNodeId,
                            "sourceTaskId", sourceStructureId,
                            "targetTaskId", generatedTask.getId(),
                            "targetUserId", targetUserId,
                            "appendPolicy", appendPolicy,
                            "appendLinkId", appendLink.id()
                    ),
                    null,
                    sourceNodeId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    operatorUserId
            );
        }
    }

    private void createDynamicBuildSubprocesses(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            PublishedProcessDefinition parentDefinition,
            String appendPolicy,
            Map<String, Object> processVariables,
            List<Map<String, Object>> generatedItems,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String parentBusinessKey = stringValue(processVariables.get("westflowBusinessKey"));
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < generatedItems.size(); index++) {
            Map<String, Object> item = generatedItems.get(index);
            String calledProcessKey = stringValue(item.get("calledProcessKey"));
            if (calledProcessKey == null || calledProcessKey.isBlank()) {
                continue;
            }
            String versionPolicy = Optional.ofNullable(stringValue(item.get("calledVersionPolicy"))).orElse("LATEST_PUBLISHED");
            Integer calledVersion = item.get("calledVersion") instanceof Number number ? number.intValue() : null;
            PublishedProcessDefinition childDefinition = "FIXED_VERSION".equals(versionPolicy)
                    ? processDefinitionService.getPublishedByProcessKeyAndVersion(calledProcessKey, calledVersion)
                    : processDefinitionService.getLatestByProcessKey(calledProcessKey);

            Map<String, Object> childVariables = new LinkedHashMap<>();
            childVariables.put("westflowProcessDefinitionId", childDefinition.processDefinitionId());
            childVariables.put("westflowProcessKey", childDefinition.processKey());
            childVariables.put("westflowProcessName", childDefinition.processName());
            childVariables.put("westflowBusinessType", stringValue(processVariables.get("westflowBusinessType")));
            childVariables.put("westflowBusinessKey", parentBusinessKey);
            childVariables.put("westflowInitiatorUserId", stringValue(processVariables.get("westflowInitiatorUserId")));
            childVariables.put("westflowParentInstanceId", processInstanceId);
            childVariables.put("westflowRootInstanceId", rootInstanceId);
            childVariables.put("westflowAppendType", "SUBPROCESS");
            childVariables.put("westflowAppendPolicy", appendPolicy);
            childVariables.put("westflowAppendTriggerMode", "DYNAMIC_BUILD");
            childVariables.put("westflowAppendSourceTaskId", sourceStructureId);
            childVariables.put("westflowAppendSourceNodeId", sourceNodeId);
            childVariables.put("westflowAppendOperatorUserId", operatorUserId);
            Object appendVariables = item.get("appendVariables");
            if (appendVariables instanceof Map<?, ?> appendVariablesMap) {
                appendVariablesMap.forEach((key, value) -> childVariables.put(String.valueOf(key), value));
            }
            ProcessInstance childInstance = flowableEngineFacade.runtimeService().startProcessInstanceByKey(
                    childDefinition.processKey(),
                    buildGeneratedSubprocessRuntimeBusinessKey(parentBusinessKey, sourceNodeId, index),
                    childVariables
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "SUBPROCESS",
                    "ADHOC_SUBPROCESS",
                    appendPolicy,
                    null,
                    childInstance.getProcessInstanceId(),
                    null,
                    childDefinition.processKey(),
                    childDefinition.processDefinitionId(),
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
            appendInstanceEvent(
                    processInstanceId,
                    null,
                    sourceNodeId,
                    "DYNAMIC_BUILD_SUBPROCESS_CREATED",
                    "动态构建已生成附属子流程",
                    "INSTANCE",
                    sourceNodeId,
                    childInstance.getProcessInstanceId(),
                    null,
                    eventDetails(
                            "sourceNodeId", sourceNodeId,
                            "sourceTaskId", sourceStructureId,
                            "childInstanceId", childInstance.getProcessInstanceId(),
                            "calledProcessKey", childDefinition.processKey(),
                            "appendPolicy", appendPolicy,
                            "appendLinkId", appendLink.id(),
                            "parentProcessDefinitionId", parentDefinition.processDefinitionId(),
                            "nodeName", nodeName
                    ),
                    null,
                    sourceNodeId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    operatorUserId
            );
        }
    }

    private List<Map<String, Object>> resolveDynamicBuilderItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            int maxGeneratedCount
    ) {
        String sourceMode = normalizeDynamicBuilderSourceMode(stringValue(config.get("sourceMode")));
        String executionStrategy = normalizeDynamicBuilderExecutionStrategy(
                stringValue(config.get("executionStrategy")),
                sourceMode
        );
        String fallbackStrategy = normalizeDynamicBuilderFallbackStrategy(stringValue(config.get("fallbackStrategy")));
        List<?> rawItems = resolveDynamicBuilderItemsByStrategy(
                buildMode,
                config,
                processVariables,
                sourceMode,
                executionStrategy,
                fallbackStrategy
        );
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Object item : rawItems) {
            if (resolved.size() >= maxGeneratedCount) {
                break;
            }
            Map<String, Object> map = mapValue(item);
            if (!map.isEmpty()) {
                resolved.add(map);
                continue;
            }
            if ("SUBPROCESS_CALLS".equals(buildMode) && item instanceof String calledProcessKey && !calledProcessKey.isBlank()) {
                resolved.add(Map.of("calledProcessKey", calledProcessKey));
                continue;
            }
            if ("APPROVER_TASKS".equals(buildMode) && item instanceof String userId && !userId.isBlank()) {
                resolved.add(Map.of("userId", userId));
            }
        }
        return resolved;
    }

    private List<?> resolveDynamicBuilderItemsByStrategy(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String sourceMode,
            String executionStrategy,
            String fallbackStrategy
    ) {
        List<?> ruleItems = resolveDynamicBuilderRuleItems(
                buildMode,
                config,
                processVariables,
                stringValue(config.get("ruleExpression")),
                false
        );
        List<?> modelItems = resolveDynamicBuilderModelItems(
                buildMode,
                stringValue(config.get("manualTemplateCode")),
                stringValue(config.get("sceneCode"))
        );
        List<?> preferred = switch (executionStrategy) {
            case "RULE_ONLY" -> ruleItems;
            case "TEMPLATE_ONLY" -> modelItems;
            case "TEMPLATE_FIRST" -> !modelItems.isEmpty() ? modelItems : ruleItems;
            default -> !ruleItems.isEmpty() ? ruleItems : modelItems;
        };
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return switch (fallbackStrategy) {
            case "USE_RULE" -> resolveDynamicBuilderRuleItems(
                    buildMode,
                    config,
                    processVariables,
                    stringValue(config.get("ruleExpression")),
                    true
            );
            case "USE_TEMPLATE" -> modelItems;
            case "SKIP_GENERATION" -> List.of();
            default -> "MODEL_DRIVEN".equals(sourceMode)
                    ? modelItems
                    : resolveDynamicBuilderRuleItems(
                            buildMode,
                            config,
                            processVariables,
                            stringValue(config.get("ruleExpression")),
                            true
                    );
        };
    }

    private List<?> resolveDynamicBuilderRuleItems(
            String buildMode,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String ruleExpression,
            boolean allowFallback
    ) {
        if (ruleExpression == null || ruleExpression.isBlank()) {
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config) : List.of();
        }
        Object value = evaluateDynamicBuilderRule(ruleExpression.trim(), processVariables);
        if (value instanceof Boolean booleanValue) {
            if (!booleanValue) {
                return List.of();
            }
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config) : List.of();
        }
        if (value instanceof List<?> items) {
            return items;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : items) {
                resolved.add(item);
            }
            return resolved;
        }
        if (value instanceof Object[] items) {
            return List.of(items);
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return "SUBPROCESS_CALLS".equals(buildMode)
                    ? List.of(Map.of("calledProcessKey", stringValue))
                    : List.of(Map.of("userId", stringValue));
        }
        if (value instanceof Map<?, ?> map) {
            return List.of(map);
        }
        if (value == null) {
            return allowFallback ? resolveDynamicBuilderFallbackItems(buildMode, config) : List.of();
        }
        return List.of(value);
    }

    private Object evaluateDynamicBuilderRule(String ruleExpression, Map<String, Object> processVariables) {
        try {
            String normalizedExpression = ruleExpression;
            if (normalizedExpression.startsWith("${") && normalizedExpression.endsWith("}")) {
                normalizedExpression = normalizedExpression.substring(2, normalizedExpression.length() - 1).trim();
            }
            if (processVariables.containsKey(normalizedExpression)) {
                return processVariables.get(normalizedExpression);
            }
            Matcher matcher = SIMPLE_COMPARISON_PATTERN.matcher(normalizedExpression);
            if (matcher.matches()) {
                String variableName = matcher.group(1);
                String operator = matcher.group(2);
                String rightOperand = matcher.group(3).trim();
                Object leftValue = processVariables.get(variableName);
                if (leftValue == null) {
                    return false;
                }
                return compareDynamicBuilderValues(leftValue, operator, rightOperand);
            }
            if ("true".equalsIgnoreCase(normalizedExpression)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalizedExpression)) {
                return false;
            }
            return processVariables.get(normalizedExpression);
        } catch (RuntimeException exception) {
            throw new ContractException(
                    "PROCESS.DYNAMIC_BUILD_RULE_FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "动态构建规则执行失败",
                    Map.of(
                            "ruleExpression", ruleExpression,
                            "error", exception.getMessage()
                    )
            );
        }
    }

    private boolean compareDynamicBuilderValues(Object leftValue, String operator, String rightOperand) {
        if (leftValue instanceof Number numberValue && isNumeric(rightOperand)) {
            BigDecimal leftNumber = new BigDecimal(String.valueOf(numberValue));
            BigDecimal rightNumber = new BigDecimal(rightOperand);
            return switch (operator) {
                case ">" -> leftNumber.compareTo(rightNumber) > 0;
                case ">=" -> leftNumber.compareTo(rightNumber) >= 0;
                case "<" -> leftNumber.compareTo(rightNumber) < 0;
                case "<=" -> leftNumber.compareTo(rightNumber) <= 0;
                case "==" -> leftNumber.compareTo(rightNumber) == 0;
                case "!=" -> leftNumber.compareTo(rightNumber) != 0;
                default -> false;
            };
        }
        String leftText = String.valueOf(leftValue);
        String rightText = normalizeQuotedText(rightOperand);
        return switch (operator) {
            case "==" -> leftText.equals(rightText);
            case "!=" -> !leftText.equals(rightText);
            case ">" -> leftText.compareTo(rightText) > 0;
            case ">=" -> leftText.compareTo(rightText) >= 0;
            case "<" -> leftText.compareTo(rightText) < 0;
            case "<=" -> leftText.compareTo(rightText) <= 0;
            default -> false;
        };
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(normalizeQuotedText(value));
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalizeQuotedText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private List<?> resolveDynamicBuilderFallbackItems(String buildMode, Map<String, Object> config) {
        if ("SUBPROCESS_CALLS".equals(buildMode)) {
            String calledProcessKey = stringValue(config.get("calledProcessKey"));
            if (calledProcessKey == null) {
                return List.of();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("calledProcessKey", calledProcessKey);
            String calledVersionPolicy = stringValue(config.get("calledVersionPolicy"));
            if (calledVersionPolicy != null) {
                item.put("calledVersionPolicy", calledVersionPolicy);
            }
            Object calledVersion = config.get("calledVersion");
            if (calledVersion instanceof Number || calledVersion instanceof String) {
                item.put("calledVersion", calledVersion);
            }
            return List.of(item);
        }
        Map<String, Object> targets = mapValue(config.get("targets"));
        List<String> userIds = stringListValue(targets.get("userIds"));
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (String userId : userIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            resolved.add(item);
        }
        return resolved;
    }

    private List<?> resolveDynamicBuilderManualTemplate(String buildMode, String manualTemplateCode) {
        if (manualTemplateCode == null || manualTemplateCode.isBlank()) {
            return List.of();
        }
        return switch (manualTemplateCode) {
            case "append_leave_audit", "append_manager_review" -> List.of(Map.of("userId", "usr_002"));
            case "append_parallel_review" -> List.of(Map.of("userId", "usr_002"), Map.of("userId", "usr_003"));
            case "append_sub_review" -> List.of(Map.of("calledProcessKey", "oa_sub_review"));
            default -> "SUBPROCESS_CALLS".equals(buildMode)
                    ? List.of(Map.of("calledProcessKey", manualTemplateCode))
                    : List.of(Map.of("userId", manualTemplateCode));
        };
    }

    private List<?> resolveDynamicBuilderModelItems(String buildMode, String manualTemplateCode, String sceneCode) {
        if (manualTemplateCode != null && !manualTemplateCode.isBlank()) {
            return resolveDynamicBuilderManualTemplate(buildMode, manualTemplateCode);
        }
        if (sceneCode == null || sceneCode.isBlank()) {
            return List.of();
        }
        return "SUBPROCESS_CALLS".equals(buildMode)
                ? List.of(Map.of("calledProcessKey", sceneCode))
                : List.of(Map.of("userId", sceneCode));
    }

    private String normalizeDynamicBuilderSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return "RULE_DRIVEN";
        }
        return switch (sourceMode.trim().toUpperCase()) {
            case "RULE", "RULE_DRIVEN" -> "RULE_DRIVEN";
            case "MANUAL_TEMPLATE" -> "MANUAL_TEMPLATE";
            case "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> "RULE_DRIVEN";
        };
    }

    private String normalizeDynamicBuilderExecutionStrategy(String executionStrategy, String sourceMode) {
        if (executionStrategy == null || executionStrategy.isBlank()) {
            return "RULE_DRIVEN".equals(sourceMode) ? "RULE_FIRST" : "TEMPLATE_FIRST";
        }
        return switch (executionStrategy.trim().toUpperCase()) {
            case "RULE_ONLY", "TEMPLATE_FIRST", "TEMPLATE_ONLY" -> executionStrategy.trim().toUpperCase();
            default -> "RULE_FIRST";
        };
    }

    private String normalizeDynamicBuilderFallbackStrategy(String fallbackStrategy) {
        if (fallbackStrategy == null || fallbackStrategy.isBlank()) {
            return "KEEP_CURRENT";
        }
        return switch (fallbackStrategy.trim().toUpperCase()) {
            case "USE_RULE", "USE_TEMPLATE", "SKIP_GENERATION" -> fallbackStrategy.trim().toUpperCase();
            default -> "KEEP_CURRENT";
        };
    }

    private String buildGeneratedSubprocessRuntimeBusinessKey(String parentBusinessKey, String sourceNodeId, int index) {
        String normalizedParentBusinessKey = parentBusinessKey == null || parentBusinessKey.isBlank()
                ? "instance"
                : parentBusinessKey;
        return normalizedParentBusinessKey + "::dynamic-build::" + sourceNodeId + "::" + (index + 1);
    }

    private String buildDynamicBuildSourceTaskId(String processInstanceId, String sourceNodeId) {
        return processInstanceId + "::dynamic-build::" + sourceNodeId;
    }

    private String resolveRuntimeOperatorUserId(Map<String, Object> processVariables) {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        String initiatorUserId = stringValue(processVariables.get("westflowInitiatorUserId"));
        return initiatorUserId == null ? "SYSTEM" : initiatorUserId;
    }

    private void synchronizeAppendLinks(String rootInstanceId) {
        runtimeAppendLinkService.listByRootInstanceId(rootInstanceId).stream()
                .filter(record -> "RUNNING".equals(record.status()))
                .forEach(record -> {
                    if (record.targetTaskId() != null && !record.targetTaskId().isBlank()) {
                        Task runtimeTask = flowableEngineFacade.taskService()
                                .createTaskQuery()
                                .taskId(record.targetTaskId())
                                .singleResult();
                        if (runtimeTask != null) {
                            return;
                        }
                        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                                .createHistoricTaskInstanceQuery()
                                .taskId(record.targetTaskId())
                                .singleResult();
                        if (historicTask == null || historicTask.getEndTime() == null) {
                            return;
                        }
                        String resolvedStatus = historicTask.getDeleteReason() != null && !historicTask.getDeleteReason().isBlank()
                                ? "TERMINATED"
                                : "COMPLETED";
                        runtimeAppendLinkService.updateStatusByTargetTaskId(
                                record.targetTaskId(),
                                resolvedStatus,
                                historicTask.getEndTime().toInstant()
                        );
                        appendInstanceEvent(
                                record.parentInstanceId(),
                                record.targetTaskId(),
                                record.sourceNodeId(),
                                "TERMINATED".equals(resolvedStatus) ? "APPEND_TERMINATED" : "TASK_APPEND_COMPLETED",
                                "TERMINATED".equals(resolvedStatus) ? "追加任务已终止" : "追加任务已完成",
                                "TASK",
                                record.sourceTaskId(),
                                record.targetTaskId(),
                                record.targetUserId(),
                                eventDetails(
                                        "appendLinkId", record.id(),
                                        "appendType", record.appendType(),
                                        "runtimeLinkType", record.runtimeLinkType(),
                                        "sourceTaskId", record.sourceTaskId(),
                                        "sourceNodeId", record.sourceNodeId(),
                                        "targetTaskId", record.targetTaskId(),
                                        "targetUserId", record.targetUserId(),
                                        "resolvedStatus", resolvedStatus
                                ),
                                null,
                                record.sourceNodeId(),
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                        return;
                    }
                    if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
                        ProcessInstance runtimeChild = flowableEngineFacade.runtimeService()
                                .createProcessInstanceQuery()
                                .processInstanceId(record.targetInstanceId())
                                .singleResult();
                        if (runtimeChild != null) {
                            return;
                        }
                        HistoricProcessInstance historicChild = flowableEngineFacade.historyService()
                                .createHistoricProcessInstanceQuery()
                                .processInstanceId(record.targetInstanceId())
                                .singleResult();
                        if (historicChild == null || historicChild.getEndTime() == null) {
                            return;
                        }
                        String resolvedStatus = isTerminatedProcess(historicChild) ? "TERMINATED" : "COMPLETED";
                        runtimeAppendLinkService.updateStatusByTargetInstanceId(
                                record.targetInstanceId(),
                                resolvedStatus,
                                historicChild.getEndTime().toInstant()
                        );
                        appendInstanceEvent(
                                record.parentInstanceId(),
                                null,
                                record.sourceNodeId(),
                                "TERMINATED".equals(resolvedStatus) ? "APPEND_TERMINATED" : "SUBPROCESS_APPENDED_FINISHED",
                                "TERMINATED".equals(resolvedStatus) ? "追加子流程已终止" : "追加子流程已完成",
                                "INSTANCE",
                                record.sourceTaskId(),
                                record.targetInstanceId(),
                                null,
                                eventDetails(
                                        "appendLinkId", record.id(),
                                        "appendType", record.appendType(),
                                        "runtimeLinkType", record.runtimeLinkType(),
                                        "sourceTaskId", record.sourceTaskId(),
                                        "sourceNodeId", record.sourceNodeId(),
                                        "childInstanceId", record.targetInstanceId(),
                                        "calledProcessKey", record.calledProcessKey(),
                                        "resolvedStatus", resolvedStatus
                                ),
                                null,
                                record.sourceNodeId(),
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                    }
                });
    }

    private ProcessInstanceLinkResponse toProcessInstanceLinkResponse(com.westflow.processruntime.model.ProcessLinkRecord record) {
        NodeMetadata parentNode = resolveNodeMetadata(record.parentInstanceId(), record.parentNodeId());
        DefinitionMetadata childDefinition = resolveDefinitionMetadata(
                record.childInstanceId(),
                record.calledDefinitionId(),
                record.calledProcessKey()
        );
        SubprocessStructureMetadata structureMetadata = resolveSubprocessStructureMetadata(record);
        return new ProcessInstanceLinkResponse(
                record.id(),
                record.rootInstanceId(),
                record.parentInstanceId(),
                record.childInstanceId(),
                record.parentNodeId(),
                parentNode.nodeName(),
                parentNode.nodeType(),
                record.calledProcessKey(),
                record.calledDefinitionId(),
                childDefinition.processName(),
                childDefinition.version(),
                record.linkType(),
                record.status(),
                record.terminatePolicy(),
                record.childFinishPolicy(),
                structureMetadata.callScope(),
                structureMetadata.joinMode(),
                structureMetadata.childStartStrategy(),
                structureMetadata.parentResumeStrategy(),
                record.createdAt() == null ? null : OffsetDateTime.ofInstant(record.createdAt(), TIME_ZONE),
                record.finishedAt() == null ? null : OffsetDateTime.ofInstant(record.finishedAt(), TIME_ZONE)
        );
    }

    private RuntimeAppendLinkResponse toRuntimeAppendLinkResponse(RuntimeAppendLinkRecord record) {
        NodeMetadata sourceNode = resolveNodeMetadata(record.parentInstanceId(), record.sourceNodeId());
        Map<String, Object> sourceNodeConfig = resolveNodeConfig(record.parentInstanceId(), record.sourceNodeId());
        DefinitionMetadata targetDefinition = resolveTargetProcessDefinition(record);
        String targetTaskName = resolveTaskName(record.targetTaskId());
        Map<String, Object> runtimeMetadata = resolveDynamicBuildRuntimeMetadata(record);
        return new RuntimeAppendLinkResponse(
                record.id(),
                record.rootInstanceId(),
                record.parentInstanceId(),
                record.sourceTaskId(),
                record.sourceNodeId(),
                sourceNode.nodeName(),
                sourceNode.nodeType(),
                record.appendType(),
                record.runtimeLinkType(),
                record.policy(),
                record.targetTaskId(),
                targetTaskName,
                record.targetInstanceId(),
                record.targetUserId(),
                record.calledProcessKey(),
                record.calledDefinitionId(),
                targetDefinition.processName(),
                targetDefinition.version(),
                record.status(),
                record.triggerMode(),
                stringValue(sourceNodeConfig.get("buildMode")),
                normalizeDynamicBuilderSourceMode(stringValue(sourceNodeConfig.get("sourceMode"))),
                stringValue(sourceNodeConfig.get("ruleExpression")),
                stringValue(sourceNodeConfig.get("manualTemplateCode")),
                stringValue(sourceNodeConfig.get("sceneCode")),
                stringValue(sourceNodeConfig.get("executionStrategy")),
                stringValue(sourceNodeConfig.get("fallbackStrategy")),
                stringValue(runtimeMetadata.get("westflowDynamicResolvedSourceMode")),
                stringValue(runtimeMetadata.get("westflowDynamicResolutionPath")),
                stringValue(runtimeMetadata.get("westflowDynamicTemplateSource")),
                record.operatorUserId(),
                record.commentText(),
                record.createdAt() == null ? null : OffsetDateTime.ofInstant(record.createdAt(), TIME_ZONE),
                record.finishedAt() == null ? null : OffsetDateTime.ofInstant(record.finishedAt(), TIME_ZONE)
        );
    }

    private List<ProcessTaskSnapshot> activeAppendTasks(String processInstanceId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(task -> "APPEND".equals(resolveTaskKind(task)))
                .map(this::toTaskView)
                .toList();
    }

    private boolean isTerminatedProcess(HistoricProcessInstance historicProcessInstance) {
        String deleteReason = historicProcessInstance.getDeleteReason();
        return deleteReason != null && deleteReason.startsWith("WESTFLOW_TERMINATE:");
    }

    private CompleteTaskResponse nextTaskResponse(String processInstanceId, String completedTaskId) {
        List<ProcessTaskSnapshot> nextTasks = flowableEngineFacade.taskService()
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
                blockingTaskViews(nextTasks).isEmpty() && !hasRunningAppendStructures(processInstanceId) ? "COMPLETED" : "RUNNING",
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
        appendInstanceEvent(
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId,
                currentUserId()
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
            String handoverFromUserId,
            String operatorUserId
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
                operatorUserId,
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
                operatorUserId,
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

    private ProcessTaskSnapshot toTaskView(Task task) {
        return new ProcessTaskSnapshot(
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

    private Optional<PublishedProcessDefinition> resolvePublishedDefinitionByInstance(String processInstanceId) {
        Map<String, Object> variables = runtimeOrHistoricVariables(processInstanceId);
        String platformDefinitionId = stringValue(variables.get("westflowProcessDefinitionId"));
        String processKey = stringValue(variables.get("westflowProcessKey"));
        try {
            return Optional.of(resolvePublishedDefinition(platformDefinitionId, platformDefinitionId, processKey, processInstanceId));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private NodeMetadata resolveNodeMetadata(String processInstanceId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return new NodeMetadata(null, null);
        }
        return resolvePublishedDefinitionByInstance(processInstanceId)
                .map(definition -> definition.dsl().nodes().stream()
                        .filter(node -> nodeId.equals(node.id()))
                        .findFirst()
                        .map(node -> new NodeMetadata(
                                node.name() == null || node.name().isBlank() ? nodeId : node.name(),
                                node.type()
                        ))
                        .orElse(new NodeMetadata(nodeId, null)))
                .orElse(new NodeMetadata(nodeId, null));
    }

    private Map<String, Object> resolveNodeConfig(String processInstanceId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Map.of();
        }
        return resolvePublishedDefinitionByInstance(processInstanceId)
                .map(definition -> nodeConfig(definition.dsl(), nodeId))
                .orElse(Map.of());
    }

    private DefinitionMetadata resolveDefinitionMetadata(String processInstanceId, String definitionId, String processKey) {
        try {
            PublishedProcessDefinition definition;
            if (definitionId != null && !definitionId.isBlank()) {
                try {
                    definition = processDefinitionService.getById(definitionId);
                } catch (RuntimeException ignored) {
                    definition = processDefinitionService.getByFlowableDefinitionId(definitionId);
                }
            } else {
                Map<String, Object> variables = runtimeOrHistoricVariables(processInstanceId);
                definition = resolvePublishedDefinition(
                        stringValue(variables.get("westflowProcessDefinitionId")),
                        stringValue(variables.get("westflowProcessDefinitionId")),
                        processKey,
                        processInstanceId
                );
            }
            return new DefinitionMetadata(definition.processName(), definition.version());
        } catch (RuntimeException exception) {
            return new DefinitionMetadata(null, null);
        }
    }

    private DefinitionMetadata resolveTargetProcessDefinition(RuntimeAppendLinkRecord record) {
        if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
            return resolveDefinitionMetadata(record.targetInstanceId(), record.calledDefinitionId(), record.calledProcessKey());
        }
        return resolveDefinitionMetadata(record.parentInstanceId(), record.calledDefinitionId(), record.calledProcessKey());
    }

    private SubprocessStructureMetadata resolveSubprocessStructureMetadata(String processInstanceId, String nodeId) {
        Map<String, Object> config = resolveNodeConfig(processInstanceId, nodeId);
        return new SubprocessStructureMetadata(
                stringValueOrDefault(config.get("callScope"), "CHILD_ONLY"),
                stringValueOrDefault(config.get("joinMode"), "AUTO_RETURN"),
                stringValueOrDefault(config.get("childStartStrategy"), "LATEST_PUBLISHED"),
                stringValueOrDefault(config.get("parentResumeStrategy"), "AUTO_RETURN")
        );
    }

    private SubprocessStructureMetadata resolveSubprocessStructureMetadata(
            com.westflow.processruntime.model.ProcessLinkRecord record
    ) {
        SubprocessStructureMetadata fallback = resolveSubprocessStructureMetadata(
                record.parentInstanceId(),
                record.parentNodeId()
        );
        return new SubprocessStructureMetadata(
                stringValueOrDefault(record.callScope(), fallback.callScope()),
                stringValueOrDefault(record.joinMode(), fallback.joinMode()),
                stringValueOrDefault(record.childStartStrategy(), fallback.childStartStrategy()),
                stringValueOrDefault(record.parentResumeStrategy(), fallback.parentResumeStrategy())
        );
    }

    private boolean requiresParentConfirmation(SubprocessStructureMetadata structureMetadata) {
        return "WAIT_PARENT_CONFIRM".equals(structureMetadata.parentResumeStrategy())
                || "WAIT_PARENT_CONFIRM".equals(structureMetadata.joinMode());
    }

    private String resolveTaskName(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        Task runtimeTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (runtimeTask != null) {
            return runtimeTask.getName();
        }
        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        return historicTask == null ? null : historicTask.getName();
    }

    private String edgeConditionExpression(ProcessDslPayload.Edge edge) {
        if (edge == null || edge.condition() == null || edge.condition().isEmpty()) {
            return null;
        }
        Object expression = edge.condition().get("expression");
        return stringValue(expression);
    }

    private String buildInclusiveDecisionSummary(
            int branchCount,
            int eligibleCount,
            int activatedCount,
            String branchMergePolicy,
            boolean defaultBranchSelected,
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
        if (!hasJoin) {
            return summary.append("，未找到汇聚节点").toString();
        }
        return summary.toString();
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
        Map<String, Object> variables;
        try {
            variables = flowableEngineFacade.taskService().getVariablesLocal(taskId);
        } catch (FlowableObjectNotFoundException ignored) {
            // 已完成任务可能已经从运行时表移除，详情接口应回退到历史变量而不是直接失败。
            return Map.of();
        }
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    private Map<String, Object> resolveDynamicBuildRuntimeMetadata(RuntimeAppendLinkRecord record) {
        if (record.targetTaskId() != null && !record.targetTaskId().isBlank()) {
            Map<String, Object> runtimeValues = taskLocalVariables(record.targetTaskId());
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
            Map<String, Object> historicValues = historicTaskLocalVariables(record.targetTaskId());
            return historicValues.isEmpty() ? Map.of() : historicValues;
        }
        if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
            Map<String, Object> values = runtimeOrHistoricVariables(record.targetInstanceId());
            return values.isEmpty() ? Map.of() : values;
        }
        return Map.of();
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
        return activeTasks.stream().anyMatch(this::isBlockingTask) || hasRunningAppendStructures(processInstance.getId())
                ? "RUNNING"
                : "COMPLETED";
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
        return isVisibleTask(task) && !"CC".equals(resolveTaskKind(task));
    }

    private boolean isVisibleTask(Task task) {
        if (task == null) {
            return false;
        }
        if (!"NORMAL".equals(resolveTaskKind(task))) {
            return true;
        }
        return !isBlockedByPendingAppendStructures(task);
    }

    private boolean isBlockedByPendingAppendStructures(Task task) {
        if (task == null) {
            return false;
        }
        return blockingDynamicBuilderNodeIds(task.getProcessInstanceId(), task.getTaskDefinitionKey()).stream()
                .anyMatch(sourceNodeId -> runtimeAppendLinkService.listByParentInstanceId(task.getProcessInstanceId()).stream()
                        .anyMatch(link -> "RUNNING".equals(link.status()) && sourceNodeId.equals(link.sourceNodeId())));
    }

    private List<String> blockingDynamicBuilderNodeIds(String processInstanceId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        return resolvePublishedDefinitionByInstance(processInstanceId)
                .map(definition -> definition.dsl().edges().stream()
                        .filter(edge -> nodeId.equals(edge.target()))
                        .map(edge -> definition.dsl().nodes().stream()
                                .filter(node -> edge.source().equals(node.id()))
                                .findFirst()
                                .orElse(null))
                        .filter(Objects::nonNull)
                        .filter(node -> "dynamic-builder".equals(node.type()))
                        .filter(node -> {
                            String appendPolicy = normalizeAppendPolicy(stringValue(nodeConfig(definition.dsl(), node.id()).get("appendPolicy")));
                            return !"PARALLEL_WITH_CURRENT".equals(appendPolicy);
                        })
                        .map(ProcessDslPayload.Node::id)
                        .distinct()
                        .toList())
                .orElse(List.of());
    }

    private boolean hasRunningAppendStructures(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        return runtimeAppendLinkService.listByParentInstanceId(processInstanceId).stream()
                .anyMatch(link -> "RUNNING".equals(link.status()));
    }

    private List<ProcessTaskSnapshot> blockingTaskViews(List<ProcessTaskSnapshot> nextTasks) {
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
        List<org.flowable.engine.runtime.ProcessInstance> runtimeInstances = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .list();
        if (!runtimeInstances.isEmpty()) {
            return runtimeInstances.stream()
                    .filter(instance -> instance.getSuperExecutionId() == null
                            || instance.getRootProcessInstanceId() == null
                            || instance.getRootProcessInstanceId().equals(instance.getProcessInstanceId()))
                    .findFirst()
                    .orElse(runtimeInstances.get(0))
                    .getProcessInstanceId();
        }
        List<HistoricProcessInstance> historicInstances = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .list();
        if (!historicInstances.isEmpty()) {
            return historicInstances.stream()
                    .filter(instance -> instance.getSuperProcessInstanceId() == null)
                    .findFirst()
                    .orElse(historicInstances.get(0))
                    .getId();
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

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private String stringValueOrDefault(Object value, String defaultValue) {
        String text = stringValue(value);
        return text == null ? defaultValue : text;
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
