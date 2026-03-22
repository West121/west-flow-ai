package com.westflow.system.notification.template.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.system.notification.template.api.NotificationTemplateDetailResponse;
import com.westflow.system.notification.template.api.NotificationTemplateFormOptionsResponse;
import com.westflow.system.notification.template.api.NotificationTemplateListItemResponse;
import com.westflow.system.notification.template.api.NotificationTemplateMutationResponse;
import com.westflow.system.notification.template.api.SaveNotificationTemplateRequest;
import com.westflow.system.notification.template.mapper.NotificationTemplateMapper;
import com.westflow.system.notification.template.model.NotificationTemplateRecord;
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
 * 通知模板管理服务。
 */
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "channelType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "templateCode", "templateName", "channelType");

    private final NotificationTemplateMapper notificationTemplateMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<NotificationTemplateListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        Filters filters = resolveFilters(request.filters());
        Comparator<NotificationTemplateRecord> comparator = resolveComparator(request.sorts());
        List<NotificationTemplateListItemResponse> records = notificationTemplateMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .filter(record -> filters.channelType() == null || filters.channelType().equals(record.channelType()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();

        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<NotificationTemplateListItemResponse> pageRecords = fromIndex >= records.size() ? List.of() : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    public NotificationTemplateDetailResponse detail(String templateId) {
        ensureProcessAdmin();
        return toDetail(requireTemplate(templateId));
    }

    public NotificationTemplateFormOptionsResponse formOptions() {
        ensureProcessAdmin();
        return new NotificationTemplateFormOptionsResponse(
                NotificationChannelType.orderedValues().stream()
                        .map(type -> new NotificationTemplateFormOptionsResponse.ChannelTypeOption(type.name(), type.label()))
                        .toList(),
                List.of(
                        new NotificationTemplateFormOptionsResponse.StatusOption("ENABLED", "启用"),
                        new NotificationTemplateFormOptionsResponse.StatusOption("DISABLED", "停用")
                )
        );
    }

    @Transactional
    public NotificationTemplateMutationResponse create(SaveNotificationTemplateRequest request) {
        ensureProcessAdmin();
        validateTemplateCode(request.templateCode(), null);
        String templateId = buildId("tpl");
        Instant now = Instant.now();
        notificationTemplateMapper.upsert(new NotificationTemplateRecord(
                templateId,
                normalize(request.templateCode()),
                normalize(request.templateName()),
                resolveType(request.channelType()).name(),
                normalize(request.titleTemplate()),
                normalize(request.contentTemplate()),
                Boolean.TRUE.equals(request.enabled()),
                normalizeNullable(request.remark()),
                now,
                now
        ));
        return new NotificationTemplateMutationResponse(templateId);
    }

    @Transactional
    public NotificationTemplateMutationResponse update(String templateId, SaveNotificationTemplateRequest request) {
        ensureProcessAdmin();
        NotificationTemplateRecord existing = requireTemplate(templateId);
        validateTemplateCode(request.templateCode(), templateId);
        Instant now = Instant.now();
        notificationTemplateMapper.upsert(new NotificationTemplateRecord(
                templateId,
                normalize(request.templateCode()),
                normalize(request.templateName()),
                resolveType(request.channelType()).name(),
                normalize(request.titleTemplate()),
                normalize(request.contentTemplate()),
                Boolean.TRUE.equals(request.enabled()),
                normalizeNullable(request.remark()),
                existing.createdAt(),
                now
        ));
        return new NotificationTemplateMutationResponse(templateId);
    }

    private NotificationTemplateListItemResponse toListItem(NotificationTemplateRecord record) {
        return new NotificationTemplateListItemResponse(
                record.templateId(),
                record.templateCode(),
                record.templateName(),
                record.channelType(),
                record.titleTemplate(),
                Boolean.TRUE.equals(record.enabled()) ? "ENABLED" : "DISABLED",
                record.createdAt()
        );
    }

    private NotificationTemplateDetailResponse toDetail(NotificationTemplateRecord record) {
        return new NotificationTemplateDetailResponse(
                record.templateId(),
                record.templateCode(),
                record.templateName(),
                record.channelType(),
                record.titleTemplate(),
                record.contentTemplate(),
                record.remark(),
                Boolean.TRUE.equals(record.enabled()) ? "ENABLED" : "DISABLED",
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean matchesKeyword(NotificationTemplateRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.templateCode().toLowerCase().contains(normalized)
                || record.templateName().toLowerCase().contains(normalized)
                || record.channelType().toLowerCase().contains(normalized)
                || record.titleTemplate().toLowerCase().contains(normalized)
                || record.contentTemplate().toLowerCase().contains(normalized);
    }

    private Comparator<NotificationTemplateRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationTemplateRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<NotificationTemplateRecord> comparator = switch (sort.field()) {
            case "templateCode" -> Comparator.comparing(NotificationTemplateRecord::templateCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "templateName" -> Comparator.comparing(NotificationTemplateRecord::templateName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channelType" -> Comparator.comparing(NotificationTemplateRecord::channelType, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationTemplateRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String channelType = null;
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
                case "channelType" -> channelType = resolveType(value).name();
                default -> {
                }
            }
        }
        return new Filters(enabled, channelType);
    }

    private Boolean resolveStatus(String value) {
        String normalized = normalize(value);
        if ("ENABLED".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("DISABLED".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "模板状态不合法",
                Map.of("status", value, "allowedStatuses", List.of("ENABLED", "DISABLED"))
        );
    }

    private NotificationChannelType resolveType(String channelType) {
        String normalized = normalize(channelType);
        try {
            return NotificationChannelType.fromCode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "渠道类型不合法",
                    Map.of("channelType", channelType, "allowedTypes", NotificationChannelType.orderedValues().stream().map(Enum::name).toList())
            );
        }
    }

    private void validateTemplateCode(String templateCode, String excludeTemplateId) {
        String normalized = normalize(templateCode);
        if (notificationTemplateMapper.existsByCode(normalized, excludeTemplateId)) {
            throw new ContractException(
                    "BIZ.NOTIFICATION_TEMPLATE_CODE_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "通知模板编码已存在",
                    Map.of("templateCode", normalized)
            );
        }
    }

    private NotificationTemplateRecord requireTemplate(String templateId) {
        NotificationTemplateRecord record = notificationTemplateMapper.selectById(templateId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "通知模板不存在",
                    Map.of("templateId", templateId)
            );
        }
        return record;
    }

    private void ensureProcessAdmin() {
        String userId = currentUserId();
        if (!fixtureAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员可以访问通知模板管理",
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
                    "必填参数不能为空"
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

    private record Filters(Boolean enabled, String channelType) {
    }
}
