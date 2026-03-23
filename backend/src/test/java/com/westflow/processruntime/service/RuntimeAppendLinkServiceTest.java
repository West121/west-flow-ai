package com.westflow.processruntime.service;

import com.westflow.processruntime.mapper.RuntimeAppendLinkMapper;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAppendLinkServiceTest {

    @Mock
    private RuntimeAppendLinkMapper runtimeAppendLinkMapper;

    @InjectMocks
    private RuntimeAppendLinkService runtimeAppendLinkService;

    @Test
    void shouldCreateAndQueryRuntimeAppendLinks() {
        RuntimeAppendLinkRecord record = record("append_1", "root_1", "parent_1");
        when(runtimeAppendLinkMapper.selectByRootInstanceId("root_1")).thenReturn(List.of(record));

        runtimeAppendLinkService.createLink(record);

        verify(runtimeAppendLinkMapper).insert(record);

        List<RuntimeAppendLinkRecord> records = runtimeAppendLinkService.listByRootInstanceId("root_1");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).id()).isEqualTo("append_1");
        verify(runtimeAppendLinkMapper).selectByRootInstanceId("root_1");
    }

    @Test
    void shouldUpdateRuntimeAppendLinkStatusByTargetTaskId() {
        Instant finishedAt = Instant.parse("2026-03-23T10:15:30Z");

        runtimeAppendLinkService.updateStatusByTargetTaskId("task_1", "COMPLETED", finishedAt);

        verify(runtimeAppendLinkMapper).updateStatusByTargetTaskId("task_1", "COMPLETED", finishedAt);
    }

    @Test
    void shouldUpdateRuntimeAppendLinkStatusByTargetInstanceId() {
        Instant finishedAt = Instant.parse("2026-03-23T10:15:30Z");

        runtimeAppendLinkService.updateStatusByTargetInstanceId("instance_1", "TERMINATED", finishedAt);

        verify(runtimeAppendLinkMapper).updateStatusByTargetInstanceId("instance_1", "TERMINATED", finishedAt);
    }

    private RuntimeAppendLinkRecord record(String id, String rootInstanceId, String parentInstanceId) {
        return new RuntimeAppendLinkRecord(
                id,
                rootInstanceId,
                parentInstanceId,
                "task_1",
                "node_1",
                "TASK",
                "ADHOC_TASK",
                "SERIAL_AFTER_CURRENT",
                "target_task_1",
                null,
                "usr_001",
                null,
                null,
                "RUNNING",
                "APPEND",
                "usr_001",
                "追加说明",
                Instant.parse("2026-03-23T10:00:00Z"),
                null
        );
    }
}
