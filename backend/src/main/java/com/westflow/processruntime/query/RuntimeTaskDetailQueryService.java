package com.westflow.processruntime.query;

import com.westflow.approval.service.ApprovalSheetQueryService;
import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processruntime.action.FlowableCountersignService;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.response.InclusiveGatewayHitResponse;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import com.westflow.processruntime.api.response.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.response.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.api.response.WorkflowFieldBinding;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import com.westflow.processruntime.trace.ProcessRuntimeTraceStore;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskDetailQueryService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final FlowableCountersignService flowableCountersignService;
    private final ProcessRuntimeTraceStore traceStore;
    private final RuntimeTaskTraceQueryService runtimeTaskTraceQueryService;
    private final RuntimeInclusiveGatewayQueryService runtimeInclusiveGatewayQueryService;
    private final RuntimeTaskDetailProjectionService runtimeTaskDetailProjectionService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeProcessPredictionService runtimeProcessPredictionService;
    private final RuntimeProcessPredictionAiNarrationService runtimeProcessPredictionAiNarrationService;
    private final RuntimeProcessPredictionActionExecutorService runtimeProcessPredictionActionExecutorService;
    private final RuntimeProcessPredictionSnapshotService runtimeProcessPredictionSnapshotService;

    public ProcessTaskDetailResponse buildDetailResponse(
            String processInstanceId,
            Task activeTask,
            HistoricTaskInstance fallbackHistoricTask,
            String platformProcessDefinitionIdOverride,
            boolean active,
            List<ProcessInstanceLinkResponse> processLinks,
            List<RuntimeAppendLinkResponse> runtimeAppendLinks
    ) {
        Map<String, Object> variables = active
                ? runtimeProcessMetadataService.runtimeVariables(processInstanceId)
                : runtimeProcessMetadataService.historicVariables(processInstanceId);
        HistoricProcessInstance historicProcessInstance = runtimeProcessMetadataService.requireHistoricProcessInstance(processInstanceId);
        PublishedProcessDefinition definition = platformProcessDefinitionIdOverride != null && !platformProcessDefinitionIdOverride.isBlank()
                ? runtimeProcessMetadataService.resolvePublishedDefinition(
                        platformProcessDefinitionIdOverride,
                        stringValue(variables.get("westflowProcessDefinitionId")),
                        stringValue(variables.get("westflowProcessKey")),
                        processInstanceId
                )
                : runtimeProcessMetadataService.resolvePublishedDefinitionByInstance(processInstanceId)
                        .orElseThrow(() -> resourceNotFound("流程定义不存在", Map.of("processInstanceId", processInstanceId)));
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
                .filter(this::isVisibleBlockingTask)
                .toList();
        List<HistoricTaskInstance> historicTasks = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .asc()
                .list();
        Task referenceActiveTask = resolveReferenceActiveTask(activeTask, blockingActiveTasks, activeTasks);
        HistoricTaskInstance referenceHistoricTask = fallbackHistoricTask != null
                ? fallbackHistoricTask
                : historicTasks.stream()
                        .max(Comparator.comparing(HistoricTaskInstance::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null);
        String nodeId = referenceActiveTask != null
                ? referenceActiveTask.getTaskDefinitionKey()
                : referenceHistoricTask == null ? null : referenceHistoricTask.getTaskDefinitionKey();
        String nodeName = referenceActiveTask != null
                ? referenceActiveTask.getName()
                : referenceHistoricTask == null ? null : referenceHistoricTask.getName();
        Map<String, Object> referenceTaskLocalVariables = referenceActiveTask != null
                ? runtimeTaskTraceQueryService.ensureReadTimeAndReturnLocalVariables(referenceActiveTask)
                : referenceHistoricTask == null ? Map.of() : runtimeTaskTraceQueryService.historicTaskLocalVariables(referenceHistoricTask.getId());
        Map<String, Object> nodeConfig = runtimeProcessMetadataService.resolveNodeConfig(processInstanceId, nodeId);
        String nodeFormKey = stringValue(nodeConfig.get("nodeFormKey"));
        String nodeFormVersion = stringValue(nodeConfig.get("nodeFormVersion"));
        List<WorkflowFieldBinding> fieldBindings = runtimeTaskDetailProjectionService.workflowFieldBindings(nodeConfig.get("fieldBindings"));
        List<ProcessInstanceEventResponse> instanceEvents = traceStore.queryInstanceEvents(processInstanceId);
        List<ProcessTaskTraceItemResponse> taskTrace = runtimeTaskTraceQueryService.buildTaskTrace(historicTasks, activeTasks);
        if (instanceEvents.isEmpty()) {
            instanceEvents = runtimeTaskTraceQueryService.buildSyntheticEvents(historicProcessInstance, taskTrace);
        }
        List<InclusiveGatewayHitResponse> inclusiveGatewayHits = runtimeInclusiveGatewayQueryService.buildInclusiveGatewayHits(
                processInstanceId,
                payload,
                historicProcessInstance,
                activeTasks
        );
        instanceEvents = runtimeTaskTraceQueryService.mergeInstanceEvents(
                instanceEvents,
                runtimeInclusiveGatewayQueryService.buildInclusiveGatewayEvents(processInstanceId, inclusiveGatewayHits)
        );
        List<CountersignTaskGroupResponse> countersignGroups = flowableCountersignService.queryTaskGroups(processInstanceId);
        taskTrace = applyTraceStatusOverrides(taskTrace, instanceEvents, countersignGroups);
        List<ProcessInstanceLinkResponse> resolvedProcessLinks = processLinks == null ? List.of() : List.copyOf(processLinks);
        List<RuntimeAppendLinkResponse> resolvedRuntimeAppendLinks = runtimeAppendLinks == null ? List.of() : List.copyOf(runtimeAppendLinks);
        Map<String, Object> processFormData = mapValue(variables.get("westflowFormData"));
        Map<String, Object> taskFormData = mapValue(variables.get("westflowTaskFormData"));
        String applicantUserId = stringValue(variables.get("westflowInitiatorUserId"));
        Map<String, String> userDisplayNames = runtimeTaskDetailProjectionService.buildUserDisplayNameMap(
                applicantUserId,
                referenceActiveTask,
                referenceHistoricTask,
                taskTrace,
                instanceEvents,
                resolvedRuntimeAppendLinks,
                countersignGroups,
                businessData,
                processFormData,
                taskFormData
        );
        Map<String, String> groupDisplayNames = runtimeTaskDetailProjectionService.buildGroupDisplayNameMap(
                referenceActiveTask != null ? runtimeTaskSupportService.candidateGroups(referenceActiveTask.getId()) : List.of(),
                taskTrace
        );
        OffsetDateTime createdAt = referenceActiveTask != null
                ? toOffsetDateTime(referenceActiveTask.getCreateTime())
                : referenceHistoricTask == null ? toOffsetDateTime(historicProcessInstance.getStartTime()) : toOffsetDateTime(referenceHistoricTask.getCreateTime());
        OffsetDateTime completedAt = referenceHistoricTask == null ? null : toOffsetDateTime(referenceHistoricTask.getEndTime());
        Long handleDurationSeconds = durationSeconds(createdAt, completedAt);
        String instanceStatus = runtimeTaskTraceQueryService.resolveInstanceStatus(historicProcessInstance, activeTasks);
        String detailTaskKind = resolveDetailTaskKind(processInstanceId, referenceActiveTask, referenceHistoricTask, nodeId);
        String detailTaskSemanticMode = resolveDetailTaskSemanticMode(processInstanceId, referenceActiveTask, referenceHistoricTask, nodeId);
        var prediction = runtimeProcessPredictionAiNarrationService.enhanceDetailPrediction(
                definition.processName(),
                businessType,
                nodeName,
                runtimeProcessPredictionService.predict(
                definition.processKey(),
                instanceStatus,
                nodeId,
                nodeName,
                detailTaskKind,
                detailTaskSemanticMode,
                resolveCurrentAction(variables),
                referenceActiveTask != null
                        ? runtimeTaskSupportService.resolveActingMode(referenceActiveTask, null)
                        : runtimeTaskSupportService.resolveActingMode(null, referenceHistoricTask),
                resolveActingForUserId(referenceActiveTask, referenceHistoricTask),
                resolveDelegatedByUserId(referenceActiveTask, referenceHistoricTask),
                resolveHandoverFromUserId(referenceActiveTask, referenceHistoricTask),
                referenceActiveTask != null ? referenceActiveTask.getAssignee() : referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee(),
                businessType,
                resolveOrganizationProfile(
                        stringValue(variables.get("westflowInitiatorDepartmentName")),
                        stringValue(variables.get("westflowInitiatorPostName"))
                ),
                createdAt,
                taskTrace,
                countersignGroups,
                payload.nodes(),
                payload.edges()
                )
        );
        prediction = runtimeProcessPredictionActionExecutorService.execute(
                processInstanceId,
                referenceActiveTask != null ? referenceActiveTask.getId() : referenceHistoricTask == null ? null : referenceHistoricTask.getId(),
                definition.processName(),
                nodeId,
                nodeName,
                referenceActiveTask != null ? referenceActiveTask.getAssignee() : referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee(),
                applicantUserId,
                prediction
        );
        runtimeProcessPredictionSnapshotService.recordSnapshot(
                processInstanceId,
                referenceActiveTask != null ? referenceActiveTask.getId() : referenceHistoricTask == null ? null : referenceHistoricTask.getId(),
                prediction
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
        return new ProcessTaskDetailResponse(
                referenceActiveTask != null ? referenceActiveTask.getId() : referenceHistoricTask == null ? null : referenceHistoricTask.getId(),
                processInstanceId,
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                applicantUserId,
                stringValue(variables.get("westflowInitiatorPostId")),
                stringValue(variables.get("westflowInitiatorPostName")),
                stringValue(variables.get("westflowInitiatorDepartmentId")),
                stringValue(variables.get("westflowInitiatorDepartmentName")),
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
                detailTaskKind,
                detailTaskSemanticMode,
                activeTasks.isEmpty() ? runtimeTaskSupportService.resolveHistoricTaskStatus(referenceHistoricTask, historicProcessInstance) : runtimeTaskSupportService.resolveTaskStatus(referenceActiveTask),
                runtimeTaskSupportService.resolveAssignmentMode(
                        referenceActiveTask == null ? List.of() : runtimeTaskSupportService.candidateUsers(referenceActiveTask.getId()),
                        referenceActiveTask == null ? List.of() : runtimeTaskSupportService.candidateGroups(referenceActiveTask.getId()),
                        referenceActiveTask != null ? referenceActiveTask.getAssignee() : referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee()
                ),
                referenceActiveTask != null ? runtimeTaskSupportService.candidateUsers(referenceActiveTask.getId()) : List.of(),
                referenceActiveTask != null ? runtimeTaskSupportService.candidateGroups(referenceActiveTask.getId()) : List.of(),
                referenceActiveTask != null ? referenceActiveTask.getAssignee() : referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee(),
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                stringValue(variables.get("westflowLastComment")),
                createdAt,
                runtimeTaskSupportService.readTimeValue(referenceTaskLocalVariables),
                createdAt,
                completedAt,
                handleDurationSeconds,
                null,
                null,
                null,
                null,
                referenceActiveTask != null
                        ? runtimeTaskSupportService.resolveActingMode(referenceActiveTask, null)
                        : runtimeTaskSupportService.resolveActingMode(null, referenceHistoricTask),
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
                processFormData,
                taskFormData,
                countersignGroups,
                inclusiveGatewayHits,
                resolvedProcessLinks,
                resolvedRuntimeAppendLinks,
                blockingActiveTasks.stream().map(Task::getId).toList(),
                prediction,
                userDisplayNames,
                groupDisplayNames
        );
    }

    public List<InclusiveGatewayHitResponse> inclusiveGatewayHits(String instanceId) {
        return runtimeInclusiveGatewayQueryService.inclusiveGatewayHits(instanceId);
    }

    private Task resolveReferenceActiveTask(Task activeTask, List<Task> blockingActiveTasks, List<Task> activeTasks) {
        if (activeTask != null) {
            return activeTask;
        }
        return blockingActiveTasks.stream()
                .filter(task -> "ADD_SIGN".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create())))
                .findFirst()
                .orElse(blockingActiveTasks.stream()
                        .filter(task -> !"APPEND".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create())))
                        .findFirst()
                        .orElse(blockingActiveTasks.stream().findFirst().orElse(activeTasks.stream().findFirst().orElse(null))));
    }

    private String resolveDetailTaskKind(
            String processInstanceId,
            Task referenceActiveTask,
            HistoricTaskInstance referenceHistoricTask,
            String nodeId
    ) {
        if (referenceActiveTask != null) {
            return runtimeTaskVisibilityService.resolveTaskKind(referenceActiveTask, RuntimeTaskQueryContext.create());
        }
        if (referenceHistoricTask != null) {
            return runtimeTaskTraceQueryService.historicTaskLocalVariables(referenceHistoricTask.getId())
                    .containsKey("westflowTaskKind")
                    ? stringValue(runtimeTaskTraceQueryService.historicTaskLocalVariables(referenceHistoricTask.getId()).get("westflowTaskKind"))
                    : runtimeTaskVisibilityService.resolveTaskKind(referenceHistoricTask.getProcessDefinitionId(), referenceHistoricTask.getTaskDefinitionKey(), RuntimeTaskQueryContext.create());
        }
        String engineProcessDefinitionId = runtimeProcessMetadataService.activeFlowableDefinitionId(processInstanceId);
        return runtimeTaskVisibilityService.resolveTaskKind(engineProcessDefinitionId, nodeId, RuntimeTaskQueryContext.create());
    }

    private List<ProcessTaskTraceItemResponse> applyTraceStatusOverrides(
            List<ProcessTaskTraceItemResponse> taskTrace,
            List<ProcessInstanceEventResponse> instanceEvents,
            List<CountersignTaskGroupResponse> countersignGroups
    ) {
        if (taskTrace.isEmpty()) {
            return taskTrace;
        }
        Set<String> revokedTaskIds = new HashSet<>();
        for (ProcessInstanceEventResponse event : instanceEvents) {
            if ("TASK_REMOVE_SIGN".equals(event.eventType())
                    && event.targetTaskId() != null
                    && !event.targetTaskId().isBlank()) {
                revokedTaskIds.add(event.targetTaskId());
            }
        }
        Map<String, String> countersignMemberStatusByTaskId = new LinkedHashMap<>();
        for (CountersignTaskGroupResponse group : countersignGroups) {
            group.members().forEach(member -> {
                if (member.taskId() != null && !member.taskId().isBlank()) {
                    countersignMemberStatusByTaskId.put(member.taskId(), member.memberStatus());
                }
            });
        }
        return taskTrace.stream()
                .map(item -> {
                    if (revokedTaskIds.contains(item.taskId())) {
                        return copyTraceItem(item, "REVOKED", true, item.isRejected(), item.isJumped(), item.isTakenBack());
                    }
                    String memberStatus = countersignMemberStatusByTaskId.get(item.taskId());
                    if ("AUTO_FINISHED".equals(memberStatus)) {
                        return copyTraceItem(item, "AUTO_FINISHED", item.isRevoked(), item.isRejected(), item.isJumped(), item.isTakenBack());
                    }
                    return item;
                })
                .toList();
    }

    private ProcessTaskTraceItemResponse copyTraceItem(
            ProcessTaskTraceItemResponse item,
            String status,
            boolean isRevoked,
            boolean isRejected,
            boolean isJumped,
            boolean isTakenBack
    ) {
        return new ProcessTaskTraceItemResponse(
                item.taskId(),
                item.nodeId(),
                item.nodeName(),
                item.taskKind(),
                item.taskSemanticMode(),
                status,
                item.assigneeUserId(),
                item.candidateUserIds(),
                item.candidateGroupIds(),
                item.action(),
                item.operatorUserId(),
                item.comment(),
                item.receiveTime(),
                item.readTime(),
                item.handleStartTime(),
                item.handleEndTime(),
                item.handleDurationSeconds(),
                item.sourceTaskId(),
                item.targetTaskId(),
                item.targetUserId(),
                item.isCcTask(),
                item.isAddSignTask(),
                isRevoked,
                isRejected,
                isJumped,
                isTakenBack,
                item.targetStrategy(),
                item.targetNodeId(),
                item.reapproveStrategy(),
                item.actingMode(),
                item.actingForUserId(),
                item.delegatedByUserId(),
                item.handoverFromUserId(),
                item.slaMetadata()
        );
    }

    private String resolveDetailTaskSemanticMode(
            String processInstanceId,
            Task referenceActiveTask,
            HistoricTaskInstance referenceHistoricTask,
            String nodeId
    ) {
        if (referenceActiveTask != null) {
            return runtimeTaskSupportService.resolveTaskSemanticMode(referenceActiveTask);
        }
        if (referenceHistoricTask != null) {
            return runtimeTaskSupportService.resolveHistoricTaskSemanticMode(referenceHistoricTask);
        }
        return runtimeTaskSupportService.resolveTaskSemanticMode(processInstanceId, nodeId);
    }

    private String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? runtimeTaskTraceQueryService.ensureReadTimeAndReturnLocalVariables(activeTask)
                : historicTask == null ? Map.of() : runtimeTaskTraceQueryService.historicTaskLocalVariables(historicTask.getId());
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
                ? runtimeTaskTraceQueryService.ensureReadTimeAndReturnLocalVariables(activeTask)
                : historicTask == null ? Map.of() : runtimeTaskTraceQueryService.historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowDelegatedByUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        return resolveActingForUserId(activeTask, historicTask);
    }

    private String resolveHandoverFromUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? runtimeTaskTraceQueryService.ensureReadTimeAndReturnLocalVariables(activeTask)
                : historicTask == null ? Map.of() : runtimeTaskTraceQueryService.historicTaskLocalVariables(historicTask.getId());
        return stringValue(localVariables.get("westflowHandoverFromUserId"));
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

    private String resolveCurrentAction(Map<String, Object> variables) {
        String currentAction = stringValue(variables.get("westflowAction"));
        if (currentAction != null) {
            return currentAction;
        }
        return stringValue(variables.get("westflowLastAction"));
    }

    private String resolveOrganizationProfile(String departmentName, String postName) {
        if (departmentName == null || departmentName.isBlank()) {
            return postName == null || postName.isBlank() ? null : postName;
        }
        if (postName == null || postName.isBlank()) {
            return departmentName;
        }
        return departmentName + " / " + postName;
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), java.time.ZoneId.of("Asia/Shanghai"));
    }

    private Long durationSeconds(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return java.time.Duration.between(start, end).getSeconds();
    }

    private boolean isVisibleBlockingTask(Task task) {
        return runtimeTaskVisibilityService.isVisibleTask(
                task,
                RuntimeTaskQueryContext.create(),
                runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
        ) && !"CC".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create()));
    }

    private ContractException resourceNotFound(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                details
        );
    }
}
