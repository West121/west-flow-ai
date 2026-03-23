package com.westflow.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.mapper.AiRegistryMapper;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.skill.AiSkillContentLoader;
import com.westflow.identity.mapper.AuthUserMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * AI 注册表目录服务，负责把数据库注册表转换成运行期可消费的目录对象。
 */
@Service
public class AiRegistryCatalogService {

    private final AiRegistryMapper aiRegistryMapper;
    private final AuthUserMapper authUserMapper;
    private final ObjectMapper objectMapper;
    private final AiSkillContentLoader aiSkillContentLoader;

    public AiRegistryCatalogService(
            AiRegistryMapper aiRegistryMapper,
            AuthUserMapper authUserMapper,
            ObjectMapper objectMapper,
            AiSkillContentLoader aiSkillContentLoader
    ) {
        this.aiRegistryMapper = aiRegistryMapper;
        this.authUserMapper = authUserMapper;
        this.objectMapper = objectMapper;
        this.aiSkillContentLoader = aiSkillContentLoader;
    }

    /**
     * 查询用户当前可用的 AI 能力编码。
     */
    public List<String> capabilities(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<String> capabilities = authUserMapper.selectAiCapabilitiesByUserId(userId);
        return capabilities == null ? List.of() : capabilities;
    }

    /**
     * 查询当前业务域可用的 Supervisor 智能体。
     */
    public Optional<AiAgentCatalogItem> findSupervisor(String userId, String domain) {
        return listAgents(userId, domain).stream()
                .filter(AiAgentCatalogItem::supervisor)
                .max(Comparator.comparingInt(AiAgentCatalogItem::priority));
    }

    /**
     * 查询当前业务域可用的 Routing 智能体。
     */
    public Optional<AiAgentCatalogItem> findRoutingAgent(String userId, String domain) {
        return listAgents(userId, domain).stream()
                .filter(item -> "ROUTING".equalsIgnoreCase(item.routeMode()))
                .max(Comparator.comparingInt(AiAgentCatalogItem::priority));
    }

    /**
     * 按编码查询当前用户可用的智能体。
     */
    public Optional<AiAgentCatalogItem> findAgent(String userId, String agentCode, String domain) {
        return listAgents(userId, domain).stream()
                .filter(item -> item.agentCode().equals(agentCode))
                .findFirst();
    }

    /**
     * 按请求技能或自然语言内容匹配当前用户可用的 Skill。
     */
    public List<AiSkillCatalogItem> matchSkills(
            String userId,
            String content,
            String domain,
            List<String> requestedSkillIds
    ) {
        List<AiSkillCatalogItem> availableSkills = listSkills(userId, domain);
        if (requestedSkillIds != null && !requestedSkillIds.isEmpty()) {
            return availableSkills.stream()
                    .filter(item -> requestedSkillIds.contains(item.skillCode()))
                    .sorted(Comparator.comparingInt(AiSkillCatalogItem::priority).reversed())
                    .toList();
        }
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalizedContent = content.toLowerCase();
        return availableSkills.stream()
                .filter(item -> item.triggerKeywords().stream()
                        .map(String::toLowerCase)
                        .anyMatch(normalizedContent::contains))
                .sorted(Comparator.comparingInt(AiSkillCatalogItem::priority).reversed())
                .toList();
    }

    /**
     * 按编码查询当前用户可用的工具。
     */
    public Optional<AiToolCatalogItem> findTool(String userId, String toolKey) {
        return listTools(userId).stream()
                .filter(item -> item.toolCode().equals(toolKey))
                .findFirst();
    }

    /**
     * 列出当前用户在指定业务域可用的读工具。
     */
    public List<AiToolCatalogItem> listReadableTools(String userId, String domain) {
        return listTools(userId).stream()
                .filter(item -> item.toolType() == AiToolType.READ)
                .filter(item -> supportsDomain(item.supportedDomains(), domain))
                .sorted(Comparator.comparingInt(AiToolCatalogItem::priority).reversed())
                .toList();
    }

    /**
     * 查询当前用户可用的 MCP 注册项。
     */
    public Optional<AiMcpCatalogItem> findMcp(String userId, String mcpCode) {
        return listMcps(userId).stream()
                .filter(item -> item.mcpCode().equals(mcpCode))
                .findFirst();
    }

    /**
     * 结合内容、业务域、技能和页面路径匹配最合适的只读工具。
     */
    public Optional<AiToolCatalogItem> matchReadTool(
            String userId,
            String content,
            String domain,
            List<String> skillIds,
            String routePath
    ) {
        String normalizedContent = content == null ? "" : content.toLowerCase();
        String normalizedRoutePath = routePath == null ? "" : routePath.toLowerCase();
        return listReadableTools(userId, domain).stream()
                .map(item -> new ScoredTool(item, scoreTool(item, normalizedContent, normalizedRoutePath, skillIds)))
                .filter(scoredTool -> scoredTool.score() > 0)
                .sorted(Comparator.comparingInt(ScoredTool::score).reversed())
                .map(ScoredTool::tool)
                .findFirst();
    }

    private List<AiAgentCatalogItem> listAgents(String userId, String domain) {
        List<String> capabilities = capabilities(userId);
        return aiRegistryMapper.selectEnabledAgentRegistries().stream()
                .map(this::toAgentCatalogItem)
                .filter(item -> supportsCapability(capabilities, item.requiredCapabilityCode()))
                .filter(item -> supportsDomain(item.supportedDomains(), domain))
                .toList();
    }

    private List<AiSkillCatalogItem> listSkills(String userId, String domain) {
        List<String> capabilities = capabilities(userId);
        return aiRegistryMapper.selectEnabledSkillRegistries().stream()
                .map(this::toSkillCatalogItem)
                .filter(item -> supportsCapability(capabilities, item.requiredCapabilityCode()))
                .filter(item -> supportsDomain(item.supportedDomains(), domain))
                .toList();
    }

    private List<AiToolCatalogItem> listTools(String userId) {
        List<String> capabilities = capabilities(userId);
        return aiRegistryMapper.selectEnabledToolRegistries().stream()
                .map(this::toToolCatalogItem)
                .filter(item -> supportsCapability(capabilities, item.requiredCapabilityCode()))
                .toList();
    }

    private List<AiMcpCatalogItem> listMcps(String userId) {
        List<String> capabilities = capabilities(userId);
        return aiRegistryMapper.selectEnabledMcpRegistries().stream()
                .map(this::toMcpCatalogItem)
                .filter(item -> supportsCapability(capabilities, item.requiredCapabilityCode()))
                .toList();
    }

    private AiAgentCatalogItem toAgentCatalogItem(AiRegistryMapper.AiAgentRegistryRow row) {
        Map<String, Object> metadata = parseMetadata(row.metadataJson());
        List<String> supportedDomains = readStringList(metadata, "businessDomains");
        String routeMode = readString(metadata, "routeMode", inferAgentRouteMode(row.agentCode()));
        boolean supervisor = readBoolean(metadata, "supervisor", "SUPERVISOR".equalsIgnoreCase(routeMode));
        int priority = readInt(metadata, "priority", defaultAgentPriority(row.agentCode()));
        return new AiAgentCatalogItem(
                row.agentCode(),
                row.agentName(),
                row.capabilityCode(),
                routeMode,
                supervisor,
                supportedDomains,
                priority,
                row.systemPrompt(),
                metadata
        );
    }

    private AiSkillCatalogItem toSkillCatalogItem(AiRegistryMapper.AiSkillRegistryRow row) {
        Map<String, Object> metadata = parseMetadata(row.metadataJson());
        String skillPath = row.skillPath();
        return new AiSkillCatalogItem(
                row.skillCode(),
                row.skillName(),
                row.requiredCapabilityCode(),
                readStringList(metadata, "businessDomains"),
                readStringList(metadata, "triggerKeywords"),
                readBoolean(metadata, "systemSkill", false),
                readInt(metadata, "priority", 50),
                skillPath,
                aiSkillContentLoader.load(skillPath),
                metadata
        );
    }

    private AiToolCatalogItem toToolCatalogItem(AiRegistryMapper.AiToolRegistryRow row) {
        Map<String, Object> metadata = parseMetadata(row.metadataJson());
        return new AiToolCatalogItem(
                row.toolCode(),
                row.toolName(),
                mapToolSource(row.toolCategory()),
                mapToolType(row.actionMode()),
                row.requiredCapabilityCode(),
                readStringList(metadata, "businessDomains"),
                readStringList(metadata, "triggerKeywords"),
                readStringList(metadata, "routePrefixes"),
                readString(metadata, "mcpCode", ""),
                readString(metadata, "aliasOf", ""),
                readInt(metadata, "priority", 50),
                metadata
        );
    }

    private AiMcpCatalogItem toMcpCatalogItem(AiRegistryMapper.AiMcpRegistryRow row) {
        Map<String, Object> metadata = parseMetadata(row.metadataJson());
        return new AiMcpCatalogItem(
                row.mcpCode(),
                row.mcpName(),
                row.endpointUrl(),
                row.transportType(),
                row.requiredCapabilityCode(),
                metadata
        );
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(text -> !text.isBlank())
                    .toList();
        }
        return List.of();
    }

    private String readString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isBlank() ? defaultValue : text;
    }

    private boolean readBoolean(Map<String, Object> metadata, String key, boolean defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private int readInt(Map<String, Object> metadata, String key, int defaultValue) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean supportsCapability(List<String> capabilities, String capabilityCode) {
        return capabilityCode == null || capabilityCode.isBlank() || capabilities.contains(capabilityCode);
    }

    private boolean supportsDomain(List<String> domains, String domain) {
        return domains.isEmpty() || domain == null || domains.contains(domain);
    }

    private int scoreTool(
            AiToolCatalogItem item,
            String normalizedContent,
            String normalizedRoutePath,
            List<String> skillIds
    ) {
        int score = item.priority();
        if (skillIds != null && !skillIds.isEmpty()) {
            boolean skillMatched = skillIds.stream().anyMatch(skillId -> item.triggerKeywords().stream()
                    .anyMatch(keyword -> keyword.equalsIgnoreCase(skillId)));
            if (skillMatched) {
                score += 60;
            }
        }
        if (!normalizedContent.isBlank()) {
            boolean keywordMatched = item.triggerKeywords().stream()
                    .map(String::toLowerCase)
                    .anyMatch(normalizedContent::contains);
            if (keywordMatched) {
                score += 40;
            }
        }
        if (!normalizedRoutePath.isBlank()) {
            boolean routeMatched = item.routePrefixes().stream()
                    .map(String::toLowerCase)
                    .anyMatch(normalizedRoutePath::startsWith);
            if (routeMatched) {
                score += 20;
            }
        }
        return score;
    }

    private String inferAgentRouteMode(String agentCode) {
        if (agentCode == null) {
            return "ROUTING";
        }
        if (agentCode.contains("supervisor")) {
            return "SUPERVISOR";
        }
        if (agentCode.contains("routing")) {
            return "ROUTING";
        }
        return "REACT";
    }

    private int defaultAgentPriority(String agentCode) {
        if (agentCode == null) {
            return 50;
        }
        if (agentCode.contains("supervisor")) {
            return 100;
        }
        if (agentCode.contains("routing")) {
            return 90;
        }
        return 60;
    }

    private AiToolSource mapToolSource(String toolCategory) {
        if (toolCategory == null || toolCategory.isBlank()) {
            return AiToolSource.PLATFORM;
        }
        return AiToolSource.valueOf(toolCategory.trim().toUpperCase());
    }

    private AiToolType mapToolType(String actionMode) {
        if (actionMode == null || actionMode.isBlank()) {
            return AiToolType.READ;
        }
        return AiToolType.valueOf(actionMode.trim().toUpperCase());
    }

    /**
     * 智能体目录项。
     */
    public record AiAgentCatalogItem(
            String agentCode,
            String agentName,
            String requiredCapabilityCode,
            String routeMode,
            boolean supervisor,
            List<String> supportedDomains,
            int priority,
            String systemPrompt,
            Map<String, Object> metadata
    ) {
    }

    /**
     * Skill 目录项。
     */
    public record AiSkillCatalogItem(
            String skillCode,
            String skillName,
            String requiredCapabilityCode,
            List<String> supportedDomains,
            List<String> triggerKeywords,
            boolean systemSkill,
            int priority,
            String skillPath,
            String content,
            Map<String, Object> metadata
    ) {
    }

    /**
     * 工具目录项。
     */
    public record AiToolCatalogItem(
            String toolCode,
            String toolName,
            AiToolSource toolSource,
            AiToolType toolType,
            String requiredCapabilityCode,
            List<String> supportedDomains,
            List<String> triggerKeywords,
            List<String> routePrefixes,
            String mcpCode,
            String aliasOf,
            int priority,
            Map<String, Object> metadata
    ) {
    }

    /**
     * MCP 目录项。
     */
    public record AiMcpCatalogItem(
            String mcpCode,
            String mcpName,
            String endpointUrl,
            String transportType,
            String requiredCapabilityCode,
            Map<String, Object> metadata
    ) {
    }

    private record ScoredTool(AiToolCatalogItem tool, int score) {
    }
}
