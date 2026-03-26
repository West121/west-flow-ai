package com.westflow.ai.mapper;

import com.westflow.ai.model.AiMessageRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
 * AI 会话消息映射。
 */
@Mapper
public interface AiMessageMapper {

    /**
     * 查询会话消息历史。
     */
    @Select("""
            SELECT
              id AS messageId,
              conversation_id AS conversationId,
              role,
              author_name AS authorName,
              content,
              blocks_json AS blocksJson,
              operator_user_id AS operatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_message
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at ASC,
                     CASE role
                         WHEN 'user' THEN 0
                         WHEN 'assistant' THEN 1
                         ELSE 2
                     END ASC,
                     id ASC
            """)
    List<AiMessageRecord> selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 统计会话消息数量。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_message
            WHERE conversation_id = #{conversationId}
            """)
    long countByConversationId(@Param("conversationId") String conversationId);

    /**
     * 插入消息。
     */
    @Insert("""
            INSERT INTO wf_ai_message (
              id,
              conversation_id,
              role,
              author_name,
              content,
              blocks_json,
              operator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{messageId},
              #{conversationId},
              #{role},
              #{authorName},
              #{content},
              #{blocksJson},
              #{operatorUserId},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insertMessage(AiMessageRecord record);

    /**
     * 删除会话下的全部消息。
     */
    @Delete("""
            DELETE FROM wf_ai_message
            WHERE conversation_id = #{conversationId}
            """)
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
