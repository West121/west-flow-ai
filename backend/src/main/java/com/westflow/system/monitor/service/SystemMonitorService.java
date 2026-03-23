package com.westflow.system.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.notification.service.NotificationChannelService;
import com.westflow.system.monitor.api.response.NotificationChannelHealthDetailResponse;
import com.westflow.system.monitor.api.response.NotificationChannelHealthListItemResponse;
import com.westflow.system.monitor.api.response.OrchestratorScanDetailResponse;
import com.westflow.system.monitor.api.response.OrchestratorScanListItemResponse;
import com.westflow.system.monitor.api.response.TriggerExecutionDetailResponse;
import com.westflow.system.monitor.api.response.TriggerExecutionListItemResponse;
import com.westflow.system.monitor.mapper.OrchestratorScanRecordMapper;
import com.westflow.system.monitor.mapper.TriggerExecutionRecordMapper;
import com.westflow.system.monitor.model.OrchestratorScanRecord;
import com.westflow.system.monitor.model.TriggerExecutionRecord;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 系统监控查询服务，覆盖编排扫描、触发执行、通知渠道健康视图。
 */
@Service
public class SystemMonitorService {

    private static final List<String> ORCHESTRATOR_FILTER_FIELDS = List.of(
            "status",
            "automationType",
            "runId",
            "targetId",
            "targetName",
            "executedAt"
    );
    private static final List<String> ORCHESTRATOR_SORT_FIELDS = List.of(
            "executedAt",
            "scannedAt",
            "status",
            "automationType",
            "targetName",
            "targetId"
    );
    private static final List<String> ORCHESTRATOR_GROUP_FIELDS = List.of(
            "status",
            "automationType",
            "runId",
            "targetId"
    );

    private static final List<String> TRIGGER_FILTER_FIELDS = List.of(
            "status",
            "action",
            "triggerId",
            "triggerKey",
            "triggerName",
            "triggerEvent",
            "executedAt",
            "enabled"
    );
    private static final List<String> TRIGGER_SORT_FIELDS = List.of(
            "executedAt",
            "triggerName",
            "triggerKey",
            "triggerEvent",
            "action",
            "status",
            "enabled"
    );
    private static final List<String> TRIGGER_GROUP_FIELDS = List.of(
            "status",
            "action",
            "triggerEvent",
            "enabled"
    );

    private static final List<String> CHANNEL_HEALTH_FILTER_FIELDS = List.of(
            "status",
            "channelType",
            "latestStatus",
            "channelCode",
            "channelName"
    );
    private static final List<String> CHANNEL_HEALTH_SORT_FIELDS = List.of(
            "totalAttempts",
            "successRate",
            "successAttempts",
            "failedAttempts",
            "lastSentAt",
            "channelName",
            "channelType",
            "latestStatus",
            "status"
    );
    private static final List<String> CHANNEL_HEALTH_GROUP_FIELDS = List.of(
            "status",
            "channelType",
            "latestStatus"
    );

    private final OrchestratorScanRecordMapper orchestratorScanRecordMapper;
    private final TriggerExecutionRecordMapper triggerExecutionRecordMapper;
    private final NotificationChannelMapper notificationChannelMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final NotificationChannelService notificationChannelService;
    private final IdentityAuthService fixtureAuthService;

    public SystemMonitorService(
            OrchestratorScanRecordMapper orchestratorScanRecordMapper,
            TriggerExecutionRecordMapper triggerExecutionRecordMapper,
            NotificationChannelMapper notificationChannelMapper,
            NotificationLogMapper notificationLogMapper,
            NotificationChannelService notificationChannelService,
            IdentityAuthService fixtureAuthService
    ) {
        this.orchestratorScanRecordMapper = orchestratorScanRecordMapper;
        this.triggerExecutionRecordMapper = triggerExecutionRecordMapper;
        this.notificationChannelMapper = notificationChannelMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.notificationChannelService = notificationChannelService;
        this.fixtureAuthService = fixtureAuthService;
    }

    public PageResponse<OrchestratorScanListItemResponse> pageOrchestratorScans(PageRequest request) {
        ensureAccess();
        OrchestratorFilters filters = parseOrchestratorFilters(request.filters());
        Comparator<OrchestratorScanRecord> comparator = resolveOrchestratorComparator(request.sorts());
        List<OrchestratorScanRecord> matched = orchestratorScanRecordMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status == null || filters.status.equalsIgnoreCase(record.status()))
                .filter(record -> filters.automationType == null || filters.automationType.equalsIgnoreCase(record.automationType()))
                .filter(record -> filters.runId == null || containsIgnoreCase(record.runId(), filters.runId()))
                .filter(record -> filters.targetId == null || containsIgnoreCase(record.targetId(), filters.targetId()))
                .filter(record -> filters.targetName == null || containsIgnoreCase(record.targetName(), filters.targetName()))
                .filter(record -> filters.executedAfter == null || !record.executedAt().isBefore(filters.executedAfter))
                .filter(record -> filters.executedBefore == null || !record.executedAt().isAfter(filters.executedBefore))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveOrchestratorGroups(matched, request.groups());
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<OrchestratorScanListItemResponse> pageRecords = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream().map(this::toOrchestratorScanListItem).toList();

        return new PageResponse<>(request.page(), request.pageSize(), total, pages, pageRecords, groups);
    }

    public OrchestratorScanDetailResponse detailOrchestratorScan(String executionId) {
        ensureAccess();
        OrchestratorScanRecord record = requireOrchestratorRecord(executionId);
        return new OrchestratorScanDetailResponse(
                record.executionId(),
                record.runId(),
                record.targetId(),
                record.targetName(),
                record.automationType(),
                record.status(),
                record.message(),
                record.executedAt(),
                record.scannedAt()
        );
    }

    public PageResponse<TriggerExecutionListItemResponse> pageTriggerExecutions(PageRequest request) {
        ensureAccess();
        TriggerFilters filters = parseTriggerFilters(request.filters());
        Comparator<TriggerExecutionRecord> comparator = resolveTriggerComparator(request.sorts());
        List<TriggerExecutionRecord> matched = triggerExecutionRecordMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status == null || filters.status.equalsIgnoreCase(record.status()))
                .filter(record -> filters.action == null || filters.action.equalsIgnoreCase(record.action()))
                .filter(record -> filters.triggerId == null || containsIgnoreCase(record.triggerId(), filters.triggerId()))
                .filter(record -> filters.triggerKey == null || containsIgnoreCase(record.triggerKey(), filters.triggerKey()))
                .filter(record -> filters.triggerName == null || containsIgnoreCase(record.triggerName(), filters.triggerName()))
                .filter(record -> filters.triggerEvent == null || filters.triggerEvent().equalsIgnoreCase(record.triggerEvent()))
                .filter(record -> filters.enabled == null || filters.enabled.equals(record.enabled()))
                .filter(record -> filters.executedAfter == null || !record.executedAt().isBefore(filters.executedAfter))
                .filter(record -> filters.executedBefore == null || !record.executedAt().isAfter(filters.executedBefore))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveTriggerGroups(matched, request.groups());
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<TriggerExecutionListItemResponse> pageRecords = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream().map(this::toTriggerExecutionListItem).toList();

        return new PageResponse<>(request.page(), request.pageSize(), total, pages, pageRecords, groups);
    }

    public TriggerExecutionDetailResponse detailTriggerExecution(String executionId) {
        ensureAccess();
        TriggerExecutionRecord record = requireTriggerRecord(executionId);
        return new TriggerExecutionDetailResponse(
                record.executionId(),
                record.triggerId(),
                record.triggerName(),
                record.triggerKey(),
                record.triggerEvent(),
                record.action(),
                record.channelIds(),
                record.enabled(),
                record.operatorUserId(),
                record.status(),
                record.description(),
                record.conditionExpression(),
                record.executedAt()
        );
    }

    public PageResponse<NotificationChannelHealthListItemResponse> pageNotificationChannelHealths(PageRequest request) {
        ensureAccess();
        ChannelFilters filters = parseChannelFilters(request.filters());
        Comparator<NotificationChannelHealthSnapshot> comparator = resolveChannelComparator(request.sorts());
        List<NotificationChannelHealthSnapshot> matched = buildNotificationChannelHealthSnapshots().stream()
                .filter(snapshot -> matchesKeyword(snapshot, request.keyword()))
                .filter(snapshot -> filters.status == null || filters.status.equals(snapshot.status))
                .filter(snapshot -> filters.channelType == null || containsIgnoreCase(snapshot.channelType, filters.channelType))
                .filter(snapshot -> filters.channelName == null || containsIgnoreCase(snapshot.channelName, filters.channelName))
                .filter(snapshot -> filters.channelCode == null || containsIgnoreCase(snapshot.channelCode, filters.channelCode))
                .filter(snapshot -> filters.latestStatus == null || filters.latestStatus.equalsIgnoreCase(snapshot.latestStatus))
                .sorted(comparator)
                .toList();

        List<PageResponse.GroupValue> groups = resolveChannelGroups(matched, request.groups());
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<NotificationChannelHealthListItemResponse> pageRecords = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream().map(this::toNotificationChannelHealthListItem).toList();

        return new PageResponse<>(request.page(), request.pageSize(), total, pages, pageRecords, groups);
    }

    public NotificationChannelHealthDetailResponse detailNotificationChannelHealth(String channelId) {
        ensureAccess();
        NotificationChannelRecord channel = requireNotificationChannel(channelId);
        NotificationChannelHealthSnapshot snapshot = resolveSingleNotificationChannelHealth(channel);
        return new NotificationChannelHealthDetailResponse(
                channel.channelId(),
                channel.channelCode(),
                channel.channelName(),
                channel.channelType(),
                snapshot.status,
                channel.enabled() != null && channel.enabled(),
                snapshot.latestStatus,
                snapshot.totalAttempts,
                snapshot.successAttempts,
                snapshot.failedAttempts,
                snapshot.successRate,
                snapshot.lastSentAt,
                snapshot.latestResponseMessage,
                channel.createdAt(),
                channel.updatedAt(),
                channel.remark(),
                resolveChannelEndpoint(channel)
        );
    }

    public NotificationChannelHealthDetailResponse recheckNotificationChannelHealth(String channelId) {
        ensureAccess();
        notificationChannelService.diagnostic(channelId);
        return detailNotificationChannelHealth(channelId);
    }

    private OrchestratorFilters parseOrchestratorFilters(List<FilterItem> filters) {
        String status = null;
        String automationType = null;
        String runId = null;
        String targetId = null;
        String targetName = null;
        Instant executedAfter = null;
        Instant executedBefore = null;

        for (FilterItem filter : filters) {
            if (!ORCHESTRATOR_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), ORCHESTRATOR_FILTER_FIELDS);
            }
            if ("executedAt".equals(filter.field())) {
                if (!"between".equalsIgnoreCase(filter.operator())) {
                    throw unsupported("编排扫描不支持的筛选操作符", filter.operator(), List.of("between"));
                }
                InstantRange range = parseInstantRange(filter.value());
                executedAfter = range.start();
                executedBefore = range.end();
                continue;
            }
            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("编排扫描不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            if ("status".equals(filter.field())) {
                status = value;
            } else if ("automationType".equals(filter.field())) {
                automationType = value;
            } else if ("runId".equals(filter.field())) {
                runId = value;
            } else if ("targetId".equals(filter.field())) {
                targetId = value;
            } else if ("targetName".equals(filter.field())) {
                targetName = value;
            }
        }
        return new OrchestratorFilters(status, automationType, runId, targetId, targetName, executedAfter, executedBefore);
    }

    private TriggerFilters parseTriggerFilters(List<FilterItem> filters) {
        String status = null;
        String action = null;
        String triggerId = null;
        String triggerKey = null;
        String triggerName = null;
        String triggerEvent = null;
        Boolean enabled = null;
        Instant executedAfter = null;
        Instant executedBefore = null;

        for (FilterItem filter : filters) {
            if (!TRIGGER_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), TRIGGER_FILTER_FIELDS);
            }
            if ("executedAt".equals(filter.field())) {
                if (!"between".equalsIgnoreCase(filter.operator())) {
                    throw unsupported("触发执行不支持的筛选操作符", filter.operator(), List.of("between"));
                }
                InstantRange range = parseInstantRange(filter.value());
                executedAfter = range.start();
                executedBefore = range.end();
                continue;
            }
            if ("enabled".equals(filter.field())) {
                if (!"eq".equalsIgnoreCase(filter.operator())) {
                    throw unsupported("触发执行不支持的筛选操作符", filter.operator(), List.of("eq"));
                }
                enabled = resolveBooleanFilter("enabled", filter.value());
                continue;
            }
            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("触发执行不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            if ("status".equals(filter.field())) {
                status = value;
            } else if ("action".equals(filter.field())) {
                action = value;
            } else if ("triggerId".equals(filter.field())) {
                triggerId = value;
            } else if ("triggerKey".equals(filter.field())) {
                triggerKey = value;
            } else if ("triggerName".equals(filter.field())) {
                triggerName = value;
            } else if ("triggerEvent".equals(filter.field())) {
                triggerEvent = value;
            }
        }
        return new TriggerFilters(status, action, triggerId, triggerKey, triggerName, triggerEvent, enabled, executedAfter, executedBefore);
    }

    private ChannelFilters parseChannelFilters(List<FilterItem> filters) {
        String status = null;
        String channelType = null;
        String latestStatus = null;
        String channelName = null;
        String channelCode = null;

        for (FilterItem filter : filters) {
            if (!CHANNEL_HEALTH_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), CHANNEL_HEALTH_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator()) && !"contains".equalsIgnoreCase(filter.operator())) {
                throw unsupported("通知渠道健康不支持的筛选操作符", filter.operator(), List.of("eq", "contains"));
            }
            String value = normalize(filter.value());
            switch (filter.field()) {
                case "status" -> status = resolveChannelStatus(value);
                case "channelType" -> channelType = value;
                case "latestStatus" -> latestStatus = value;
                case "channelName" -> channelName = value;
                case "channelCode" -> channelCode = value;
                default -> {}
            }
        }
        return new ChannelFilters(status, channelType, latestStatus, channelName, channelCode);
    }

    private Comparator<OrchestratorScanRecord> resolveOrchestratorComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(OrchestratorScanRecord::executedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!ORCHESTRATOR_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), ORCHESTRATOR_SORT_FIELDS);
        }
        Comparator<OrchestratorScanRecord> comparator = switch (sort.field()) {
            case "scannedAt" -> Comparator.comparing(OrchestratorScanRecord::scannedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(OrchestratorScanRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "automationType" -> Comparator.comparing(OrchestratorScanRecord::automationType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "targetName" -> Comparator.comparing(OrchestratorScanRecord::targetName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "targetId" -> Comparator.comparing(OrchestratorScanRecord::targetId, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(OrchestratorScanRecord::executedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Comparator<TriggerExecutionRecord> resolveTriggerComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(TriggerExecutionRecord::executedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!TRIGGER_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), TRIGGER_SORT_FIELDS);
        }
        Comparator<TriggerExecutionRecord> comparator = switch (sort.field()) {
            case "triggerName" -> Comparator.comparing(TriggerExecutionRecord::triggerName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "triggerKey" -> Comparator.comparing(TriggerExecutionRecord::triggerKey, Comparator.nullsLast(Comparator.naturalOrder()));
            case "triggerEvent" -> Comparator.comparing(TriggerExecutionRecord::triggerEvent, Comparator.nullsLast(Comparator.naturalOrder()));
            case "action" -> Comparator.comparing(TriggerExecutionRecord::action, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(TriggerExecutionRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "enabled" -> Comparator.comparing(TriggerExecutionRecord::enabled, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(TriggerExecutionRecord::executedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Comparator<NotificationChannelHealthSnapshot> resolveChannelComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationChannelHealthSnapshot::lastSentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!CHANNEL_HEALTH_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), CHANNEL_HEALTH_SORT_FIELDS);
        }
        Comparator<NotificationChannelHealthSnapshot> comparator = switch (sort.field()) {
            case "totalAttempts" -> Comparator.comparing(NotificationChannelHealthSnapshot::totalAttempts);
            case "successRate" -> Comparator.comparing(NotificationChannelHealthSnapshot::successRate);
            case "successAttempts" -> Comparator.comparing(NotificationChannelHealthSnapshot::successAttempts);
            case "failedAttempts" -> Comparator.comparing(NotificationChannelHealthSnapshot::failedAttempts);
            case "channelName" -> Comparator.comparing(NotificationChannelHealthSnapshot::channelName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channelType" -> Comparator.comparing(NotificationChannelHealthSnapshot::channelType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "latestStatus" -> Comparator.comparing(NotificationChannelHealthSnapshot::latestStatus, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(NotificationChannelHealthSnapshot::status, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationChannelHealthSnapshot::lastSentAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private List<PageResponse.GroupValue> resolveOrchestratorGroups(List<OrchestratorScanRecord> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!ORCHESTRATOR_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), ORCHESTRATOR_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (OrchestratorScanRecord record : records) {
                String value = switch (group.field()) {
                    case "status" -> record.status();
                    case "automationType" -> record.automationType();
                    case "runId" -> record.runId();
                    case "targetId" -> record.targetId();
                    default -> "";
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private List<PageResponse.GroupValue> resolveTriggerGroups(List<TriggerExecutionRecord> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!TRIGGER_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), TRIGGER_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (TriggerExecutionRecord record : records) {
                String value = switch (group.field()) {
                    case "status" -> record.status();
                    case "action" -> record.action();
                    case "triggerEvent" -> record.triggerEvent();
                    case "enabled" -> record.enabled() == null ? "" : String.valueOf(record.enabled());
                    default -> "";
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private List<PageResponse.GroupValue> resolveChannelGroups(List<NotificationChannelHealthSnapshot> records, List<GroupItem> groups) {
        List<PageResponse.GroupValue> result = new ArrayList<>();
        for (GroupItem group : groups) {
            if (!CHANNEL_HEALTH_GROUP_FIELDS.contains(group.field())) {
                throw unsupported("不支持的分组字段", group.field(), CHANNEL_HEALTH_GROUP_FIELDS);
            }
            Map<String, Boolean> dedupe = new LinkedHashMap<>();
            for (NotificationChannelHealthSnapshot snapshot : records) {
                String value = switch (group.field()) {
                    case "status" -> snapshot.status;
                    case "channelType" -> snapshot.channelType;
                    case "latestStatus" -> snapshot.latestStatus;
                    default -> "";
                };
                dedupe.put(value == null ? "" : value, true);
            }
            dedupe.forEach((value, ignored) -> result.add(new PageResponse.GroupValue(group.field(), value)));
        }
        return result;
    }

    private boolean matchesKeyword(OrchestratorScanRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(record.runId(), normalized)
                || containsIgnoreCase(record.targetId(), normalized)
                || containsIgnoreCase(record.targetName(), normalized)
                || containsIgnoreCase(record.automationType(), normalized)
                || containsIgnoreCase(record.status(), normalized)
                || containsIgnoreCase(record.message(), normalized);
    }

    private boolean matchesKeyword(TriggerExecutionRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(record.triggerId(), normalized)
                || containsIgnoreCase(record.triggerName(), normalized)
                || containsIgnoreCase(record.triggerKey(), normalized)
                || containsIgnoreCase(record.triggerEvent(), normalized)
                || containsIgnoreCase(record.action(), normalized)
                || containsIgnoreCase(record.status(), normalized)
                || containsIgnoreCase(record.description(), normalized)
                || containsIgnoreCase(record.conditionExpression(), normalized)
                || containsIgnoreCase(record.operatorUserId(), normalized);
    }

    private boolean matchesKeyword(NotificationChannelHealthSnapshot snapshot, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(snapshot.channelId, normalized)
                || containsIgnoreCase(snapshot.channelCode, normalized)
                || containsIgnoreCase(snapshot.channelName, normalized)
                || containsIgnoreCase(snapshot.channelType, normalized)
                || containsIgnoreCase(snapshot.status, normalized)
                || containsIgnoreCase(snapshot.latestStatus, normalized)
                || containsIgnoreCase(snapshot.remark, normalized)
                || containsIgnoreCase(snapshot.channelEndpoint, normalized);
    }

    private OrchestratorScanListItemResponse toOrchestratorScanListItem(OrchestratorScanRecord record) {
        return new OrchestratorScanListItemResponse(
                record.executionId(),
                record.runId(),
                record.targetId(),
                record.targetName(),
                record.automationType(),
                record.status(),
                record.message(),
                record.executedAt(),
                record.scannedAt()
        );
    }

    private TriggerExecutionListItemResponse toTriggerExecutionListItem(TriggerExecutionRecord record) {
        return new TriggerExecutionListItemResponse(
                record.executionId(),
                record.triggerId(),
                record.triggerName(),
                record.triggerKey(),
                record.triggerEvent(),
                record.action(),
                record.enabled(),
                record.operatorUserId(),
                record.status(),
                record.executedAt()
        );
    }

    private NotificationChannelHealthListItemResponse toNotificationChannelHealthListItem(NotificationChannelHealthSnapshot snapshot) {
        return new NotificationChannelHealthListItemResponse(
                snapshot.channelId,
                snapshot.channelCode,
                snapshot.channelName,
                snapshot.channelType,
                snapshot.status,
                snapshot.latestStatus,
                snapshot.totalAttempts,
                snapshot.successAttempts,
                snapshot.failedAttempts,
                snapshot.successRate,
                snapshot.lastSentAt,
                snapshot.latestResponseMessage
        );
    }

    private OrchestratorScanRecord requireOrchestratorRecord(String executionId) {
        OrchestratorScanRecord record = orchestratorScanRecordMapper.selectAll().stream()
                .filter(item -> item.executionId().equals(executionId))
                .findFirst()
                .orElse(null);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "编排扫描记录不存在",
                    Map.of("executionId", executionId)
            );
        }
        return record;
    }

    private TriggerExecutionRecord requireTriggerRecord(String executionId) {
        TriggerExecutionRecord record = triggerExecutionRecordMapper.selectById(executionId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "触发执行记录不存在",
                    Map.of("executionId", executionId)
            );
        }
        return record;
    }

    private NotificationChannelRecord requireNotificationChannel(String channelId) {
        NotificationChannelRecord channel = notificationChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "通知渠道不存在",
                    Map.of("channelId", channelId)
            );
        }
        return channel;
    }

    private List<NotificationChannelHealthSnapshot> buildNotificationChannelHealthSnapshots() {
        Map<String, List<NotificationLogRecord>> logsByChannel = notificationLogMapper.selectAll().stream()
                .collect(Collectors.groupingBy(NotificationLogRecord::channelId));
        return notificationChannelMapper.selectAll().stream()
                .map(channel -> resolveChannelHealthSnapshot(channel, logsByChannel.getOrDefault(channel.channelId(), List.of())))
                .toList();
    }

    private NotificationChannelHealthSnapshot resolveChannelHealthSnapshot(
            NotificationChannelRecord channel,
            List<NotificationLogRecord> records
    ) {
        long totalAttempts = records.size();
        long successAttempts = records.stream().filter(NotificationLogRecord::success).count();
        long failedAttempts = totalAttempts - successAttempts;
        int successRate = totalAttempts == 0 ? 0 : (int) Math.round((double) successAttempts / totalAttempts * 100);
        List<NotificationLogRecord> sortedRecords = new ArrayList<>(records);
        sortedRecords.sort(Comparator.comparing(NotificationLogRecord::sentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        NotificationLogRecord latestRecord = sortedRecords.isEmpty() ? null : sortedRecords.get(0);
        String latestStatus = latestRecord == null ? "NONE" : latestRecord.status();
        Instant lastSentAt = latestRecord == null ? null : latestRecord.sentAt();
        String latestResponseMessage = latestRecord == null ? "" : latestRecord.responseMessage();
        String status = Boolean.TRUE.equals(channel.enabled()) ? "ENABLED" : "DISABLED";
        return new NotificationChannelHealthSnapshot(
                channel.channelId(),
                channel.channelCode(),
                channel.channelName(),
                channel.channelType(),
                status,
                channel.remark(),
                channel.createdAt(),
                channel.updatedAt(),
                lastSentAt,
                totalAttempts,
                successAttempts,
                failedAttempts,
                successRate,
                latestStatus,
                latestResponseMessage,
                resolveChannelEndpoint(channel)
        );
    }

    private NotificationChannelHealthSnapshot resolveSingleNotificationChannelHealth(NotificationChannelRecord channel) {
        Map<String, List<NotificationLogRecord>> logsByChannel = notificationLogMapper.selectAll().stream()
                .collect(Collectors.groupingBy(NotificationLogRecord::channelId));
        return resolveChannelHealthSnapshot(channel, logsByChannel.getOrDefault(channel.channelId(), List.of()));
    }

    private String resolveChannelEndpoint(NotificationChannelRecord channel) {
        if (channel == null || channel.config() == null) {
            return null;
        }
        Object endpoint = channel.config().get("endpoint");
        return endpoint == null ? null : String.valueOf(endpoint);
    }

    private String resolveChannelStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("ENABLED".equals(normalized) || "DISABLED".equals(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "通知渠道状态不合法",
                Map.of("status", value, "allowed", List.of("ENABLED", "DISABLED"))
        );
    }

    private String normalize(JsonNode value) {
        if (value == null || !value.isTextual() && !value.isNumber() && !value.isBoolean()) {
            return "";
        }
        return value.asText().trim();
    }

    private Boolean resolveBooleanFilter(String field, JsonNode value) {
        String normalized = normalize(value);
        if ("true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "no".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "布尔型筛选参数不合法",
                Map.of("field", field, "value", value == null ? "" : value.toString(), "allowed", List.of("true", "false", "yes", "no", "1", "0"))
        );
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
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "时间范围解析失败",
                    Map.of("value", value.toString())
            );
        }
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private void ensureAccess() {
        String userId = currentUserId();
        if (!fixtureAuthService.canAccessPhase2SystemManagement(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员和流程管理员可访问监控管理",
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

    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowed", allowedFields)
        );
    }

    private record OrchestratorFilters(
            String status,
            String automationType,
            String runId,
            String targetId,
            String targetName,
            Instant executedAfter,
            Instant executedBefore
    ) {
    }

    private record TriggerFilters(
            String status,
            String action,
            String triggerId,
            String triggerKey,
            String triggerName,
            String triggerEvent,
            Boolean enabled,
            Instant executedAfter,
            Instant executedBefore
    ) {
    }

    private record ChannelFilters(
            String status,
            String channelType,
            String latestStatus,
            String channelName,
            String channelCode
    ) {
    }

    private record InstantRange(Instant start, Instant end) {
    }

    private record NotificationChannelHealthSnapshot(
            String channelId,
            String channelCode,
            String channelName,
            String channelType,
            String status,
            String remark,
            Instant createdAt,
            Instant updatedAt,
            Instant lastSentAt,
            long totalAttempts,
            long successAttempts,
            long failedAttempts,
            int successRate,
            String latestStatus,
            String latestResponseMessage,
            String channelEndpoint
    ) {
    }
}
