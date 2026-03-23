package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.mapper.ProcessTerminationAuditMapper;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
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
class ProcessTerminationAuditServiceTest {

    @Autowired
    private ProcessTerminationAuditService processTerminationAuditService;

    @Autowired
    private ProcessTerminationAuditMapper processTerminationAuditMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_process_termination_audit");
    }

    @Test
    void shouldRecordAndQueryTerminationAuditTrail() {
        ProcessTerminationPlanResponse plan = new ProcessTerminationPlanResponse(
                "root_1",
                "child_1",
                ProcessTerminationScope.CHILD,
                ProcessTerminationPropagationPolicy.CASCADE_ALL,
                "测试终止原因",
                "usr_admin",
                Instant.parse("2026-03-23T08:00:00Z"),
                1,
                List.of()
        );

        processTerminationAuditService.recordPlan(
                plan,
                "SUBPROCESS",
                "root_1",
                ProcessTerminationAuditEventType.PLANNED,
                "PLANNED",
                "{\"scope\":\"CHILD\"}"
        );

        assertThat(processTerminationAuditMapper.selectByRootInstanceId("root_1")).singleElement().satisfies(record -> {
            assertThat(record.rootInstanceId()).isEqualTo("root_1");
            assertThat(record.targetInstanceId()).isEqualTo("child_1");
            assertThat(record.reason()).isEqualTo("测试终止原因");
            assertThat(record.eventType()).isEqualTo(ProcessTerminationAuditEventType.PLANNED);
        });

        assertThat(processTerminationAuditService.listByRootInstanceId("root_1")).hasSize(1);
        assertThat(processTerminationAuditService.listByTargetInstanceId("child_1")).hasSize(1);
    }
}
