package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.CompleteTaskRequest;
import com.westflow.processruntime.api.request.DelegateTaskRequest;
import com.westflow.processruntime.api.request.TransferTaskRequest;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.query.RuntimeProcessLinkQueryService;
import com.westflow.processruntime.query.RuntimeTaskSupportService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskExecutionService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final FlowableCountersignService flowableCountersignService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final ProcessLinkService processLinkService;
    private final RuntimeProcessLinkQueryService runtimeProcessLinkQueryService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;

    public CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        Task task = requireTaskForAction(taskId, "办理");
        String taskKind = taskActionSupportService.resolveTaskKind(task);
        String operatorUserId = actionSupportService.normalizeUserId(request.operatorUserId());
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetTaskId(taskId);
        if ("CC".equals(taskKind)) {
            throw actionSupportService.actionNotAllowed("当前抄送任务不支持办理", Map.of("taskId", taskId));
        }
        if ("NORMAL".equals(taskKind) && taskActionSupportService.hasActiveAddSignChild(taskId)) {
            throw actionSupportService.actionNotAllowed("当前任务存在未处理的加签任务", Map.of("taskId", taskId));
        }
        Map<String, Object> variables = taskActionSupportService.actionVariables(
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
        taskActionSupportService.appendComment(task, request.comment());
        flowableEngineFacade.taskService().setVariableLocal(taskId, "westflowTaskKind", taskKind);
        flowableTaskActionService.complete(taskId, variables);
        if (appendLink != null) {
            runtimeAppendLinkService.updateStatusByTargetTaskId(taskId, "COMPLETED", Instant.now());
            processActionSupportService.appendInstanceEvent(
                    task.getProcessInstanceId(),
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    "TASK_APPEND_COMPLETED",
                    "追加任务已完成",
                    "TASK",
                    task.getId(),
                    task.getId(),
                    actionSupportService.currentUserId(),
                    processActionSupportService.eventDetails(
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
        var subprocessLink = processLinkService.getByChildInstanceId(task.getProcessInstanceId());
        if (subprocessLink != null) {
            runtimeProcessLinkQueryService.synchronizeProcessLinks(subprocessLink.rootInstanceId(), actionSupportService.currentUserId());
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
                .filter(taskActionSupportService::isVisibleTask)
                .map(taskActionSupportService::toTaskView)
                .toList();
        String status = taskActionSupportService.blockingTaskViews(nextTasks).isEmpty()
                && !taskActionSupportService.hasRunningAppendStructures(task.getProcessInstanceId())
                ? "COMPLETED"
                : "RUNNING";
        return new CompleteTaskResponse(task.getProcessInstanceId(), taskId, status, nextTasks);
    }

    public CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        Task task = requireTaskForAction(taskId, "转办");
        taskActionSupportService.appendComment(task, request.comment());
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                taskActionSupportService.actionVariables(
                        "TRANSFER",
                        actionSupportService.currentUserId(),
                        request.comment(),
                        Map.of("targetUserId", request.targetUserId())
                )
        );
        flowableTaskActionService.transfer(taskId, request.targetUserId());
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_TRANSFERRED",
                "任务已转办",
                "TASK",
                task.getId(),
                task.getId(),
                request.targetUserId(),
                processActionSupportService.eventDetails(
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
        Task updatedTask = taskActionSupportService.requireActiveTask(taskId);
        return new CompleteTaskResponse(
                updatedTask.getProcessInstanceId(),
                taskId,
                "RUNNING",
                List.of(taskActionSupportService.toTaskView(updatedTask))
        );
    }

    public CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        Task task = requireTaskForAction(taskId, "委派");
        String targetUserId = actionSupportService.requireTargetUserId(
                request == null ? null : request.targetUserId(),
                taskId
        );
        String comment = request == null ? null : request.comment();

        taskActionSupportService.appendComment(task, comment);
        flowableEngineFacade.runtimeService().setVariables(
                task.getProcessInstanceId(),
                taskActionSupportService.actionVariables(
                        "DELEGATE",
                        actionSupportService.currentUserId(),
                        comment,
                        Map.of("targetUserId", targetUserId)
                )
        );
        flowableTaskActionService.delegate(taskId, targetUserId);

        Task delegatedTask = taskActionSupportService.requireActiveTask(taskId);
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_DELEGATED",
                "任务已委派",
                "TASK",
                task.getId(),
                delegatedTask.getId(),
                targetUserId,
                processActionSupportService.eventDetails(
                        "comment", comment,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", delegatedTask.getId(),
                        "targetUserId", targetUserId,
                        "actingMode", "DELEGATE",
                        "actingForUserId", actionSupportService.currentUserId(),
                        "delegatedByUserId", actionSupportService.currentUserId()
                ),
                null,
                null,
                null,
                "DELEGATE",
                actionSupportService.currentUserId(),
                actionSupportService.currentUserId(),
                null
        );

        return new CompleteTaskResponse(
                delegatedTask.getProcessInstanceId(),
                task.getId(),
                "RUNNING",
                List.of(taskActionSupportService.toTaskView(delegatedTask))
        );
    }

    public BatchTaskActionResponse batchComplete(BatchTaskActionRequest request) {
        return batchTaskAction("COMPLETE", request.taskIds(), taskId -> {
            CompleteTaskResponse response = complete(
                    taskId,
                    new CompleteTaskRequest(
                            "APPROVE",
                            actionSupportService.normalizeUserId(request.operatorUserId()),
                            request.comment(),
                            request.taskFormData()
                    )
            );
            return new BatchTaskActionResponse.Item(
                    taskId,
                    response.instanceId(),
                    true,
                    "OK",
                    response.status(),
                    "success",
                    taskId,
                    null,
                    null,
                    null,
                    null,
                    response.nextTasks()
            );
        });
    }

    private Task requireTaskForAction(String taskId, String actionLabel) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        List<String> candidateUserIds = runtimeTaskSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = runtimeTaskSupportService.candidateGroups(taskId);
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        boolean isCandidate = runtimeTaskSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        if (!isAssignee) {
            if (task.getAssignee() == null && isCandidate) {
                throw actionSupportService.actionNotAllowed(
                        "请先认领任务后再执行" + actionLabel,
                        processActionSupportService.eventDetails(
                                "taskId",
                                taskId,
                                "userId",
                                actionSupportService.currentUserId(),
                                "assigneeUserId",
                                task.getAssignee()
                        )
                );
            }
            throw actionSupportService.actionNotAllowed(
                    "当前任务不允许执行" + actionLabel,
                    processActionSupportService.eventDetails(
                            "taskId",
                            taskId,
                            "userId",
                            actionSupportService.currentUserId(),
                            "assigneeUserId",
                            task.getAssignee()
                    )
            );
        }
        return task;
    }

    private BatchTaskActionResponse batchTaskAction(
            String action,
            List<String> taskIds,
            Function<String, BatchTaskActionResponse.Item> executor
    ) {
        List<BatchTaskActionResponse.Item> items = new ArrayList<>();
        for (String taskId : taskIds) {
            try {
                items.add(executor.apply(taskId));
            } catch (ContractException ex) {
                items.add(new BatchTaskActionResponse.Item(
                        taskId,
                        null,
                        false,
                        ex.getCode(),
                        "FAILED",
                        ex.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ));
            } catch (RuntimeException ex) {
                items.add(new BatchTaskActionResponse.Item(
                        taskId,
                        null,
                        false,
                        "SYS.INTERNAL_ERROR",
                        "FAILED",
                        "系统异常，请稍后重试",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ));
            }
        }
        int successCount = (int) items.stream().filter(BatchTaskActionResponse.Item::success).count();
        return new BatchTaskActionResponse(action, taskIds.size(), successCount, taskIds.size() - successCount, items);
    }
}
