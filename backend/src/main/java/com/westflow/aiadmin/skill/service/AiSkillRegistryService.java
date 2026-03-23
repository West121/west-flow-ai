package com.westflow.aiadmin.skill.service;

import com.westflow.aiadmin.mapper.AiCapabilityOptionMapper;
import com.westflow.aiadmin.skill.api.AiSkillDetailResponse;
import com.westflow.aiadmin.skill.api.AiSkillFormOptionsResponse;
import com.westflow.aiadmin.skill.api.AiSkillListItemResponse;
import com.westflow.aiadmin.skill.api.AiSkillMutationResponse;
import com.westflow.aiadmin.skill.api.SaveAiSkillRequest;
import com.westflow.aiadmin.skill.mapper.AiSkillRegistryMapper;
import com.westflow.aiadmin.skill.model.AiSkillRegistryRecord;
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
 * AI Skill 注册表管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiSkillRegistryService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "skillCode", "skillName", "status");
    private static final List<String> SUPPORTED_STATUSES = List.of("ENABLED", "DISABLED");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiSkillRegistryMapper aiSkillRegistryMapper;
    private final AiCapabilityOptionMapper aiCapabilityOptionMapper;
    private final AiAdminObservabilityService aiAdminObservabilityService;

    /**
     * 分页查询 Skill 注册表。
     */
    public PageResponse<AiSkillListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiSkillRegistryRecord> comparator = resolveComparator(request.sorts());
        List<AiSkillListItemResponse> matched = aiSkillRegistryMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(AiAdminSupport.toStatus(record.enabled())))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询 Skill 注册表详情。
     */
    public AiSkillDetailResponse detail(String skillId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(skillId));
    }

    /**
     * 获取 Skill 表单选项。
     */
    public AiSkillFormOptionsResponse formOptions() {
        aiAdminAccessService.ensureAiAdminAccess();
        return new AiSkillFormOptionsResponse(
                AiAdminSupport.mergeDistinctStrings(List.of(
                                aiCapabilityOptionMapper.selectUserCapabilityCodes(),
                                aiCapabilityOptionMapper.selectAgentCapabilityCodes(),
                                aiCapabilityOptionMapper.selectToolCapabilityCodes(),
                                aiCapabilityOptionMapper.selectMcpCapabilityCodes(),
                                aiCapabilityOptionMapper.selectSkillCapabilityCodes()
                        )).stream()
                        .map(option -> new AiSkillFormOptionsResponse.CapabilityOption(option, option))
                        .toList(),
                List.of(
                        new AiSkillFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new AiSkillFormOptionsResponse.StatusOption("DISABLED", "停用")
                )
        );
    }

    /**
     * 新建 Skill 注册记录。
     */
    @Transactional
    public AiSkillMutationResponse create(SaveAiSkillRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        String skillCode = AiAdminSupport.normalize(request.skillCode());
        String skillName = AiAdminSupport.normalize(request.skillName());
        validateCode(skillCode, null);
        validateStatus(request.enabled());
        AiSkillRegistryRecord record = new AiSkillRegistryRecord(
                AiAdminSupport.buildId("ai_skill"),
                skillCode,
                skillName,
                AiAdminSupport.normalizeNullable(request.skillPath()),
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        aiSkillRegistryMapper.insert(record);
        return new AiSkillMutationResponse(record.skillId());
    }

    /**
     * 更新 Skill 注册记录。
     */
    @Transactional
    public AiSkillMutationResponse update(String skillId, SaveAiSkillRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        requireRecord(skillId);
        String skillCode = AiAdminSupport.normalize(request.skillCode());
        String skillName = AiAdminSupport.normalize(request.skillName());
        validateCode(skillCode, skillId);
        validateStatus(request.enabled());
        AiSkillRegistryRecord record = new AiSkillRegistryRecord(
                skillId,
                skillCode,
                skillName,
                AiAdminSupport.normalizeNullable(request.skillPath()),
                AiAdminSupport.normalizeNullable(request.requiredCapabilityCode()),
                request.enabled(),
                normalizeMetadataJson(request.metadataJson()),
                null,
                LocalDateTime.now()
        );
        aiSkillRegistryMapper.update(record);
        return new AiSkillMutationResponse(skillId);
    }

    private AiSkillListItemResponse toListItem(AiSkillRegistryRecord record) {
        return new AiSkillListItemResponse(
                record.skillId(),
                record.skillCode(),
                record.skillName(),
                record.skillPath(),
                record.requiredCapabilityCode(),
                record.enabled(),
                AiAdminSupport.toStatus(record.enabled()),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiSkillDetailResponse toDetail(AiSkillRegistryRecord record) {
        AiAdminObservabilityService.RegistryDiagnostics diagnostics = aiAdminObservabilityService.describeSkill(record);
        return new AiSkillDetailResponse(
                record.skillId(),
                record.skillCode(),
                record.skillName(),
                record.skillPath(),
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

    private Comparator<AiSkillRegistryRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiSkillRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiSkillRegistryRecord> comparator = switch (sort.field()) {
            case "skillCode" -> Comparator.comparing(AiSkillRegistryRecord::skillCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "skillName" -> Comparator.comparing(AiSkillRegistryRecord::skillName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(record -> AiAdminSupport.toStatus(record.enabled()));
            case "createdAt" -> Comparator.comparing(AiSkillRegistryRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiSkillRegistryRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiSkillRegistryRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.skillCode().toLowerCase().contains(normalized)
                || record.skillName().toLowerCase().contains(normalized)
                || (record.skillPath() != null && record.skillPath().toLowerCase().contains(normalized))
                || (record.requiredCapabilityCode() != null && record.requiredCapabilityCode().toLowerCase().contains(normalized))
                || (record.metadataJson() != null && record.metadataJson().toLowerCase().contains(normalized));
    }

    private AiSkillRegistryRecord requireRecord(String skillId) {
        AiSkillRegistryRecord record = aiSkillRegistryMapper.selectById(skillId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "Skill 注册记录不存在",
                    Map.of("skillId", skillId)
            );
        }
        return record;
    }

    private void validateCode(String skillCode, String excludeSkillId) {
        Long total = aiSkillRegistryMapper.countBySkillCode(skillCode, excludeSkillId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.ALREADY_EXISTS",
                    HttpStatus.CONFLICT,
                    "Skill 编码已存在",
                    Map.of("skillCode", skillCode)
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
