package com.westflow.aiadmin.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.agent.mapper.AiAgentRegistryMapper;
import com.westflow.aiadmin.agent.model.AiAgentRegistryRecord;
import com.westflow.aiadmin.confirmation.mapper.AiConfirmationAdminMapper;
import com.westflow.aiadmin.confirmation.model.AiConfirmationAdminRecord;
import com.westflow.aiadmin.conversation.mapper.AiConversationAdminMapper;
import com.westflow.aiadmin.conversation.model.AiConversationAdminRecord;
import com.westflow.aiadmin.mcp.mapper.AiMcpRegistryMapper;
import com.westflow.aiadmin.mcp.model.AiMcpRegistryRecord;
import com.westflow.aiadmin.skill.mapper.AiSkillRegistryMapper;
import com.westflow.aiadmin.skill.model.AiSkillRegistryRecord;
import com.westflow.aiadmin.tool.mapper.AiToolRegistryMapper;
import com.westflow.aiadmin.tool.model.AiToolRegistryRecord;
import com.westflow.aiadmin.toolcall.mapper.AiToolCallAdminMapper;
import com.westflow.aiadmin.toolcall.model.AiToolCallAdminRecord;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 聚合 AI 管理后台的注册表和运行态观测信息。
 */
@Service
@RequiredArgsConstructor
public class AiAdminObservabilityService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_ROUTE_MODE = "SUPERVISOR";
    private static final int DEFAULT_PRIORITY = 100;

    private final AiAgentRegistryMapper aiAgentRegistryMapper;
    private final AiToolRegistryMapper aiToolRegistryMapper;
    private final AiSkillRegistryMapper aiSkillRegistryMapper;
    private final AiMcpRegistryMapper aiMcpRegistryMapper;
    private final AiToolCallAdminMapper aiToolCallAdminMapper;
    private final AiConversationAdminMapper aiConversationAdminMapper;
    private final AiConfirmationAdminMapper aiConfirmationAdminMapper;
    private final ObjectMapper objectMapper;

    public ToolCallDiagnostics describeToolCall(AiToolCallAdminRecord record) {
        ResolvedToolChain chain = resolveToolChain(record.toolKey(), record.toolSource());
        AiConfirmationAdminRecord confirmation = resolveConfirmation(record.confirmationId());
        FailureInfo failure = parseFailure(record.status(), record.resultJson());
        return new ToolCallDiagnostics(
                resolveConversationTitle(record.conversationId()),
                failure.failureCode(),
                failure.failureReason(),
                confirmation == null ? null : confirmation.status(),
                confirmation != null && confirmation.approved(),
                confirmation == null ? null : confirmation.resolvedBy(),
                confirmation == null ? null : confirmation.comment(),
                chain.linkedTool(),
                chain.linkedSkill(),
                chain.linkedMcp(),
                chain.linkedAgents()
        );
    }

    public ConfirmationDiagnostics describeConfirmation(AiConfirmationAdminRecord record) {
        AiToolCallAdminRecord toolCall = record.toolCallId() == null ? null : aiToolCallAdminMapper.selectById(record.toolCallId());
        if (toolCall == null) {
            return new ConfirmationDiagnostics(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }
        ResolvedToolChain chain = resolveToolChain(toolCall.toolKey(), toolCall.toolSource());
        FailureInfo failure = parseFailure(toolCall.status(), toolCall.resultJson());
        return new ConfirmationDiagnostics(
                toolCall.toolKey(),
                toolCall.toolType(),
                toolCall.toolSource(),
                toolCall.toolSource(),
                toolCall.status(),
                resolveConversationTitle(toolCall.conversationId()),
                failure.failureReason(),
                chain.linkedTool(),
                chain.linkedAgents()
        );
    }

    public AgentDiagnostics describeAgent(AiAgentRegistryRecord record) {
        MetadataView metadata = metadataOf(record.metadataJson(), record.agentName());
        List<AiRegistryLinkResponse> linkedTools = aiToolRegistryMapper.selectAll().stream()
                .filter(tool -> matchesCapability(tool.requiredCapabilityCode(), record.capabilityCode()))
                .map(this::toToolLink)
                .toList();
        List<AiRegistryLinkResponse> linkedSkills = aiSkillRegistryMapper.selectAll().stream()
                .filter(skill -> matchesCapability(skill.requiredCapabilityCode(), record.capabilityCode()))
                .map(this::toSkillLink)
                .toList();
        List<AiRegistryLinkResponse> linkedMcps = aiMcpRegistryMapper.selectAll().stream()
                .filter(mcp -> matchesCapability(mcp.requiredCapabilityCode(), record.capabilityCode()))
                .map(this::toMcpLink)
                .toList();
        AiObservabilitySummaryResponse observability = summarize(aiToolCallAdminMapper.selectAll().stream()
                .filter(toolCall -> describeToolCall(toolCall).linkedAgents().stream()
                        .anyMatch(agent -> Objects.equals(agent.entityId(), record.agentId())))
                .toList());
        return new AgentDiagnostics(
                metadata.routeMode(),
                metadata.supervisor(),
                metadata.priority(),
                metadata.contextTags(),
                metadata.description(),
                observability,
                linkedTools,
                linkedSkills,
                linkedMcps
        );
    }

    public RegistryDiagnostics describeTool(AiToolRegistryRecord record) {
        MetadataView metadata = metadataOf(record.metadataJson(), record.toolName());
        List<AiRegistryLinkResponse> linkedAgents = linkedAgentsForCapability(record.requiredCapabilityCode());
        AiRegistryLinkResponse linkedResource = aiSkillRegistryMapper.selectAll().stream()
                .filter(skill -> Objects.equals(skill.skillCode(), record.toolCode()))
                .findFirst()
                .map(this::toSkillLink)
                .orElse(null);
        AiRegistryLinkResponse linkedMcp = resolveMcpLink(record.toolCode(), record.toolCategory());
        AiObservabilitySummaryResponse observability = summarize(aiToolCallAdminMapper.selectAll().stream()
                .filter(toolCall -> Objects.equals(toolCall.toolKey(), record.toolCode()))
                .toList());
        return new RegistryDiagnostics(metadata.description(), observability, linkedAgents, linkedResource, linkedMcp, List.of(), List.of());
    }

    public RegistryDiagnostics describeSkill(AiSkillRegistryRecord record) {
        MetadataView metadata = metadataOf(record.metadataJson(), record.skillName());
        List<AiRegistryLinkResponse> linkedAgents = linkedAgentsForCapability(record.requiredCapabilityCode());
        AiRegistryLinkResponse linkedResource = aiToolRegistryMapper.selectAll().stream()
                .filter(tool -> Objects.equals(tool.toolCode(), record.skillCode()))
                .findFirst()
                .map(this::toToolLink)
                .orElse(null);
        AiRegistryLinkResponse linkedMcp = resolveMcpLink(record.skillCode(), null);
        AiObservabilitySummaryResponse observability = summarize(aiToolCallAdminMapper.selectAll().stream()
                .filter(toolCall -> Objects.equals(toolCall.toolKey(), record.skillCode()))
                .toList());
        return new RegistryDiagnostics(metadata.description(), observability, linkedAgents, linkedResource, linkedMcp, List.of(), List.of());
    }

    public RegistryDiagnostics describeMcp(AiMcpRegistryRecord record) {
        MetadataView metadata = metadataOf(record.metadataJson(), record.mcpName());
        List<AiRegistryLinkResponse> linkedAgents = linkedAgentsForCapability(record.requiredCapabilityCode());
        List<AiRegistryLinkResponse> linkedTools = aiToolRegistryMapper.selectAll().stream()
                .filter(tool -> matchesMcp(tool.toolCode(), record.mcpCode())
                        || matchesCapability(tool.requiredCapabilityCode(), record.requiredCapabilityCode()))
                .map(this::toToolLink)
                .toList();
        List<AiRegistryLinkResponse> linkedSkills = aiSkillRegistryMapper.selectAll().stream()
                .filter(skill -> matchesMcp(skill.skillCode(), record.mcpCode())
                        || matchesCapability(skill.requiredCapabilityCode(), record.requiredCapabilityCode()))
                .map(this::toSkillLink)
                .toList();
        AiObservabilitySummaryResponse observability = summarizeToolCallsForMcp(record.mcpCode(), record.requiredCapabilityCode());
        return new RegistryDiagnostics(metadata.description(), observability, linkedAgents, null, null, linkedTools, linkedSkills);
    }

    public AiObservabilitySummaryResponse summarizeToolCallsForMcp(String mcpCode, String requiredCapabilityCode) {
        return summarize(aiToolCallAdminMapper.selectAll().stream()
                .filter(toolCall -> matchesMcp(toolCall.toolKey(), mcpCode)
                        || ("MCP".equalsIgnoreCase(toolCall.toolSource())
                        && matchesCapability(resolveCapabilityForToolCall(toolCall), requiredCapabilityCode)))
                .toList());
    }

    private String resolveCapabilityForToolCall(AiToolCallAdminRecord toolCall) {
        ResolvedToolChain chain = resolveToolChain(toolCall.toolKey(), toolCall.toolSource());
        if (chain.linkedTool() != null) {
            return chain.linkedTool().capabilityCode();
        }
        if (chain.linkedSkill() != null) {
            return chain.linkedSkill().capabilityCode();
        }
        if (chain.linkedMcp() != null) {
            return chain.linkedMcp().capabilityCode();
        }
        return null;
    }

    private ResolvedToolChain resolveToolChain(String toolKey, String toolSource) {
        AiToolRegistryRecord tool = aiToolRegistryMapper.selectAll().stream()
                .filter(item -> Objects.equals(item.toolCode(), toolKey))
                .findFirst()
                .orElse(null);
        AiSkillRegistryRecord skill = aiSkillRegistryMapper.selectAll().stream()
                .filter(item -> Objects.equals(item.skillCode(), toolKey))
                .findFirst()
                .orElse(null);
        AiMcpRegistryRecord mcp = aiMcpRegistryMapper.selectAll().stream()
                .filter(item -> matchesMcp(toolKey, item.mcpCode()))
                .max(Comparator.comparingInt(item -> item.mcpCode() == null ? 0 : item.mcpCode().length()))
                .orElse(null);
        Set<String> capabilities = Arrays.asList(
                        tool == null ? null : tool.requiredCapabilityCode(),
                        skill == null ? null : skill.requiredCapabilityCode(),
                        mcp == null ? null : mcp.requiredCapabilityCode()
                ).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<AiRegistryLinkResponse> agents = aiAgentRegistryMapper.selectAll().stream()
                .filter(agent -> capabilities.stream().anyMatch(capability -> matchesCapability(agent.capabilityCode(), capability)))
                .map(this::toAgentLink)
                .toList();
        if (agents.isEmpty() && "MCP".equalsIgnoreCase(toolSource) && mcp != null && mcp.requiredCapabilityCode() != null) {
            agents = linkedAgentsForCapability(mcp.requiredCapabilityCode());
        }
        return new ResolvedToolChain(
                tool == null ? null : toToolLink(tool),
                skill == null ? null : toSkillLink(skill),
                mcp == null ? null : toMcpLink(mcp),
                agents
        );
    }

    private AiRegistryLinkResponse resolveMcpLink(String toolKey, String toolCategory) {
        if (toolKey == null) {
            return null;
        }
        return aiMcpRegistryMapper.selectAll().stream()
                .filter(mcp -> matchesMcp(toolKey, mcp.mcpCode())
                        || ("MCP".equalsIgnoreCase(toolCategory)
                        && matchesCapability(mcp.requiredCapabilityCode(), resolveToolRequiredCapability(toolKey))))
                .max(Comparator.comparingInt(mcp -> mcp.mcpCode() == null ? 0 : mcp.mcpCode().length()))
                .map(this::toMcpLink)
                .orElse(null);
    }

    private String resolveToolRequiredCapability(String toolCode) {
        return aiToolRegistryMapper.selectAll().stream()
                .filter(tool -> Objects.equals(tool.toolCode(), toolCode))
                .map(AiToolRegistryRecord::requiredCapabilityCode)
                .findFirst()
                .orElse(null);
    }

    private List<AiRegistryLinkResponse> linkedAgentsForCapability(String capabilityCode) {
        if (capabilityCode == null) {
            return List.of();
        }
        return aiAgentRegistryMapper.selectAll().stream()
                .filter(agent -> matchesCapability(agent.capabilityCode(), capabilityCode))
                .map(this::toAgentLink)
                .toList();
    }

    private AiObservabilitySummaryResponse summarize(List<AiToolCallAdminRecord> records) {
        List<AiToolCallAdminRecord> matched = new ArrayList<>(records);
        long totalToolCalls = matched.size();
        long successfulToolCalls = matched.stream()
                .filter(record -> "EXECUTED".equalsIgnoreCase(record.status()) || "CONFIRMED".equalsIgnoreCase(record.status()))
                .count();
        long failedToolCalls = matched.stream()
                .filter(record -> "FAILED".equalsIgnoreCase(record.status()) || "REJECTED".equalsIgnoreCase(record.status()))
                .count();
        long pendingConfirmations = matched.stream()
                .filter(record -> record.requiresConfirmation()
                        && !"EXECUTED".equalsIgnoreCase(record.status())
                        && !"CONFIRMED".equalsIgnoreCase(record.status()))
                .count();
        Long averageDurationMillis = matched.stream()
                .map(this::durationOf)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), durations -> {
                    if (durations.isEmpty()) {
                        return null;
                    }
                    long sum = durations.stream().mapToLong(Long::longValue).sum();
                    return Math.max(sum / durations.size(), 0L);
                }));
        AiToolCallAdminRecord latest = matched.stream()
                .max(Comparator.comparing(AiToolCallAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        String latestFailureReason = matched.stream()
                .filter(record -> "FAILED".equalsIgnoreCase(record.status()) || "REJECTED".equalsIgnoreCase(record.status()))
                .sorted(Comparator.comparing(AiToolCallAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(record -> parseFailure(record.status(), record.resultJson()).failureReason())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new AiObservabilitySummaryResponse(
                totalToolCalls,
                successfulToolCalls,
                failedToolCalls,
                pendingConfirmations,
                averageDurationMillis,
                latest == null ? null : latest.toolCallId(),
                latest == null ? null : AiAdminSupport.toOffsetDateTime(latest.createdAt()),
                latestFailureReason
        );
    }

    private Long durationOf(AiToolCallAdminRecord record) {
        if (record.createdAt() == null || record.completedAt() == null) {
            return null;
        }
        return Math.max(Duration.between(record.createdAt(), record.completedAt()).toMillis(), 0L);
    }

    private AiConfirmationAdminRecord resolveConfirmation(String confirmationId) {
        if (confirmationId == null) {
            return null;
        }
        return aiConfirmationAdminMapper.selectById(confirmationId);
    }

    private String resolveConversationTitle(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        AiConversationAdminRecord conversation = aiConversationAdminMapper.selectById(conversationId);
        return conversation == null ? null : conversation.title();
    }

    private FailureInfo parseFailure(String status, String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return new FailureInfo(null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String failureCode = root.hasNonNull("error") ? root.path("error").asText() : null;
            String failureReason = root.hasNonNull("message")
                    ? root.path("message").asText()
                    : root.hasNonNull("reason") ? root.path("reason").asText() : failureCode;
            if (failureReason == null && ("FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status))) {
                failureReason = resultJson;
            }
            return new FailureInfo(failureCode, failureReason);
        } catch (Exception ignored) {
            return new FailureInfo(null, resultJson);
        }
    }

    private MetadataView metadataOf(String metadataJson, String fallbackName) {
        Map<String, Object> metadata = readMetadata(metadataJson);
        String routeMode = stringValue(metadata.get("routeMode"));
        Integer priority = integerValue(metadata.get("priority"));
        Boolean supervisor = booleanValue(metadata.get("supervisor"));
        List<String> contextTags = listValue(metadata.get("contextTags"));
        String description = stringValue(metadata.get("description"));
        return new MetadataView(
                routeMode == null ? DEFAULT_ROUTE_MODE : routeMode,
                supervisor == null || supervisor,
                priority == null ? DEFAULT_PRIORITY : priority,
                contextTags,
                description == null ? "%s：当前未配置额外说明".formatted(fallbackName) : description
        );
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value).trim());
    }

    private List<String> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return List.of();
    }

    private boolean matchesMcp(String toolKey, String mcpCode) {
        if (toolKey == null || mcpCode == null || mcpCode.isBlank()) {
            return false;
        }
        return toolKey.equals(mcpCode) || toolKey.startsWith(mcpCode + ".");
    }

    private boolean matchesCapability(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private AiRegistryLinkResponse toAgentLink(AiAgentRegistryRecord record) {
        return new AiRegistryLinkResponse(
                "AGENT",
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.capabilityCode(),
                AiAdminSupport.toStatus(record.enabled())
        );
    }

    private AiRegistryLinkResponse toToolLink(AiToolRegistryRecord record) {
        return new AiRegistryLinkResponse(
                "TOOL",
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.requiredCapabilityCode(),
                AiAdminSupport.toStatus(record.enabled())
        );
    }

    private AiRegistryLinkResponse toSkillLink(AiSkillRegistryRecord record) {
        return new AiRegistryLinkResponse(
                "SKILL",
                record.skillId(),
                record.skillCode(),
                record.skillName(),
                record.requiredCapabilityCode(),
                AiAdminSupport.toStatus(record.enabled())
        );
    }

    private AiRegistryLinkResponse toMcpLink(AiMcpRegistryRecord record) {
        return new AiRegistryLinkResponse(
                "MCP",
                record.mcpId(),
                record.mcpCode(),
                record.mcpName(),
                record.requiredCapabilityCode(),
                AiAdminSupport.toStatus(record.enabled())
        );
    }

    public record ToolCallDiagnostics(
            String conversationTitle,
            String failureCode,
            String failureReason,
            String confirmationStatus,
            boolean confirmationApproved,
            String confirmationResolvedBy,
            String confirmationComment,
            AiRegistryLinkResponse linkedTool,
            AiRegistryLinkResponse linkedSkill,
            AiRegistryLinkResponse linkedMcp,
            List<AiRegistryLinkResponse> linkedAgents
    ) {
    }

    public record ConfirmationDiagnostics(
            String toolKey,
            String toolType,
            String toolSource,
            String hitSource,
            String toolCallStatus,
            String conversationTitle,
            String failureReason,
            AiRegistryLinkResponse linkedTool,
            List<AiRegistryLinkResponse> linkedAgents
    ) {
    }

    public record AgentDiagnostics(
            String routeMode,
            boolean supervisor,
            int priority,
            List<String> contextTags,
            String description,
            AiObservabilitySummaryResponse observability,
            List<AiRegistryLinkResponse> linkedTools,
            List<AiRegistryLinkResponse> linkedSkills,
            List<AiRegistryLinkResponse> linkedMcps
    ) {
    }

    public record RegistryDiagnostics(
            String description,
            AiObservabilitySummaryResponse observability,
            List<AiRegistryLinkResponse> linkedAgents,
            AiRegistryLinkResponse linkedResource,
            AiRegistryLinkResponse linkedMcp,
            List<AiRegistryLinkResponse> linkedTools,
            List<AiRegistryLinkResponse> linkedSkills
    ) {
    }

    private record FailureInfo(
            String failureCode,
            String failureReason
    ) {
    }

    private record MetadataView(
            String routeMode,
            boolean supervisor,
            int priority,
            List<String> contextTags,
            String description
    ) {
    }

    private record ResolvedToolChain(
            AiRegistryLinkResponse linkedTool,
            AiRegistryLinkResponse linkedSkill,
            AiRegistryLinkResponse linkedMcp,
            List<AiRegistryLinkResponse> linkedAgents
    ) {
    }
}
