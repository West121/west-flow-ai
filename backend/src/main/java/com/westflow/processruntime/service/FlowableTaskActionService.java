package com.westflow.processruntime.service;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
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
        requireTask(taskId);
        taskService.delegateTask(taskId, targetUserId);
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
}
