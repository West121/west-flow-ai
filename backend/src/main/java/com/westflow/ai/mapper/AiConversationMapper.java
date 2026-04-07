package com.westflow.ai.mapper;

import com.westflow.ai.model.AiConversationRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 会话持久化映射。
 */
@Mapper
public interface AiConversationMapper {

    /**
     * 分页查询会话。
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
            WHERE operator_user_id = #{operatorUserId}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(preview) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(COALESCE(context_tags_json, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
              )
            ORDER BY updated_at DESC, created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AiConversationRecord> selectPage(
            @Param("operatorUserId") String operatorUserId,
            @Param("keyword") String keyword,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    /**
     * 统计会话数量。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_conversation
            WHERE operator_user_id = #{operatorUserId}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(preview) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(COALESCE(context_tags_json, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
              )
            """)
    long countPage(@Param("operatorUserId") String operatorUserId, @Param("keyword") String keyword);

    /**
     * 按 ID 查询会话。
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
    AiConversationRecord selectById(@Param("conversationId") String conversationId);

    /**
     * 插入会话记录。
     */
    @Insert("""
            INSERT INTO wf_ai_conversation (
              id,
              title,
              preview,
              status,
              context_tags_json,
              message_count,
              operator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{conversationId},
              #{title},
              #{preview},
              #{status},
              #{contextTagsJson},
              #{messageCount},
              #{operatorUserId},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insertConversation(AiConversationRecord record);

    /**
     * 更新会话摘要信息。
     */
    @Update("""
            UPDATE wf_ai_conversation
            SET preview = #{preview},
                status = #{status},
                message_count = #{messageCount},
                updated_at = #{updatedAt}
            WHERE id = #{conversationId}
            """)
    int updateConversationSnapshot(
            @Param("conversationId") String conversationId,
            @Param("preview") String preview,
            @Param("status") String status,
            @Param("messageCount") int messageCount,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 删除会话记录。
     */
    @Delete("""
            DELETE FROM wf_ai_conversation
            WHERE id = #{conversationId}
            """)
    int deleteConversation(@Param("conversationId") String conversationId);

    /**
     * 查询当前用户全部会话。
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
            WHERE operator_user_id = #{operatorUserId}
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiConversationRecord> selectByOperatorUserId(@Param("operatorUserId") String operatorUserId);

    /**
     * 删除当前用户的全部会话。
     */
    @Delete("""
            DELETE FROM wf_ai_conversation
            WHERE operator_user_id = #{operatorUserId}
            """)
    int deleteByOperatorUserId(@Param("operatorUserId") String operatorUserId);
}
