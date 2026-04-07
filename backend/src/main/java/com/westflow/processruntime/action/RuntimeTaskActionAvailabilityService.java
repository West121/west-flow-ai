package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.TaskActionAvailabilityResponse;
import com.westflow.processruntime.query.RuntimeTaskSupportService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskActionAvailabilityService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;

    public TaskActionAvailabilityResponse actions(String taskId) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        String taskKind = taskActionSupportService.resolveTaskKind(task);
        String taskSemanticMode = runtimeTaskSupportService.resolveTaskSemanticMode(task);
        boolean isNormalTask = "NORMAL".equals(taskKind);
        boolean isAppendTask = "APPEND".equals(taskKind);
        boolean isAddSignTask = "ADD_SIGN".equals(taskKind);
        boolean isFlowHandleTask = isNormalTask || isAppendTask || isAddSignTask;
        List<String> candidateUserIds = runtimeTaskSupportService.candidateUsers(task.getId());
        List<String> candidateGroupIds = runtimeTaskSupportService.candidateGroups(task.getId());
        boolean isCandidate = runtimeTaskSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        boolean canClaim = task.getAssignee() == null && isCandidate;
        boolean canHandle = isAssignee;
        boolean canUrge = taskActionSupportService.hasInitiatorPermission(task) && !isAssignee && !isCandidate;
        boolean blockedByAddSign = isNormalTask && taskActionSupportService.hasActiveAddSignChild(task.getId());
        boolean blockedByAppendPolicy = isNormalTask && taskActionSupportService.isBlockedByPendingAppendStructures(task);
        boolean canTakeBack = isNormalTask && canTakeBack(task);
        boolean canAddSign = isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy;
        boolean canRemoveSign = isNormalTask && canHandle && blockedByAddSign && !blockedByAppendPolicy;
        boolean canComplete = isFlowHandleTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy;
        boolean canSign = canHandle && !blockedByAddSign && !blockedByAppendPolicy;
        boolean canRead = "CC".equals(taskKind)
                && runtimeTaskSupportService.supportsSemanticRead(taskSemanticMode)
                && (isAssignee || isCandidate)
                && "CC_PENDING".equals(runtimeTaskSupportService.resolveTaskStatus(task));
        return new TaskActionAvailabilityResponse(
                isFlowHandleTask && canClaim,
                canComplete,
                canComplete,
                isFlowHandleTask && canHandle,
                isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                canAddSign,
                canRemoveSign,
                taskActionSupportService.hasInitiatorPermission(task),
                canUrge,
                canRead,
                isNormalTask && canHandle && !blockedByAddSign && !blockedByAppendPolicy,
                isNormalTask && canHandle,
                canTakeBack,
                false,
                isNormalTask && canHandle && !blockedByAppendPolicy,
                false,
                canSign
        );
    }

    private boolean canTakeBack(Task task) {
        if (task == null || !"NORMAL".equals(taskActionSupportService.resolveTaskKind(task)) || taskActionSupportService.hasActiveAddSignChild(task.getId())) {
            return false;
        }
        HistoricTaskInstance previousTask;
        try {
            previousTask = taskActionSupportService.requirePreviousUserTask(task);
        } catch (RuntimeException exception) {
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
