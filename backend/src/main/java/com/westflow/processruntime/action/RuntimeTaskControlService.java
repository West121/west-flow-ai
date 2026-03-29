package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.RevokeTaskRequest;
import com.westflow.processruntime.api.request.TakeBackTaskRequest;
import com.westflow.processruntime.api.request.UrgeTaskRequest;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import com.westflow.processruntime.query.RuntimeTaskSupportService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskControlService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;

    public CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        if (!canTakeBack(task)) {
            throw actionSupportService.actionNotAllowed(
                    "当前任务不满足拿回条件",
                    Map.of("taskId", taskId, "userId", actionSupportService.currentUserId())
            );
        }
        HistoricTaskInstance previousTask = taskActionSupportService.requirePreviousUserTask(task);
        String comment = request == null ? null : request.comment();
        taskActionSupportService.appendComment(task, comment);
        flowableEngineFacade.taskService().setVariableLocal(taskId, "westflowAction", "TAKE_BACK");
        flowableTaskActionService.moveToActivity(
                taskId,
                previousTask.getTaskDefinitionKey(),
                taskActionSupportService.actionVariables(
                        "TAKE_BACK",
                        actionSupportService.currentUserId(),
                        comment,
                        Map.of(
                                "targetNodeId", previousTask.getTaskDefinitionKey(),
                                "sourceTaskId", taskId,
                                "targetUserId", previousTask.getAssignee()
                        )
                )
        );
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_TAKEN_BACK",
                "任务已拿回",
                "TASK",
                task.getId(),
                task.getId(),
                previousTask.getAssignee(),
                processActionSupportService.eventDetails(
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
        return taskActionSupportService.nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    public CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        String comment = request == null ? null : request.comment();
        String processInstanceId = task.getProcessInstanceId();
        String initiatorUserId = actionSupportService.stringValue(
                processActionSupportService.runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId")
        );

        if (initiatorUserId == null || !initiatorUserId.equals(actionSupportService.currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以撤销流程",
                    Map.of("taskId", taskId, "userId", actionSupportService.currentUserId())
            );
        }

        taskActionSupportService.appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                processInstanceId,
                taskActionSupportService.actionVariables("REVOKE", actionSupportService.currentUserId(), comment, Map.of())
        );
        flowableEngineFacade.runtimeService().deleteProcessInstance(processInstanceId, "WESTFLOW_REVOKED");

        processActionSupportService.appendInstanceEvent(
                processInstanceId,
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_REVOKED",
                "流程已撤销",
                "INSTANCE",
                task.getId(),
                task.getId(),
                null,
                processActionSupportService.eventDetails(
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

        runtimeBusinessLinkService.findByInstanceId(processInstanceId).ifPresent(link -> {
            processActionSupportService.updateBusinessProcessLink(link.businessType(), link.businessId(), processInstanceId, "REVOKED");
            runtimeBusinessLinkService.updateStatus(processInstanceId, "REVOKED");
        });

        return new CompleteTaskResponse(processInstanceId, task.getId(), "REVOKED", List.of());
    }

    public CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        String comment = request == null ? null : request.comment();
        String initiatorUserId = actionSupportService.stringValue(
                processActionSupportService.runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId")
        );

        if (initiatorUserId == null || !initiatorUserId.equals(actionSupportService.currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅发起人可以催办",
                    Map.of("taskId", taskId, "userId", actionSupportService.currentUserId())
            );
        }

        taskActionSupportService.appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                taskActionSupportService.actionVariables("URGE", actionSupportService.currentUserId(), comment, Map.of())
        );

        String targetUserId = taskActionSupportService.resolveCurrentTaskOwner(task);
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_URGED",
                "任务已催办",
                "TASK",
                task.getId(),
                task.getId(),
                targetUserId,
                processActionSupportService.eventDetails(
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
                List.of(taskActionSupportService.toTaskView(task))
        );
    }

    private boolean canTakeBack(Task task) {
        if (task == null || !"NORMAL".equals(taskActionSupportService.resolveTaskKind(task)) || taskActionSupportService.hasActiveAddSignChild(task.getId())) {
            return false;
        }
        HistoricTaskInstance previousTask;
        try {
            previousTask = taskActionSupportService.requirePreviousUserTask(task);
        } catch (ContractException exception) {
            return false;
        }
        if (!actionSupportService.currentUserId().equals(previousTask.getAssignee())) {
            return false;
        }
        Map<String, Object> localVariables = taskActionSupportService.taskLocalVariables(task.getId());
        if (actionSupportService.stringValue(localVariables.get("westflowAction")) != null
                || runtimeTaskSupportService.readTimeValue(localVariables) != null) {
            return false;
        }
        return flowableEngineFacade.taskService().getTaskComments(task.getId()).isEmpty();
    }
}
