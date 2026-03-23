package com.westflow.system.message.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.system.message.model.SystemMessageRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 站内消息数据访问层，直接读写真实数据库。
 */
@Component
@RequiredArgsConstructor
public class SystemMessageMapper {

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void clear() {
        jdbcTemplate.update("DELETE FROM wf_system_message_read");
        jdbcTemplate.update("DELETE FROM wf_system_message");
    }

    public void upsert(SystemMessageRecord record) {
        if (selectById(record.messageId()) == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO wf_system_message (
                      id,
                      title,
                      content,
                      status,
                      target_type,
                      target_user_ids_json,
                      target_department_ids_json,
                      sender_user_id,
                      sent_at,
                      created_at,
                      updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.messageId(),
                    record.title(),
                    record.content(),
                    record.status(),
                    record.targetType(),
                    serializeList(record.targetUserIds()),
                    serializeList(record.targetDepartmentIds()),
                    record.senderUserId(),
                    toTimestamp(record.sentAt()),
                    toTimestamp(record.createdAt()),
                    toTimestamp(record.updatedAt())
            );
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE wf_system_message
                   SET title = ?,
                       content = ?,
                       status = ?,
                       target_type = ?,
                       target_user_ids_json = ?,
                       target_department_ids_json = ?,
                       sender_user_id = ?,
                       sent_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                record.title(),
                record.content(),
                record.status(),
                record.targetType(),
                serializeList(record.targetUserIds()),
                serializeList(record.targetDepartmentIds()),
                record.senderUserId(),
                toTimestamp(record.sentAt()),
                toTimestamp(record.updatedAt()),
                record.messageId()
        );
    }

    public SystemMessageRecord selectById(String messageId) {
        List<SystemMessageRecord> records = jdbcTemplate.query(
                """
                SELECT
                  id,
                  title,
                  content,
                  status,
                  target_type,
                  target_user_ids_json,
                  target_department_ids_json,
                  sender_user_id,
                  sent_at,
                  created_at,
                  updated_at
                FROM wf_system_message
                WHERE id = ?
                """,
                this::mapRecord,
                messageId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    public List<SystemMessageRecord> selectAll() {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  title,
                  content,
                  status,
                  target_type,
                  target_user_ids_json,
                  target_department_ids_json,
                  sender_user_id,
                  sent_at,
                  created_at,
                  updated_at
                FROM wf_system_message
                ORDER BY created_at DESC, id DESC
                """,
                this::mapRecord
        );
    }

    public boolean hasRead(String messageId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_system_message_read
                WHERE message_id = ?
                  AND user_id = ?
                """,
                Integer.class,
                messageId,
                userId
        );
        return count != null && count > 0;
    }

    public void markRead(String messageId, String userId) {
        if (hasRead(messageId, userId)) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO wf_system_message_read (
                  id,
                  message_id,
                  user_id,
                  read_at
                ) VALUES (?, ?, ?, ?)
                """,
                buildReadId(messageId, userId),
                messageId,
                userId,
                toTimestamp(Instant.now())
        );
    }

    private SystemMessageRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new SystemMessageRecord(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("status"),
                resultSet.getString("target_type"),
                deserializeList(resultSet.getString("target_user_ids_json")),
                deserializeList(resultSet.getString("target_department_ids_json")),
                resultSet.getString("sender_user_id"),
                toInstant(resultSet.getTimestamp("sent_at")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private String serializeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化站内消息目标列表", exception);
        }
    }

    private List<String> deserializeList(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化站内消息目标列表", exception);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String buildReadId(String messageId, String userId) {
        return "msg_read_" + Integer.toHexString((messageId + ":" + userId).hashCode());
    }
}
