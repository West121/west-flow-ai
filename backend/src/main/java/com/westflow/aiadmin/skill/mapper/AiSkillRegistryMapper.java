package com.westflow.aiadmin.skill.mapper;

import com.westflow.aiadmin.skill.model.AiSkillRegistryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI Skill 注册表映射。
 */
@Mapper
public interface AiSkillRegistryMapper {

    /**
     * 查询全部 Skill 注册记录。
     */
    @Select("""
            SELECT
              id AS skillId,
              skill_code AS skillCode,
              skill_name AS skillName,
              skill_path AS skillPath,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_skill_registry
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<AiSkillRegistryRecord> selectAll();

    /**
     * 按 ID 查询 Skill 注册记录。
     */
    @Select("""
            SELECT
              id AS skillId,
              skill_code AS skillCode,
              skill_name AS skillName,
              skill_path AS skillPath,
              required_capability_code AS requiredCapabilityCode,
              enabled,
              metadata_json AS metadataJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_ai_skill_registry
            WHERE id = #{skillId}
            """)
    AiSkillRegistryRecord selectById(@Param("skillId") String skillId);

    /**
     * 按编码检查是否重复。
     */
    @Select("""
            SELECT COUNT(1)
            FROM wf_ai_skill_registry
            WHERE skill_code = #{skillCode}
              AND (#{excludeSkillId} IS NULL OR id <> #{excludeSkillId})
            """)
    Long countBySkillCode(@Param("skillCode") String skillCode, @Param("excludeSkillId") String excludeSkillId);

    /**
     * 新增 Skill 注册记录。
     */
    @Insert("""
            INSERT INTO wf_ai_skill_registry (
              id,
              skill_code,
              skill_name,
              skill_path,
              required_capability_code,
              enabled,
              metadata_json,
              created_at,
              updated_at
            ) VALUES (
              #{skillId},
              #{skillCode},
              #{skillName},
              #{skillPath},
              #{requiredCapabilityCode},
              #{enabled},
              #{metadataJson},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(AiSkillRegistryRecord record);

    /**
     * 更新 Skill 注册记录。
     */
    @Update("""
            UPDATE wf_ai_skill_registry
            SET skill_code = #{skillCode},
                skill_name = #{skillName},
                skill_path = #{skillPath},
                required_capability_code = #{requiredCapabilityCode},
                enabled = #{enabled},
                metadata_json = #{metadataJson},
                updated_at = #{updatedAt}
            WHERE id = #{skillId}
            """)
    int update(AiSkillRegistryRecord record);
}
