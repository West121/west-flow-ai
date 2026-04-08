package com.westflow.processruntime.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.api.response.ProcessPredictionFeatureSnapshotResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeProcessPredictionSnapshotServiceTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private RuntimeProcessPredictionSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        snapshotService = new RuntimeProcessPredictionSnapshotService(jdbcTemplate, new ObjectMapper());
        jdbcTemplate.execute(
                """
                CREATE TABLE wf_process_prediction_snapshot (
                    id VARCHAR(64) PRIMARY KEY,
                    process_instance_id VARCHAR(64) NOT NULL,
                    task_id VARCHAR(64),
                    process_key VARCHAR(128) NOT NULL,
                    current_node_id VARCHAR(128),
                    business_type VARCHAR(64),
                    assignee_user_id VARCHAR(64),
                    organization_profile VARCHAR(255),
                    working_day_profile VARCHAR(64),
                    sample_profile VARCHAR(255),
                    sample_tier VARCHAR(64),
                    overdue_risk_level VARCHAR(32),
                    confidence VARCHAR(32),
                    historical_sample_size INTEGER NOT NULL DEFAULT 0,
                    outlier_filtered_sample_size INTEGER NOT NULL DEFAULT 0,
                    remaining_duration_minutes BIGINT,
                    current_elapsed_minutes BIGINT,
                    current_node_duration_p50_minutes BIGINT,
                    current_node_duration_p75_minutes BIGINT,
                    current_node_duration_p90_minutes BIGINT,
                    predicted_finish_time TIMESTAMP,
                    predicted_risk_threshold_time TIMESTAMP,
                    feature_json CLOB NOT NULL,
                    recorded_at TIMESTAMP NOT NULL
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE wf_process_prediction_aggregate (
                    id VARCHAR(64) PRIMARY KEY,
                    stat_date DATE NOT NULL,
                    process_key VARCHAR(128) NOT NULL,
                    current_node_id VARCHAR(128),
                    business_type VARCHAR(64),
                    organization_profile VARCHAR(255),
                    working_day_profile VARCHAR(64),
                    sample_tier VARCHAR(64),
                    overdue_risk_level VARCHAR(32),
                    sample_count INTEGER NOT NULL DEFAULT 0,
                    avg_remaining_duration_minutes BIGINT,
                    avg_current_elapsed_minutes BIGINT,
                    latest_p50_minutes BIGINT,
                    latest_p75_minutes BIGINT,
                    latest_p90_minutes BIGINT,
                    updated_at TIMESTAMP NOT NULL
                )
                """
        );
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void shouldDeduplicateSameSnapshotWithinWindow() {
        ProcessPredictionResponse prediction = prediction("task-001", "approve", "HIGH");

        snapshotService.recordSnapshot("pi-001", "task-001", prediction);
        snapshotService.recordSnapshot("pi-001", "task-001", prediction);

        assertThat(count("SELECT COUNT(1) FROM wf_process_prediction_snapshot")).isEqualTo(1);
        assertThat(count("SELECT COUNT(1) FROM wf_process_prediction_aggregate")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sample_count FROM wf_process_prediction_aggregate",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void shouldReuseNullNodeAggregateBucketAcrossDifferentTasks() {
        ProcessPredictionResponse first = prediction("task-001", null, "MEDIUM");
        ProcessPredictionResponse second = prediction("task-002", null, "MEDIUM");

        snapshotService.recordSnapshot("pi-002", "task-001", first);
        snapshotService.recordSnapshot("pi-002", "task-002", second);

        assertThat(count("SELECT COUNT(1) FROM wf_process_prediction_snapshot")).isEqualTo(2);
        assertThat(count("SELECT COUNT(1) FROM wf_process_prediction_aggregate")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sample_count FROM wf_process_prediction_aggregate",
                Integer.class
        )).isEqualTo(2);
    }

    private long count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0L : value.longValue();
    }

    private ProcessPredictionResponse prediction(String taskId, String currentNodeId, String riskLevel) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ProcessPredictionResponse(
                now.plusHours(4),
                now.plusHours(2),
                240L,
                60L,
                90L,
                120L,
                180L,
                riskLevel,
                "HIGH",
                12,
                10,
                "PROC::" + safe(currentNodeId) + "::weekday::" + taskId,
                "FLOW_NODE_DAY",
                "WEEKDAY",
                "ORG::DEFAULT",
                "历史中位时长与当前节点停留时长综合估算。",
                null,
                "预计仍需约 4 小时。",
                "当前节点已经接近历史高风险区间。",
                "审批节点停留时长偏长",
                List.of("当前节点停留偏长"),
                List.of("建议优先催办当前处理人"),
                List.of("考虑为该节点增加并行抄送"),
                List.of(),
                new ProcessPredictionFeatureSnapshotResponse(
                        "leave_approval",
                        currentNodeId,
                        "LEAVE",
                        "usr_002",
                        "ORG::DEFAULT",
                        "WEEKDAY",
                        "FLOW_NODE_DAY",
                        12,
                        10
                ),
                List.of()
        );
    }

    private String safe(String value) {
        return value == null ? "root" : value;
    }
}
