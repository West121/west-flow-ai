package com.westflow.ai.mapper;

import com.westflow.ai.model.AiToolCallRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 工具调用映射。
 */
@Mapper
public interface AiToolCallMapper {

    /**
     * 查询会话下的工具调用。
     */
    @Select("""
            SELECT
              id AS toolCallId,
              conversation_id AS conversationId,
              tool_key AS toolKey,
              tool_type AS toolType,
              tool_source AS toolSource,
              status,
              requires_confirmation AS requiresConfirmation,
              arguments_json AS argumentsJson,
              result_json AS resultJson,
              summary,
              confirmation_id AS confirmationId,
              operator_user_id AS operatorUserId,
              created_at AS createdAt,
              completed_at AS completedAt
            FROM wf_ai_tool_call
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<AiToolCallRecord> selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 按 ID 查询工具调用。
     */
    @Select("""
            SELECT
              id AS toolCallId,
              conversation_id AS conversationId,
              tool_key AS toolKey,
              tool_type AS toolType,
              tool_source AS toolSource,
              status,
              requires_confirmation AS requiresConfirmation,
              arguments_json AS argumentsJson,
              result_json AS resultJson,
              summary,
              confirmation_id AS confirmationId,
              operator_user_id AS operatorUserId,
              created_at AS createdAt,
              completed_at AS completedAt
            FROM wf_ai_tool_call
            WHERE id = #{toolCallId}
            """)
    AiToolCallRecord selectById(@Param("toolCallId") String toolCallId);

    /**
     * 插入工具调用。
     */
    @Insert("""
            INSERT INTO wf_ai_tool_call (
              id,
              conversation_id,
              tool_key,
              tool_type,
              tool_source,
              status,
              requires_confirmation,
              arguments_json,
              result_json,
              summary,
              confirmation_id,
              operator_user_id,
              created_at,
              completed_at
            ) VALUES (
              #{toolCallId},
              #{conversationId},
              #{toolKey},
              #{toolType},
              #{toolSource},
              #{status},
              #{requiresConfirmation},
              #{argumentsJson},
              #{resultJson},
              #{summary},
              #{confirmationId},
              #{operatorUserId},
              #{createdAt},
              #{completedAt}
            )
            """)
    int insertToolCall(AiToolCallRecord record);

    /**
     * 更新工具调用的最终状态。
     */
    @Update("""
            UPDATE wf_ai_tool_call
            SET status = #{status},
                requires_confirmation = #{requiresConfirmation},
                confirmation_id = #{confirmationId},
                result_json = #{resultJson},
                summary = #{summary},
                completed_at = #{completedAt}
            WHERE id = #{toolCallId}
            """)
    int updateToolCallResult(
            @Param("toolCallId") String toolCallId,
            @Param("status") String status,
            @Param("requiresConfirmation") boolean requiresConfirmation,
            @Param("confirmationId") String confirmationId,
            @Param("resultJson") String resultJson,
            @Param("summary") String summary,
            @Param("completedAt") LocalDateTime completedAt
    );

    /**
     * 删除会话下的全部工具调用。
     */
    @Delete("""
            DELETE FROM wf_ai_tool_call
            WHERE conversation_id = #{conversationId}
            """)
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
