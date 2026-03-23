package com.westflow.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * AI 注册表查询映射，统一读取 Agent、Tool、MCP、Skill 的启用配置。
 */
@Mapper
public interface AiRegistryMapper {

    /**
     * 读取已启用的智能体注册表记录。
     */
    @Select("""
            SELECT
              id,
              agent_code AS agentCode,
              agent_name AS agentName,
              capability_code AS capabilityCode,
              enabled,
              system_prompt AS systemPrompt,
              metadata_json AS metadataJson
            FROM wf_ai_agent_registry
            WHERE enabled = TRUE
            ORDER BY agent_code ASC
            """)
    List<AiAgentRegistryRow> selectEnabledAgentRegistries();

    /**
     * 读取已启用的工具注册表记录。
     */
    @Select("""
            SELECT
              id,
              tool_code AS toolCode,
              tool_name AS toolName,
              tool_category AS toolCategory,
              action_mode AS actionMode,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson
            FROM wf_ai_tool_registry
            WHERE enabled = TRUE
            ORDER BY tool_code ASC
            """)
    List<AiToolRegistryRow> selectEnabledToolRegistries();

    /**
     * 读取已启用的 MCP 注册表记录。
     */
    @Select("""
            SELECT
              id,
              mcp_code AS mcpCode,
              mcp_name AS mcpName,
              endpoint_url AS endpointUrl,
              transport_type AS transportType,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson
            FROM wf_ai_mcp_registry
            WHERE enabled = TRUE
            ORDER BY mcp_code ASC
            """)
    List<AiMcpRegistryRow> selectEnabledMcpRegistries();

    /**
     * 读取已启用的 Skill 注册表记录。
     */
    @Select("""
            SELECT
              id,
              skill_code AS skillCode,
              skill_name AS skillName,
              skill_path AS skillPath,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson
            FROM wf_ai_skill_registry
            WHERE enabled = TRUE
            ORDER BY skill_code ASC
            """)
    List<AiSkillRegistryRow> selectEnabledSkillRegistries();

    /**
     * 智能体注册表行。
     */
    record AiAgentRegistryRow(
            String id,
            String agentCode,
            String agentName,
            String capabilityCode,
            Boolean enabled,
            String systemPrompt,
            String metadataJson
    ) {
    }

    /**
     * 工具注册表行。
     */
    record AiToolRegistryRow(
            String id,
            String toolCode,
            String toolName,
            String toolCategory,
            String actionMode,
            String requiredCapabilityCode,
            Boolean enabled,
            String metadataJson
    ) {
    }

    /**
     * MCP 注册表行。
     */
    record AiMcpRegistryRow(
            String id,
            String mcpCode,
            String mcpName,
            String endpointUrl,
            String transportType,
            String requiredCapabilityCode,
            Boolean enabled,
            String metadataJson
    ) {
    }

    /**
     * Skill 注册表行。
     */
    record AiSkillRegistryRow(
            String id,
            String skillCode,
            String skillName,
            String skillPath,
            String requiredCapabilityCode,
            Boolean enabled,
            String metadataJson
    ) {
    }
}
