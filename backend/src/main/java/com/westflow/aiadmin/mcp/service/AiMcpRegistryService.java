package com.westflow.aiadmin.mcp.service;

import com.westflow.aiadmin.mapper.AiCapabilityOptionMapper;
import com.westflow.aiadmin.mcp.api.AiMcpDetailResponse;
import com.westflow.aiadmin.mcp.api.AiMcpFormOptionsResponse;
import com.westflow.aiadmin.mcp.api.AiMcpListItemResponse;
import com.westflow.aiadmin.mcp.api.AiMcpMutationResponse;
import com.westflow.aiadmin.mcp.api.SaveAiMcpRequest;
import com.westflow.aiadmin.mcp.mapper.AiMcpRegistryMapper;
import com.westflow.aiadmin.mcp.model.AiMcpRegistryRecord;
import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminObservabilityService;
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
 * AI MCP 注册表管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiMcpRegistryService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "transportType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "mcpCode", "mcpName", "transportType", "status");
    private static final List<String> SUPPORTED_STATUSES = List.of("ENABLED", "DISABLED");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");
    private static final List<String> SUPPORTED_TRANSPORT_TYPES = List.of("INTERNAL", "STREAMABLE_HTTP", "STDIO");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiMcpRegistryMapper aiMcpRegistryMapper;
    private final AiCapabilityOptionMapper aiCapabilityOptionMapper;
    private final AiAdminObservabilityService aiAdminObservabilityService;

    /**
     * 分页查询 MCP 注册表。
     */
    public PageResponse<AiMcpListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiMcpRegistryRecord> comparator = resolveComparator(request.sorts());
        List<AiMcpListItemResponse> matched = aiMcpRegistryMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(AiAdminSupport.toStatus(record.enabled())))
                .filter(record -> filters.transportType() == null || filters.transportType().equals(record.transportType()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询 MCP 注册表详情。
     */
    public AiMcpDetailResponse detail(String mcpId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(mcpId));
    }

    /**
     * 获取 MCP 表单选项。
     */
    public AiMcpFormOptionsResponse formOptions() {
        aiAdminAccessService.ensureAiAdminAccess();
        return new AiMcpFormOptionsResponse(
                AiAdminSupport.mergeDistinctStrings(List.of(
                                aiCapabilityOptionMapper.selectUserCapabilityCodes(),
                                aiCapabilityOptionMapper.selectAgentCapabilityCodes(),
                                aiCapabilityOptionMapper.selectToolCapabilityCodes(),
                                aiCapabilityOptionMapper.selectMcpCapabilityCodes(),
                                aiCapabilityOptionMapper.selectSkillCapabilityCodes()
                        )).stream()
                        .map(option -> new AiMcpFormOptionsResponse.CapabilityOption(option, option))
                        .toList(),
                List.of(
                        new AiMcpFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new AiMcpFormOptionsResponse.StatusOption("DISABLED", "停用")
                ),
                List.of(
                        new AiMcpFormOptionsResponse.TransportTypeOption("INTERNAL", "平台内置"),
                        new AiMcpFormOptionsResponse.TransportTypeOption("STREAMABLE_HTTP", "Streamable HTTP"),
                        new AiMcpFormOptionsResponse.TransportTypeOption("STDIO", "STDIO")
                )
        );
    }

    /**
     * 新建 MCP 注册记录。
     */
    @Transactional
    public AiMcpMutationResponse create(SaveAiMcpRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        String mcpCode = AiAdminSupport.normalize(request.mcpCode());
        String mcpName = AiAdminSupport.normalize(request.mcpName());
        String transportType = validateTransportType(AiAdminSupport.normalize(request.transportType()));
        validateCode(mcpCode, null);
        validateStatus(request.enabled());
        AiMcpRegistryRecord record = new AiMcpRegistryRecord(
                AiAdminSupport.buildId("ai_mcp"),
                mcpCode,
                mcpName,
                AiAdminSupport.normalizeNullable(request.endpointUrl()),
                transportType,
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        aiMcpRegistryMapper.insert(record);
        return new AiMcpMutationResponse(record.mcpId());
    }

    /**
     * 更新 MCP 注册记录。
     */
    @Transactional
    public AiMcpMutationResponse update(String mcpId, SaveAiMcpRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        requireRecord(mcpId);
        String mcpCode = AiAdminSupport.normalize(request.mcpCode());
        String mcpName = AiAdminSupport.normalize(request.mcpName());
        String transportType = validateTransportType(AiAdminSupport.normalize(request.transportType()));
        validateCode(mcpCode, mcpId);
        validateStatus(request.enabled());
        AiMcpRegistryRecord record = new AiMcpRegistryRecord(
                mcpId,
                mcpCode,
                mcpName,
                AiAdminSupport.normalizeNullable(request.endpointUrl()),
                transportType,
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                null,
                LocalDateTime.now()
        );
        aiMcpRegistryMapper.update(record);
        return new AiMcpMutationResponse(mcpId);
    }

    private AiMcpListItemResponse toListItem(AiMcpRegistryRecord record) {
        return new AiMcpListItemResponse(
                record.mcpId(),
                record.mcpCode(),
                record.mcpName(),
                record.endpointUrl(),
                record.transportType(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiMcpDetailResponse toDetail(AiMcpRegistryRecord record) {
        AiAdminObservabilityService.RegistryDiagnostics diagnostics = aiAdminObservabilityService.describeMcp(record);
        return new AiMcpDetailResponse(
                record.mcpId(),
                record.mcpCode(),
                record.mcpName(),
                record.endpointUrl(),
                record.transportType(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                diagnostics.description(),
                record.metadataJson(),
                diagnostics.observability(),
                diagnostics.linkedAgents(),
                diagnostics.linkedTools(),
                diagnostics.linkedSkills(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        String transportType = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> status = validateStatus(value);
                case "transportType" -> transportType = validateTransportType(value);
                default -> {
                }
            }
        }
        return new Filters(status, transportType);
    }

    private Comparator<AiMcpRegistryRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiMcpRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiMcpRegistryRecord> comparator = switch (sort.field()) {
            case "mcpCode" -> Comparator.comparing(AiMcpRegistryRecord::mcpCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "mcpName" -> Comparator.comparing(AiMcpRegistryRecord::mcpName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "transportType" -> Comparator.comparing(AiMcpRegistryRecord::transportType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(record -> AiAdminSupport.toStatus(record.enabled()));
            case "createdAt" -> Comparator.comparing(AiMcpRegistryRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiMcpRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiMcpRegistryRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.mcpCode().toLowerCase().contains(normalized)
                || record.mcpName().toLowerCase().contains(normalized)
                || (record.endpointUrl() != null && record.endpointUrl().toLowerCase().contains(normalized))
                || record.transportType().toLowerCase().contains(normalized)
                || (record.requiredCapabilityCode() != null && record.requiredCapabilityCode().toLowerCase().contains(normalized))
                || (record.metadataJson() != null && record.metadataJson().toLowerCase().contains(normalized));
    }

    private AiMcpRegistryRecord requireRecord(String mcpId) {
        AiMcpRegistryRecord record = aiMcpRegistryMapper.selectById(mcpId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "MCP 注册记录不存在",
                    Map.of("mcpId", mcpId)
            );
        }
        return record;
    }

    private void validateCode(String mcpCode, String excludeMcpId) {
        Long total = aiMcpRegistryMapper.countByMcpCode(mcpCode, excludeMcpId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.ALREADY_EXISTS",
                    HttpStatus.CONFLICT,
                    "MCP 编码已存在",
                    Map.of("mcpCode", mcpCode)
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

    private String validateTransportType(String transportType) {
        if (SUPPORTED_TRANSPORT_TYPES.contains(transportType)) {
            return transportType;
        }
        throw unsupported("传输方式不合法", transportType, SUPPORTED_TRANSPORT_TYPES);
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
            String status,
            String transportType
    ) {
    }
}
