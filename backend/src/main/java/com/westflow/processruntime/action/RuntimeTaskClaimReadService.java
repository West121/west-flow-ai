package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.ClaimTaskRequest;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.ClaimTaskResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.query.RuntimeTaskSupportService;
import java.sql.Timestamp;
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
public class RuntimeTaskClaimReadService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;

    public CompleteTaskResponse read(String taskId) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        String taskKind = taskActionSupportService.resolveTaskKind(task);
        String taskSemanticMode = runtimeTaskSupportService.resolveTaskSemanticMode(task);
        if (!"CC".equals(taskKind) || !runtimeTaskSupportService.supportsSemanticRead(taskSemanticMode)) {
            throw actionSupportService.actionNotAllowed("当前任务不支持已阅", Map.of("taskId", taskId));
        }
        List<String> candidateUserIds = runtimeTaskSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = runtimeTaskSupportService.candidateGroups(taskId);
        boolean canRead = actionSupportService.currentUserId().equals(task.getAssignee())
                || runtimeTaskSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        if (!canRead) {
            throw actionSupportService.actionNotAllowed(
                    "当前用户不能操作该抄送任务",
                    Map.of("taskId", taskId, "userId", actionSupportService.currentUserId())
            );
        }

        Task activeTask = claimTaskIfNeeded(task, actionSupportService.currentUserId());
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowTaskKind", taskKind);
        if (taskSemanticMode != null) {
            flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowTaskSemanticMode", taskSemanticMode);
        }
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowReadTime", Timestamp.from(Instant.now()));
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowAction", "READ");
        flowableEngineFacade.taskService().setVariableLocal(activeTask.getId(), "westflowTargetUserId", actionSupportService.currentUserId());
        flowableTaskActionService.complete(activeTask.getId(), Map.of());
        processActionSupportService.appendInstanceEvent(
                activeTask.getProcessInstanceId(),
                activeTask.getId(),
                activeTask.getTaskDefinitionKey(),
                runtimeTaskSupportService.resolveReadEventType(taskSemanticMode),
                runtimeTaskSupportService.resolveReadEventName(taskSemanticMode),
                runtimeTaskSupportService.resolveReadActionCategory(taskSemanticMode),
                activeTask.getParentTaskId(),
                activeTask.getId(),
                actionSupportService.currentUserId(),
                processActionSupportService.eventDetails(
                        "sourceTaskId", activeTask.getParentTaskId(),
                        "targetTaskId", activeTask.getId(),
                        "targetUserId", actionSupportService.currentUserId()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return taskActionSupportService.nextTaskResponse(activeTask.getProcessInstanceId(), taskId);
    }

    public BatchTaskActionResponse batchRead(BatchTaskActionRequest request) {
        return batchTaskAction("READ", request.taskIds(), taskId -> {
            CompleteTaskResponse response = read(taskId);
            return new BatchTaskActionResponse.Item(
                    taskId,
                    response.instanceId(),
                    true,
                    "OK",
                    response.status(),
                    "success",
                    null,
                    null,
                    null,
                    null,
                    null,
                    response.nextTasks()
            );
        });
    }

    public ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        List<String> candidateUserIds = runtimeTaskSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = runtimeTaskSupportService.candidateGroups(taskId);
        if (task.getAssignee() != null && !actionSupportService.currentUserId().equals(task.getAssignee())) {
            throw actionSupportService.actionNotAllowed(
                    "当前任务已被他人认领",
                    processActionSupportService.eventDetails("taskId", taskId, "assigneeUserId", task.getAssignee())
            );
        }
        if (task.getAssignee() == null && !runtimeTaskSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds)) {
            throw actionSupportService.actionNotAllowed(
                    "当前任务不允许认领",
                    processActionSupportService.eventDetails("taskId", taskId, "userId", actionSupportService.currentUserId())
            );
        }
        String assigneeUserId = actionSupportService.currentUserId();
        if (request != null && request.comment() != null && !request.comment().isBlank()) {
            flowableEngineFacade.taskService().addComment(taskId, task.getProcessInstanceId(), request.comment().trim());
        }
        flowableTaskActionService.claim(taskId, assigneeUserId);
        Task claimedTask = taskActionSupportService.requireActiveTask(taskId);
        return new ClaimTaskResponse(
                claimedTask.getId(),
                claimedTask.getProcessInstanceId(),
                "PENDING",
                claimedTask.getAssignee()
        );
    }

    public BatchTaskActionResponse batchClaim(BatchTaskActionRequest request) {
        return batchTaskAction("CLAIM", request.taskIds(), taskId -> {
            ClaimTaskResponse response = claim(taskId, new ClaimTaskRequest(request.comment()));
            return new BatchTaskActionResponse.Item(
                    taskId,
                    response.instanceId(),
                    true,
                    "OK",
                    response.status(),
                    "success",
                    null,
                    response.assigneeUserId(),
                    null,
                    null,
                    null,
                    List.of()
            );
        });
    }

    private Task claimTaskIfNeeded(Task task, String assigneeUserId) {
        if (task.getAssignee() == null) {
            flowableTaskActionService.claim(task.getId(), assigneeUserId);
            return taskActionSupportService.requireActiveTask(task.getId());
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
