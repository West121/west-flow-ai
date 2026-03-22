package com.westflow.ai.skill;

import java.util.List;

/**
 * AI Skill 描述。
 */
public record AiSkillDescriptor(
        String skillId,
        String skillName,
        List<String> supportedDomains,
        List<String> triggerKeywords,
        boolean systemSkill,
        int priority
) {
    public AiSkillDescriptor {
        skillId = requireText(skillId, "skillId");
        skillName = requireText(skillName, "skillName");
        supportedDomains = supportedDomains == null ? List.of() : List.copyOf(supportedDomains);
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
