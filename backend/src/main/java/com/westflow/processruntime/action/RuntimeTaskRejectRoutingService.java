package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.RejectTaskRequest;
import com.westflow.processruntime.api.request.ReturnTaskRequest;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskRejectRoutingService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final FlowableCountersignService flowableCountersignService;

    public CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        Task task = requireTaskForAction(taskId, "退回");
        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy().trim();
        RejectTarget target = resolveReturnTarget(task, request.targetTaskId(), request.targetNodeId(), targetStrategy);
        taskActionSupportService.appendComment(task, request.comment());
        routeTaskToTarget(
                task,
                target,
                targetStrategy,
                normalizeReapproveStrategy(null),
                taskActionSupportService.actionVariables(
                        "RETURN",
                        actionSupportService.currentUserId(),
                        request.comment(),
                        Map.of("targetStrategy", targetStrategy)
                )
        );
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_RETURNED",
                "任务已退回",
                "TASK",
                task.getId(),
                task.getId(),
                target.targetUserId(),
                processActionSupportService.eventDetails(
                        "comment", request.comment(),
                        "targetStrategy", targetStrategy,
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", target.targetUserId(),
                        "targetNodeId", target.nodeId(),
                        "targetNodeName", target.nodeName()
                ),
                targetStrategy,
                target.nodeId(),
                null,
                null,
                null,
                null,
                null
        );
        return taskActionSupportService.nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    public CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        Task task = requireTaskForAction(taskId, "驳回");
        String targetStrategy = request.targetStrategy() == null || request.targetStrategy().isBlank()
                ? "PREVIOUS_USER_TASK"
                : request.targetStrategy().trim();
        RejectTarget target = resolveRejectTarget(task, request, targetStrategy);
        String reapproveStrategy = normalizeReapproveStrategy(request.reapproveStrategy());
        taskActionSupportService.appendComment(task, request.comment());
        routeTaskToTarget(
                task,
                target,
                targetStrategy,
                reapproveStrategy,
                taskActionSupportService.actionVariables(
                        "REJECT_ROUTE",
                        actionSupportService.currentUserId(),
                        request.comment(),
                        Map.of(
                                "targetStrategy", targetStrategy,
                                "targetNodeId", target.nodeId(),
                                "targetNodeName", target.nodeName(),
                                "reapproveStrategy", reapproveStrategy
                        )
                )
        );
        processActionSupportService.appendInstanceEvent(
                task.getProcessInstanceId(),
                task.getId(),
                task.getTaskDefinitionKey(),
                "TASK_REJECTED",
                "任务已驳回",
                "TASK",
                task.getId(),
                task.getId(),
                target.targetUserId(),
                processActionSupportService.eventDetails(
                        "comment", request.comment(),
                        "sourceTaskId", task.getId(),
                        "targetTaskId", task.getId(),
                        "targetUserId", target.targetUserId(),
                        "targetStrategy", targetStrategy,
                        "targetNodeId", target.nodeId(),
                        "targetNodeName", target.nodeName(),
                        "reapproveStrategy", reapproveStrategy
                ),
                targetStrategy,
                target.nodeId(),
                reapproveStrategy,
                null,
                null,
                null,
                null
        );
        return taskActionSupportService.nextTaskResponse(task.getProcessInstanceId(), taskId);
    }

    public BatchTaskActionResponse batchReject(BatchTaskActionRequest request) {
        return batchTaskAction("REJECT", request.taskIds(), taskId -> {
            CompleteTaskResponse response = reject(
                    taskId,
                    new RejectTaskRequest(
                            request.targetStrategy() == null || request.targetStrategy().isBlank()
                                    ? "PREVIOUS_USER_TASK"
                                    : request.targetStrategy().trim(),
                            request.targetTaskId(),
                            request.targetNodeId(),
                            request.reapproveStrategy(),
                            request.comment()
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
                    request.targetStrategy(),
                    request.targetNodeId(),
                    request.reapproveStrategy(),
                    response.nextTasks()
            );
        });
    }

    private Task requireTaskForAction(String taskId, String actionLabel) {
        Task task = taskActionSupportService.requireActiveTask(taskId);
        List<String> candidateUserIds = taskActionSupportService.candidateUsers(taskId);
        List<String> candidateGroupIds = taskActionSupportService.candidateGroups(taskId);
        boolean isAssignee = actionSupportService.currentUserId().equals(task.getAssignee());
        boolean isCandidate = taskActionSupportService.isCurrentUserCandidate(task, candidateUserIds, candidateGroupIds);
        if (!isAssignee) {
            if (task.getAssignee() == null && isCandidate) {
                throw actionSupportService.actionNotAllowed(
                        "请先认领任务后再执行" + actionLabel,
                        processActionSupportService.eventDetails(
                                "taskId", taskId,
                                "userId", actionSupportService.currentUserId(),
                                "assigneeUserId", task.getAssignee()
                        )
                );
            }
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

    private RejectTarget resolveRejectTarget(Task task, RejectTaskRequest request, String targetStrategy) {
        return switch (targetStrategy) {
            case "PREVIOUS_USER_TASK" -> {
                HistoricTaskInstance previousTask = taskActionSupportService.requirePreviousUserTask(task);
                yield new RejectTarget(previousTask.getTaskDefinitionKey(), previousTask.getName(), previousTask.getAssignee());
            }
            case "INITIATOR" -> resolveInitiatorTarget(task);
            case "ANY_USER_TASK" -> {
                HistoricTaskInstance targetTask = requireHistoricTargetTask(task, request.targetTaskId(), request.targetNodeId());
                yield new RejectTarget(targetTask.getTaskDefinitionKey(), targetTask.getName(), targetTask.getAssignee());
            }
            default -> throw actionSupportService.actionNotAllowed(
                    "当前真实运行态仅支持驳回到上一步、发起人或任意历史人工节点",
                    Map.of("targetStrategy", targetStrategy)
            );
        };
    }

    private RejectTarget resolveReturnTarget(Task task, String targetTaskId, String targetNodeId, String targetStrategy) {
        return switch (targetStrategy) {
            case "PREVIOUS_USER_TASK" -> {
                HistoricTaskInstance previousTask = taskActionSupportService.requirePreviousUserTask(task);
                yield new RejectTarget(previousTask.getTaskDefinitionKey(), previousTask.getName(), previousTask.getAssignee());
            }
            case "INITIATOR" -> resolveInitiatorTarget(task);
            case "ANY_USER_TASK" -> {
                HistoricTaskInstance targetTask = requireHistoricTargetTask(task, targetTaskId, targetNodeId);
                yield new RejectTarget(targetTask.getTaskDefinitionKey(), targetTask.getName(), targetTask.getAssignee());
            }
            default -> throw actionSupportService.actionNotAllowed(
                    "当前真实运行态仅支持退回到上一步、发起人或任意历史人工节点",
                    Map.of("targetStrategy", targetStrategy)
            );
        };
    }

    private RejectTarget resolveInitiatorTarget(Task task) {
        String initiatorUserId = actionSupportService.stringValue(
                processActionSupportService.runtimeVariables(task.getProcessInstanceId()).get("westflowInitiatorUserId")
        );
        String startNodeId = resolveStartNodeId(task.getProcessDefinitionId());
        return new RejectTarget(
                startNodeId,
                taskActionSupportService.resolveNodeName(task.getProcessDefinitionId(), startNodeId),
                initiatorUserId
        );
    }

    private String resolveStartNodeId(String processDefinitionId) {
        BpmnModel bpmnModel = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            throw new ContractException("PROCESS.RUNTIME_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "流程定义缺少开始节点");
        }
        return bpmnModel.getMainProcess().getFlowElements().stream()
                .filter(element -> element instanceof StartEvent)
                .map(BaseElement::getId)
                .findFirst()
                .orElseThrow(() -> new ContractException("PROCESS.RUNTIME_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "流程定义缺少开始节点"));
    }

    private HistoricTaskInstance requireHistoricTargetTask(Task task, String targetTaskId, String targetNodeId) {
        HistoricTaskInstance targetTask;
        if (targetTaskId != null && !targetTaskId.isBlank()) {
            targetTask = flowableEngineFacade.historyService()
                    .createHistoricTaskInstanceQuery()
                    .taskId(targetTaskId)
                    .singleResult();
            if (targetTask == null
                    || targetTask.getEndTime() == null
                    || !task.getProcessInstanceId().equals(targetTask.getProcessInstanceId())) {
                throw actionSupportService.actionNotAllowed(
                        "目标历史任务不存在",
                        Map.of("taskId", task.getId(), "targetTaskId", targetTaskId)
                );
            }
        } else if (targetNodeId != null && !targetNodeId.isBlank()) {
            targetTask = flowableEngineFacade.historyService()
                    .createHistoricTaskInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .taskDefinitionKey(targetNodeId)
                    .finished()
                    .orderByHistoricTaskInstanceEndTime()
                    .desc()
                    .list()
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (targetTask == null) {
                throw actionSupportService.actionNotAllowed(
                        "目标历史节点不存在",
                        Map.of("taskId", task.getId(), "targetNodeId", targetNodeId)
                );
            }
        } else {
            throw actionSupportService.actionNotAllowed(
                    "目标历史节点不能为空",
                    Map.of("taskId", task.getId(), "targetTaskId", targetTaskId, "targetNodeId", targetNodeId)
            );
        }
        if (!"NORMAL".equals(taskActionSupportService.resolveHistoricTaskKind(targetTask))) {
            throw actionSupportService.actionNotAllowed(
                    "目标节点不是人工审批节点",
                    Map.of("taskId", task.getId(), "targetTaskId", targetTask.getId(), "targetNodeId", targetTask.getTaskDefinitionKey())
            );
        }
        return targetTask;
    }

    private void routeTaskToTarget(
            Task task,
            RejectTarget target,
            String targetStrategy,
            String reapproveStrategy,
            Map<String, Object> actionVariables
    ) {
        String approvalMode = taskActionSupportService.countersignApprovalMode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (approvalMode == null) {
            flowableTaskActionService.moveToActivity(task.getId(), target.nodeId(), actionVariables);
            return;
        }

        String currentNodeId = task.getTaskDefinitionKey();
        String actualTargetNodeId = target.nodeId();
        Map<String, Object> processVariables = new LinkedHashMap<>(actionVariables);
        List<String> rerouteAssignees = resolveCountersignRerouteAssignees(
                task,
                target,
                targetStrategy,
                approvalMode,
                reapproveStrategy
        );
        if (!rerouteAssignees.isEmpty()) {
            processVariables.put(countersignCollectionVariable(currentNodeId), rerouteAssignees);
            if ("OR_SIGN".equals(approvalMode) || "VOTE".equals(approvalMode)) {
                processVariables.put(countersignDecisionVariable(currentNodeId), "PENDING");
            }
            actualTargetNodeId = currentNodeId.equals(target.nodeId())
                    ? resolveCountersignReentryNodeId(task.getProcessDefinitionId(), currentNodeId)
                    : target.nodeId();
        }

        flowableTaskActionService.moveActivityIdTo(task.getProcessInstanceId(), currentNodeId, actualTargetNodeId, processVariables);
        flowableCountersignService.rebuildTaskGroupsForNode(task.getProcessDefinitionId(), task.getProcessInstanceId(), currentNodeId);
    }

    private List<String> resolveCountersignRerouteAssignees(
            Task task,
            RejectTarget target,
            String targetStrategy,
            String approvalMode,
            String reapproveStrategy
    ) {
        String currentNodeId = task.getTaskDefinitionKey();
        if ("INITIATOR".equals(targetStrategy) && target.targetUserId() != null && !target.targetUserId().isBlank()) {
            return List.of(target.targetUserId());
        }
        if (!currentNodeId.equals(target.nodeId()) || target.targetUserId() == null || target.targetUserId().isBlank()) {
            return List.of();
        }
        List<String> assignees = currentCountersignAssignees(task.getProcessInstanceId(), currentNodeId);
        if (assignees.isEmpty()) {
            return List.of(target.targetUserId());
        }
        int targetIndex = assignees.indexOf(target.targetUserId());
        if (targetIndex < 0) {
            return List.of(target.targetUserId());
        }
        if ("SEQUENTIAL".equals(approvalMode)) {
            if ("RESTART_ALL".equals(reapproveStrategy) || "CONTINUE_PROGRESS".equals(reapproveStrategy)) {
                return List.copyOf(assignees.subList(targetIndex, assignees.size()));
            }
            return List.of(target.targetUserId());
        }
        return List.of(target.targetUserId());
    }

    private List<String> currentCountersignAssignees(String processInstanceId, String nodeId) {
        Object runtimeValue = flowableEngineFacade.runtimeService().getVariable(processInstanceId, countersignCollectionVariable(nodeId));
        if (runtimeValue instanceof List<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
        }
        if (runtimeValue instanceof String value && !value.isBlank()) {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private String resolveCountersignReentryNodeId(String processDefinitionId, String nodeId) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null) {
            return nodeId;
        }
        BaseElement element = model.getFlowElement(nodeId);
        if (!(element instanceof FlowNode flowNode) || flowNode.getIncomingFlows() == null || flowNode.getIncomingFlows().isEmpty()) {
            return nodeId;
        }
        SequenceFlow incoming = flowNode.getIncomingFlows().get(0);
        if (incoming == null || incoming.getSourceRef() == null || incoming.getSourceRef().isBlank()) {
            return nodeId;
        }
        return incoming.getSourceRef();
    }

    private String normalizeReapproveStrategy(String reapproveStrategy) {
        return reapproveStrategy == null || reapproveStrategy.isBlank() ? "CONTINUE" : reapproveStrategy.trim();
    }

    private String countersignCollectionVariable(String nodeId) {
        return "wfCountersignAssignees_" + nodeId;
    }

    private String countersignDecisionVariable(String nodeId) {
        return "wfCountersignDecision_" + nodeId;
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

    private record RejectTarget(String nodeId, String nodeName, String targetUserId) {
    }
}
