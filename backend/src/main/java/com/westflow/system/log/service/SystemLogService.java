package com.westflow.system.log.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.system.log.mapper.AuditLogMapper;
import com.westflow.system.log.mapper.LoginLogMapper;
import com.westflow.system.log.model.AuditLogRecord;
import com.westflow.system.log.model.LoginLogRecord;
import com.westflow.system.log.response.AuditLogDetailResponse;
import com.westflow.system.log.response.AuditLogListItemResponse;
import com.westflow.system.log.response.LoginLogDetailResponse;
import com.westflow.system.log.response.LoginLogListItemResponse;
import com.westflow.system.log.response.SystemNotificationLogDetailResponse;
import com.westflow.system.log.response.SystemNotificationLogListItemResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 日志管理聚合服务，统一提供审计、登录与通知发送日志列表与详情。
public class SystemLogService {

    private static final List<String> AUDIT_FILTER_FIELDS = List.of("status", "method", "module", "loginId", "path", "createdAt");
    private static final List<String> AUDIT_SORT_FIELDS = List.of("createdAt", "status", "method", "path", "module", "loginId");
    private static final List<String> AUDIT_GROUP_FIELDS = List.of("status", "method", "module", "loginId");

    private static final List<String> LOGIN_FILTER_FIELDS = List.of("status", "username", "userId", "createdAt");
    private static final List<String> LOGIN_SORT_FIELDS = List.of("createdAt", "status", "username", "userId", "statusCode");
    private static final List<String> LOGIN_GROUP_FIELDS = List.of("status", "userId");

    private static final List<String> NOTIFICATION_FILTER_FIELDS = List.of("status", "channelType", "channelCode", "recipient");
    private static final List<String> NOTIFICATION_SORT_FIELDS = List.of("sentAt", "channelName", "recipient", "title", "status");
    private static final List<String> NOTIFICATION_GROUP_FIELDS = List.of("status", "channelType", "recipient");

    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final NotificationChannelMapper notificationChannelMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<AuditLogListItemResponse> pageAudit(PageRequest request) {
        ensureAccess();
        AuditFilters filters = parseAuditFilters(request.filters());
        Comparator<AuditLogRecord> comparator = resolveAuditComparator(request.sorts());
        List<AuditLogRecord> matched = auditLogMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status == null || filters.status.equalsIgnoreCase(record.status()))
                .filter(record -> filters.method == null || filters.method.equalsIgnoreCase(record.method()))
                .filter(record -> filters.module == null || filters.module().equalsIgnoreCase(record.module()))
                .filter(record -> filters.loginId == null || filters.loginId().equals(record.loginId()))
                .filter(record -> filters.path == null || containsIgnoreCase(record.path(), filters.path()))
                .filter(record -> filters.createdAfter == null || !record.createdAt().isBefore(filters.createdAfter))
                .filter(record -> filters.createdBefore == null || !record.createdAt().isAfter(filters.createdBefore))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveAuditGroups(matched, request.groups());

        return new PageResponse<>(
                request.page(),
                request.pageSize(),
                matched.size(),
                pageCount(matched.size(), request.pageSize()),
                matched.stream()
                        .skip(offset(request.page(), request.pageSize()))
                        .limit(request.pageSize())
                        .map(this::toAuditListItem)
                        .toList(),
                groups
        );
    }

    public AuditLogDetailResponse detailAudit(String logId) {
        ensureAccess();
        AuditLogRecord record = requireAudit(logId);
        return new AuditLogDetailResponse(
                record.logId(),
                record.requestId(),
                record.module(),
                record.path(),
                record.method(),
                record.status(),
                record.statusCode(),
                record.loginId(),
                record.username(),
                record.clientIp(),
                record.userAgent(),
                record.errorMessage(),
                record.durationMs(),
                record.createdAt()
        );
    }

    public PageResponse<LoginLogListItemResponse> pageLogin(PageRequest request) {
        ensureAccess();
        LoginFilters filters = parseLoginFilters(request.filters());
        Comparator<LoginLogRecord> comparator = resolveLoginComparator(request.sorts());
        List<LoginLogRecord> matched = loginLogMapper.selectAll().stream()
                .filter(record -> filters.status == null || filters.status.equalsIgnoreCase(record.status()))
                .filter(record -> filters.username == null || containsIgnoreCase(record.username(), filters.username()))
                .filter(record -> filters.userId == null || filters.userId().equals(record.userId()))
                .filter(record -> filters.createdAfter == null || !record.createdAt().isBefore(filters.createdAfter))
                .filter(record -> filters.createdBefore == null || !record.createdAt().isAfter(filters.createdBefore))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveLoginGroups(matched, request.groups());

        return new PageResponse<>(
                request.page(),
                request.pageSize(),
                matched.size(),
                pageCount(matched.size(), request.pageSize()),
                matched.stream()
                        .skip(offset(request.page(), request.pageSize()))
                        .limit(request.pageSize())
                        .map(this::toLoginListItem)
                        .toList(),
                groups
        );
    }

    public LoginLogDetailResponse detailLogin(String logId) {
        ensureAccess();
        LoginLogRecord record = requireLogin(logId);
        return new LoginLogDetailResponse(
                record.logId(),
                record.requestId() == null ? "" : record.requestId(),
                "/api/v1/auth/login",
                record.username(),
                record.status(),
                record.statusCode(),
                record.userId(),
                record.resultMessage(),
                record.clientIp(),
                record.userAgent(),
                record.durationMs(),
                record.createdAt()
        );
    }

    public PageResponse<SystemNotificationLogListItemResponse> pageNotification(PageRequest request) {
        ensureAccess();
        NotificationFilters filters = parseNotificationFilters(request.filters());
        Comparator<NotificationLogRecord> comparator = resolveNotificationComparator(request.sorts());
        List<NotificationLogRecord> matched = notificationLogMapper.selectAll().stream()
                .filter(record -> matchesNotificationKeyword(record, request.keyword()))
                .filter(record -> filters.status == null || filters.status.equalsIgnoreCase(record.status()))
                .filter(record -> filters.channelType == null || filters.channelType().equalsIgnoreCase(record.channelType()))
                .filter(record -> filters.channelCode == null || filters.channelCode().equalsIgnoreCase(record.channelCode()))
                .filter(record -> filters.recipient == null || containsIgnoreCase(record.recipient(), filters.recipient()))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveNotificationGroups(matched, request.groups());

        return new PageResponse<>(
                request.page(),
                request.pageSize(),
                matched.size(),
                pageCount(matched.size(), request.pageSize()),
                matched.stream()
                        .skip(offset(request.page(), request.pageSize()))
                        .limit(request.pageSize())
                        .map(this::toNotificationListItem)
                        .toList(),
                groups
        );
    }

    public SystemNotificationLogDetailResponse detailNotification(String recordId) {
        ensureAccess();
        NotificationLogRecord record = requireNotification(recordId);
        NotificationChannelRecord channel = notificationChannelMapper.selectById(record.channelId());
        return new SystemNotificationLogDetailResponse(
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

    private AuditFilters parseAuditFilters(List<FilterItem> filters) {
        String status = null;
        String method = null;
        String module = null;
        String loginId = null;
        String path = null;
        Instant createdAfter = null;
        Instant createdBefore = null;

        for (FilterItem filter : filters) {
            if (!AUDIT_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), AUDIT_FILTER_FIELDS);
            }

            if ("createdAt".equals(filter.field())) {
                if (!"between".equalsIgnoreCase(filter.operator())) {
                    throw unsupported("审计日志不支持的筛选操作符", filter.operator(), List.of("between"));
                }
                InstantRange range = parseInstantRange(filter.value());
                createdAfter = range.start();
                createdBefore = range.end();
                continue;
            }

            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("审计日志不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            switch (filter.field()) {
                case "status" -> status = value;
                case "method" -> method = value;
                case "module" -> module = value;
                case "loginId" -> loginId = value;
                case "path" -> path = value;
                default -> {}
            }
        }

        return new AuditFilters(status, method, module, loginId, path, createdAfter, createdBefore);
    }

    private LoginFilters parseLoginFilters(List<FilterItem> filters) {
        String status = null;
        String username = null;
        String userId = null;
        Instant createdAfter = null;
        Instant createdBefore = null;

        for (FilterItem filter : filters) {
            if (!LOGIN_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), LOGIN_FILTER_FIELDS);
            }
            if ("createdAt".equals(filter.field())) {
                if (!"between".equalsIgnoreCase(filter.operator())) {
                    throw unsupported("登录日志不支持的筛选操作符", filter.operator(), List.of("between"));
                }
                InstantRange range = parseInstantRange(filter.value());
                createdAfter = range.start();
                createdBefore = range.end();
                continue;
            }
            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("登录日志不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            if ("status".equals(filter.field())) {
                status = value;
            } else if ("username".equals(filter.field())) {
                username = value;
            } else if ("userId".equals(filter.field())) {
                userId = value;
            }
        }

        return new LoginFilters(status, username, userId, createdAfter, createdBefore);
    }

    private NotificationFilters parseNotificationFilters(List<FilterItem> filters) {
        String status = null;
        String channelType = null;
        String channelCode = null;
        String recipient = null;

        for (FilterItem filter : filters) {
            if (!NOTIFICATION_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), NOTIFICATION_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("通知日志不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            switch (filter.field()) {
                case "status" -> status = value;
                case "channelType" -> channelType = value;
                case "channelCode" -> channelCode = value;
                case "recipient" -> recipient = value;
                default -> {}
            }
        }
        return new NotificationFilters(status, channelType, channelCode, recipient);
    }

    private Comparator<AuditLogRecord> resolveAuditComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AuditLogRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!AUDIT_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), AUDIT_SORT_FIELDS);
        }
        Comparator<AuditLogRecord> comparator = switch (sort.field()) {
            case "status" -> Comparator.comparing(AuditLogRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "method" -> Comparator.comparing(AuditLogRecord::method, Comparator.nullsLast(Comparator.naturalOrder()));
            case "path" -> Comparator.comparing(AuditLogRecord::path, Comparator.nullsLast(Comparator.naturalOrder()));
            case "module" -> Comparator.comparing(AuditLogRecord::module, Comparator.nullsLast(Comparator.naturalOrder()));
            case "loginId" -> Comparator.comparing(AuditLogRecord::loginId, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AuditLogRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Comparator<LoginLogRecord> resolveLoginComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(LoginLogRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!LOGIN_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), LOGIN_SORT_FIELDS);
        }
        Comparator<LoginLogRecord> comparator = switch (sort.field()) {
            case "status" -> Comparator.comparing(LoginLogRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "username" -> Comparator.comparing(LoginLogRecord::username, Comparator.nullsLast(Comparator.naturalOrder()));
            case "userId" -> Comparator.comparing(LoginLogRecord::userId, Comparator.nullsLast(Comparator.naturalOrder()));
            case "statusCode" -> Comparator.comparing(LoginLogRecord::statusCode, Comparator.naturalOrder());
            default -> Comparator.comparing(LoginLogRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Comparator<NotificationLogRecord> resolveNotificationComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationLogRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!NOTIFICATION_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), NOTIFICATION_SORT_FIELDS);
        }
        Comparator<NotificationLogRecord> comparator = switch (sort.field()) {
            case "recipient" -> Comparator.comparing(NotificationLogRecord::recipient, Comparator.nullsLast(Comparator.naturalOrder()));
            case "title" -> Comparator.comparing(NotificationLogRecord::title, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channelName" -> Comparator.comparing(record -> resolveChannelName(notificationChannelMapper.selectById(record.channelId()), record),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationLogRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private List<PageResponse.GroupValue> resolveAuditGroups(List<AuditLogRecord> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!AUDIT_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), AUDIT_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (AuditLogRecord record : records) {
                String value = switch (group.field()) {
                    case "status" -> record.status();
                    case "method" -> record.method();
                    case "module" -> record.module();
                    case "loginId" -> record.loginId();
                    default -> null;
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private List<PageResponse.GroupValue> resolveLoginGroups(List<LoginLogRecord> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!LOGIN_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), LOGIN_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (LoginLogRecord record : records) {
                String value = switch (group.field()) {
                    case "status" -> record.status();
                    case "userId" -> record.userId();
                    default -> null;
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private List<PageResponse.GroupValue> resolveNotificationGroups(List<NotificationLogRecord> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!NOTIFICATION_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), NOTIFICATION_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (NotificationLogRecord record : records) {
                String value = switch (group.field()) {
                    case "status" -> record.status();
                    case "channelType" -> record.channelType();
                    case "recipient" -> record.recipient();
                    default -> null;
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private AuditLogRecord requireAudit(String logId) {
        return auditLogMapper.selectAll().stream()
                .filter(record -> record.logId().equals(logId))
                .findFirst()
                .orElseThrow(() -> new ContractException("BIZ.RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "审计日志不存在", Map.of("logId", logId)));
    }

    private LoginLogRecord requireLogin(String logId) {
        return loginLogMapper.selectAll().stream()
                .filter(record -> record.logId().equals(logId))
                .findFirst()
                .orElseThrow(() -> new ContractException("BIZ.RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "登录日志不存在", Map.of("logId", logId)));
    }

    private NotificationLogRecord requireNotification(String recordId) {
        return notificationLogMapper.selectAll().stream()
                .filter(record -> record.logId().equals(recordId))
                .findFirst()
                .orElseThrow(() -> new ContractException("BIZ.RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "通知记录不存在", Map.of("recordId", recordId)));
    }

    private AuditLogListItemResponse toAuditListItem(AuditLogRecord record) {
        return new AuditLogListItemResponse(
                record.logId(),
                record.requestId(),
                record.module(),
                record.path(),
                record.method(),
                record.status(),
                record.statusCode(),
                record.loginId(),
                record.username(),
                record.clientIp(),
                record.createdAt()
        );
    }

    private LoginLogListItemResponse toLoginListItem(LoginLogRecord record) {
        return new LoginLogListItemResponse(
                record.logId(),
                record.username(),
                record.status(),
                record.statusCode(),
                record.userId(),
                record.clientIp(),
                record.createdAt()
        );
    }

    private SystemNotificationLogListItemResponse toNotificationListItem(NotificationLogRecord record) {
        NotificationChannelRecord channel = notificationChannelMapper.selectById(record.channelId());
        return new SystemNotificationLogListItemResponse(
                record.logId(),
                record.channelId(),
                resolveChannelName(channel, record),
                record.channelCode(),
                record.channelType(),
                record.recipient(),
                record.title(),
                record.status(),
                record.sentAt()
        );
    }

    private boolean matchesKeyword(AuditLogRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.requestId().toLowerCase().contains(normalized)
                || (record.path() != null && record.path().toLowerCase().contains(normalized))
                || (record.method() != null && record.method().toLowerCase().contains(normalized))
                || (record.loginId() != null && record.loginId().toLowerCase().contains(normalized))
                || (record.username() != null && record.username().toLowerCase().contains(normalized))
                || (record.module() != null && record.module().toLowerCase().contains(normalized));
    }

    private boolean matchesNotificationKeyword(NotificationLogRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.channelCode().toLowerCase().contains(normalized)
                || record.channelType().toLowerCase().contains(normalized)
                || (record.recipient() != null && record.recipient().toLowerCase().contains(normalized))
                || (record.title() != null && record.title().toLowerCase().contains(normalized))
                || (record.status() != null && record.status().toLowerCase().contains(normalized))
                || (record.content() != null && record.content().toLowerCase().contains(normalized));
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

    private void ensureAccess() {
        String userId = currentUserId();
        if (!fixtureAuthService.canAccessPhase2SystemManagement(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员和流程管理员可访问日志管理",
                    Map.of("userId", userId)
            );
        }
    }

    private String currentUserId() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        if (userId == null || userId.isBlank()) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "未登录或登录态已过期");
        }
        return userId;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private String normalize(JsonNode value) {
        if (value == null || !value.isTextual() && !value.isNumber()) {
            return "";
        }
        return value.asText().trim();
    }

    private InstantRange parseInstantRange(JsonNode value) {
        if (value == null || !value.isArray() || value.size() != 2) {
            throw new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, "时间范围筛选值非法");
        }
        try {
            Instant start = Instant.parse(value.get(0).asText());
            Instant end = Instant.parse(value.get(1).asText());
            return new InstantRange(start, end);
        } catch (DateTimeParseException exception) {
            throw new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, "时间范围解析失败", Map.of("value", value.toString()));
        }
    }

    private long pageCount(long total, long pageSize) {
        return total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    private long offset(int page, long pageSize) {
        return Math.max(0L, (long) (page - 1) * pageSize);
    }

    private ContractException unsupported(String message, String field, List<String> allowList) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowed", allowList)
        );
    }

    // 协议中常见的记录 ID 约定，便于日志记录追踪。
    public static String buildId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record AuditFilters(
            String status,
            String method,
            String module,
            String loginId,
            String path,
            Instant createdAfter,
            Instant createdBefore
    ) {
    }

    private record LoginFilters(
            String status,
            String username,
            String userId,
            Instant createdAfter,
            Instant createdBefore
    ) {
    }

    private record NotificationFilters(
            String status,
            String channelType,
            String channelCode,
            String recipient
    ) {
    }

    private record InstantRange(Instant start, Instant end) {
    }
}
