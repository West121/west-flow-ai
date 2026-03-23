package com.westflow.aiadmin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * AI 能力码选项映射。
 */
@Mapper
public interface AiCapabilityOptionMapper {

    /**
     * 查询用户能力码。
     */
    @Select("""
            SELECT DISTINCT capability_code
            FROM wf_user_ai_capability
            WHERE capability_code IS NOT NULL AND capability_code <> ''
            ORDER BY capability_code ASC
            """)
    List<String> selectUserCapabilityCodes();

    /**
     * 查询智能体能力码。
     */
    @Select("""
            SELECT DISTINCT capability_code
            FROM wf_ai_agent_registry
            WHERE capability_code IS NOT NULL AND capability_code <> ''
            ORDER BY capability_code ASC
            """)
    List<String> selectAgentCapabilityCodes();

    /**
     * 查询工具能力码。
     */
    @Select("""
            SELECT DISTINCT required_capability_code
            FROM wf_ai_tool_registry
            WHERE required_capability_code IS NOT NULL AND required_capability_code <> ''
            ORDER BY required_capability_code ASC
            """)
    List<String> selectToolCapabilityCodes();

    /**
     * 查询 MCP 能力码。
     */
    @Select("""
            SELECT DISTINCT required_capability_code
            FROM wf_ai_mcp_registry
            WHERE required_capability_code IS NOT NULL AND required_capability_code <> ''
            ORDER BY required_capability_code ASC
            """)
    List<String> selectMcpCapabilityCodes();

    /**
     * 查询 Skill 能力码。
     */
    @Select("""
            SELECT DISTINCT required_capability_code
            FROM wf_ai_skill_registry
            WHERE required_capability_code IS NOT NULL AND required_capability_code <> ''
            ORDER BY required_capability_code ASC
            """)
    List<String> selectSkillCapabilityCodes();
}
