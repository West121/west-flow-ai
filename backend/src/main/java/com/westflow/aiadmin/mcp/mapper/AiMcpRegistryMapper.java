package com.westflow.aiadmin.mcp.mapper;

import com.westflow.aiadmin.mcp.model.AiMcpRegistryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI MCP 注册表映射。
 */
@Mapper
public interface AiMcpRegistryMapper {

    /**
     * 查询全部 MCP 注册记录。
     */
    @Select("""
            SELECT
              id AS mcpId,
              mcp_code AS mcpCode,
              mcp_name AS mcpName,
              endpoint_url AS endpointUrl,
              transport_type AS transportType,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_mcp_registry
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiMcpRegistryRecord> selectAll();

    /**
     * 按 ID 查询 MCP 注册记录。
     */
    @Select("""
            SELECT
              id AS mcpId,
              mcp_code AS mcpCode,
              mcp_name AS mcpName,
              endpoint_url AS endpointUrl,
              transport_type AS transportType,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_mcp_registry
            WHERE id = #{mcpId}
            """)
    AiMcpRegistryRecord selectById(@Param("mcpId") String mcpId);

    /**
     * 按编码检查是否重复。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_mcp_registry
            WHERE mcp_code = #{mcpCode}
              AND (#{excludeMcpId} IS NULL OR id <> #{excludeMcpId})
            """)
    Long countByMcpCode(@Param("mcpCode") String mcpCode, @Param("excludeMcpId") String excludeMcpId);

    /**
     * 新增 MCP 注册记录。
     */
    @Insert("""
            INSERT INTO wf_ai_mcp_registry (
              id,
              mcp_code,
              mcp_name,
              endpoint_url,
              transport_type,
              required_capability_code,
              enabled,
              metadata_json,
              created_at,
              updated_at
            ) VALUES (
              #{mcpId},
              #{mcpCode},
              #{mcpName},
              #{endpointUrl},
              #{transportType},
              #{requiredCapabilityCode},
              #{enabled},
              #{metadataJson},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(AiMcpRegistryRecord record);

    /**
     * 更新 MCP 注册记录。
     */
    @Update("""
            UPDATE wf_ai_mcp_registry
            SET mcp_code = #{mcpCode},
                mcp_name = #{mcpName},
                endpoint_url = #{endpointUrl},
                transport_type = #{transportType},
                required_capability_code = #{requiredCapabilityCode},
                enabled = #{enabled},
                metadata_json = #{metadataJson},
                updated_at = #{updatedAt}
            WHERE id = #{mcpId}
            """)
    int update(AiMcpRegistryRecord record);
}
