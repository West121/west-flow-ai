package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.HandoverTaskRequest;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.HandoverExecutionResponse;
import com.westflow.processruntime.api.response.HandoverExecutionTaskItemResponse;
import com.westflow.processruntime.api.response.HandoverPreviewResponse;
import com.westflow.processruntime.api.response.HandoverPreviewTaskItemResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.query.RuntimeProcessPredictionRefreshService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeHandoverService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeProcessPredictionRefreshService runtimeProcessPredictionRefreshService;

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

    public HandoverPreviewResponse previewHandover(String sourceUserId, HandoverTaskRequest request) {
        String normalizedSourceUserId = actionSupportService.normalizeTargetUserId(sourceUserId, "sourceUserId");
        String targetUserId = actionSupportService.requireTargetUserId(request.targetUserId(), normalizedSourceUserId);
        List<HandoverPreviewTaskItemResponse> previewTasks = taskActionSupportService.activeTasksAssignedTo(normalizedSourceUserId).stream()
                .map(task -> processActionSupportService.toHandoverPreviewTask(task, normalizedSourceUserId))
                .toList();
        return new HandoverPreviewResponse(
                normalizedSourceUserId,
                processActionSupportService.resolveUserDisplayName(normalizedSourceUserId),
                targetUserId,
                processActionSupportService.resolveUserDisplayName(targetUserId),
                previewTasks.size(),
                previewTasks
        );
    }

    @Transactional
    public HandoverExecutionResponse executeHandover(String sourceUserId, HandoverTaskRequest request) {
        String normalizedSourceUserId = actionSupportService.normalizeTargetUserId(sourceUserId, "sourceUserId");
        String targetUserId = actionSupportService.requireTargetUserId(request.targetUserId(), normalizedSourceUserId);
        String comment = request.comment();
        List<Task> sourceTasks = taskActionSupportService.activeTasksAssignedTo(normalizedSourceUserId);

        List<HandoverExecutionTaskItemResponse> executionTasks = new ArrayList<>();
        List<ProcessTaskSnapshot> nextTasks = new ArrayList<>();
        Set<String> touchedProcessInstanceIds = new LinkedHashSet<>();
        for (Task task : sourceTasks) {
            taskActionSupportService.appendComment(task, comment);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowAction", "HANDOVER");
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowActingMode", "HANDOVER");
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowActingForUserId", normalizedSourceUserId);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowHandoverFromUserId", normalizedSourceUserId);
            flowableEngineFacade.taskService().setVariableLocal(task.getId(), "westflowTargetUserId", targetUserId);
            flowableTaskActionService.transfer(task.getId(), targetUserId);

            Task updatedTask = taskActionSupportService.requireActiveTask(task.getId());
            processActionSupportService.appendInstanceEvent(
                    updatedTask.getProcessInstanceId(),
                    updatedTask.getId(),
                    updatedTask.getTaskDefinitionKey(),
                    "TASK_HANDOVERED",
                    "任务已离职转办",
                    "TASK",
                    task.getId(),
                    updatedTask.getId(),
                    targetUserId,
                    processActionSupportService.eventDetails(
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
            nextTasks.add(taskActionSupportService.toTaskView(updatedTask));
            executionTasks.add(processActionSupportService.toHandoverExecutionTask(task, updatedTask, normalizedSourceUserId, targetUserId, comment));
            touchedProcessInstanceIds.add(updatedTask.getProcessInstanceId());
        }
        touchedProcessInstanceIds.forEach(runtimeProcessPredictionRefreshService::refreshForProcessInstance);

        Task firstTask = sourceTasks.stream().findFirst().orElse(null);
        return new HandoverExecutionResponse(
                normalizedSourceUserId,
                processActionSupportService.resolveUserDisplayName(normalizedSourceUserId),
                targetUserId,
                processActionSupportService.resolveUserDisplayName(targetUserId),
                executionTasks.size(),
                firstTask == null ? null : firstTask.getProcessInstanceId(),
                firstTask == null ? null : firstTask.getId(),
                executionTasks.isEmpty() ? "COMPLETED" : "RUNNING",
                nextTasks,
                executionTasks
        );
    }
}
