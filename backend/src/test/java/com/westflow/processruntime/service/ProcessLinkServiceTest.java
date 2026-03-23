package com.westflow.processruntime.service;

import com.westflow.processruntime.model.ProcessLinkRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessLinkServiceTest {

    @Autowired
    private ProcessLinkService processLinkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_process_link");
    }

    @Test
    void shouldInsertAndQueryProcessLinksByParentInstance() {
        processLinkService.createLink(new ProcessLinkRecord(
                "plink_001",
                "root_001",
                "parent_001",
                "child_001",
                "call_activity_001",
                "plm_purchase_review",
                "plm_purchase_review:3",
                "CALL_ACTIVITY",
                "RUNNING",
                "TERMINATE_SUBPROCESS_ONLY",
                "RETURN_TO_PARENT",
                Instant.parse("2026-03-23T08:00:00Z"),
                null
        ));

        List<ProcessLinkRecord> links = processLinkService.listByParentInstanceId("parent_001");
        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.childInstanceId()).isEqualTo("child_001");
            assertThat(link.calledProcessKey()).isEqualTo("plm_purchase_review");
            assertThat(link.status()).isEqualTo("RUNNING");
        });
    }

    @Test
    void shouldUpdateLinkStatusToTerminated() {
        processLinkService.createLink(new ProcessLinkRecord(
                "plink_002",
                "root_001",
                "parent_001",
                "child_002",
                "call_activity_002",
                "oa_leave_subflow",
                "oa_leave_subflow:1",
                "CALL_ACTIVITY",
                "RUNNING",
                "TERMINATE_PARENT_AND_SUBPROCESS",
                "TERMINATE_PARENT",
                Instant.parse("2026-03-23T08:10:00Z"),
                null
        ));

        processLinkService.updateStatus("child_002", "TERMINATED", Instant.parse("2026-03-23T08:30:00Z"));

        ProcessLinkRecord record = processLinkService.getByChildInstanceId("child_002");
        assertThat(record).isNotNull();
        assertThat(record.status()).isEqualTo("TERMINATED");
        assertThat(record.finishedAt()).isEqualTo(Instant.parse("2026-03-23T08:30:00Z"));
    }
}
