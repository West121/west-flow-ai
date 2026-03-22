package com.westflow.ai.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AI Skill 注册表。
 */
public class AiSkillRegistry {

    private final List<AiSkillDescriptor> descriptors;

    public AiSkillRegistry(List<AiSkillDescriptor> descriptors) {
        this.descriptors = descriptors == null ? List.of() : List.copyOf(descriptors);
    }

    public Optional<AiSkillDescriptor> findById(String skillId) {
        return descriptors.stream().filter(descriptor -> descriptor.skillId().equals(skillId)).findFirst();
    }

    public List<String> selectSkillIds(List<String> requestedSkillIds, String domain) {
        if (requestedSkillIds == null || requestedSkillIds.isEmpty()) {
            return List.of();
        }
        return requestedSkillIds.stream()
                .filter(skillId -> findById(skillId)
                        .map(descriptor -> supportsDomain(descriptor, domain))
                        .orElse(false))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * 根据消息内容和业务域推荐可复用 Skill。
     */
    public List<String> matchSkillIds(String content, String domain) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalizedContent = content.toLowerCase();
        return descriptors.stream()
                .filter(descriptor -> supportsDomain(descriptor, domain))
                .filter(descriptor -> descriptor.triggerKeywords().stream()
                        .map(String::toLowerCase)
                        .anyMatch(normalizedContent::contains))
                .sorted(Comparator.comparingInt(AiSkillDescriptor::priority).reversed())
                .map(AiSkillDescriptor::skillId)
                .toList();
    }

    private boolean supportsDomain(AiSkillDescriptor descriptor, String domain) {
        return descriptor.supportedDomains().isEmpty()
                || domain == null
                || descriptor.supportedDomains().contains(domain);
    }
}
