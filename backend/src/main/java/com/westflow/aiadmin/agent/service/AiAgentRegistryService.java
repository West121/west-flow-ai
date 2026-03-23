package com.westflow.aiadmin.agent.service;

import com.westflow.aiadmin.agent.api.AiAgentDetailResponse;
import com.westflow.aiadmin.agent.api.AiAgentFormOptionsResponse;
import com.westflow.aiadmin.agent.api.AiAgentListItemResponse;
import com.westflow.aiadmin.agent.api.AiAgentMutationResponse;
import com.westflow.aiadmin.agent.api.SaveAiAgentRequest;
import com.westflow.aiadmin.agent.mapper.AiAgentRegistryMapper;
import com.westflow.aiadmin.agent.model.AiAgentRegistryRecord;
import com.westflow.aiadmin.mapper.AiCapabilityOptionMapper;
import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminSupport;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 智能体注册表管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiAgentRegistryService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "agentCode", "agentName", "capabilityCode", "status");
    private static final List<String> SUPPORTED_STATUSES = List.of("ENABLED", "DISABLED");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiAgentRegistryMapper aiAgentRegistryMapper;
    private final AiCapabilityOptionMapper aiCapabilityOptionMapper;

    /**
     * 分页查询智能体注册表。
     */
    public PageResponse<AiAgentListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiAgentRegistryRecord> comparator = resolveComparator(request.sorts());
        List<AiAgentListItemResponse> matched = aiAgentRegistryMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(AiAdminSupport.toStatus(record.enabled())))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询智能体注册表详情。
     */
    public AiAgentDetailResponse detail(String agentId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(agentId));
    }

    /**
     * 获取智能体注册表表单选项。
     */
    public AiAgentFormOptionsResponse formOptions() {
        aiAdminAccessService.ensureAiAdminAccess();
        return new AiAgentFormOptionsResponse(
                AiAdminSupport.mergeDistinctStrings(List.of(
                                aiCapabilityOptionMapper.selectUserCapabilityCodes(),
                                aiCapabilityOptionMapper.selectAgentCapabilityCodes(),
                                aiCapabilityOptionMapper.selectToolCapabilityCodes(),
                                aiCapabilityOptionMapper.selectMcpCapabilityCodes(),
                                aiCapabilityOptionMapper.selectSkillCapabilityCodes()
                        )).stream()
                        .map(option -> new AiAgentFormOptionsResponse.CapabilityOption(option, option))
                        .toList(),
                List.of(
                        new AiAgentFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new AiAgentFormOptionsResponse.StatusOption("DISABLED", "停用")
                )
        );
    }

    /**
     * 新建智能体注册记录。
     */
    @Transactional
    public AiAgentMutationResponse create(SaveAiAgentRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        String agentCode = AiAdminSupport.normalize(request.agentCode());
        String agentName = AiAdminSupport.normalize(request.agentName());
        String capabilityCode = AiAdminSupport.normalize(request.capabilityCode());
        validateCode(agentCode, null);
        validateStatus(request.enabled());
        AiAgentRegistryRecord record = new AiAgentRegistryRecord(
                AiAdminSupport.buildId("ai_agent"),
                agentCode,
                agentName,
                capabilityCode,
                request.enabled(),
                AiAdminSupport.normalizeNullable(request.systemPrompt()),
                normalizeMetadataJson(request.metadataJson()),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        aiAgentRegistryMapper.insert(record);
        return new AiAgentMutationResponse(record.agentId());
    }

    /**
     * 更新智能体注册记录。
     */
    @Transactional
    public AiAgentMutationResponse update(String agentId, SaveAiAgentRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        requireRecord(agentId);
        String agentCode = AiAdminSupport.normalize(request.agentCode());
        String agentName = AiAdminSupport.normalize(request.agentName());
        String capabilityCode = AiAdminSupport.normalize(request.capabilityCode());
        validateCode(agentCode, agentId);
        validateStatus(request.enabled());
        AiAgentRegistryRecord record = new AiAgentRegistryRecord(
                agentId,
                agentCode,
                agentName,
                capabilityCode,
                request.enabled(),
                AiAdminSupport.normalizeNullable(request.systemPrompt()),
                normalizeMetadataJson(request.metadataJson()),
                null,
                LocalDateTime.now()
        );
        aiAgentRegistryMapper.update(record);
        return new AiAgentMutationResponse(agentId);
    }

    private AiAgentListItemResponse toListItem(AiAgentRegistryRecord record) {
        return new AiAgentListItemResponse(
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.capabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiAgentDetailResponse toDetail(AiAgentRegistryRecord record) {
        return new AiAgentDetailResponse(
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.capabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                record.systemPrompt(),
                record.metadataJson(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("status".equals(filter.field())) {
                status = validateStatus(value);
            }
        }
        return new Filters(status);
    }

    private Comparator<AiAgentRegistryRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiAgentRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiAgentRegistryRecord> comparator = switch (sort.field()) {
            case "agentCode" -> Comparator.comparing(AiAgentRegistryRecord::agentCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "agentName" -> Comparator.comparing(AiAgentRegistryRecord::agentName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "capabilityCode" -> Comparator.comparing(AiAgentRegistryRecord::capabilityCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(record -> AiAdminSupport.toStatus(record.enabled()));
            case "createdAt" -> Comparator.comparing(AiAgentRegistryRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiAgentRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiAgentRegistryRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.agentCode().toLowerCase().contains(normalized)
                || record.agentName().toLowerCase().contains(normalized)
                || record.capabilityCode().toLowerCase().contains(normalized)
                || (record.systemPrompt() != null && record.systemPrompt().toLowerCase().contains(normalized))
                || (record.metadataJson() != null && record.metadataJson().toLowerCase().contains(normalized));
    }

    private AiAgentRegistryRecord requireRecord(String agentId) {
        AiAgentRegistryRecord record = aiAgentRegistryMapper.selectById(agentId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "智能体注册记录不存在",
                    Map.of("agentId", agentId)
            );
        }
        return record;
    }

    private void validateCode(String agentCode, String excludeAgentId) {
        Long total = aiAgentRegistryMapper.countByAgentCode(agentCode, excludeAgentId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.ALREADY_EXISTS",
                    HttpStatus.CONFLICT,
                    "智能体编码已存在",
                    Map.of("agentCode", agentCode)
            );
        }
    }

    private String validateStatus(Boolean enabled) {
        if (enabled == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "启用状态不能为空",
                    Map.of("enabled", "null")
            );
        }
        return AiAdminSupport.toStatus(enabled);
    }

    /**
     * 校验列表筛选中的启用状态。
     */
    private String validateStatus(String status) {
        if (SUPPORTED_STATUSES.contains(status)) {
            return status;
        }
        throw unsupported("启用状态不合法", status, SUPPORTED_STATUSES);
    }

    private String normalizeMetadataJson(String metadataJson) {
        String normalized = AiAdminSupport.normalizeNullable(metadataJson);
        return normalized == null ? "{}" : normalized;
    }

    private ContractException unsupported(String message, String value, List<String> allowedValues) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("value", value, "allowedValues", allowedValues)
        );
    }

    private record Filters(
            String status
    ) {
    }
}
