package com.westflow.system.dict.item.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.system.dict.item.api.SaveSystemDictItemRequest;
import com.westflow.system.dict.item.api.SystemDictItemDetailResponse;
import com.westflow.system.dict.item.api.SystemDictItemFormOptionsResponse;
import com.westflow.system.dict.item.api.SystemDictItemListItemResponse;
import com.westflow.system.dict.item.api.SystemDictItemMutationResponse;
import com.westflow.system.dict.item.mapper.SystemDictItemMapper;
import com.westflow.system.dict.item.model.SystemDictItemRecord;
import com.westflow.system.dict.type.mapper.SystemDictTypeMapper;
import com.westflow.system.dict.type.model.SystemDictTypeRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典项管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemDictItemService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "dictTypeId", "dictTypeCode");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "itemCode", "itemLabel", "itemValue", "sortOrder");
    private static final List<String> SUPPORTED_GROUP_FIELDS = List.of("status", "dictTypeCode");
    private static final List<String> SUPPORTED_STATUS = List.of("ENABLED", "DISABLED");

    private final SystemDictItemMapper systemDictItemMapper;
    private final SystemDictTypeMapper systemDictTypeMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<SystemDictItemListItemResponse> page(PageRequest request) {
        ensureSystemAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<SystemDictItemRecord> comparator = resolveComparator(request.sorts());
        List<SystemDictItemRecord> matched = systemDictItemMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .filter(record -> filters.dictTypeId() == null || filters.dictTypeId().equals(record.dictTypeId()))
                .filter(record -> filters.dictTypeCode() == null || filters.dictTypeCode().equals(resolveTypeCode(record.dictTypeId())))
                .sorted(comparator)
                .toList();

        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<SystemDictItemListItemResponse> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream()
                        .map(this::toListItem)
                        .toList();

        List<PageResponse.GroupValue> groups = resolveGroups(request.groups(), matched);

        return new PageResponse<>(request.page(), pageSize, total, pages, records, groups);
    }

    public SystemDictItemDetailResponse detail(String dictItemId) {
        ensureSystemAccess();
        return toDetail(requireItem(dictItemId));
    }

    public SystemDictItemFormOptionsResponse formOptions() {
        ensureSystemAccess();
        return new SystemDictItemFormOptionsResponse(
                systemDictTypeMapper.selectAll().stream()
                        .map(type -> new SystemDictItemFormOptionsResponse.DictTypeOption(
                                type.dictTypeId(),
                                type.typeCode(),
                                type.typeName()
                        ))
                        .toList(),
                List.of(
                        new SystemDictItemFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new SystemDictItemFormOptionsResponse.StatusOption("DISABLED", "停用")
                )
        );
    }

    @Transactional
    public SystemDictItemMutationResponse create(SaveSystemDictItemRequest request) {
        ensureSystemAccess();
        SystemDictTypeRecord type = requireType(request.dictTypeId());
        validateItemCode(type.dictTypeId(), request.itemCode(), null);
        String dictItemId = buildId("dict_item");
        Instant now = Instant.now();
        systemDictItemMapper.upsert(new SystemDictItemRecord(
                dictItemId,
                type.dictTypeId(),
                normalize(request.itemCode()),
                normalize(request.itemLabel()),
                normalize(request.itemValue()),
                normalizeSortOrder(request.sortOrder()),
                normalizeNullable(request.remark()),
                Boolean.TRUE.equals(request.enabled()),
                now,
                now
        ));
        return new SystemDictItemMutationResponse(dictItemId);
    }

    @Transactional
    public SystemDictItemMutationResponse update(String dictItemId, SaveSystemDictItemRequest request) {
        ensureSystemAccess();
        SystemDictItemRecord existing = requireItem(dictItemId);
        SystemDictTypeRecord type = requireType(request.dictTypeId());
        validateItemCode(type.dictTypeId(), request.itemCode(), dictItemId);
        systemDictItemMapper.upsert(new SystemDictItemRecord(
                existing.dictItemId(),
                type.dictTypeId(),
                normalize(request.itemCode()),
                normalize(request.itemLabel()),
                normalize(request.itemValue()),
                normalizeSortOrder(request.sortOrder()),
                normalizeNullable(request.remark()),
                Boolean.TRUE.equals(request.enabled()),
                existing.createdAt(),
                Instant.now()
        ));
        return new SystemDictItemMutationResponse(existing.dictItemId());
    }

    private SystemDictItemListItemResponse toListItem(SystemDictItemRecord record) {
        return new SystemDictItemListItemResponse(
                record.dictItemId(),
                record.dictTypeId(),
                resolveTypeCode(record.dictTypeId()),
                resolveTypeName(record.dictTypeId()),
                record.itemCode(),
                record.itemLabel(),
                record.itemValue(),
                record.sortOrder(),
                record.enabled() ? "ENABLED" : "DISABLED",
                record.createdAt()
        );
    }

    private SystemDictItemDetailResponse toDetail(SystemDictItemRecord record) {
        return new SystemDictItemDetailResponse(
                record.dictItemId(),
                record.dictTypeId(),
                resolveTypeCode(record.dictTypeId()),
                resolveTypeName(record.dictTypeId()),
                record.itemCode(),
                record.itemLabel(),
                record.itemValue(),
                record.sortOrder(),
                record.remark(),
                record.enabled() ? "ENABLED" : "DISABLED",
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean matchesKeyword(SystemDictItemRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.itemCode().toLowerCase().contains(normalized)
                || record.itemLabel().toLowerCase().contains(normalized)
                || record.itemValue().toLowerCase().contains(normalized)
                || resolveTypeCode(record.dictTypeId()).toLowerCase().contains(normalized)
                || resolveTypeName(record.dictTypeId()).toLowerCase().contains(normalized)
                || (record.remark() != null && record.remark().toLowerCase().contains(normalized));
    }

    private Comparator<SystemDictItemRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(SystemDictItemRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<SystemDictItemRecord> comparator = switch (sort.field()) {
            case "itemCode" -> Comparator.comparing(SystemDictItemRecord::itemCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "itemLabel" -> Comparator.comparing(SystemDictItemRecord::itemLabel, Comparator.nullsLast(Comparator.naturalOrder()));
            case "itemValue" -> Comparator.comparing(SystemDictItemRecord::itemValue, Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortOrder" -> Comparator.comparing(SystemDictItemRecord::sortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(SystemDictItemRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private List<PageResponse.GroupValue> resolveGroups(List<GroupItem> groups, List<SystemDictItemRecord> records) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .flatMap(group -> {
                    if (!SUPPORTED_GROUP_FIELDS.contains(group.field())) {
                        throw unsupported("不支持的分组字段", group.field(), SUPPORTED_GROUP_FIELDS);
                    }
                    Stream<String> values = switch (group.field()) {
                        case "status" -> records.stream().map(this::resolveStatusText);
                        case "dictTypeCode" -> records.stream().map(record -> resolveTypeCode(record.dictTypeId()));
                        default -> Stream.<String>empty();
                    };
                    return values.distinct().map(value -> new PageResponse.GroupValue(group.field(), value));
                })
                .toList();
    }

    private String resolveStatusText(SystemDictItemRecord record) {
        return Boolean.TRUE.equals(record.enabled()) ? "ENABLED" : "DISABLED";
    }

    private String resolveTypeCode(String dictTypeId) {
        SystemDictTypeRecord type = systemDictTypeMapper.selectById(dictTypeId);
        return type == null ? "-" : type.typeCode();
    }

    private String resolveTypeName(String dictTypeId) {
        SystemDictTypeRecord type = systemDictTypeMapper.selectById(dictTypeId);
        return type == null ? "-" : type.typeName();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String dictTypeId = null;
        String dictTypeCode = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> enabled = resolveStatus(value);
                case "dictTypeId" -> dictTypeId = normalizeNullable(value);
                case "dictTypeCode" -> dictTypeCode = normalizeNullable(value);
                default -> {
                }
            }
        }
        return new Filters(enabled, dictTypeId, dictTypeCode);
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
                "字典项状态不合法",
                Map.of("status", value, "allowedStatuses", SUPPORTED_STATUS)
        );
    }

    private void validateItemCode(String dictTypeId, String itemCode, String excludeDictItemId) {
        String normalized = normalize(itemCode);
        if (systemDictItemMapper.existsByCodeInType(dictTypeId, normalized, excludeDictItemId)) {
            throw new ContractException(
                    "BIZ.DICT_ITEM_CODE_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "同类型下字典项编码已存在",
                    Map.of(
                            "dictTypeId", dictTypeId,
                            "itemCode", normalized
                    )
            );
        }
    }

    private SystemDictTypeRecord requireType(String dictTypeId) {
        SystemDictTypeRecord type = systemDictTypeMapper.selectById(dictTypeId);
        if (type == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.BAD_REQUEST,
                    "字典类型不存在",
                    Map.of("dictTypeId", dictTypeId)
            );
        }
        return type;
    }

    private SystemDictItemRecord requireItem(String dictItemId) {
        SystemDictItemRecord record = systemDictItemMapper.selectById(dictItemId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "字典项不存在",
                    Map.of("dictItemId", dictItemId)
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
                    "仅系统管理员可以访问字典项管理",
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

    private Integer normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "排序值不能为空"
            );
        }
        return sortOrder;
    }

    private record Filters(Boolean enabled, String dictTypeId, String dictTypeCode) {
    }
}
