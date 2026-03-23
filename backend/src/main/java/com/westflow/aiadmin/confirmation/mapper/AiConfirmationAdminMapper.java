package com.westflow.aiadmin.confirmation.mapper;

import com.westflow.aiadmin.confirmation.model.AiConfirmationAdminRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 确认记录管理映射。
 */
@Mapper
public interface AiConfirmationAdminMapper {

    /**
     * 查询全部确认记录。
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
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiConfirmationAdminRecord> selectAll();

    /**
     * 按 ID 查询确认记录。
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
    AiConfirmationAdminRecord selectById(@Param("confirmationId") String confirmationId);
}
