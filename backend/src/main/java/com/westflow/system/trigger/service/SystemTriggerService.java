package com.westflow.system.trigger.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.system.trigger.api.SaveSystemTriggerRequest;
import com.westflow.system.trigger.api.SystemTriggerDetailResponse;
import com.westflow.system.trigger.api.SystemTriggerFormOptionsResponse;
import com.westflow.system.trigger.api.SystemTriggerListItemResponse;
import com.westflow.system.trigger.api.SystemTriggerMutationResponse;
import com.westflow.system.trigger.mapper.SystemTriggerMapper;
import com.westflow.system.trigger.model.TriggerDefinitionRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemTriggerService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("automationStatus", "triggerEvent");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "triggerName", "triggerKey", "triggerEvent");

    private final SystemTriggerMapper systemTriggerMapper;
    private final NotificationChannelMapper notificationChannelMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<SystemTriggerListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        Filters filters = resolveFilters(request.filters());
        Comparator<TriggerDefinitionRecord> comparator = resolveComparator(request.sorts());
        List<SystemTriggerListItemResponse> records = systemTriggerMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .filter(record -> filters.triggerEvent() == null || filters.triggerEvent().equalsIgnoreCase(record.triggerEvent()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();

        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<SystemTriggerListItemResponse> pageRecords = fromIndex >= records.size()
                ? List.of()
                : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    public SystemTriggerDetailResponse detail(String triggerId) {
        ensureProcessAdmin();
        return toDetail(requireTrigger(triggerId));
    }

    public SystemTriggerFormOptionsResponse formOptions() {
        ensureProcessAdmin();
        return new SystemTriggerFormOptionsResponse(List.of(
                new SystemTriggerFormOptionsResponse.TriggerEventOption("TASK_CREATED", "任务创建"),
                new SystemTriggerFormOptionsResponse.TriggerEventOption("TASK_COMPLETED", "任务完成"),
                new SystemTriggerFormOptionsResponse.TriggerEventOption("INSTANCE_STARTED", "实例启动"),
                new SystemTriggerFormOptionsResponse.TriggerEventOption("INSTANCE_COMPLETED", "实例结束")
        ));
    }

    @Transactional
    public SystemTriggerMutationResponse create(SaveSystemTriggerRequest request) {
        ensureProcessAdmin();
        validateRequest(request, null);
        String triggerId = buildId("trg");
        Instant now = Instant.now();
        systemTriggerMapper.upsert(new TriggerDefinitionRecord(
                triggerId,
                normalize(request.triggerName(), "triggerName"),
                normalize(request.triggerKey(), "triggerKey"),
                resolveTriggerEvent(request.triggerEvent()),
                normalizeNullable(request.businessType()),
                normalizeChannelIds(request.channelIds()),
                normalizeNullable(request.conditionExpression()),
                normalizeNullable(request.description()),
                Boolean.TRUE.equals(request.enabled()),
                now,
                now
        ));
        return new SystemTriggerMutationResponse(triggerId);
    }

    @Transactional
    public SystemTriggerMutationResponse update(String triggerId, SaveSystemTriggerRequest request) {
        ensureProcessAdmin();
        TriggerDefinitionRecord existing = requireTrigger(triggerId);
        validateRequest(request, triggerId);
        systemTriggerMapper.upsert(new TriggerDefinitionRecord(
                triggerId,
                normalize(request.triggerName(), "triggerName"),
                normalize(request.triggerKey(), "triggerKey"),
                resolveTriggerEvent(request.triggerEvent()),
                normalizeNullable(request.businessType()),
                normalizeChannelIds(request.channelIds()),
                normalizeNullable(request.conditionExpression()),
                normalizeNullable(request.description()),
                Boolean.TRUE.equals(request.enabled()),
                existing.createdAt(),
                Instant.now()
        ));
        return new SystemTriggerMutationResponse(triggerId);
    }

    private TriggerDefinitionRecord requireTrigger(String triggerId) {
        TriggerDefinitionRecord record = systemTriggerMapper.selectById(triggerId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "触发器不存在",
                    Map.of("triggerId", triggerId)
            );
        }
        return record;
    }

    private SystemTriggerListItemResponse toListItem(TriggerDefinitionRecord record) {
        return new SystemTriggerListItemResponse(
                record.triggerId(),
                record.triggerName(),
                record.triggerKey(),
                record.triggerEvent(),
                record.enabled() ? "ACTIVE" : "DISABLED",
                record.createdAt()
        );
    }

    private SystemTriggerDetailResponse toDetail(TriggerDefinitionRecord record) {
        return new SystemTriggerDetailResponse(
                record.triggerId(),
                record.triggerName(),
                record.triggerKey(),
                record.triggerEvent(),
                record.businessType(),
                record.channelIds(),
                record.conditionExpression(),
                record.description(),
                record.enabled(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean matchesKeyword(TriggerDefinitionRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.triggerName().toLowerCase().contains(normalized)
                || record.triggerKey().toLowerCase().contains(normalized)
                || record.triggerEvent().toLowerCase().contains(normalized)
                || (record.businessType() != null && record.businessType().toLowerCase().contains(normalized));
    }

    private Comparator<TriggerDefinitionRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(TriggerDefinitionRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<TriggerDefinitionRecord> comparator = switch (sort.field()) {
            case "triggerName" -> Comparator.comparing(TriggerDefinitionRecord::triggerName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "triggerKey" -> Comparator.comparing(TriggerDefinitionRecord::triggerKey, Comparator.nullsLast(Comparator.naturalOrder()));
            case "triggerEvent" -> Comparator.comparing(TriggerDefinitionRecord::triggerEvent, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(TriggerDefinitionRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String triggerEvent = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "automationStatus" -> enabled = resolveAutomationStatus(value);
                case "triggerEvent" -> triggerEvent = resolveTriggerEvent(value);
                default -> {
                }
            }
        }
        return new Filters(enabled, triggerEvent);
    }

    private Boolean resolveAutomationStatus(String automationStatus) {
        String normalized = normalize(automationStatus, "automationStatus");
        if ("ACTIVE".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("DISABLED".equalsIgnoreCase(normalized) || "PAUSED".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "自动化状态不合法",
                Map.of("automationStatus", automationStatus)
        );
    }

    private String resolveTriggerEvent(String triggerEvent) {
        String normalized = normalize(triggerEvent, "triggerEvent").toUpperCase();
        if (List.of("TASK_CREATED", "TASK_COMPLETED", "INSTANCE_STARTED", "INSTANCE_COMPLETED").contains(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "触发事件不合法",
                Map.of("triggerEvent", triggerEvent)
        );
    }

    private void validateRequest(SaveSystemTriggerRequest request, String excludeTriggerId) {
        String triggerKey = normalize(request.triggerKey(), "triggerKey");
        if (systemTriggerMapper.existsByKey(triggerKey, excludeTriggerId)) {
            throw new ContractException(
                    "BIZ.TRIGGER_KEY_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "触发器编码已存在",
                    Map.of("triggerKey", triggerKey)
            );
        }
        normalize(request.triggerName(), "triggerName");
        resolveTriggerEvent(request.triggerEvent());
        normalizeChannelIds(request.channelIds());
    }

    private List<String> normalizeChannelIds(List<String> channelIds) {
        if (channelIds == null) {
            return List.of();
        }
        List<String> normalized = channelIds.stream()
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .distinct()
                .toList();
        for (String channelId : normalized) {
            if (notificationChannelMapper.selectById(channelId) == null) {
                throw new ContractException(
                        "BIZ.RESOURCE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "通知渠道不存在",
                        Map.of("channelId", channelId)
                );
            }
        }
        return normalized;
    }

    private void ensureProcessAdmin() {
        String userId = StpUtil.getLoginIdAsString();
        if (!fixtureAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问触发器管理",
                    Map.of("userId", userId)
            );
        }
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

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
            String triggerEvent
    ) {
    }
}
