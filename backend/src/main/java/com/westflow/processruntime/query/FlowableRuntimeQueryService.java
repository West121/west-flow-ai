package com.westflow.processruntime.query;

import com.westflow.common.query.PageResponse;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(TaskService.class)
// 基于 Flowable 查询当前用户的待办任务分页。
public class FlowableRuntimeQueryService {

    private final TaskService taskService;

    // 按审批人分页查询待办任务。
    public PageResponse<TodoTaskSummary> pageTodoTasks(String assigneeUserId, long page, long pageSize) {
        TaskQuery query = taskService.createTaskQuery()
                .taskAssignee(assigneeUserId)
                .active()
                .orderByTaskCreateTime()
                .desc();

        long total = query.count();
        long safePage = Math.max(page, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        int firstResult = Math.toIntExact((safePage - 1L) * safePageSize);
        int maxResults = Math.toIntExact(safePageSize);
        List<TodoTaskSummary> records = total == 0
                ? List.of()
                : query.listPage(firstResult, maxResults).stream()
                        .map(this::toSummary)
                        .toList();
        long pages = total == 0 ? 0 : (total + safePageSize - 1L) / safePageSize;
        return new PageResponse<>(safePage, safePageSize, total, pages, records, List.of());
    }

    // 将 Flowable 任务对象映射成最小待办摘要。
    private TodoTaskSummary toSummary(Task task) {
        return new TodoTaskSummary(
                task.getId(),
                task.getName(),
                task.getTaskDefinitionKey(),
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                task.getAssignee(),
                task.getCreateTime(),
                task.getDueDate(),
                task.getPriority()
        );
    }

    // 待办任务最小摘要。
    public record TodoTaskSummary(
            String taskId,
            String taskName,
            String taskDefinitionKey,
            String processInstanceId,
            String processDefinitionId,
            String assigneeUserId,
            Date createdAt,
            Date dueDate,
            Integer priority
    ) {
    }
}
