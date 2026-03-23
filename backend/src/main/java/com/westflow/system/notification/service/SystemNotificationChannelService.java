package com.westflow.system.notification.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.system.notification.api.SaveSystemNotificationChannelRequest;
import com.westflow.system.notification.api.SystemNotificationChannelDetailResponse;
import com.westflow.system.notification.api.SystemNotificationChannelFormOptionsResponse;
import com.westflow.system.notification.api.SystemNotificationChannelListItemResponse;
import com.westflow.system.notification.api.SystemNotificationChannelMutationResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统通知渠道管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemNotificationChannelService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "channelType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "channelName", "channelType", "endpoint");

    private final NotificationChannelMapper notificationChannelMapper;
    private final IdentityAuthService fixtureAuthService;

    /**
     * 分页查询通知渠道。
     */
    public PageResponse<SystemNotificationChannelListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        Filters filters = resolveFilters(request.filters());
        Comparator<NotificationChannelRecord> comparator = resolveComparator(request.sorts());
        List<SystemNotificationChannelListItemResponse> records = notificationChannelMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .filter(record -> filters.channelType() == null || filters.channelType().equals(toExternalType(record.channelType())))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();

        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<SystemNotificationChannelListItemResponse> pageRecords = fromIndex >= records.size()
                ? List.of()
                : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    /**
     * 查询通知渠道详情。
     */
    public SystemNotificationChannelDetailResponse detail(String channelId) {
        ensureProcessAdmin();
        return toDetail(requireChannel(channelId));
    }

    /**
     * 获取通知渠道表单选项。
     */
    public SystemNotificationChannelFormOptionsResponse formOptions() {
        ensureProcessAdmin();
        // 系统侧只暴露当前一期真正开放的渠道类型。
        return new SystemNotificationChannelFormOptionsResponse(List.of(
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("IN_APP", "站内通知"),
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("EMAIL", "邮件"),
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("WEBHOOK", "Webhook"),
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("SMS", "短信"),
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("WECHAT_WORK", "企业微信"),
                new SystemNotificationChannelFormOptionsResponse.ChannelTypeOption("DINGTALK", "钉钉")
        ));
    }

    /**
     * 新建通知渠道。
     */
    @Transactional
    public SystemNotificationChannelMutationResponse create(SaveSystemNotificationChannelRequest request) {
        ensureProcessAdmin();
        String channelId = buildId("nch");
        Instant now = Instant.now();
        NotificationChannelRecord record = new NotificationChannelRecord(
                channelId,
                buildChannelCode(request.channelType()),
                resolveInternalType(request.channelType()).name(),
                normalize(request.channelName(), "channelName"),
                Boolean.TRUE.equals(request.enabled()),
                false,
                buildConfig(request),
                normalizeNullable(request.remark()),
                now,
                now,
                null
        );
        notificationChannelMapper.upsert(record);
        return new SystemNotificationChannelMutationResponse(channelId);
    }

    /**
     * 更新通知渠道。
     */
    @Transactional
    public SystemNotificationChannelMutationResponse update(String channelId, SaveSystemNotificationChannelRequest request) {
        ensureProcessAdmin();
        NotificationChannelRecord existing = requireChannel(channelId);
        Instant now = Instant.now();
        NotificationChannelRecord record = new NotificationChannelRecord(
                channelId,
                existing.channelCode(),
                resolveInternalType(request.channelType()).name(),
                normalize(request.channelName(), "channelName"),
                Boolean.TRUE.equals(request.enabled()),
                false,
                buildConfig(request),
                normalizeNullable(request.remark()),
                existing.createdAt(),
                now,
                existing.lastSentAt()
        );
        notificationChannelMapper.upsert(record);
        return new SystemNotificationChannelMutationResponse(channelId);
    }

    private NotificationChannelRecord requireChannel(String channelId) {
        NotificationChannelRecord record = notificationChannelMapper.selectById(channelId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "通知渠道不存在",
                    Map.of("channelId", channelId)
            );
        }
        return record;
    }

    private SystemNotificationChannelListItemResponse toListItem(NotificationChannelRecord record) {
        return new SystemNotificationChannelListItemResponse(
                record.channelId(),
                record.channelName(),
                toExternalType(record.channelType()),
                resolveEndpoint(record),
                record.enabled() ? "ENABLED" : "DISABLED",
                record.createdAt()
        );
    }

    private SystemNotificationChannelDetailResponse toDetail(NotificationChannelRecord record) {
        return new SystemNotificationChannelDetailResponse(
                record.channelId(),
                record.channelName(),
                toExternalType(record.channelType()),
                resolveEndpoint(record),
                resolveSecret(record),
                record.remark(),
                record.enabled() ? "ENABLED" : "DISABLED",
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean matchesKeyword(NotificationChannelRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.channelName().toLowerCase().contains(normalized)
                || toExternalType(record.channelType()).toLowerCase().contains(normalized)
                || resolveEndpoint(record).toLowerCase().contains(normalized);
    }

    private Comparator<NotificationChannelRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationChannelRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<NotificationChannelRecord> comparator = switch (sort.field()) {
            case "channelName" -> Comparator.comparing(NotificationChannelRecord::channelName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channelType" -> Comparator.comparing(record -> toExternalType(record.channelType()), Comparator.nullsLast(Comparator.naturalOrder()));
            case "endpoint" -> Comparator.comparing(this::resolveEndpoint, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationChannelRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
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
                case "channelType" -> channelType = normalize(value, "channelType");
                default -> {
                }
            }
        }
        return new Filters(enabled, channelType);
    }

    private Boolean resolveStatus(String status) {
        String normalized = normalize(status, "status");
        if ("ENABLED".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("DISABLED".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "通知渠道状态不合法",
                Map.of("status", status)
        );
    }

    private NotificationChannelType resolveInternalType(String channelType) {
        String normalized = normalize(channelType, "channelType").toUpperCase();
        return switch (normalized) {
            case "WECHAT_WORK" -> NotificationChannelType.WECHAT;
            case "DINGTALK" -> NotificationChannelType.DINGTALK;
            case "EMAIL" -> NotificationChannelType.EMAIL;
            case "WEBHOOK" -> NotificationChannelType.WEBHOOK;
            case "SMS" -> NotificationChannelType.SMS;
            case "IN_APP" -> NotificationChannelType.IN_APP;
            default -> throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道类型不合法",
                    Map.of("channelType", channelType)
            );
        };
    }

    private String toExternalType(String internalType) {
        return switch (internalType) {
            case "WECHAT" -> "WECHAT_WORK";
            default -> internalType;
        };
    }

    private Map<String, Object> buildConfig(SaveSystemNotificationChannelRequest request) {
        String endpoint = normalize(request.endpoint(), "endpoint");
        String secret = normalizeNullable(request.secret());
        NotificationChannelType type = resolveInternalType(request.channelType());
        Map<String, Object> config = new LinkedHashMap<>();
        switch (type) {
            case EMAIL -> {
                // 邮件先保留最小可执行配置，后续再接真实 SMTP 管理表。
                config.put("smtpHost", "smtp.mock.local");
                config.put("smtpPort", 25);
                config.put("fromAddress", endpoint);
                config.put("username", endpoint);
                if (secret != null) {
                    config.put("password", secret);
                }
            }
            case WEBHOOK, WECHAT, DINGTALK, SMS -> {
                config.put("url", endpoint);
                if (secret != null) {
                    config.put("secret", secret);
                }
            }
            case IN_APP -> {
                config.put("topic", endpoint);
                if (secret != null) {
                    config.put("secret", secret);
                }
            }
            default -> {
            }
        }
        return config;
    }

    private String resolveEndpoint(NotificationChannelRecord record) {
        Map<String, Object> config = record.config();
        if (config == null || config.isEmpty()) {
            return "";
        }
        if (config.get("url") != null) {
            return String.valueOf(config.get("url"));
        }
        if (config.get("fromAddress") != null) {
            return String.valueOf(config.get("fromAddress"));
        }
        if (config.get("topic") != null) {
            return String.valueOf(config.get("topic"));
        }
        if (config.get("smtpHost") != null) {
            return String.valueOf(config.get("smtpHost"));
        }
        return "";
    }

    private String resolveSecret(NotificationChannelRecord record) {
        Map<String, Object> config = record.config();
        if (config == null || config.isEmpty()) {
            return null;
        }
        if (config.get("secret") != null) {
            return String.valueOf(config.get("secret"));
        }
        if (config.get("password") != null) {
            return String.valueOf(config.get("password"));
        }
        return null;
    }

    private void ensureProcessAdmin() {
        String userId = StpUtil.getLoginIdAsString();
        if (!fixtureAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问通知渠道管理",
                    Map.of("userId", userId)
            );
        }
    }

    private String buildChannelCode(String channelType) {
        String type = resolveInternalType(channelType).name().toLowerCase();
        return type + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalize(String value, String field) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "字段不能为空",
                    Map.of("field", field)
            );
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ContractException unsupported(String message, String actual, List<String> allowedValues) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("actual", actual, "allowedValues", allowedValues)
        );
    }

    private record Filters(
            Boolean enabled,
            String channelType
    ) {
    }
}
