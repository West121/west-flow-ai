package com.westflow.system.message.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.system.message.api.SaveSystemMessageRequest;
import com.westflow.system.message.api.SystemMessageDetailResponse;
import com.westflow.system.message.api.SystemMessageFormOptionsResponse;
import com.westflow.system.message.api.SystemMessageListItemResponse;
import com.westflow.system.message.api.SystemMessageMutationResponse;
import com.westflow.system.message.mapper.SystemMessageMapper;
import com.westflow.system.message.model.SystemMessageRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 站内消息管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemMessageService {

    private static final List<String> SUPPORTED_STATUSES = List.of("DRAFT", "SENT", "CANCELLED");
    private static final List<String> SUPPORTED_TARGET_TYPES = List.of("ALL", "USER", "DEPARTMENT");
    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "readStatus", "targetUser");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "sentAt", "title", "status", "targetType");
    private static final List<String> SUPPORTED_GROUP_FIELDS = List.of("status", "targetType", "readStatus");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final SystemMessageMapper systemMessageMapper;
    private final IdentityAuthService fixtureAuthService;

    public PageResponse<SystemMessageListItemResponse> page(PageRequest request) {
        ensureSystemAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<SystemMessageRecord> comparator = resolveComparator(request.sorts());
        String userId = currentUserId();

        List<SystemMessageRecord> matched = systemMessageMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(record.status()))
                .filter(record -> filters.targetUser() == null || matchTargetUser(record, filters.targetUser()))
                .filter(record -> filters.readStatus() == null || filters.readStatus().equals(resolveReadStatus(record.messageId(), userId, record.status())))
                .sorted(comparator)
                .toList();

        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<SystemMessageListItemResponse> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream()
                        .map(record -> toListItem(record, userId))
                        .toList();

        List<PageResponse.GroupValue> groups = resolveGroups(request.groups(), matched, userId);
        return new PageResponse<>(request.page(), pageSize, total, pages, records, groups);
    }

    public SystemMessageDetailResponse detail(String messageId) {
        ensureSystemAccess();
        String userId = currentUserId();
        return toDetail(requireMessage(messageId), userId);
    }

    public SystemMessageFormOptionsResponse formOptions() {
        ensureSystemAccess();
        return new SystemMessageFormOptionsResponse(
                List.of(
                        new SystemMessageFormOptionsResponse.StatusOption("DRAFT", "草稿"),
                        new SystemMessageFormOptionsResponse.StatusOption("SENT", "已发送"),
                        new SystemMessageFormOptionsResponse.StatusOption("CANCELLED", "已撤回")
                ),
                List.of(
                        new SystemMessageFormOptionsResponse.TargetTypeOption("ALL", "全部用户"),
                        new SystemMessageFormOptionsResponse.TargetTypeOption("USER", "指定用户"),
                        new SystemMessageFormOptionsResponse.TargetTypeOption("DEPARTMENT", "指定部门")
                ),
                List.of(
                        new SystemMessageFormOptionsResponse.ReadStatusOption("UNREAD", "未读"),
                        new SystemMessageFormOptionsResponse.ReadStatusOption("READ", "已读")
                )
        );
    }

    public SystemMessageMutationResponse create(SaveSystemMessageRequest request) {
        ensureSystemAccess();
        String status = normalizeStatus(request.status());
        String targetType = normalizeTargetType(request.targetType());
        String title = normalize(request.title());
        String content = normalize(request.content());
        List<String> targetUserIds = normalizeTargetUsers(request.targetUserIds());
        List<String> targetDepartmentIds = normalizeTargetDepartments(request.targetDepartmentIds());
        validateTargetConfig(targetType, targetUserIds, targetDepartmentIds);

        String messageId = buildId("msg");
        Instant now = Instant.now();
        Instant sentAt = resolveSentAt(status, request.sentAt(), now);

        systemMessageMapper.upsert(new SystemMessageRecord(
                messageId,
                title,
                content,
                status,
                targetType,
                targetUserIds,
                targetDepartmentIds,
                currentUserId(),
                sentAt,
                now,
                now
        ));
        return new SystemMessageMutationResponse(messageId);
    }

    public SystemMessageMutationResponse update(String messageId, SaveSystemMessageRequest request) {
        ensureSystemAccess();
        SystemMessageRecord existing = requireMessage(messageId);

        String status = normalizeStatus(request.status());
        String targetType = normalizeTargetType(request.targetType());
        String title = normalize(request.title());
        String content = normalize(request.content());
        List<String> targetUserIds = normalizeTargetUsers(request.targetUserIds());
        List<String> targetDepartmentIds = normalizeTargetDepartments(request.targetDepartmentIds());
        validateTargetConfig(targetType, targetUserIds, targetDepartmentIds);
        Instant now = Instant.now();
        Instant sentAt = resolveSentAt(status, request.sentAt(), now, existing.sentAt());

        systemMessageMapper.upsert(new SystemMessageRecord(
                existing.messageId(),
                title,
                content,
                status,
                targetType,
                targetUserIds,
                targetDepartmentIds,
                existing.senderUserId(),
                sentAt,
                existing.createdAt(),
                now
        ));
        return new SystemMessageMutationResponse(existing.messageId());
    }

    private SystemMessageListItemResponse toListItem(SystemMessageRecord record, String userId) {
        return new SystemMessageListItemResponse(
                record.messageId(),
                record.title(),
                record.status(),
                record.targetType(),
                resolveReadStatus(record.messageId(), userId, record.status()),
                record.sentAt(),
                record.createdAt(),
                record.targetUserIds(),
                record.targetDepartmentIds()
        );
    }

    private SystemMessageDetailResponse toDetail(SystemMessageRecord record, String userId) {
        return new SystemMessageDetailResponse(
                record.messageId(),
                record.title(),
                record.content(),
                record.status(),
                record.targetType(),
                resolveReadStatus(record.messageId(), userId, record.status()),
                record.sentAt(),
                record.createdAt(),
                record.updatedAt(),
                record.senderUserId(),
                record.targetUserIds(),
                record.targetDepartmentIds()
        );
    }

    private boolean matchesKeyword(SystemMessageRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.title().toLowerCase().contains(normalized)
                || record.content().toLowerCase().contains(normalized)
                || record.status().toLowerCase().contains(normalized)
                || record.targetType().toLowerCase().contains(normalized);
    }

    private Comparator<SystemMessageRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(SystemMessageRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<SystemMessageRecord> comparator = switch (sort.field()) {
            case "title" -> Comparator.comparing(SystemMessageRecord::title, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SystemMessageRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "targetType" -> Comparator.comparing(SystemMessageRecord::targetType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "sentAt" -> Comparator.comparing(SystemMessageRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(SystemMessageRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private List<PageResponse.GroupValue> resolveGroups(List<GroupItem> groups, List<SystemMessageRecord> records, String userId) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .flatMap(group -> {
                    if (!SUPPORTED_GROUP_FIELDS.contains(group.field())) {
                        throw unsupported("不支持的分组字段", group.field(), SUPPORTED_GROUP_FIELDS);
                    }
                    Stream<String> values = switch (group.field()) {
                        case "status" -> records.stream().map(SystemMessageRecord::status);
                        case "targetType" -> records.stream().map(SystemMessageRecord::targetType);
                        case "readStatus" ->
                                records.stream().map(record -> resolveReadStatus(record.messageId(), userId, record.status()));
                        default -> Stream.<String>empty();
                    };
                    return values.distinct().map(value -> new PageResponse.GroupValue(group.field(), value));
                })
                .toList();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        String readStatus = null;
        String targetUser = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> status = normalizeStatus(value);
                case "readStatus" -> readStatus = resolveReadStatusFilter(value);
                case "targetUser" -> targetUser = normalizeNullable(value);
                default -> {
                }
            }
        }
        return new Filters(status, readStatus, targetUser);
    }

    private String resolveReadStatus(String messageId, String userId, String status) {
        if (!"SENT".equals(status)) {
            return "UNREAD";
        }
        return systemMessageMapper.hasRead(messageId, userId) ? "READ" : "UNREAD";
    }

    private String resolveReadStatusFilter(String value) {
        String normalized = normalizeNullable(value);
        if ("READ".equalsIgnoreCase(normalized) || "UNREAD".equalsIgnoreCase(normalized)) {
            return normalized.toUpperCase();
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "消息已读状态不合法",
                Map.of("readStatus", value, "allowedReadStatuses", List.of("READ", "UNREAD"))
        );
    }

    private boolean matchTargetUser(SystemMessageRecord record, String targetUser) {
        return switch (record.targetType()) {
            case "ALL" -> true;
            case "USER" -> record.targetUserIds().contains(targetUser);
            case "DEPARTMENT" -> record.targetDepartmentIds().contains(targetUser);
            default -> false;
        };
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullable(status);
        if (normalized == null || !SUPPORTED_STATUSES.contains(normalized.toUpperCase())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "消息状态不合法",
                    Map.of("status", status, "allowedStatuses", SUPPORTED_STATUSES)
            );
        }
        return normalized.toUpperCase();
    }

    private String normalizeTargetType(String targetType) {
        String normalized = normalizeNullable(targetType);
        if (normalized == null || !SUPPORTED_TARGET_TYPES.contains(normalized.toUpperCase())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "消息目标类型不合法",
                    Map.of("targetType", targetType, "allowedTargetTypes", SUPPORTED_TARGET_TYPES)
            );
        }
        return normalized.toUpperCase();
    }

    private List<String> normalizeTargetUsers(List<String> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return List.of();
        }
        return targetUserIds.stream()
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private List<String> normalizeTargetDepartments(List<String> targetDepartmentIds) {
        if (targetDepartmentIds == null || targetDepartmentIds.isEmpty()) {
            return List.of();
        }
        return targetDepartmentIds.stream()
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private void validateTargetConfig(String targetType, List<String> targetUserIds, List<String> targetDepartmentIds) {
        if ("USER".equals(targetType) && targetUserIds.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "按指定用户投递时目标用户不能为空"
            );
        }
        if ("DEPARTMENT".equals(targetType) && targetDepartmentIds.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "按指定部门投递时目标部门不能为空"
            );
        }
    }

    private Instant resolveSentAt(String status, Instant requestSentAt, Instant now) {
        if (!"SENT".equals(status)) {
            return requestSentAt;
        }
        if (requestSentAt == null) {
            return now;
        }
        return requestSentAt;
    }

    private Instant resolveSentAt(String status, Instant requestSentAt, Instant now, Instant existingSentAt) {
        if (!"SENT".equals(status)) {
            return requestSentAt;
        }
        if (requestSentAt != null) {
            return requestSentAt;
        }
        return existingSentAt != null ? existingSentAt : now;
    }

    private SystemMessageRecord requireMessage(String messageId) {
        SystemMessageRecord record = systemMessageMapper.selectById(messageId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "站内消息不存在",
                    Map.of("messageId", messageId)
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
                    "仅系统管理员可以访问消息管理",
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

    private record Filters(String status, String readStatus, String targetUser) {
    }
}
