package com.westflow.notification.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.model.NotificationLogRecord;
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
 * 通知发送日志数据访问层，直接读写真实数据库。
 */
@Component
@RequiredArgsConstructor
public class NotificationLogMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void clear() {
        jdbcTemplate.update("DELETE FROM wf_notification_log");
    }

    public void insert(NotificationLogRecord record) {
        jdbcTemplate.update(
                """
                INSERT INTO wf_notification_log (
                  id,
                  channel_id,
                  channel_code,
                  channel_type,
                  recipient,
                  title,
                  content,
                  provider_name,
                  success,
                  status,
                  response_message,
                  payload_json,
                  sent_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.logId(),
                record.channelId(),
                record.channelCode(),
                record.channelType(),
                record.recipient(),
                record.title(),
                record.content(),
                record.providerName(),
                record.success(),
                record.status(),
                record.responseMessage(),
                serialize(record.payload()),
                toTimestamp(record.sentAt())
        );
    }

    public List<NotificationLogRecord> selectAll() {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_id,
                  channel_code,
                  channel_type,
                  recipient,
                  title,
                  content,
                  provider_name,
                  success,
                  status,
                  response_message,
                  payload_json,
                  sent_at
                FROM wf_notification_log
                ORDER BY sent_at DESC, id DESC
                """,
                this::mapRecord
        );
    }

    public List<NotificationLogRecord> selectByInstanceId(String instanceId) {
        String instancePattern = "%\"instanceId\":\"" + instanceId + "\"%";
        String processPattern = "%\"processInstanceId\":\"" + instanceId + "\"%";
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_id,
                  channel_code,
                  channel_type,
                  recipient,
                  title,
                  content,
                  provider_name,
                  success,
                  status,
                  response_message,
                  payload_json,
                  sent_at
                FROM wf_notification_log
                WHERE payload_json LIKE ?
                   OR payload_json LIKE ?
                ORDER BY sent_at ASC, id ASC
                """,
                this::mapRecord,
                instancePattern,
                processPattern
        );
    }

    public List<NotificationLogRecord> selectByChannelId(String channelId) {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  channel_id,
                  channel_code,
                  channel_type,
                  recipient,
                  title,
                  content,
                  provider_name,
                  success,
                  status,
                  response_message,
                  payload_json,
                  sent_at
                FROM wf_notification_log
                WHERE channel_id = ?
                ORDER BY sent_at DESC, id DESC
                """,
                this::mapRecord,
                channelId
        );
    }

    private NotificationLogRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new NotificationLogRecord(
                resultSet.getString("id"),
                resultSet.getString("channel_id"),
                resultSet.getString("channel_code"),
                resultSet.getString("channel_type"),
                resultSet.getString("recipient"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("provider_name"),
                resultSet.getBoolean("success"),
                resultSet.getString("status"),
                resultSet.getString("response_message"),
                deserialize(resultSet.getString("payload_json")),
                toInstant(resultSet.getTimestamp("sent_at"))
        );
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化通知发送日志 payload", exception);
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化通知发送日志 payload", exception);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
