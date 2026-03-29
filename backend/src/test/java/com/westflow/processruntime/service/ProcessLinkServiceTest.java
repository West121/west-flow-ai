package com.westflow.processruntime.service;

import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.mapper.ProcessLinkMapper;
import com.westflow.processruntime.model.ProcessLinkRecord;
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
class ProcessLinkServiceTest {

    @Mock
    private ProcessLinkMapper processLinkMapper;

    @InjectMocks
    private ProcessLinkService processLinkService;

    @Test
    void shouldInsertAndQueryProcessLinksByParentInstance() {
        ProcessLinkRecord record = record("plink_001", "root_001", "parent_001", "child_001");
        when(processLinkMapper.selectByParentInstanceId("parent_001")).thenReturn(List.of(record));

        processLinkService.createLink(record);

        verify(processLinkMapper).insert(record);

        List<ProcessLinkRecord> links = processLinkService.listByParentInstanceId("parent_001");

        assertThat(links).hasSize(1);
        assertThat(links.get(0).childInstanceId()).isEqualTo("child_001");
        assertThat(links.get(0).calledProcessKey()).isEqualTo("plm_purchase_review");
        assertThat(links.get(0).status()).isEqualTo("RUNNING");
    }

    @Test
    void shouldUpdateLinkStatusToTerminated() {
        ProcessLinkRecord record = record("plink_002", "root_001", "parent_001", "child_002");
        when(processLinkMapper.selectByChildInstanceId("child_002")).thenReturn(record);

        processLinkService.updateStatus("child_002", "TERMINATED", Instant.parse("2026-03-23T08:30:00Z"));

        verify(processLinkMapper).updateStatus("child_002", "TERMINATED", Instant.parse("2026-03-23T08:30:00Z"));
        ProcessLinkRecord selected = processLinkService.getByChildInstanceId("child_002");
        assertThat(selected).isEqualTo(record);
    }

    @Test
    void shouldResolveRootInstanceIdFromChildLink() {
        when(processLinkMapper.selectByChildInstanceId("child_003")).thenReturn(record("plink_003", "root_001", "parent_001", "child_003"));

        assertThat(processLinkService.resolveRootInstanceId("child_003")).isEqualTo("root_001");
        assertThat(processLinkService.resolveRootInstanceId("root_001")).isEqualTo("root_001");
    }

    private ProcessLinkRecord record(String id, String rootInstanceId, String parentInstanceId, String childInstanceId) {
        return new ProcessLinkRecord(
                id,
                rootInstanceId,
                parentInstanceId,
                childInstanceId,
                "call_activity_001",
                "plm_purchase_review",
                "plm_purchase_review:3",
                "CALL_ACTIVITY",
                "RUNNING",
                "TERMINATE_SUBPROCESS_ONLY",
                "RETURN_TO_PARENT",
                "CHILD_ONLY",
                "AUTO_RETURN",
                "LATEST_PUBLISHED",
                "AUTO_RETURN",
                Instant.parse("2026-03-23T08:00:00Z"),
                null
        );
    }
}
