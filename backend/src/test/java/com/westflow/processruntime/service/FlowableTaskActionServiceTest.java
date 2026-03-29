package com.westflow.processruntime.service;

import com.westflow.common.error.ContractException;
import com.westflow.processruntime.action.FlowableTaskActionService;
import java.util.List;
import java.util.Map;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowableTaskActionServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private TaskQuery taskQuery;

    @InjectMocks
    private FlowableTaskActionService flowableTaskActionService;

    @Test
    void shouldClaimExistingTask() {
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        flowableTaskActionService.claim("task-1", "zhangsan");

        verify(taskService).claim("task-1", "zhangsan");
    }

    @Test
    void shouldCompleteExistingTaskWithVariables() {
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        flowableTaskActionService.complete("task-1", Map.of("approved", true));

        verify(taskService).complete("task-1", Map.of("approved", true));
    }

    @Test
    void shouldFailWhenTaskMissing() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("missing")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> flowableTaskActionService.claim("missing", "zhangsan"))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("任务不存在");
    }
}
