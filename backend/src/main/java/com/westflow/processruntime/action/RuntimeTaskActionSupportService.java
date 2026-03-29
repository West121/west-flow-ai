package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.query.RuntimeTaskQueryContext;
import com.westflow.processruntime.query.RuntimeTaskSupportService;
import com.westflow.processruntime.query.RuntimeTaskVisibilityService;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskActionSupportService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionCoreSupportService coreSupportService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;

    public Task requireActiveTask(String taskId) {
        Task task = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw coreSupportService.taskNotFound(taskId);
        }
        return task;
    }

    public Map<String, Object> taskLocalVariables(String taskId) {
        return taskLocalVariables(taskId, RuntimeTaskQueryContext.create().taskLocalVariablesByTaskId());
    }

    public Map<String, Object> taskLocalVariables(String taskId, Map<String, Map<String, Object>> taskLocalVariablesByTaskId) {
        return runtimeTaskVisibilityService.taskLocalVariables(
                taskId,
                RuntimeTaskQueryContext.of(
                        taskLocalVariablesByTaskId,
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>()
                )
        );
    }

    public Map<String, Object> historicTaskLocalVariables(String taskId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .taskId(taskId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    public String resolveTaskKind(Task task) {
        return resolveTaskKind(task, new HashMap<>(), new HashMap<>());
    }

    public String resolveTaskKind(
            Task task,
            Map<String, Map<String, Object>> taskLocalVariablesByTaskId,
            Map<String, String> taskKindByNodeKey
    ) {
        return runtimeTaskVisibilityService.resolveTaskKind(
                task,
                RuntimeTaskQueryContext.of(
                        taskLocalVariablesByTaskId,
                        taskKindByNodeKey,
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>()
                )
        );
    }

    public String resolveTaskKind(String engineProcessDefinitionId, String nodeId) {
        return resolveTaskKind(engineProcessDefinitionId, nodeId, new HashMap<>());
    }

    public String resolveTaskKind(String engineProcessDefinitionId, String nodeId, Map<String, String> taskKindByNodeKey) {
        return runtimeTaskVisibilityService.resolveTaskKind(
                engineProcessDefinitionId,
                nodeId,
                RuntimeTaskQueryContext.of(
                        new HashMap<>(),
                        taskKindByNodeKey,
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>()
                )
        );
    }

    public String resolveHistoricTaskKind(HistoricTaskInstance task) {
        String historicTaskKind = coreSupportService.stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (historicTaskKind != null) {
            return historicTaskKind;
        }
        return resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
    }

    public boolean isVisibleTask(Task task) {
        return isVisibleTask(task, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public boolean isVisibleTask(
            Task task,
            Map<String, Map<String, Object>> taskLocalVariablesByTaskId,
            Map<String, String> taskKindByNodeKey,
            Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId,
            Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey,
            Map<String, Boolean> sourceTaskCompletedByTaskId
    ) {
        return runtimeTaskVisibilityService.isVisibleTask(
                task,
                RuntimeTaskQueryContext.of(
                        taskLocalVariablesByTaskId,
                        taskKindByNodeKey,
                        appendLinksByInstanceId,
                        blockingDynamicBuilderNodeIdsByTargetKey,
                        sourceTaskCompletedByTaskId
                ),
                runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
        );
    }

    public boolean isVisibleTask(
            Task task,
            String taskKind,
            Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId,
            Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey,
            Map<String, Boolean> sourceTaskCompletedByTaskId
    ) {
        return runtimeTaskVisibilityService.isVisibleTask(
                task,
                taskKind,
                RuntimeTaskQueryContext.of(
                        new HashMap<>(),
                        new HashMap<>(),
                        appendLinksByInstanceId,
                        blockingDynamicBuilderNodeIdsByTargetKey,
                        sourceTaskCompletedByTaskId
                ),
                runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
        );
    }

    public boolean isBlockedByPendingAppendStructures(Task task) {
        return isBlockedByPendingAppendStructures(task, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public boolean isBlockedByPendingAppendStructures(
            Task task,
            Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId,
            Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey,
            Map<String, Boolean> sourceTaskCompletedByTaskId
    ) {
        return !runtimeTaskVisibilityService.isVisibleTask(
                task,
                "NORMAL",
                RuntimeTaskQueryContext.of(
                        new HashMap<>(),
                        new HashMap<>(),
                        appendLinksByInstanceId,
                        blockingDynamicBuilderNodeIdsByTargetKey,
                        sourceTaskCompletedByTaskId
                ),
                runtimeProcessMetadataService::resolvePublishedDefinitionByInstance
        );
    }

    public boolean hasRunningAppendStructures(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        return runtimeAppendLinkService.listByParentInstanceId(processInstanceId).stream()
                .anyMatch(link -> "RUNNING".equals(link.status()));
    }

    public boolean hasActiveAddSignChild(String taskId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .active()
                .list()
                .stream()
                .anyMatch(task -> taskId.equals(task.getParentTaskId()) && "ADD_SIGN".equals(resolveTaskKind(task)));
    }

    public List<ProcessTaskSnapshot> blockingTaskViews(List<ProcessTaskSnapshot> nextTasks) {
        return nextTasks.stream()
                .filter(task -> !"CC".equals(task.taskKind()))
                .toList();
    }

    public boolean hasInitiatorPermission(Task task) {
        if (task == null) {
            return false;
        }
        String processInstanceId = task.getProcessInstanceId();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        String initiatorUserId = coreSupportService.stringValue(runtimeProcessMetadataService.runtimeVariables(processInstanceId).get("westflowInitiatorUserId"));
        return coreSupportService.currentUserId().equals(initiatorUserId);
    }

    public String resolveCurrentTaskOwner(Task task) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            return task.getAssignee();
        }
        List<String> candidates = runtimeTaskSupportService.candidateUsers(task.getId());
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public HistoricTaskInstance requirePreviousUserTask(Task task) {
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
                .orElseThrow(() -> coreSupportService.actionNotAllowed(
                        "当前任务不存在可退回的上一步人工节点",
                        Map.of("taskId", task.getId(), "processInstanceId", task.getProcessInstanceId())
                ));
    }

    public List<String> candidateUsers(String taskId) {
        return runtimeTaskSupportService.candidateUsers(taskId);
    }

    public List<String> candidateGroups(String taskId) {
        return runtimeTaskSupportService.candidateGroups(taskId);
    }

    public boolean isCurrentUserCandidate(Task task, List<String> candidateUserIds, List<String> candidateGroupIds) {
        return runtimeTaskSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
    }

    public Task claimTaskIfNeeded(Task task, String assigneeUserId) {
        if (task.getAssignee() == null) {
            flowableEngineFacade.taskService().claim(task.getId(), assigneeUserId);
            return requireActiveTask(task.getId());
        }
        return task;
    }

    public Map<String, Object> ensureReadTimeAndReturnLocalVariables(Task task) {
        if (task == null) {
            return Map.of();
        }
        Map<String, Object> localVariables = taskLocalVariables(task.getId());
        if (!"NORMAL".equals(resolveTaskKind(task))) {
            return localVariables;
        }
        if (task.getAssignee() == null || !coreSupportService.currentUserId().equals(task.getAssignee())) {
            return localVariables;
        }
        if (runtimeTaskSupportService.readTimeValue(localVariables) != null) {
            return localVariables;
        }
        flowableEngineFacade.taskService()
                .setVariableLocal(task.getId(), "westflowReadTime", Timestamp.from(Instant.now()));
        return taskLocalVariables(task.getId());
    }

    public List<Task> activeTasksAssignedTo(String assigneeUserId) {
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

    public Map<String, Object> actionVariables(
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

    public void appendComment(Task task, String comment) {
        if (comment != null && !comment.isBlank()) {
            flowableEngineFacade.taskService().addComment(task.getId(), task.getProcessInstanceId(), comment.trim());
        }
    }

    public String requireTargetNode(String processDefinitionId, String targetNodeId) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw coreSupportService.actionNotAllowed(
                    "目标节点不能为空",
                    Map.of("targetNodeId", targetNodeId)
            );
        }
        var model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || model.getFlowElement(targetNodeId) == null) {
            throw coreSupportService.actionNotAllowed(
                    "目标节点不存在",
                    Map.of("targetNodeId", targetNodeId)
            );
        }
        return targetNodeId;
    }

    public String resolveNodeName(String processDefinitionId, String targetNodeId) {
        var model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || model.getFlowElement(targetNodeId) == null) {
            return targetNodeId;
        }
        String name = model.getFlowElement(targetNodeId).getName();
        return name == null || name.isBlank() ? targetNodeId : name;
    }

    public CompleteTaskResponse nextTaskResponse(String processInstanceId, String completedTaskId) {
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

    public String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = coreSupportService.stringValue(localVariables.get("westflowActingForUserId"));
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

    public String resolveDelegatedByUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = coreSupportService.stringValue(localVariables.get("westflowDelegatedByUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        return resolveActingForUserId(activeTask, historicTask);
    }

    public ProcessTaskSnapshot toTaskView(Task task) {
        return new ProcessTaskSnapshot(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                resolveTaskKind(task),
                runtimeTaskSupportService.resolveTaskStatus(task),
                runtimeTaskSupportService.resolveAssignmentMode(
                        runtimeTaskSupportService.candidateUsers(task.getId()),
                        runtimeTaskSupportService.candidateGroups(task.getId()),
                        task.getAssignee()
                ),
                runtimeTaskSupportService.candidateUsers(task.getId()),
                runtimeTaskSupportService.candidateGroups(task.getId()),
                task.getAssignee(),
                runtimeTaskSupportService.resolveActingMode(task, null),
                resolveActingForUserId(task, null),
                resolveDelegatedByUserId(task, null),
                null
        );
    }

    public OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    public Long durationSeconds(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }

    public boolean isHistoricTaskRevoked(HistoricTaskInstance task) {
        return task.getDeleteReason() != null && !task.getDeleteReason().isBlank();
    }

    public boolean isHistoricTaskAutoFinished(HistoricTaskInstance task) {
        if (task == null || task.getDeleteReason() == null || task.getDeleteReason().isBlank()) {
            return false;
        }
        if (!"MI_END".equals(task.getDeleteReason())) {
            return false;
        }
        String approvalMode = countersignApprovalMode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        return "OR_SIGN".equals(approvalMode) || "VOTE".equals(approvalMode);
    }

    public String countersignApprovalMode(String processDefinitionId, String nodeId) {
        if (processDefinitionId == null || nodeId == null) {
            return null;
        }
        var model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
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
}
