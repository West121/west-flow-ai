package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.request.AddSignTaskRequest;
import com.westflow.processruntime.api.request.AppendTaskRequest;
import com.westflow.processruntime.api.request.RemoveSignTaskRequest;
import com.westflow.processruntime.api.response.AppendTaskResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.query.RuntimeProcessLinkQueryService;
import com.westflow.processruntime.service.append.DynamicBuildAppendRuntimeService;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeAppendActionService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final RuntimeProcessLinkQueryService runtimeProcessLinkQueryService;
    private final DynamicBuildAppendRuntimeService dynamicBuildAppendRuntimeService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final ProcessDefinitionService processDefinitionService;

    public CompleteTaskResponse addSign(String taskId, AddSignTaskRequest request) {
        Task task = requireTaskForAction(taskId, "加签");
        if (!"NORMAL".equals(taskActionSupportService.resolveTaskKind(task))) {
            throw actionSupportService.actionNotAllowed("当前任务不支持加签", Map.of("taskId", taskId));
        }
        if (taskActionSupportService.hasActiveAddSignChild(taskId)) {
            throw actionSupportService.actionNotAllowed("当前任务存在未完成的加签任务", Map.of("taskId", taskId));
        }

        String targetUserId = actionSupportService.requireTargetUserId(request.targetUserId(), taskId);
        taskActionSupportService.appendComment(task, request.comment());
        Task addSignTask = flowableTaskActionService.createAdhocTask(
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                "ADD_SIGN",
                targetUserId,
                List.of(targetUserId),
                task.getId(),
                processActionSupportService.eventDetails(
                        "westflowTaskKind", "ADD_SIGN",
                        "westflowSourceTaskId", task.getId(),
                        "westflowTargetUserId", targetUserId
                )
        );
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                addSignTask.getId(),
                addSignTask.getTaskDefinitionKey(),
                "TASK_ADD_SIGN",
                "任务已加签",
                "TASK",
                task.getId(),
                addSignTask.getId(),
                targetUserId,
                processActionSupportService.eventDetails(
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
        return new CompleteTaskResponse(
                task.getProcessInstanceId(),
                taskId,
                "RUNNING",
                List.of(taskActionSupportService.toTaskView(addSignTask))
        );
    }

    @Transactional
    public AppendTaskResponse appendTask(String taskId, AppendTaskRequest request) {
        Task sourceTask = requireTaskForAppend(taskId);
        if (!"NORMAL".equals(taskActionSupportService.resolveTaskKind(sourceTask))) {
            throw actionSupportService.actionNotAllowed("当前任务不支持追加", Map.of("taskId", taskId));
        }
        Map<String, Object> parentVariables = processActionSupportService.runtimeVariables(sourceTask.getProcessInstanceId());
        List<String> targetUserIds = actionSupportService.normalizeTargetUserIds(request.targetUserIds(), taskId);
        String appendPolicy = actionSupportService.normalizeAppendPolicy(request.appendPolicy());
        String sourceNodeId = sourceTask.getTaskDefinitionKey();
        String sourceNodeName = sourceTask.getName();
        String targetUserId = targetUserIds.get(0);
        String comment = request.comment();
        taskActionSupportService.appendComment(sourceTask, comment);
        String appendLinkId = UUID.randomUUID().toString();

        Map<String, Object> localVariables = new LinkedHashMap<>();
        if (request.appendVariables() != null && !request.appendVariables().isEmpty()) {
            localVariables.putAll(request.appendVariables());
        }
        localVariables.putAll(processActionSupportService.eventDetails(
                "westflowTaskKind", "APPEND",
                "westflowAppendType", "TASK",
                "westflowSourceTaskId", sourceTask.getId(),
                "westflowSourceNodeId", sourceNodeId,
                "westflowAppendPolicy", appendPolicy,
                "westflowTriggerMode", "APPEND",
                "westflowTargetUserId", targetUserId,
                "westflowOperatorUserId", actionSupportService.currentUserId()
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
                runtimeProcessMetadataService.resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()),
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
                null,
                null,
                "USER",
                actionSupportService.stringValue(parentVariables.get("westflowBusinessType")),
                null,
                "RUNNING",
                "APPEND",
                actionSupportService.currentUserId(),
                comment,
                Instant.now(),
                null
        );
        runtimeAppendLinkService.createLink(appendLink);
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendLinkId", appendLink.id());
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendType", "TASK");
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowAppendPolicy", appendPolicy);
        flowableEngineFacade.taskService().setVariableLocal(appendedTask.getId(), "westflowTaskKind", "APPEND");
        processActionSupportService.appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                appendedTask.getId(),
                appendedTask.getTaskDefinitionKey(),
                "TASK_APPENDED",
                "任务已追加",
                "TASK",
                sourceTask.getId(),
                appendedTask.getId(),
                targetUserId,
                processActionSupportService.eventDetails(
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
                runtimeProcessLinkQueryService.activeAppendTasks(sourceTask.getProcessInstanceId()),
                runtimeProcessLinkQueryService.appendLinks(sourceTask.getProcessInstanceId(), actionSupportService.currentUserId())
        );
    }

    @Transactional
    public AppendTaskResponse appendSubprocess(String taskId, AppendTaskRequest request) {
        Task sourceTask = requireTaskForAppend(taskId);
        if (!"NORMAL".equals(taskActionSupportService.resolveTaskKind(sourceTask))) {
            throw actionSupportService.actionNotAllowed("当前任务不支持追加", Map.of("taskId", taskId));
        }
        String calledProcessKey = actionSupportService.normalizeCalledProcessKey(request.calledProcessKey(), taskId);
        String appendPolicy = actionSupportService.normalizeAppendPolicy(request.appendPolicy());
        String versionPolicy = actionSupportService.normalizeVersionPolicy(request.calledVersionPolicy());
        PublishedProcessDefinition definition = "FIXED_VERSION".equals(versionPolicy)
                ? processDefinitionService.getPublishedByProcessKeyAndVersion(calledProcessKey, request.calledVersion())
                : processDefinitionService.getLatestByProcessKey(calledProcessKey);
        String sourceNodeId = sourceTask.getTaskDefinitionKey();
        String comment = request.comment();
        taskActionSupportService.appendComment(sourceTask, comment);
        Map<String, Object> parentVariables = processActionSupportService.runtimeVariables(sourceTask.getProcessInstanceId());

        Map<String, Object> variables = new LinkedHashMap<>();
        String businessKey = actionSupportService.stringValue(parentVariables.get("westflowBusinessKey"));
        variables.put("westflowProcessDefinitionId", definition.processDefinitionId());
        variables.put("westflowProcessKey", definition.processKey());
        variables.put("westflowProcessName", definition.processName());
        variables.put("westflowBusinessType", actionSupportService.stringValue(parentVariables.get("westflowBusinessType")));
        variables.put("westflowBusinessKey", businessKey);
        variables.put("westflowInitiatorUserId", actionSupportService.stringValue(parentVariables.get("westflowInitiatorUserId")));
        variables.put("westflowParentInstanceId", sourceTask.getProcessInstanceId());
        variables.put("westflowRootInstanceId", runtimeProcessMetadataService.resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()));
        variables.put("westflowAppendType", "SUBPROCESS");
        variables.put("westflowAppendPolicy", appendPolicy);
        variables.put("westflowAppendTriggerMode", "APPEND");
        variables.put("westflowAppendSourceTaskId", sourceTask.getId());
        variables.put("westflowAppendSourceNodeId", sourceNodeId);
        variables.put("westflowAppendOperatorUserId", actionSupportService.currentUserId());
        if (request.appendVariables() != null && !request.appendVariables().isEmpty()) {
            variables.putAll(request.appendVariables());
        }
        String childRuntimeBusinessKey = buildAppendSubprocessRuntimeBusinessKey(businessKey, sourceTask.getId());
        ProcessInstance childInstance = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey(definition.processKey(), childRuntimeBusinessKey, variables);
        RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                UUID.randomUUID().toString(),
                runtimeProcessMetadataService.resolveRuntimeTreeRootInstanceId(sourceTask.getProcessInstanceId()),
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
                versionPolicy,
                "FIXED_VERSION".equals(versionPolicy) ? request.calledVersion() : null,
                "PROCESS_KEY",
                actionSupportService.stringValue(parentVariables.get("westflowBusinessType")),
                null,
                "RUNNING",
                "APPEND",
                actionSupportService.currentUserId(),
                comment,
                Instant.now(),
                null
        );
        runtimeAppendLinkService.createLink(appendLink);
        processActionSupportService.appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                null,
                sourceNodeId,
                "SUBPROCESS_APPENDED",
                "子流程已追加",
                "INSTANCE",
                sourceTask.getId(),
                null,
                null,
                processActionSupportService.eventDetails(
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
                runtimeProcessLinkQueryService.activeAppendTasks(sourceTask.getProcessInstanceId()),
                runtimeProcessLinkQueryService.appendLinks(sourceTask.getProcessInstanceId(), actionSupportService.currentUserId())
        );
    }

    @Transactional
    public void executeDynamicBuilder(String processInstanceId, String sourceNodeId) {
        dynamicBuildAppendRuntimeService.executeDynamicBuilder(processInstanceId, sourceNodeId);
    }

    public CompleteTaskResponse removeSign(String taskId, RemoveSignTaskRequest request) {
        Task sourceTask = requireTaskForAction(taskId, "减签");
        Task addSignTask = actionSupportService.requireActiveTask(request.targetTaskId());
        if (!"ADD_SIGN".equals(actionSupportService.resolveTaskKind(addSignTask)) || !taskId.equals(addSignTask.getParentTaskId())) {
            throw actionSupportService.actionNotAllowed("目标任务不是当前节点的加签任务", Map.of("taskId", taskId, "targetTaskId", request.targetTaskId()));
        }

        actionSupportService.appendComment(sourceTask, request.comment());
        flowableTaskActionService.deleteTask(addSignTask.getId(), "WESTFLOW_REMOVE_SIGN");
        actionSupportService.appendInstanceEvent(
                sourceTask.getProcessInstanceId(),
                addSignTask.getId(),
                addSignTask.getTaskDefinitionKey(),
                "TASK_REMOVE_SIGN",
                "加签任务已移除",
                "TASK",
                taskId,
                addSignTask.getId(),
                addSignTask.getAssignee(),
                actionSupportService.eventDetails(
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
        return actionSupportService.nextTaskResponse(sourceTask.getProcessInstanceId(), taskId);
    }

    private Task requireTaskForAction(String taskId, String actionLabel) {
        Task task = actionSupportService.requireActiveTask(taskId);
        List<String> candidateUserIds = actionSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = actionSupportService.candidateGroups(taskId);
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        boolean isCandidate = actionSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        if (!isAssignee) {
            if (task.getAssignee() == null && isCandidate) {
                throw actionSupportService.actionNotAllowed(
                        "请先认领任务后再执行" + actionLabel,
                        actionSupportService.eventDetails("taskId", taskId, "userId", actionSupportService.currentUserId(), "assigneeUserId", task.getAssignee())
                );
            }
            throw actionSupportService.actionNotAllowed(
                    "当前任务不允许执行" + actionLabel,
                    actionSupportService.eventDetails("taskId", taskId, "userId", actionSupportService.currentUserId(), "assigneeUserId", task.getAssignee())
                );
            }
        return task;
    }

    private Task requireTaskForAppend(String taskId) {
        Task task = actionSupportService.requireActiveTask(taskId);
        List<String> candidateUserIds = actionSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = actionSupportService.candidateGroups(taskId);
        String initiatorUserId = actionSupportService.stringValue(actionSupportService.runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId"));
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        boolean isCandidate = actionSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        boolean isInitiator = actionSupportService.currentUserId().equals(initiatorUserId);
        boolean canAppend = isAssignee || isInitiator;
        if (!canAppend) {
            if (task.getAssignee() == null && isCandidate) {
                throw actionSupportService.actionNotAllowed(
                        "请先认领任务后再执行追加",
                        actionSupportService.eventDetails(
                                "taskId", taskId,
                                "userId", actionSupportService.currentUserId(),
                                "assigneeUserId", task.getAssignee(),
                                "initiatorUserId", initiatorUserId
                        )
                );
            }
            throw actionSupportService.actionNotAllowed(
                    "当前任务不允许执行追加",
                    actionSupportService.eventDetails(
                            "taskId", taskId,
                            "userId", actionSupportService.currentUserId(),
                            "assigneeUserId", task.getAssignee(),
                            "initiatorUserId", initiatorUserId
                    )
            );
        }
        return task;
    }

    private String buildAppendSubprocessRuntimeBusinessKey(String parentBusinessKey, String sourceTaskId) {
        if (parentBusinessKey == null || parentBusinessKey.isBlank()) {
            return "append-" + sourceTaskId;
        }
        return parentBusinessKey + "::append::" + sourceTaskId;
    }
}
