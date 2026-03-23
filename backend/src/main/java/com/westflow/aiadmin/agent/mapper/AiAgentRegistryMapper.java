package com.westflow.aiadmin.agent.mapper;

import com.westflow.aiadmin.agent.model.AiAgentRegistryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 智能体注册表映射。
 */
@Mapper
public interface AiAgentRegistryMapper {

    /**
     * 查询全部智能体注册记录。
     */
    @Select("""
            SELECT
              id AS agentId,
              agent_code AS agentCode,
              agent_name AS agentName,
              capability_code AS capabilityCode,
              enabled,
              system_prompt AS systemPrompt,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_agent_registry
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiAgentRegistryRecord> selectAll();

    /**
     * 按 ID 查询智能体注册记录。
     */
    @Select("""
            SELECT
              id AS agentId,
              agent_code AS agentCode,
              agent_name AS agentName,
              capability_code AS capabilityCode,
              enabled,
              system_prompt AS systemPrompt,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_agent_registry
            WHERE id = #{agentId}
            """)
    AiAgentRegistryRecord selectById(@Param("agentId") String agentId);

    /**
     * 按编码检查是否重复。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_agent_registry
            WHERE agent_code = #{agentCode}
              AND (#{excludeAgentId} IS NULL OR id <> #{excludeAgentId})
            """)
    Long countByAgentCode(@Param("agentCode") String agentCode, @Param("excludeAgentId") String excludeAgentId);

    /**
     * 新增智能体注册记录。
     */
    @Insert("""
            INSERT INTO wf_ai_agent_registry (
              id,
              agent_code,
              agent_name,
              capability_code,
              enabled,
              system_prompt,
              metadata_json,
              created_at,
              updated_at
            ) VALUES (
              #{agentId},
              #{agentCode},
              #{agentName},
              #{capabilityCode},
              #{enabled},
              #{systemPrompt},
              #{metadataJson},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(AiAgentRegistryRecord record);

    /**
     * 更新智能体注册记录。
     */
    @Update("""
            UPDATE wf_ai_agent_registry
            SET agent_code = #{agentCode},
                agent_name = #{agentName},
                capability_code = #{capabilityCode},
                enabled = #{enabled},
                system_prompt = #{systemPrompt},
                metadata_json = #{metadataJson},
                updated_at = #{updatedAt}
            WHERE id = #{agentId}
            """)
    int update(AiAgentRegistryRecord record);
}
