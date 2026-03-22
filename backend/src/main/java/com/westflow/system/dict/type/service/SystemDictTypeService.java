package com.westflow.system.dict.type.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.system.dict.item.mapper.SystemDictItemMapper;
import com.westflow.system.dict.type.api.SaveSystemDictTypeRequest;
import com.westflow.system.dict.type.api.SystemDictTypeDetailResponse;
import com.westflow.system.dict.type.api.SystemDictTypeFormOptionsResponse;
import com.westflow.system.dict.type.api.SystemDictTypeListItemResponse;
import com.westflow.system.dict.type.api.SystemDictTypeMutationResponse;
import com.westflow.system.dict.type.mapper.SystemDictTypeMapper;
import com.westflow.system.dict.type.model.SystemDictTypeRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemDictTypeService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "typeCode", "typeName", "status");
    private static final List<String> SUPPORTED_GROUP_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_STATUS = List.of("ENABLED", "DISABLED");

    private final SystemDictTypeMapper systemDictTypeMapper;
    private final SystemDictItemMapper systemDictItemMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<SystemDictTypeListItemResponse> page(PageRequest request) {
        ensureSystemAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<SystemDictTypeRecord> comparator = resolveComparator(request.sorts());
        List<SystemDictTypeRecord> matched = systemDictTypeMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .sorted(comparator)
                .toList();

        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<SystemDictTypeListItemResponse> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream()
                        .map(this::toListItem)
                        .toList();

        List<PageResponse.GroupValue> groups = resolveGroups(request.groups(), matched);

        return new PageResponse<>(request.page(), pageSize, total, pages, records, groups);
    }

    public SystemDictTypeDetailResponse detail(String dictTypeId) {
        ensureSystemAccess();
        return toDetail(requireType(dictTypeId));
    }

    public SystemDictTypeFormOptionsResponse formOptions() {
        ensureSystemAccess();
        return new SystemDictTypeFormOptionsResponse(List.of(
                new SystemDictTypeFormOptionsResponse.StatusOption("ENABLED", "启用"),
                new SystemDictTypeFormOptionsResponse.StatusOption("DISABLED", "停用")
        ));
    }

    @Transactional
    public SystemDictTypeMutationResponse create(SaveSystemDictTypeRequest request) {
        ensureSystemAccess();
        validateTypeCode(request.typeCode(), null);
        String dictTypeId = buildId("dict_type");
        Instant now = Instant.now();
        systemDictTypeMapper.upsert(new SystemDictTypeRecord(
                dictTypeId,
                normalize(request.typeCode()),
                normalize(request.typeName()),
                normalizeNullable(request.description()),
                Boolean.TRUE.equals(request.enabled()),
                now,
                now
        ));
        return new SystemDictTypeMutationResponse(dictTypeId);
    }

    @Transactional
    public SystemDictTypeMutationResponse update(String dictTypeId, SaveSystemDictTypeRequest request) {
        ensureSystemAccess();
        requireType(dictTypeId);
        validateTypeCode(request.typeCode(), dictTypeId);
        SystemDictTypeRecord existing = requireType(dictTypeId);
        systemDictTypeMapper.upsert(new SystemDictTypeRecord(
                existing.dictTypeId(),
                normalize(request.typeCode()),
                normalize(request.typeName()),
                normalizeNullable(request.description()),
                Boolean.TRUE.equals(request.enabled()),
                existing.createdAt(),
                Instant.now()
        ));
        return new SystemDictTypeMutationResponse(existing.dictTypeId());
    }

    private SystemDictTypeListItemResponse toListItem(SystemDictTypeRecord record) {
        return new SystemDictTypeListItemResponse(
                record.dictTypeId(),
                record.typeCode(),
                record.typeName(),
                record.description(),
                record.enabled() ? "ENABLED" : "DISABLED",
                systemDictItemMapper.countByTypeId(record.dictTypeId()),
                record.createdAt()
        );
    }

    private SystemDictTypeDetailResponse toDetail(SystemDictTypeRecord record) {
        return new SystemDictTypeDetailResponse(
                record.dictTypeId(),
                record.typeCode(),
                record.typeName(),
                record.description(),
                record.enabled() ? "ENABLED" : "DISABLED",
                systemDictItemMapper.countByTypeId(record.dictTypeId()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean matchesKeyword(SystemDictTypeRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.typeCode().toLowerCase().contains(normalized)
                || record.typeName().toLowerCase().contains(normalized)
                || (record.description() != null && record.description().toLowerCase().contains(normalized));
    }

    private Comparator<SystemDictTypeRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(SystemDictTypeRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<SystemDictTypeRecord> comparator = switch (sort.field()) {
            case "typeCode" -> Comparator.comparing(SystemDictTypeRecord::typeCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "typeName" -> Comparator.comparing(SystemDictTypeRecord::typeName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SystemDictTypeRecord::enabled, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(SystemDictTypeRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("status".equals(filter.field())) {
                enabled = resolveStatus(value);
            }
        }
        return new Filters(enabled);
    }

    private Boolean resolveStatus(String value) {
        String normalized = normalizeNullable(value);
        if ("ENABLED".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("DISABLED".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "字典类型状态不合法",
                Map.of("status", value, "allowedStatuses", SUPPORTED_STATUS)
        );
    }

    private List<PageResponse.GroupValue> resolveGroups(List<GroupItem> groups, List<SystemDictTypeRecord> records) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .flatMap(group -> {
                    if (!SUPPORTED_GROUP_FIELDS.contains(group.field())) {
                        throw unsupported("不支持的分组字段", group.field(), SUPPORTED_GROUP_FIELDS);
                    }
                    if (!"status".equals(group.field())) {
                        return List.<PageResponse.GroupValue>of().stream();
                    }
                    return records.stream()
                            .map(this::resolveStatusText)
                            .distinct()
                            .map(value -> new PageResponse.GroupValue(group.field(), value))
                            .toList()
                            .stream();
                })
                .toList();
    }

    private String resolveStatusText(SystemDictTypeRecord record) {
        return Boolean.TRUE.equals(record.enabled()) ? "ENABLED" : "DISABLED";
    }

    private void validateTypeCode(String typeCode, String excludeDictTypeId) {
        String normalized = normalize(typeCode);
        if (systemDictTypeMapper.existsByCode(normalized, excludeDictTypeId)) {
            throw new ContractException(
                    "BIZ.DICT_TYPE_CODE_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "字典类型编码已存在",
                    Map.of("typeCode", normalized)
            );
        }
    }

    private SystemDictTypeRecord requireType(String dictTypeId) {
        SystemDictTypeRecord record = systemDictTypeMapper.selectById(dictTypeId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "字典类型不存在",
                    Map.of("dictTypeId", dictTypeId)
            );
        }
        return record;
    }

    private void ensureSystemAccess() {
        String userId = currentUserId();
        if (!fixtureAuthService.isProcessAdmin(userId) && !fixtureAuthService.isSystemAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员可以访问字典类型管理",
                    Map.of("userId", userId)
            );
        }
    }

    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }

    private String currentUserId() {
        return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalize(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "参数不能为空"
            );
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private record Filters(Boolean enabled) {
    }
}
