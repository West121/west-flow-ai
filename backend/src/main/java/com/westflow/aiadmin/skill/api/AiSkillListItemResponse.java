package com.westflow.aiadmin.skill.api;

import java.time.OffsetDateTime;

/**
 * AI Skill 注册表列表项。
 */
public record AiSkillListItemResponse(
        String skillId,
        String skillCode,
        String skillName,
        String skillPath,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
