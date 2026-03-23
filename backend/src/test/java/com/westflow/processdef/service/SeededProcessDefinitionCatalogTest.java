package com.westflow.processdef.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断言内置 OA / PLM 流程定义种子已经准备好，便于本地启动后直接测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/migration/V1__init.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SeededProcessDefinitionCatalogTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSeedOaAndPlmPublishedDefinitions() {
        List<String> processKeys = jdbcTemplate.queryForList(
                """
                SELECT process_key
                FROM wf_process_definition
                WHERE status = 'PUBLISHED'
                  AND process_key IN ('oa_leave', 'oa_expense', 'oa_common', 'plm_ecr', 'plm_eco', 'plm_material_change')
                ORDER BY process_key
                """,
                String.class
        );

        assertThat(processKeys).containsExactly(
                "oa_common",
                "oa_expense",
                "oa_leave",
                "plm_eco",
                "plm_ecr",
                "plm_material_change"
        );
    }
}
