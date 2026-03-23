package com.westflow.aiadmin.toolcall.mapper;

import com.westflow.aiadmin.toolcall.model.AiToolCallAdminRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 工具调用管理映射。
 */
@Mapper
public interface AiToolCallAdminMapper {

    /**
     * 查询全部工具调用记录。
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
            ORDER BY created_at DESC, id DESC
            """)
    List<AiToolCallAdminRecord> selectAll();

    /**
     * 按 ID 查询工具调用记录。
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
    AiToolCallAdminRecord selectById(@Param("toolCallId") String toolCallId);
}
