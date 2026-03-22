package com.westflow.system.notification.record.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.system.notification.record.api.NotificationRecordDetailResponse;
import com.westflow.system.notification.record.api.NotificationRecordListItemResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 通知发送记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class NotificationRecordService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "channelType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("sentAt", "channelName", "recipient", "title");

    private final NotificationLogMapper notificationLogMapper;
    private final NotificationChannelMapper notificationChannelMapper;
    private final IdentityAuthService fixtureAuthService;

    public PageResponse<NotificationRecordListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        Filters filters = resolveFilters(request.filters());
        Comparator<NotificationLogRecord> comparator = resolveComparator(request.sorts());
        List<NotificationRecordListItemResponse> records = notificationLogMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.success() == null || filters.success().equals(record.success()))
                .filter(record -> filters.channelType() == null || filters.channelType().equals(resolveChannelType(record)))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();

        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<NotificationRecordListItemResponse> pageRecords = fromIndex >= records.size() ? List.of() : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    public NotificationRecordDetailResponse detail(String recordId) {
        ensureProcessAdmin();
        NotificationLogRecord record = requireRecord(recordId);
        NotificationChannelRecord channel = notificationChannelMapper.selectById(record.channelId());
        return new NotificationRecordDetailResponse(
                record.logId(),
                record.channelId(),
                resolveChannelName(channel, record),
                record.channelCode(),
                record.channelType(),
                resolveChannelEndpoint(channel),
                record.recipient(),
                record.title(),
                record.content(),
                record.providerName(),
                record.success(),
                record.status(),
                record.responseMessage(),
                record.payload(),
                record.sentAt()
        );
    }

    private NotificationRecordListItemResponse toListItem(NotificationLogRecord record) {
        NotificationChannelRecord channel = notificationChannelMapper.selectById(record.channelId());
        return new NotificationRecordListItemResponse(
                record.logId(),
                record.channelId(),
                resolveChannelName(channel, record),
                record.channelCode(),
                record.channelType(),
                record.recipient(),
                record.title(),
                record.success() ? "SUCCESS" : "FAILED",
                record.sentAt()
        );
    }

    private boolean matchesKeyword(NotificationLogRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        NotificationChannelRecord channel = notificationChannelMapper.selectById(record.channelId());
        return record.channelCode().toLowerCase().contains(normalized)
                || record.channelType().toLowerCase().contains(normalized)
                || record.recipient().toLowerCase().contains(normalized)
                || record.title().toLowerCase().contains(normalized)
                || record.content().toLowerCase().contains(normalized)
                || record.responseMessage().toLowerCase().contains(normalized)
                || resolveChannelName(channel, record).toLowerCase().contains(normalized);
    }

    private Comparator<NotificationLogRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationLogRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<NotificationLogRecord> comparator = switch (sort.field()) {
            case "channelName" -> Comparator.comparing(record -> resolveChannelName(notificationChannelMapper.selectById(record.channelId()), record), Comparator.nullsLast(Comparator.naturalOrder()));
            case "recipient" -> Comparator.comparing(NotificationLogRecord::recipient, Comparator.nullsLast(Comparator.naturalOrder()));
            case "title" -> Comparator.comparing(NotificationLogRecord::title, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationLogRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean success = null;
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
                case "status" -> success = resolveStatus(value);
                case "channelType" -> channelType = normalize(value);
                default -> {
                }
            }
        }
        return new Filters(success, channelType);
    }

    private Boolean resolveStatus(String value) {
        String normalized = normalize(value);
        if ("SUCCESS".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("FAILED".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "通知记录状态不合法",
                Map.of("status", value, "allowedStatuses", List.of("SUCCESS", "FAILED"))
        );
    }

    private NotificationLogRecord requireRecord(String recordId) {
        return notificationLogMapper.selectAll().stream()
                .filter(record -> record.logId().equals(recordId))
                .findFirst()
                .orElseThrow(() -> new ContractException(
                        "BIZ.RESOURCE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "通知记录不存在",
                        Map.of("recordId", recordId)
                ));
    }

    private String resolveChannelType(NotificationLogRecord record) {
        return normalize(record.channelType());
    }

    private String resolveChannelName(NotificationChannelRecord channel, NotificationLogRecord record) {
        if (channel != null && channel.channelName() != null && !channel.channelName().isBlank()) {
            return channel.channelName();
        }
        return record.channelCode();
    }

    private String resolveChannelEndpoint(NotificationChannelRecord channel) {
        if (channel == null || channel.config() == null) {
            return null;
        }
        Object endpoint = channel.config().get("endpoint");
        return endpoint == null ? null : String.valueOf(endpoint);
    }

    private void ensureProcessAdmin() {
        String userId = currentUserId();
        if (!fixtureAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员可以访问通知记录管理",
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private record Filters(Boolean success, String channelType) {
    }
}
