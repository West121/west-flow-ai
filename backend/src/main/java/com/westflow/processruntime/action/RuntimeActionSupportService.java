package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.HandoverExecutionTaskItemResponse;
import com.westflow.processruntime.api.response.HandoverPreviewTaskItemResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeActionSupportService {

    private final RuntimeActionCoreSupportService coreSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;

    public String currentUserId() {
        return coreSupportService.currentUserId();
    }

    public String normalizeUserId(String userId) {
        return coreSupportService.normalizeUserId(userId);
    }

    public String stringValue(Object value) {
        return coreSupportService.stringValue(value);
    }

    public String stringValueOrDefault(Object value, String defaultValue) {
        return coreSupportService.stringValueOrDefault(value, defaultValue);
    }

    public ContractException taskNotFound(String taskId) {
        return coreSupportService.taskNotFound(taskId);
    }

    public ContractException resourceNotFound(String message, Map<String, Object> details) {
        return coreSupportService.resourceNotFound(message, details);
    }

    public ContractException actionNotAllowed(String message, Map<String, Object> details) {
        return coreSupportService.actionNotAllowed(message, details);
    }

    public String requireTargetUserId(String targetUserId, String taskId) {
        return coreSupportService.requireTargetUserId(targetUserId, taskId);
    }

    public String normalizeTargetUserId(String userId, String fieldName) {
        return coreSupportService.normalizeTargetUserId(userId, fieldName);
    }

    public List<String> normalizeTargetUserIds(List<String> targetUserIds, String taskId) {
        return coreSupportService.normalizeTargetUserIds(targetUserIds, taskId);
    }

    public String normalizeAppendPolicy(String appendPolicy) {
        return coreSupportService.normalizeAppendPolicy(appendPolicy);
    }

    public String normalizeVersionPolicy(String versionPolicy) {
        return coreSupportService.normalizeVersionPolicy(versionPolicy);
    }

    public String normalizeCalledProcessKey(String calledProcessKey, String taskId) {
        return coreSupportService.normalizeCalledProcessKey(calledProcessKey, taskId);
    }

    public Task requireActiveTask(String taskId) {
        return taskActionSupportService.requireActiveTask(taskId);
    }

    public HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
        return processActionSupportService.requireHistoricProcessInstance(processInstanceId);
    }

    public ProcessInstance requireRunningInstance(String instanceId) {
        return processActionSupportService.requireRunningInstance(instanceId);
    }

    public Integer integerValue(Object value) {
        return processActionSupportService.integerValue(value);
    }

    public Map<String, Object> runtimeVariables(String processInstanceId) {
        return processActionSupportService.runtimeVariables(processInstanceId);
    }

    public Map<String, Object> resolveNodeConfig(String processInstanceId, String nodeId) {
        return processActionSupportService.resolveNodeConfig(processInstanceId, nodeId);
    }

    public Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        return processActionSupportService.runtimeOrHistoricVariables(processInstanceId);
    }

    public Map<String, Object> historicVariables(String processInstanceId) {
        return processActionSupportService.historicVariables(processInstanceId);
    }

    public String activeFlowableDefinitionId(String processInstanceId) {
        return processActionSupportService.activeFlowableDefinitionId(processInstanceId);
    }

    public String activeProcessKey(String processInstanceId) {
        return processActionSupportService.activeProcessKey(processInstanceId);
    }

    public Map<String, Object> taskLocalVariables(String taskId) {
        return taskActionSupportService.taskLocalVariables(taskId);
    }

    public Map<String, Object> taskLocalVariables(String taskId, Map<String, Map<String, Object>> taskLocalVariablesByTaskId) {
        return taskActionSupportService.taskLocalVariables(taskId, taskLocalVariablesByTaskId);
    }

    public Map<String, Object> historicTaskLocalVariables(String taskId) {
        return taskActionSupportService.historicTaskLocalVariables(taskId);
    }

    public String resolveTaskKind(Task task) {
        return taskActionSupportService.resolveTaskKind(task);
    }

    public String resolveTaskKind(Task task, Map<String, Map<String, Object>> taskLocalVariablesByTaskId, Map<String, String> taskKindByNodeKey) {
        return taskActionSupportService.resolveTaskKind(task, taskLocalVariablesByTaskId, taskKindByNodeKey);
    }

    public String resolveTaskKind(String engineProcessDefinitionId, String nodeId) {
        return taskActionSupportService.resolveTaskKind(engineProcessDefinitionId, nodeId);
    }

    public String resolveTaskKind(String engineProcessDefinitionId, String nodeId, Map<String, String> taskKindByNodeKey) {
        return taskActionSupportService.resolveTaskKind(engineProcessDefinitionId, nodeId, taskKindByNodeKey);
    }

    public String resolveHistoricTaskKind(HistoricTaskInstance task) {
        return taskActionSupportService.resolveHistoricTaskKind(task);
    }

    public boolean isVisibleTask(Task task) {
        return taskActionSupportService.isVisibleTask(task);
    }

    public boolean isVisibleTask(Task task, Map<String, Map<String, Object>> taskLocalVariablesByTaskId, Map<String, String> taskKindByNodeKey, Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId, Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey, Map<String, Boolean> sourceTaskCompletedByTaskId) {
        return taskActionSupportService.isVisibleTask(task, taskLocalVariablesByTaskId, taskKindByNodeKey, appendLinksByInstanceId, blockingDynamicBuilderNodeIdsByTargetKey, sourceTaskCompletedByTaskId);
    }

    public boolean isVisibleTask(Task task, String taskKind, Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId, Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey, Map<String, Boolean> sourceTaskCompletedByTaskId) {
        return taskActionSupportService.isVisibleTask(task, taskKind, appendLinksByInstanceId, blockingDynamicBuilderNodeIdsByTargetKey, sourceTaskCompletedByTaskId);
    }

    public boolean isBlockedByPendingAppendStructures(Task task) {
        return taskActionSupportService.isBlockedByPendingAppendStructures(task);
    }

    public boolean isBlockedByPendingAppendStructures(Task task, Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId, Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey, Map<String, Boolean> sourceTaskCompletedByTaskId) {
        return taskActionSupportService.isBlockedByPendingAppendStructures(task, appendLinksByInstanceId, blockingDynamicBuilderNodeIdsByTargetKey, sourceTaskCompletedByTaskId);
    }

    public boolean hasRunningAppendStructures(String processInstanceId) {
        return taskActionSupportService.hasRunningAppendStructures(processInstanceId);
    }

    public boolean hasActiveAddSignChild(String taskId) {
        return taskActionSupportService.hasActiveAddSignChild(taskId);
    }

    public List<ProcessTaskSnapshot> blockingTaskViews(List<ProcessTaskSnapshot> nextTasks) {
        return taskActionSupportService.blockingTaskViews(nextTasks);
    }

    public boolean hasInitiatorPermission(Task task) {
        return taskActionSupportService.hasInitiatorPermission(task);
    }

    public String resolveCurrentTaskOwner(Task task) {
        return taskActionSupportService.resolveCurrentTaskOwner(task);
    }

    public HistoricTaskInstance requirePreviousUserTask(Task task) {
        return taskActionSupportService.requirePreviousUserTask(task);
    }

    public List<String> candidateUsers(String taskId) {
        return taskActionSupportService.candidateUsers(taskId);
    }

    public List<String> candidateGroups(String taskId) {
        return taskActionSupportService.candidateGroups(taskId);
    }

    public boolean isCurrentUserCandidate(Task task, List<String> candidateUserIds, List<String> candidateGroupIds) {
        return taskActionSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
    }

    public Task claimTaskIfNeeded(Task task, String assigneeUserId) {
        return taskActionSupportService.claimTaskIfNeeded(task, assigneeUserId);
    }

    public Map<String, Object> ensureReadTimeAndReturnLocalVariables(Task task) {
        return taskActionSupportService.ensureReadTimeAndReturnLocalVariables(task);
    }

    public List<Task> activeTasksAssignedTo(String assigneeUserId) {
        return taskActionSupportService.activeTasksAssignedTo(assigneeUserId);
    }

    public Map<String, Object> actionVariables(String action, String operatorUserId, String comment, Map<String, Object> taskFormData) {
        return taskActionSupportService.actionVariables(action, operatorUserId, comment, taskFormData);
    }

    public void appendComment(Task task, String comment) {
        taskActionSupportService.appendComment(task, comment);
    }

    public String requireTargetNode(String processDefinitionId, String targetNodeId) {
        return taskActionSupportService.requireTargetNode(processDefinitionId, targetNodeId);
    }

    public String resolveNodeName(String processDefinitionId, String targetNodeId) {
        return taskActionSupportService.resolveNodeName(processDefinitionId, targetNodeId);
    }

    public CompleteTaskResponse nextTaskResponse(String processInstanceId, String completedTaskId) {
        return taskActionSupportService.nextTaskResponse(processInstanceId, completedTaskId);
    }

    public void appendInstanceEvent(String instanceId, String taskId, String nodeId, String eventType, String eventName, String actionCategory, String sourceTaskId, String targetTaskId, String targetUserId, Map<String, Object> details, String targetStrategy, String targetNodeId, String reapproveStrategy, String actingMode, String actingForUserId, String delegatedByUserId, String handoverFromUserId) {
        processActionSupportService.appendInstanceEvent(instanceId, taskId, nodeId, eventType, eventName, actionCategory, sourceTaskId, targetTaskId, targetUserId, details, targetStrategy, targetNodeId, reapproveStrategy, actingMode, actingForUserId, delegatedByUserId, handoverFromUserId);
    }

    public void appendInstanceEvent(String instanceId, String taskId, String nodeId, String eventType, String eventName, String actionCategory, String sourceTaskId, String targetTaskId, String targetUserId, Map<String, Object> details, String targetStrategy, String targetNodeId, String reapproveStrategy, String actingMode, String actingForUserId, String delegatedByUserId, String handoverFromUserId, String operatorUserId) {
        processActionSupportService.appendInstanceEvent(instanceId, taskId, nodeId, eventType, eventName, actionCategory, sourceTaskId, targetTaskId, targetUserId, details, targetStrategy, targetNodeId, reapproveStrategy, actingMode, actingForUserId, delegatedByUserId, handoverFromUserId, operatorUserId);
    }

    public Map<String, Object> eventDetails(Object... keyValues) {
        return processActionSupportService.eventDetails(keyValues);
    }

    public String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        return taskActionSupportService.resolveActingForUserId(activeTask, historicTask);
    }

    public String resolveDelegatedByUserId(Task activeTask, HistoricTaskInstance historicTask) {
        return taskActionSupportService.resolveDelegatedByUserId(activeTask, historicTask);
    }

    public ProcessTaskSnapshot toTaskView(Task task) {
        return taskActionSupportService.toTaskView(task);
    }

    public HandoverPreviewTaskItemResponse toHandoverPreviewTask(Task task, String sourceUserId) {
        return processActionSupportService.toHandoverPreviewTask(task, sourceUserId);
    }

    public HandoverExecutionTaskItemResponse toHandoverExecutionTask(Task sourceTask, Task updatedTask, String sourceUserId, String targetUserId, String comment) {
        return processActionSupportService.toHandoverExecutionTask(sourceTask, updatedTask, sourceUserId, targetUserId, comment);
    }

    public String resolveUserDisplayName(String userId) {
        return processActionSupportService.resolveUserDisplayName(userId);
    }

    public String resolveGroupDisplayName(String groupId) {
        return processActionSupportService.resolveGroupDisplayName(groupId);
    }

    public void updateBusinessProcessLink(String businessType, String businessId, String processInstanceId, String status) {
        processActionSupportService.updateBusinessProcessLink(businessType, businessId, processInstanceId, status);
    }

    public String resolveBusinessTitle(String businessType, Map<String, Object> businessData) {
        return processActionSupportService.resolveBusinessTitle(businessType, businessData);
    }

    public OffsetDateTime toOffsetDateTime(java.util.Date date) {
        return taskActionSupportService.toOffsetDateTime(date);
    }

    public Long durationSeconds(OffsetDateTime start, OffsetDateTime end) {
        return taskActionSupportService.durationSeconds(start, end);
    }

    public boolean isHistoricTaskRevoked(HistoricTaskInstance task) {
        return taskActionSupportService.isHistoricTaskRevoked(task);
    }

    public boolean isHistoricTaskAutoFinished(HistoricTaskInstance task) {
        return taskActionSupportService.isHistoricTaskAutoFinished(task);
    }

    public String countersignApprovalMode(String processDefinitionId, String nodeId) {
        return taskActionSupportService.countersignApprovalMode(processDefinitionId, nodeId);
    }

    public String resolveProcessInstanceIdByBusinessKey(String businessId) {
        return processActionSupportService.resolveProcessInstanceIdByBusinessKey(businessId);
    }

    public String resolveStartBusinessType(StartProcessRequest request) {
        return processActionSupportService.resolveStartBusinessType(request);
    }

    public void revokeProcessInstanceQuietly(FlowableTaskActionService flowableTaskActionService, String processInstanceId, String reason) {
        processActionSupportService.revokeProcessInstanceQuietly(flowableTaskActionService, processInstanceId, reason);
    }

    public HistoricTaskInstance requireHistoricTaskSource(String instanceId, String sourceTaskId) {
        return processActionSupportService.requireHistoricTaskSource(instanceId, sourceTaskId);
    }

    public ProcessInstance startHistoricProcessInstance(String processKey, String businessKey, Map<String, Object> variables) {
        return processActionSupportService.startHistoricProcessInstance(processKey, businessKey, variables);
    }

    public Task firstActiveTask(String processInstanceId) {
        return processActionSupportService.firstActiveTask(processInstanceId);
    }

    public void transferMatchingTasks(String processInstanceId, HistoricTaskInstance sourceTask) {
        processActionSupportService.transferMatchingTasks(processInstanceId, sourceTask);
    }

    public void copyBusinessLinkOnWakeUp(String sourceInstanceId, String targetInstanceId, String platformProcessDefinitionId) {
        processActionSupportService.copyBusinessLinkOnWakeUp(sourceInstanceId, targetInstanceId, platformProcessDefinitionId);
    }
}
