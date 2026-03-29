package com.westflow.processruntime.service;

import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.query.FlowableRuntimeQueryService;
import java.util.Date;
import java.util.List;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowableRuntimeQueryServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private TaskQuery taskQuery;

    @InjectMocks
    private FlowableRuntimeQueryService flowableRuntimeQueryService;

    @Test
    void shouldPageTodoTasksForAssignee() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getName()).thenReturn("审批任务");
        when(task.getTaskDefinitionKey()).thenReturn("approve_manager");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getProcessDefinitionId()).thenReturn("pd-1");
        when(task.getAssignee()).thenReturn("zhangsan");
        when(task.getCreateTime()).thenReturn(new Date(1_000L));
        when(task.getDueDate()).thenReturn(null);
        when(task.getPriority()).thenReturn(50);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("zhangsan")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(task));
        when(taskQuery.count()).thenReturn(1L);

        PageResponse<FlowableRuntimeQueryService.TodoTaskSummary> page = flowableRuntimeQueryService.pageTodoTasks("zhangsan", 1, 10);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(1L);
        assertThat(page.pageSize()).isEqualTo(10L);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).taskId()).isEqualTo("task-1");
        assertThat(page.records().get(0).taskName()).isEqualTo("审批任务");
        assertThat(page.records().get(0).assigneeUserId()).isEqualTo("zhangsan");
    }
}
