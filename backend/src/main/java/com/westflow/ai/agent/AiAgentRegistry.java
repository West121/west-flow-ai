package com.westflow.ai.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AI 智能体注册表。
 */
public class AiAgentRegistry {

    private final List<AiAgentDescriptor> descriptors;

    public AiAgentRegistry(List<AiAgentDescriptor> descriptors) {
        this.descriptors = descriptors == null ? List.of() : List.copyOf(descriptors);
    }

    public Optional<AiAgentDescriptor> findSupervisor(String domain) {
        return descriptors.stream()
                .filter(AiAgentDescriptor::supervisor)
                .filter(descriptor -> supportsDomain(descriptor, domain))
                .max(Comparator.comparingInt(AiAgentDescriptor::priority));
    }

    public Optional<AiAgentDescriptor> findRoutingAgent(String domain) {
        return descriptors.stream()
                .filter(descriptor -> "ROUTING".equalsIgnoreCase(descriptor.routeMode()))
                .filter(descriptor -> supportsDomain(descriptor, domain))
                .max(Comparator.comparingInt(AiAgentDescriptor::priority));
    }

    public Optional<AiAgentDescriptor> findById(String agentId) {
        return descriptors.stream().filter(descriptor -> descriptor.agentId().equals(agentId)).findFirst();
    }

    private boolean supportsDomain(AiAgentDescriptor descriptor, String domain) {
        return descriptor.supportedDomains().isEmpty()
                || domain == null
                || descriptor.supportedDomains().contains(domain);
    }
}
