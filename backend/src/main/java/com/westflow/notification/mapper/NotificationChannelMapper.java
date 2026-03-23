package com.westflow.notification.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知渠道数据访问层，直接读写真实数据库。
 */
@Component
@RequiredArgsConstructor
public class NotificationChannelMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void clear() {
        jdbcTemplate.update("DELETE FROM wf_notification_channel");
    }

    public void upsert(NotificationChannelRecord record) {
        if (selectById(record.channelId()) == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO wf_notification_channel (
                      id,
                      channel_code,
                      channel_type,
                      channel_name,
                      enabled,
                      mock_mode,
                      config_json,
                      remark,
                      last_sent_at,
                      created_at,
                      updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.channelId(),
                    record.channelCode(),
                    record.channelType(),
                    record.channelName(),
                    record.enabled(),
                    record.mockMode(),
                    serialize(record.config()),
                    record.remark(),
                    toTimestamp(record.lastSentAt()),
                    toTimestamp(record.createdAt()),
                    toTimestamp(record.updatedAt())
            );
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE wf_notification_channel
                   SET channel_code = ?,
                       channel_type = ?,
                       channel_name = ?,
                       enabled = ?,
                       mock_mode = ?,
                       config_json = ?,
                       remark = ?,
                       last_sent_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                record.channelCode(),
                record.channelType(),
                record.channelName(),
                record.enabled(),
                record.mockMode(),
                serialize(record.config()),
                record.remark(),
                toTimestamp(record.lastSentAt()),
                toTimestamp(record.updatedAt()),
                record.channelId()
        );
    }

    public NotificationChannelRecord selectById(String channelId) {
        List<NotificationChannelRecord> records = jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_code,
                  channel_type,
                  channel_name,
                  enabled,
                  mock_mode,
                  config_json,
                  remark,
                  created_at,
                  updated_at,
                  last_sent_at
                FROM wf_notification_channel
                WHERE id = ?
                """,
                this::mapRecord,
                channelId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    public NotificationChannelRecord selectByCode(String channelCode) {
        List<NotificationChannelRecord> records = jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_code,
                  channel_type,
                  channel_name,
                  enabled,
                  mock_mode,
                  config_json,
                  remark,
                  created_at,
                  updated_at,
                  last_sent_at
                FROM wf_notification_channel
                WHERE channel_code = ?
                """,
                this::mapRecord,
                channelCode
        );
        return records.isEmpty() ? null : records.get(0);
    }

    public List<NotificationChannelRecord> selectAll() {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_code,
                  channel_type,
                  channel_name,
                  enabled,
                  mock_mode,
                  config_json,
                  remark,
                  created_at,
                  updated_at,
                  last_sent_at
                FROM wf_notification_channel
                ORDER BY created_at DESC, id DESC
                """,
                this::mapRecord
        );
    }

    public boolean existsByCode(String channelCode, String excludeChannelId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_notification_channel
                WHERE channel_code = ?
                  AND (? IS NULL OR id <> ?)
                """,
                Integer.class,
                channelCode,
                excludeChannelId,
                excludeChannelId
        );
        return count != null && count > 0;
    }

    public void markLastSentAt(String channelId, Instant lastSentAt) {
        jdbcTemplate.update(
                """
                UPDATE wf_notification_channel
                   SET last_sent_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                toTimestamp(lastSentAt),
                toTimestamp(lastSentAt),
                channelId
        );
    }

    private NotificationChannelRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new NotificationChannelRecord(
                resultSet.getString("id"),
                resultSet.getString("channel_code"),
                resultSet.getString("channel_type"),
                resultSet.getString("channel_name"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("mock_mode"),
                deserialize(resultSet.getString("config_json")),
                resultSet.getString("remark"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                toInstant(resultSet.getTimestamp("last_sent_at"))
        );
    }

    private String serialize(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Map.of() : config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化通知渠道配置", exception);
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化通知渠道配置", exception);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
