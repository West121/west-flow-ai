package com.westflow.aiadmin.skill.api;

import java.time.OffsetDateTime;

/**
 * AI Skill 注册表详情。
 */
public record AiSkillDetailResponse(
        String skillId,
        String skillCode,
        String skillName,
        String skillPath,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
