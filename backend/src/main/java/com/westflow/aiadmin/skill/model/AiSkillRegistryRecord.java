package com.westflow.aiadmin.skill.model;

import java.time.LocalDateTime;

/**
 * AI Skill 注册表记录。
 */
public record AiSkillRegistryRecord(
        String skillId,
        String skillCode,
        String skillName,
        String skillPath,
        String requiredCapabilityCode,
        boolean enabled,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
