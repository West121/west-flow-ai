package com.westflow.aimcpdemo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * MCP Demo 查询服务。
 */
@Service
public class AiMcpDemoQueryService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public AiMcpDemoQueryService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.system(SHANGHAI_ZONE));
    }

    private AiMcpDemoQueryService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * 查询本地时间快照。
     */
    public Map<String, Object> currentTimeSnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", SHANGHAI_ZONE.getId());
        payload.put("isoTime", now.atZone(SHANGHAI_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("displayTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)));
        payload.put("weekday", now.getDayOfWeek().name());
        return payload;
    }

    /**
     * 查询本地审批配置摘要。
     */
    public Map<String, Object> approvalSummary(String processKey, String category, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalizedProcessKey = normalize(processKey);
        String normalizedCategory = normalize(category);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestedProcessKey", normalizedProcessKey);
        payload.put("requestedCategory", normalizedCategory);
        payload.put("publishedDefinitionCount", scalarCount("SELECT COUNT(*) FROM wf_process_definition WHERE status = 'PUBLISHED'"));
        payload.put("bindingCount", scalarCount("SELECT COUNT(*) FROM wf_business_process_binding"));
        payload.put("approvalOpinionConfigCount", scalarCount("SELECT COUNT(*) FROM wf_approval_opinion_config"));
        payload.put("taskGroupCount", scalarCount("SELECT COUNT(*) FROM wf_task_group"));
        payload.put("taskVoteSnapshotCount", scalarCount("SELECT COUNT(*) FROM wf_task_vote_snapshot"));
        payload.put("recentPublishedDefinitions", recentPublishedDefinitions(normalizedProcessKey, normalizedCategory, safeLimit));
        return payload;
    }

    private List<Map<String, Object>> recentPublishedDefinitions(String processKey, String category, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT process_key, process_name, category, version, status, updated_at
                FROM wf_process_definition
                WHERE status = 'PUBLISHED'
                """);
        List<Object> params = new java.util.ArrayList<>();
        if (!processKey.isBlank()) {
            sql.append(" AND process_key = ? ");
            params.add(processKey);
        }
        if (!category.isBlank()) {
            sql.append(" AND category = ? ");
            params.add(category);
        }
        sql.append(" ORDER BY updated_at DESC, version DESC LIMIT ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (resultSet, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("processKey", resultSet.getString("process_key"));
            row.put("processName", resultSet.getString("process_name"));
            row.put("category", resultSet.getString("category"));
            row.put("version", resultSet.getInt("version"));
            row.put("status", resultSet.getString("status"));
            row.put("updatedAt", Objects.toString(resultSet.getTimestamp("updated_at"), null));
            return row;
        });
    }

    private int scalarCount(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
