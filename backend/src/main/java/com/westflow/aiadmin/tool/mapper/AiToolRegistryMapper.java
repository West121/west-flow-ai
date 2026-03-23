package com.westflow.aiadmin.tool.mapper;

import com.westflow.aiadmin.tool.model.AiToolRegistryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 工具注册表映射。
 */
@Mapper
public interface AiToolRegistryMapper {

    /**
     * 查询全部工具注册记录。
     */
    @Select("""
            SELECT
              id AS toolId,
              tool_code AS toolCode,
              tool_name AS toolName,
              tool_category AS toolCategory,
              action_mode AS actionMode,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_tool_registry
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiToolRegistryRecord> selectAll();

    /**
     * 按 ID 查询工具注册记录。
     */
    @Select("""
            SELECT
              id AS toolId,
              tool_code AS toolCode,
              tool_name AS toolName,
              tool_category AS toolCategory,
              action_mode AS actionMode,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_tool_registry
            WHERE id = #{toolId}
            """)
    AiToolRegistryRecord selectById(@Param("toolId") String toolId);

    /**
     * 按编码检查是否重复。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_tool_registry
            WHERE tool_code = #{toolCode}
              AND (#{excludeToolId} IS NULL OR id <> #{excludeToolId})
            """)
    Long countByToolCode(@Param("toolCode") String toolCode, @Param("excludeToolId") String excludeToolId);

    /**
     * 新增工具注册记录。
     */
    @Insert("""
            INSERT INTO wf_ai_tool_registry (
              id,
              tool_code,
              tool_name,
              tool_category,
              action_mode,
              required_capability_code,
              enabled,
              metadata_json,
              created_at,
              updated_at
            ) VALUES (
              #{toolId},
              #{toolCode},
              #{toolName},
              #{toolCategory},
              #{actionMode},
              #{requiredCapabilityCode},
              #{enabled},
              #{metadataJson},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(AiToolRegistryRecord record);

    /**
     * 更新工具注册记录。
     */
    @Update("""
            UPDATE wf_ai_tool_registry
            SET tool_code = #{toolCode},
                tool_name = #{toolName},
                tool_category = #{toolCategory},
                action_mode = #{actionMode},
                required_capability_code = #{requiredCapabilityCode},
                enabled = #{enabled},
                metadata_json = #{metadataJson},
                updated_at = #{updatedAt}
            WHERE id = #{toolId}
            """)
    int update(AiToolRegistryRecord record);
}
