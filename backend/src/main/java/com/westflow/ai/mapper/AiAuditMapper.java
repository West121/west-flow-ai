package com.westflow.ai.mapper;

import com.westflow.ai.model.AiAuditRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
 * AI 审计轨迹映射。
 */
@Mapper
public interface AiAuditMapper {

    /**
     * 查询会话的审计轨迹。
     */
    @Select("""
            SELECT
              id AS auditId,
              conversation_id AS conversationId,
              tool_call_id AS toolCallId,
              action_type AS actionType,
              summary,
              operator_user_id AS operatorUserId,
              occurred_at AS occurredAt
            FROM wf_ai_audit
            WHERE conversation_id = #{conversationId}
            ORDER BY occurred_at ASC, id ASC
            """)
    List<AiAuditRecord> selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 插入审计轨迹。
     */
    @Insert("""
            INSERT INTO wf_ai_audit (
              id,
              conversation_id,
              tool_call_id,
              action_type,
              summary,
              operator_user_id,
              occurred_at
            ) VALUES (
              #{auditId},
              #{conversationId},
              #{toolCallId},
              #{actionType},
              #{summary},
              #{operatorUserId},
              #{occurredAt}
            )
            """)
    int insertAudit(AiAuditRecord record);

    /**
     * 删除会话下的全部审计轨迹。
     */
    @Delete("""
            DELETE FROM wf_ai_audit
            WHERE conversation_id = #{conversationId}
            """)
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
