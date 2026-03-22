package com.westflow.processbinding.service;

import com.westflow.common.error.ContractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = "DELETE FROM wf_business_process_binding", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BusinessProcessBindingServiceTest {

    @Autowired
    private BusinessProcessBindingService businessProcessBindingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_business_process_binding");
    }

    @Test
    void shouldResolveHighestPriorityEnabledBindingForBusinessTypeAndScene() {
        insertBinding("bind_001", "OA_LEAVE", "default", "oa_leave_low", false, 10);
        insertBinding("bind_002", "OA_LEAVE", "default", "oa_leave_high", true, 30);
        insertBinding("bind_003", "OA_LEAVE", "default", "oa_leave_mid", true, 20);

        assertThat(businessProcessBindingService.resolveProcessKey("OA_LEAVE", "default"))
                .isEqualTo("oa_leave_high");
    }

    @Test
    void shouldRejectMissingBindingWithBusinessError() {
        assertThatThrownBy(() -> businessProcessBindingService.resolveProcessKey("OA_COMMON", "default"))
                .isInstanceOf(ContractException.class)
                .satisfies(error -> assertThat(((ContractException) error).getCode())
                        .isEqualTo("BIZ.BUSINESS_PROCESS_BINDING_NOT_FOUND"));
    }

    private void insertBinding(
            String id,
            String businessType,
            String sceneCode,
            String processKey,
            boolean enabled,
            int priority
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO wf_business_process_binding (
                    id,
                    business_type,
                    scene_code,
                    process_key,
                    process_definition_id,
                    enabled,
                    priority,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                businessType,
                sceneCode,
                processKey,
                null,
                enabled,
                priority
        );
    }
}
