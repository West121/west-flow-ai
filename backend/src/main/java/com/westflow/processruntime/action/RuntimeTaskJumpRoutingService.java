package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.JumpTaskRequest;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskJumpRoutingService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;

    public CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        Task task = requireTaskForAction(taskId, "跳转");
        String targetNodeId = taskActionSupportService.requireTargetNode(task.getProcessDefinitionId(), request.targetNodeId());
        String targetNodeName = taskActionSupportService.resolveNodeName(task.getProcessDefinitionId(), targetNodeId);
        taskActionSupportService.appendComment(task, request.comment());
        flowableTaskActionService.moveToActivity(
                taskId,
                targetNodeId,
                taskActionSupportService.actionVariables(
                        "JUMP",
                        actionSupportService.currentUserId(),
                        request.comment(),
                        java.util.Map.of("targetNodeId", targetNodeId)
                )
        );
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_JUMPED",
                "任务已跳转",
                "TASK",
                task.getId(),
                task.getId(),
                null,
                processActionSupportService.eventDetails(
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
        return taskActionSupportService.nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    private Task requireTaskForAction(String taskId, String actionLabel) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        if (!isAssignee) {
            throw actionSupportService.actionNotAllowed(
                    "当前任务不允许执行" + actionLabel,
                    processActionSupportService.eventDetails(
                            "taskId", taskId,
                            "userId", actionSupportService.currentUserId(),
                            "assigneeUserId", task.getAssignee()
                    )
            );
        }
        return task;
    }
}
