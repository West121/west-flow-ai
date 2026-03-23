package com.westflow.aiadmin.tool.service;

import com.westflow.aiadmin.mapper.AiCapabilityOptionMapper;
import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminObservabilityService;
import com.westflow.aiadmin.support.AiAdminSupport;
import com.westflow.aiadmin.tool.api.AiToolDetailResponse;
import com.westflow.aiadmin.tool.api.AiToolFormOptionsResponse;
import com.westflow.aiadmin.tool.api.AiToolListItemResponse;
import com.westflow.aiadmin.tool.api.AiToolMutationResponse;
import com.westflow.aiadmin.tool.api.SaveAiToolRequest;
import com.westflow.aiadmin.tool.mapper.AiToolRegistryMapper;
import com.westflow.aiadmin.tool.model.AiToolRegistryRecord;
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
 * AI 工具注册表管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiToolRegistryService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "toolCategory", "actionMode");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "toolCode", "toolName", "toolCategory", "actionMode", "status");
    private static final List<String> SUPPORTED_STATUSES = List.of("ENABLED", "DISABLED");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");
    private static final List<String> SUPPORTED_TOOL_CATEGORIES = List.of("PLATFORM", "MCP", "SKILL");
    private static final List<String> SUPPORTED_ACTION_MODES = List.of("READ", "WRITE");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiToolRegistryMapper aiToolRegistryMapper;
    private final AiCapabilityOptionMapper aiCapabilityOptionMapper;
    private final AiAdminObservabilityService aiAdminObservabilityService;

    /**
     * 分页查询工具注册表。
     */
    public PageResponse<AiToolListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiToolRegistryRecord> comparator = resolveComparator(request.sorts());
        List<AiToolListItemResponse> matched = aiToolRegistryMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(AiAdminSupport.toStatus(record.enabled())))
                .filter(record -> filters.toolCategory() == null || filters.toolCategory().equals(record.toolCategory()))
                .filter(record -> filters.actionMode() == null || filters.actionMode().equals(record.actionMode()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询工具注册表详情。
     */
    public AiToolDetailResponse detail(String toolId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(toolId));
    }

    /**
     * 获取工具表单选项。
     */
    public AiToolFormOptionsResponse formOptions() {
        aiAdminAccessService.ensureAiAdminAccess();
        return new AiToolFormOptionsResponse(
                AiAdminSupport.mergeDistinctStrings(List.of(
                                aiCapabilityOptionMapper.selectUserCapabilityCodes(),
                                aiCapabilityOptionMapper.selectAgentCapabilityCodes(),
                                aiCapabilityOptionMapper.selectToolCapabilityCodes(),
                                aiCapabilityOptionMapper.selectMcpCapabilityCodes(),
                                aiCapabilityOptionMapper.selectSkillCapabilityCodes()
                        )).stream()
                        .map(option -> new AiToolFormOptionsResponse.CapabilityOption(option, option))
                        .toList(),
                List.of(
                        new AiToolFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new AiToolFormOptionsResponse.StatusOption("DISABLED", "停用")
                ),
                List.of(
                        new AiToolFormOptionsResponse.CategoryOption("PLATFORM", "平台工具"),
                        new AiToolFormOptionsResponse.CategoryOption("MCP", "外部 MCP"),
                        new AiToolFormOptionsResponse.CategoryOption("SKILL", "技能")
                ),
                List.of(
                        new AiToolFormOptionsResponse.ActionModeOption("READ", "读操作"),
                        new AiToolFormOptionsResponse.ActionModeOption("WRITE", "写操作")
                )
        );
    }

    /**
     * 新建工具注册记录。
     */
    @Transactional
    public AiToolMutationResponse create(SaveAiToolRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        String toolCode = AiAdminSupport.normalize(request.toolCode());
        String toolName = AiAdminSupport.normalize(request.toolName());
        String toolCategory = validateToolCategory(AiAdminSupport.normalize(request.toolCategory()));
        String actionMode = validateActionMode(AiAdminSupport.normalize(request.actionMode()));
        validateCode(toolCode, null);
        validateStatus(request.enabled());
        AiToolRegistryRecord record = new AiToolRegistryRecord(
                AiAdminSupport.buildId("ai_tool"),
                toolCode,
                toolName,
                toolCategory,
                actionMode,
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        aiToolRegistryMapper.insert(record);
        return new AiToolMutationResponse(record.toolId());
    }

    /**
     * 更新工具注册记录。
     */
    @Transactional
    public AiToolMutationResponse update(String toolId, SaveAiToolRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        requireRecord(toolId);
        String toolCode = AiAdminSupport.normalize(request.toolCode());
        String toolName = AiAdminSupport.normalize(request.toolName());
        String toolCategory = validateToolCategory(AiAdminSupport.normalize(request.toolCategory()));
        String actionMode = validateActionMode(AiAdminSupport.normalize(request.actionMode()));
        validateCode(toolCode, toolId);
        validateStatus(request.enabled());
        AiToolRegistryRecord record = new AiToolRegistryRecord(
                toolId,
                toolCode,
                toolName,
                toolCategory,
                actionMode,
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                null,
                LocalDateTime.now()
        );
        aiToolRegistryMapper.update(record);
        return new AiToolMutationResponse(toolId);
    }

    private AiToolListItemResponse toListItem(AiToolRegistryRecord record) {
        return new AiToolListItemResponse(
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.toolCategory(),
                record.actionMode(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiToolDetailResponse toDetail(AiToolRegistryRecord record) {
        AiAdminObservabilityService.RegistryDiagnostics diagnostics = aiAdminObservabilityService.describeTool(record);
        return new AiToolDetailResponse(
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.toolCategory(),
                record.actionMode(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                diagnostics.description(),
                record.metadataJson(),
                diagnostics.observability(),
                diagnostics.linkedAgents(),
                diagnostics.linkedResource(),
                diagnostics.linkedMcp(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        String toolCategory = null;
        String actionMode = null;
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
                case "toolCategory" -> toolCategory = validateToolCategory(value);
                case "actionMode" -> actionMode = validateActionMode(value);
                default -> {
                }
            }
        }
        return new Filters(status, toolCategory, actionMode);
    }

    private Comparator<AiToolRegistryRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiToolRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiToolRegistryRecord> comparator = switch (sort.field()) {
            case "toolCode" -> Comparator.comparing(AiToolRegistryRecord::toolCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "toolName" -> Comparator.comparing(AiToolRegistryRecord::toolName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "toolCategory" -> Comparator.comparing(AiToolRegistryRecord::toolCategory, Comparator.nullsLast(Comparator.naturalOrder()));
            case "actionMode" -> Comparator.comparing(AiToolRegistryRecord::actionMode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(record -> AiAdminSupport.toStatus(record.enabled()));
            case "createdAt" -> Comparator.comparing(AiToolRegistryRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiToolRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiToolRegistryRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.toolCode().toLowerCase().contains(normalized)
                || record.toolName().toLowerCase().contains(normalized)
                || record.toolCategory().toLowerCase().contains(normalized)
                || record.actionMode().toLowerCase().contains(normalized)
                || (record.requiredCapabilityCode() != null && record.requiredCapabilityCode().toLowerCase().contains(normalized))
                || (record.metadataJson() != null && record.metadataJson().toLowerCase().contains(normalized));
    }

    private AiToolRegistryRecord requireRecord(String toolId) {
        AiToolRegistryRecord record = aiToolRegistryMapper.selectById(toolId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "工具注册记录不存在",
                    Map.of("toolId", toolId)
            );
        }
        return record;
    }

    private void validateCode(String toolCode, String excludeToolId) {
        Long total = aiToolRegistryMapper.countByToolCode(toolCode, excludeToolId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.ALREADY_EXISTS",
                    HttpStatus.CONFLICT,
                    "工具编码已存在",
                    Map.of("toolCode", toolCode)
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

    private String validateToolCategory(String toolCategory) {
        if (SUPPORTED_TOOL_CATEGORIES.contains(toolCategory)) {
            return toolCategory;
        }
        throw unsupported("工具分类不合法", toolCategory, SUPPORTED_TOOL_CATEGORIES);
    }

    private String validateActionMode(String actionMode) {
        if (SUPPORTED_ACTION_MODES.contains(actionMode)) {
            return actionMode;
        }
        throw unsupported("工具动作模式不合法", actionMode, SUPPORTED_ACTION_MODES);
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
            String toolCategory,
            String actionMode
    ) {
    }
}
