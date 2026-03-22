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
import com.westflow.processruntime.api.ClaimTaskRequest;
import com.westflow.processruntime.api.ClaimTaskResponse;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.CompleteTaskResponse;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessNotificationSendRecordResponse;
import com.westflow.processruntime.api.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.WorkflowFieldBinding;
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
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于真实 Flowable 的最小运行态服务。
 * 当前先承接真实发起、待办、审批单详情、认领和完成动作。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableProcessRuntimeService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final ProcessDefinitionService processDefinitionService;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final ProcessRuntimeTraceStore traceStore;
    private final JdbcTemplate jdbcTemplate;

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
            case CC -> List.of();
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
     * 查询任务可执行动作。
     */
    public TaskActionAvailabilityResponse actions(String taskId) {
        Task task = requireActiveTask(taskId);
        List<String> candidateUserIds = candidateUsers(task.getId());
        boolean isCandidate = candidateUserIds.contains(currentUserId());
        boolean isAssignee = currentUserId().equals(task.getAssignee());
        boolean canHandle = isAssignee || (task.getAssignee() == null && isCandidate);
        String taskKind = resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        return new TaskActionAvailabilityResponse(
                task.getAssignee() == null && isCandidate,
                canHandle,
                canHandle,
                false,
                false,
                false,
                false,
                false,
                false,
                "CC".equals(taskKind) && canHandle,
                false,
                false,
                false,
                false,
                false,
                false
        );
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
        String operatorUserId = normalizeUserId(request.operatorUserId());
        if (task.getAssignee() == null) {
            flowableTaskActionService.claim(taskId, operatorUserId);
            task = requireActiveTask(taskId);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("westflowLastAction", request.action());
        variables.put("westflowLastOperatorUserId", operatorUserId);
        variables.put("westflowLastComment", request.comment());
        variables.put("westflowTaskFormData", request.taskFormData() == null ? Map.of() : request.taskFormData());
        if (request.taskFormData() != null && !request.taskFormData().isEmpty()) {
            variables.putAll(request.taskFormData());
        }
        if (request.comment() != null && !request.comment().isBlank()) {
            flowableEngineFacade.taskService().addComment(taskId, task.getProcessInstanceId(), request.comment().trim());
        }
        flowableTaskActionService.complete(taskId, variables);
        List<DemoTaskView> nextTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .map(this::toTaskView)
                .toList();
        return new CompleteTaskResponse(
                task.getProcessInstanceId(),
                taskId,
                nextTasks.isEmpty() ? "COMPLETED" : "RUNNING",
                nextTasks
        );
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
        List<HistoricTaskInstance> historicTasks = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .asc()
                .list();
        Task referenceActiveTask = activeTask != null
                ? activeTask
                : activeTasks.stream().findFirst().orElse(null);
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
                activeTasks.isEmpty() ? "SUCCESS" : "PENDING",
                stringValue(variables.get("westflowInitiatorUserId")),
                payload,
                toOffsetDateTime(historicProcessInstance.getStartTime())
        );
        List<ProcessNotificationSendRecordResponse> notificationRecords = traceStore.queryNotificationSendRecords(
                processInstanceId,
                activeTasks.isEmpty() ? "SUCCESS" : "PENDING",
                stringValue(variables.get("westflowInitiatorUserId")),
                payload,
                toOffsetDateTime(historicProcessInstance.getStartTime())
        );
        OffsetDateTime createdAt = referenceActiveTask != null
                ? toOffsetDateTime(referenceActiveTask.getCreateTime())
                : referenceHistoricTask == null ? toOffsetDateTime(historicProcessInstance.getStartTime()) : toOffsetDateTime(referenceHistoricTask.getCreateTime());
        OffsetDateTime completedAt = referenceHistoricTask == null ? null : toOffsetDateTime(referenceHistoricTask.getEndTime());
        Long handleDurationSeconds = durationSeconds(createdAt, completedAt);
        List<String> activeTaskIds = activeTasks.stream().map(Task::getId).toList();
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
                activeTasks.isEmpty() ? "SUCCESS" : "PENDING",
                payload.nodes(),
                payload.edges(),
                instanceEvents,
                taskTrace,
                automationTrace,
                notificationRecords,
                nodeId,
                nodeName,
                resolveTaskKind(referenceActiveTask != null ? referenceActiveTask.getProcessDefinitionId() : historicProcessInstance.getProcessDefinitionId(), nodeId),
                activeTasks.isEmpty() ? "COMPLETED" : resolveTaskStatus(referenceActiveTask),
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
                null,
                null,
                null,
                null,
                createdAt,
                completedAt == null ? createdAt : completedAt,
                completedAt,
                activeTasks.isEmpty() ? "COMPLETED" : "RUNNING",
                payload.processFormKey(),
                payload.processFormVersion(),
                nodeFormKey,
                nodeFormVersion,
                nodeFormKey != null ? nodeFormKey : payload.processFormKey(),
                nodeFormVersion != null ? nodeFormVersion : payload.processFormVersion(),
                fieldBindings,
                mapValue(variables.get("westflowFormData")),
                mapValue(variables.get("westflowTaskFormData")),
                activeTaskIds
        );
    }

    private List<ProcessTaskTraceItemResponse> buildTaskTrace(List<HistoricTaskInstance> historicTasks, List<Task> activeTasks) {
        List<ProcessTaskTraceItemResponse> items = new ArrayList<>();
        Set<String> knownTaskIds = new LinkedHashSet<>();
        for (HistoricTaskInstance task : historicTasks) {
            knownTaskIds.add(task.getId());
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            OffsetDateTime endedAt = toOffsetDateTime(task.getEndTime());
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    "NORMAL",
                    "COMPLETED",
                    task.getAssignee(),
                    List.of(),
                    null,
                    null,
                    null,
                    createdAt,
                    createdAt,
                    createdAt,
                    endedAt,
                    durationSeconds(createdAt, endedAt),
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        for (Task task : activeTasks) {
            if (knownTaskIds.contains(task.getId())) {
                continue;
            }
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            items.add(new ProcessTaskTraceItemResponse(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey()),
                    resolveTaskStatus(task),
                    task.getAssignee(),
                    candidateUsers(task.getId()),
                    null,
                    null,
                    null,
                    createdAt,
                    createdAt,
                    createdAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
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
                resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey()),
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
                resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey()),
                resolveTaskStatus(task),
                "USER",
                candidateUsers(task.getId()),
                task.getAssignee(),
                null,
                null,
                null,
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
        return task.getAssignee() == null && !candidateUsers(task.getId()).isEmpty() ? "PENDING_CLAIM" : "PENDING";
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
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
}
