package com.westflow.aiadmin.skill.api;

import com.westflow.aiadmin.support.AiObservabilitySummaryResponse;
import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

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
        String description,
        String metadataJson,
        AiObservabilitySummaryResponse observability,
        List<AiRegistryLinkResponse> linkedAgents,
        AiRegistryLinkResponse linkedTool,
        AiRegistryLinkResponse linkedMcp,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
