package com.westflow.system.notification.template.mapper;

import com.westflow.system.notification.template.model.NotificationTemplateRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知模板数据访问层，直接读写真实数据库。
 */
@Component
@RequiredArgsConstructor
public class NotificationTemplateMapper {

    private final JdbcTemplate jdbcTemplate;

    public void clear() {
        jdbcTemplate.update("DELETE FROM wf_notification_template");
    }

    public void upsert(NotificationTemplateRecord record) {
        if (selectById(record.templateId()) == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO wf_notification_template (
                      id,
                      template_code,
                      template_name,
                      channel_type,
                      title_template,
                      content_template,
                      remark,
                      enabled,
                      created_at,
                      updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.templateId(),
                    record.templateCode(),
                    record.templateName(),
                    record.channelType(),
                    record.titleTemplate(),
                    record.contentTemplate(),
                    record.remark(),
                    record.enabled(),
                    toTimestamp(record.createdAt()),
                    toTimestamp(record.updatedAt())
            );
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE wf_notification_template
                   SET template_code = ?,
                       template_name = ?,
                       channel_type = ?,
                       title_template = ?,
                       content_template = ?,
                       remark = ?,
                       enabled = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                record.templateCode(),
                record.templateName(),
                record.channelType(),
                record.titleTemplate(),
                record.contentTemplate(),
                record.remark(),
                record.enabled(),
                toTimestamp(record.updatedAt()),
                record.templateId()
        );
    }

    public NotificationTemplateRecord selectById(String templateId) {
        List<NotificationTemplateRecord> records = jdbcTemplate.query(
                """
                SELECT
                  id,
                  template_code,
                  template_name,
                  channel_type,
                  title_template,
                  content_template,
                  remark,
                  enabled,
                  created_at,
                  updated_at
                FROM wf_notification_template
                WHERE id = ?
                """,
                this::mapRecord,
                templateId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    public NotificationTemplateRecord selectByCode(String templateCode) {
        List<NotificationTemplateRecord> records = jdbcTemplate.query(
                """
                SELECT
                  id,
                  template_code,
                  template_name,
                  channel_type,
                  title_template,
                  content_template,
                  remark,
                  enabled,
                  created_at,
                  updated_at
                FROM wf_notification_template
                WHERE template_code = ?
                """,
                this::mapRecord,
                templateCode
        );
        return records.isEmpty() ? null : records.get(0);
    }

    public List<NotificationTemplateRecord> selectAll() {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  template_code,
                  template_name,
                  channel_type,
                  title_template,
                  content_template,
                  remark,
                  enabled,
                  created_at,
                  updated_at
                FROM wf_notification_template
                ORDER BY created_at DESC, id DESC
                """,
                this::mapRecord
        );
    }

    public boolean existsByCode(String templateCode, String excludeTemplateId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_notification_template
                WHERE template_code = ?
                  AND (? IS NULL OR id <> ?)
                """,
                Integer.class,
                templateCode,
                excludeTemplateId,
                excludeTemplateId
        );
        return count != null && count > 0;
    }

    private NotificationTemplateRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new NotificationTemplateRecord(
                resultSet.getString("id"),
                resultSet.getString("template_code"),
                resultSet.getString("template_name"),
                resultSet.getString("channel_type"),
                resultSet.getString("title_template"),
                resultSet.getString("content_template"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("remark"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
