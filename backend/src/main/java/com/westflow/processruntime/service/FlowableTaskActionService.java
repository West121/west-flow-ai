package com.westflow.processruntime.service;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
// 封装 Flowable 任务的认领和完成动作。
public class FlowableTaskActionService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final TaskService taskService;

    // 认领指定任务。
    public void claim(String taskId, String assigneeUserId) {
        requireTask(taskId);
        taskService.claim(taskId, assigneeUserId);
    }

    // 完成指定任务，可附带流程变量。
    public void complete(String taskId, Map<String, Object> variables) {
        requireTask(taskId);
        if (variables == null || variables.isEmpty()) {
            taskService.complete(taskId);
            return;
        }
        taskService.complete(taskId, variables);
    }

    // 转办时直接改派当前任务处理人。
    public void transfer(String taskId, String targetUserId) {
        requireTask(taskId);
        taskService.setAssignee(taskId, targetUserId);
    }

    // 委派时沿用 Flowable 原生委派语义。
    public void delegate(String taskId, String targetUserId) {
        Task task = requireTask(taskId);
        String ownerUserId = task.getAssignee() == null || task.getAssignee().isBlank()
                ? targetUserId
                : task.getAssignee();
        taskService.setOwner(taskId, ownerUserId);
        taskService.setAssignee(taskId, targetUserId);
    }

    // 把当前执行流切到指定节点，用于跳转/退回/驳回。
    public void moveToActivity(String taskId, String targetNodeId, Map<String, Object> variables) {
        Task task = requireTask(taskId);
        var builder = flowableEngineFacade.runtimeService()
                .createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId());
        if (variables != null && !variables.isEmpty()) {
            builder.processVariables(variables);
        }
        builder.moveExecutionToActivityId(task.getExecutionId(), targetNodeId).changeState();
    }

    // 撤销时直接终止整个流程实例。
    public void revokeProcessInstance(String processInstanceId, String deleteReason) {
        deleteAdhocTasksByProcessInstanceId(processInstanceId, deleteReason);
        flowableEngineFacade.runtimeService().deleteProcessInstance(processInstanceId, deleteReason);
    }

    // 创建挂在流程实例上的临时扩展任务，用于抄送与加签等平台动作。
    public Task createAdhocTask(
            String processInstanceId,
            String processDefinitionId,
            String nodeId,
            String nodeName,
            String taskKind,
            String assigneeUserId,
            List<String> candidateUserIds,
            String parentTaskId,
            Map<String, Object> localVariables
    ) {
        Task task = taskService.newTask();
        if (!(task instanceof TaskEntity taskEntity)) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前引擎不支持创建扩展任务"
            );
        }
        taskEntity.setCreateTime(new Date());
        taskEntity.setProcessInstanceId(processInstanceId);
        taskEntity.setProcessDefinitionId(processDefinitionId);
        taskEntity.setTaskDefinitionKey(nodeId);
        taskEntity.setTaskDefinitionId(nodeId);
        taskEntity.setName(nodeName);
        taskEntity.setParentTaskId(parentTaskId);
        taskEntity.setCategory(taskKind);
        taskService.saveTask(taskEntity);
        if (assigneeUserId != null && !assigneeUserId.isBlank()) {
            taskService.setAssignee(taskEntity.getId(), assigneeUserId);
        }
        if (candidateUserIds != null) {
            candidateUserIds.stream()
                    .filter(userId -> userId != null && !userId.isBlank())
                    .distinct()
                    .forEach(userId -> taskService.addCandidateUser(taskEntity.getId(), userId));
        }
        if (localVariables != null && !localVariables.isEmpty()) {
            taskService.setVariablesLocal(taskEntity.getId(), localVariables);
        }
        return requireTask(taskEntity.getId());
    }

    // 删除扩展任务，并保留删除原因用于历史轨迹显示。
    public void deleteTask(String taskId, String deleteReason) {
        requireTask(taskId);
        taskService.deleteTask(taskId, deleteReason);
    }

    // 清理没有执行流归属的扩展任务，避免引擎级联删实例时访问空 execution。
    public void deleteAdhocTasksByProcessInstanceId(String processInstanceId, String deleteReason) {
        taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list()
                .stream()
                .filter(this::isAdhocTask)
                .map(TaskInfo::getId)
                .forEach(taskId -> taskService.deleteTask(taskId, deleteReason));
    }

    // 用查询确认任务仍然存在。
    private Task requireTask(String taskId) {
        Task task = taskQuery(taskId).singleResult();
        if (task == null) {
            throw new ContractException(
                    "PROCESS.TASK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "任务不存在",
                    Map.of("taskId", taskId)
            );
        }
        return task;
    }

    private TaskQuery taskQuery(String taskId) {
        return taskService.createTaskQuery().taskId(taskId);
    }

    private boolean isAdhocTask(Task task) {
        return task.getExecutionId() == null || task.getExecutionId().isBlank();
    }
}
