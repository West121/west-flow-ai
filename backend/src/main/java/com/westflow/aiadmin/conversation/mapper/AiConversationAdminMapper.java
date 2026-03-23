package com.westflow.aiadmin.conversation.mapper;

import com.westflow.aiadmin.conversation.model.AiConversationAdminRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 会话管理映射。
 */
@Mapper
public interface AiConversationAdminMapper {

    /**
     * 查询全部会话记录。
     */
    @Select("""
            SELECT
              id AS conversationId,
              title,
              preview,
              status,
              context_tags_json AS contextTagsJson,
              message_count AS messageCount,
              operator_user_id AS operatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_conversation
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiConversationAdminRecord> selectAll();

    /**
     * 按 ID 查询会话记录。
     */
    @Select("""
            SELECT
              id AS conversationId,
              title,
              preview,
              status,
              context_tags_json AS contextTagsJson,
              message_count AS messageCount,
              operator_user_id AS operatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_conversation
            WHERE id = #{conversationId}
            """)
    AiConversationAdminRecord selectById(@Param("conversationId") String conversationId);
}
