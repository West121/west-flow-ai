package com.westflow.orchestrator.repository;

import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 编排执行记录仓储，供轨迹查询和运行态回放复用。
 */
@Repository
@RequiredArgsConstructor
public class OrchestratorExecutionRepository {

    private final JdbcTemplate jdbcTemplate;

    public void clear() {
        jdbcTemplate.update("DELETE FROM wf_orchestrator_execution");
    }

    public void insert(OrchestratorScanExecutionRecord record) {
        jdbcTemplate.update(
                """
                INSERT INTO wf_orchestrator_execution (
                  id,
                  run_id,
                  target_id,
                  automation_type,
                  status,
                  message,
                  executed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                record.executionId(),
                record.runId(),
                record.targetId(),
                record.automationType() == null ? null : record.automationType().name(),
                record.status() == null ? null : record.status().name(),
                record.message(),
                toTimestamp(record.executedAt())
        );
    }

    public List<OrchestratorScanExecutionRecord> selectByTargetId(String targetId) {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  run_id,
                  target_id,
                  automation_type,
                  status,
                  message,
                  executed_at
                FROM wf_orchestrator_execution
                WHERE target_id = ?
                ORDER BY executed_at ASC, id ASC
                """,
                this::mapRecord,
                targetId
        );
    }

    public long countSucceededByTargetId(String targetId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_orchestrator_execution
                WHERE target_id = ?
                  AND status = ?
                """,
                Integer.class,
                targetId,
                OrchestratorExecutionStatus.SUCCEEDED.name()
        );
        return count == null ? 0L : count;
    }

    public List<OrchestratorScanExecutionRecord> selectByInstanceId(String instanceId) {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  run_id,
                  target_id,
                  automation_type,
                  status,
                  message,
                  executed_at
                FROM wf_orchestrator_execution
                WHERE target_id LIKE ?
                ORDER BY executed_at ASC, id ASC
                """,
                this::mapRecord,
                "orc_target_" + instanceId + "_%"
        );
    }

    private OrchestratorScanExecutionRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new OrchestratorScanExecutionRecord(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("target_id"),
                parseAutomationType(resultSet.getString("automation_type")),
                parseStatus(resultSet.getString("status")),
                resultSet.getString("message"),
                toInstant(resultSet.getTimestamp("executed_at"))
        );
    }

    private OrchestratorAutomationType parseAutomationType(String value) {
        return value == null || value.isBlank() ? null : OrchestratorAutomationType.valueOf(value);
    }

    private OrchestratorExecutionStatus parseStatus(String value) {
        return value == null || value.isBlank() ? null : OrchestratorExecutionStatus.valueOf(value);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
