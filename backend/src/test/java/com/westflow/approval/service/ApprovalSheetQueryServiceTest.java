package com.westflow.approval.service;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批单业务数据查询服务测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovalSheetQueryServiceTest {

    @Autowired
    private ApprovalSheetQueryService approvalSheetQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_ecr_change (
                    id VARCHAR(64) PRIMARY KEY,
                    bill_no VARCHAR(64) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    change_title VARCHAR(255) NOT NULL,
                    change_reason CLOB NOT NULL,
                    affected_product_code VARCHAR(64),
                    priority_level VARCHAR(32),
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("DELETE FROM plm_ecr_change");
        jdbcTemplate.update("""
                INSERT INTO plm_ecr_change (
                    id,
                    bill_no,
                    scene_code,
                    change_title,
                    change_reason,
                    affected_product_code,
                    priority_level,
                    process_instance_id,
                    status,
                    creator_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "ecr_001",
                "ECR-20260323-000001",
                "default",
                "升级物料版本",
                "客户要求",
                "PRD-1001",
                "HIGH",
                "pi_ecr_001",
                "RUNNING",
                "usr_001"
        );
    }

    @Test
    void shouldResolvePlmEcrBusinessData() {
        Map<String, Object> data = approvalSheetQueryService.resolveBusinessData("PLM_ECR", "ecr_001");
        assertThat(data.get("billNo")).isEqualTo("ECR-20260323-000001");
        assertThat(data.get("changeTitle")).isEqualTo("升级物料版本");
        assertThat(data.get("affectedProductCode")).isEqualTo("PRD-1001");
        assertThat(data.get("priorityLevel")).isEqualTo("HIGH");
    }
}
