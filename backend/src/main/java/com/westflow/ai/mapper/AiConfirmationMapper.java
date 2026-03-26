package com.westflow.ai.mapper;

import com.westflow.ai.model.AiConfirmationRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 确认记录映射。
 */
@Mapper
public interface AiConfirmationMapper {

    /**
     * 按确认 ID 查询记录。
     */
    @Select("""
            SELECT
              id AS confirmationId,
              tool_call_id AS toolCallId,
              status,
              approved,
              comment,
              resolved_by AS resolvedBy,
              created_at AS createdAt,
              resolved_at AS resolvedAt,
              updated_at AS updatedAt
            FROM wf_ai_confirmation
            WHERE id = #{confirmationId}
            """)
    AiConfirmationRecord selectById(@Param("confirmationId") String confirmationId);

    /**
     * 按工具调用查询确认记录。
     */
    @Select("""
            SELECT
              id AS confirmationId,
              tool_call_id AS toolCallId,
              status,
              approved,
              comment,
              resolved_by AS resolvedBy,
              created_at AS createdAt,
              resolved_at AS resolvedAt,
              updated_at AS updatedAt
            FROM wf_ai_confirmation
            WHERE tool_call_id = #{toolCallId}
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiConfirmationRecord> selectByToolCallId(@Param("toolCallId") String toolCallId);

    /**
     * 插入确认记录。
     */
    @Insert("""
            INSERT INTO wf_ai_confirmation (
              id,
              tool_call_id,
              status,
              approved,
              comment,
              resolved_by,
              created_at,
              resolved_at,
              updated_at
            ) VALUES (
              #{confirmationId},
              #{toolCallId},
              #{status},
              #{approved},
              #{comment},
              #{resolvedBy},
              #{createdAt},
              #{resolvedAt},
              #{updatedAt}
            )
            """)
    int insertConfirmation(AiConfirmationRecord record);

    /**
     * 更新确认状态。
     */
    @Update("""
            UPDATE wf_ai_confirmation
            SET status = #{status},
                approved = #{approved},
                comment = #{comment},
                resolved_by = #{resolvedBy},
                resolved_at = #{resolvedAt},
                updated_at = #{updatedAt}
            WHERE id = #{confirmationId}
            """)
    int updateConfirmation(AiConfirmationRecord record);

    /**
     * 删除会话下工具调用关联的全部确认记录。
     */
    @Delete("""
            DELETE FROM wf_ai_confirmation
            WHERE tool_call_id IN (
              SELECT id FROM wf_ai_tool_call WHERE conversation_id = #{conversationId}
            )
            """)
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
