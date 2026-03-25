package com.westflow.notification.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.notification.response.NotificationChannelDiagnosticResponse;
import com.westflow.notification.response.NotificationChannelDetailResponse;
import com.westflow.notification.response.NotificationChannelFormOptionsResponse;
import com.westflow.notification.response.NotificationChannelListItemResponse;
import com.westflow.notification.response.NotificationChannelMutationResponse;
import com.westflow.notification.request.SaveNotificationChannelRequest;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationLogRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 管理通知渠道的查询、配置校验和保存。
public class NotificationChannelService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "channelType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "channelCode", "channelName", "lastSentAt");

    private final NotificationChannelMapper notificationChannelMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final Environment environment;

    // 通知渠道列表支持关键字、状态和渠道类型筛选。
    public PageResponse<NotificationChannelListItemResponse> page(PageRequest request) {
        // 渠道列表先走内存过滤，后续接数据库时只替换 mapper 即可。
        Filters filters = resolveFilters(request.filters());
        Comparator<NotificationChannelRecord> comparator = resolveComparator(request.sorts());
        List<NotificationChannelListItemResponse> records = notificationChannelMapper.selectAll().stream()
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
        List<NotificationChannelListItemResponse> pageRecords = fromIndex >= records.size()
                ? List.of()
                : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    // 返回渠道详情，供编辑页直接回填。
    public NotificationChannelDetailResponse detail(String channelId) {
        // 详情页直接返回完整配置，方便编辑页一次性回填。
        NotificationChannelRecord record = requireChannel(channelId);
        return toDetail(record);
    }

    // 聚合配置与最近发送结果，供诊断页直接展示。
    public NotificationChannelDiagnosticResponse diagnostic(String channelId) {
        NotificationChannelRecord channel = requireChannel(channelId);
        NotificationChannelType type = resolveType(channel.channelType());
        List<String> missingConfigFields = findMissingRequiredFields(type, channel.config(), Boolean.TRUE.equals(channel.mockMode()));
        List<NotificationLogRecord> logs = notificationLogMapper.selectByChannelId(channel.channelId());
        NotificationLogRecord latestLog = logs.isEmpty() ? null : logs.get(0);
        NotificationLogRecord latestFailure = logs.stream()
                .filter(log -> !log.success())
                .findFirst()
                .orElse(null);
        return new NotificationChannelDiagnosticResponse(
                channel.channelId(),
                channel.channelCode(),
                channel.channelType(),
                channel.channelName(),
                channel.enabled(),
                missingConfigFields.isEmpty(),
                missingConfigFields,
                resolveHealthStatus(channel, missingConfigFields, latestLog),
                channel.lastSentAt(),
                latestLog == null ? null : latestLog.success(),
                latestLog == null ? null : latestLog.status(),
                latestLog == null ? null : latestLog.providerName(),
                latestLog == null ? null : latestLog.responseMessage(),
                latestLog == null ? null : latestLog.sentAt(),
                latestFailure == null ? null : latestFailure.sentAt(),
                latestFailure == null ? null : latestFailure.responseMessage()
        );
    }

    // 返回可用渠道类型枚举，供表单下拉框使用。
    public NotificationChannelFormOptionsResponse formOptions() {
        return new NotificationChannelFormOptionsResponse(
                NotificationChannelType.orderedValues().stream()
                        .map(type -> new NotificationChannelFormOptionsResponse.ChannelTypeOption(
                                type.name(),
                                type.label(),
                                type.realSend()
                        ))
                        .toList()
        );
    }

    @Transactional
    // 新建通知渠道并返回新主键。
    public NotificationChannelMutationResponse create(SaveNotificationChannelRequest request) {
        validateChannelCode(request.channelCode(), null);
        NotificationChannelType type = resolveType(request.channelType());
        boolean diagnosticMockMode = resolveDiagnosticMockMode(type, request.config());
        validateConfig(type, request.config(), diagnosticMockMode);
        String channelId = buildId("nch");
        Instant now = Instant.now();
        notificationChannelMapper.upsert(buildRecord(channelId, request, now, now, null, diagnosticMockMode));
        return new NotificationChannelMutationResponse(channelId);
    }

    @Transactional
    // 更新通知渠道并保留原始创建时间。
    public NotificationChannelMutationResponse update(String channelId, SaveNotificationChannelRequest request) {
        requireChannel(channelId);
        validateChannelCode(request.channelCode(), channelId);
        NotificationChannelType type = resolveType(request.channelType());
        boolean diagnosticMockMode = resolveDiagnosticMockMode(type, request.config());
        validateConfig(type, request.config(), diagnosticMockMode);
        NotificationChannelRecord existing = requireChannel(channelId);
        Instant now = Instant.now();
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                channelId,
                request.channelCode().trim(),
                type.name(),
                request.channelName().trim(),
                Boolean.TRUE.equals(request.enabled()),
                diagnosticMockMode,
                normalizeConfig(request.config()),
                normalizeNullable(request.remark()),
                existing.createdAt(),
                now,
                existing.lastSentAt()
        ));
        return new NotificationChannelMutationResponse(channelId);
    }

    // 按主键读取通知渠道，不存在时抛出资源不存在异常。
    public NotificationChannelRecord requireChannel(String channelId) {
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

    // 组装渠道持久化记录。
    private NotificationChannelRecord buildRecord(
            String channelId,
            SaveNotificationChannelRequest request,
            Instant createdAt,
            Instant updatedAt,
            Instant lastSentAt,
            boolean diagnosticMockMode
    ) {
        return new NotificationChannelRecord(
                channelId,
                request.channelCode().trim(),
                resolveType(request.channelType()).name(),
                request.channelName().trim(),
                Boolean.TRUE.equals(request.enabled()),
                diagnosticMockMode,
                normalizeConfig(request.config()),
                normalizeNullable(request.remark()),
                createdAt,
                updatedAt,
                lastSentAt
        );
    }

    // 关键字命中渠道编码、名称和类型任一字段即算匹配。
    private boolean matchesKeyword(NotificationChannelRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.channelCode().toLowerCase().contains(normalized)
                || record.channelName().toLowerCase().contains(normalized)
                || record.channelType().toLowerCase().contains(normalized);
    }

    // 解析列表排序规则。
    private Comparator<NotificationChannelRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(NotificationChannelRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<NotificationChannelRecord> comparator = switch (sort.field()) {
            case "channelCode" -> Comparator.comparing(NotificationChannelRecord::channelCode, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channelName" -> Comparator.comparing(NotificationChannelRecord::channelName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt" -> Comparator.comparing(NotificationChannelRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "lastSentAt" -> Comparator.comparing(NotificationChannelRecord::lastSentAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(NotificationChannelRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    // 解析筛选条件并转换成内部结构。
    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String channelType = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> enabled = resolveBoolean(value, "状态筛选值不合法");
                case "channelType" -> channelType = resolveType(value).name();
                default -> {
                }
            }
        }
        return new Filters(enabled, channelType);
    }

    // 把前端状态值归一成布尔值。
    private Boolean resolveBoolean(String value, String message) {
        if ("ENABLED".equalsIgnoreCase(value)) {
            return true;
        }
        if ("DISABLED".equalsIgnoreCase(value)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("value", value)
        );
    }

    // 校验渠道编码是否重复。
    private void validateChannelCode(String channelCode, String excludeChannelId) {
        String normalized = channelCode.trim();
        if (notificationChannelMapper.existsByCode(normalized, excludeChannelId)) {
            throw new ContractException(
                    "BIZ.NOTIFICATION_CHANNEL_CODE_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "通知渠道编码已存在",
                    Map.of("channelCode", normalized)
            );
        }
    }

    // 解析渠道类型枚举。
    private NotificationChannelType resolveType(String channelType) {
        String normalized = normalizeNullable(channelType);
        if (normalized == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道类型不能为空",
                    Map.of("channelType", channelType)
            );
        }
        try {
            return NotificationChannelType.fromCode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道类型不合法",
                    Map.of("channelType", channelType, "allowedTypes", NotificationChannelType.orderedValues().stream().map(Enum::name).toList())
            );
        }
    }

    // 校验渠道配置是否满足类型要求。
    private void validateConfig(NotificationChannelType type, Map<String, Object> config, boolean diagnosticMockMode) {
        List<String> missingFields = findMissingRequiredFields(type, config, diagnosticMockMode);
        if (!missingFields.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道配置缺少必要参数",
                    Map.of("field", missingFields.get(0), "missingFields", missingFields)
            );
        }
    }

    // 复用创建/更新校验规则，给诊断视图返回缺失字段列表。
    private List<String> findMissingRequiredFields(NotificationChannelType type, Map<String, Object> config, boolean diagnosticMockMode) {
        if (diagnosticMockMode) {
            return List.of();
        }
        Map<String, Object> normalized = normalizeConfig(config);
        return switch (type) {
            case EMAIL -> collectMissingFields(normalized, "smtpHost", "smtpPort", "fromAddress");
            case WEBHOOK -> collectMissingFields(normalized, "url");
            case SMS -> collectMissingFields(normalized, List.of(List.of("endpoint", "url")), List.of("accessToken"));
            case WECHAT -> collectMissingFields(
                    normalized,
                    List.of(List.of("endpoint", "url")),
                    List.of("accessToken", "agentId", "corpId")
            );
            case DINGTALK -> collectMissingFields(
                    normalized,
                    List.of(List.of("endpoint", "url")),
                    List.of("accessToken", "agentId", "appKey")
            );
            default -> List.of();
        };
    }

    private List<String> collectMissingFields(Map<String, Object> config, String... fields) {
        return collectMissingFields(config, List.of(), List.of(fields));
    }

    private List<String> collectMissingFields(
            Map<String, Object> config,
            List<List<String>> alternatives,
            List<String> directFields
    ) {
        List<String> missingFields = new java.util.ArrayList<String>();
        for (List<String> group : alternatives) {
            boolean present = group.stream().anyMatch(field -> !isBlank(config.get(field)));
            if (!present && !group.isEmpty()) {
                missingFields.add(group.get(0));
            }
        }
        for (String field : directFields) {
            if (isBlank(config.get(field))) {
                missingFields.add(field);
            }
        }
        return List.copyOf(missingFields);
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    // 配置值做空值归一。
    private Map<String, Object> normalizeConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(config);
    }

    // 把空白备注归一为 null。
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // 转换为列表页数据。
    private NotificationChannelListItemResponse toListItem(NotificationChannelRecord record) {
        return new NotificationChannelListItemResponse(
                record.channelId(),
                record.channelCode(),
                record.channelType(),
                record.channelName(),
                record.enabled(),
                record.lastSentAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    // 转换为详情页数据。
    private NotificationChannelDetailResponse toDetail(NotificationChannelRecord record) {
        return new NotificationChannelDetailResponse(
                record.channelId(),
                record.channelCode(),
                record.channelType(),
                record.channelName(),
                record.enabled(),
                record.config(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.lastSentAt()
        );
    }

    private String resolveHealthStatus(
            NotificationChannelRecord record,
            List<String> missingConfigFields,
            NotificationLogRecord latestLog
    ) {
        if (!Boolean.TRUE.equals(record.enabled())) {
            return "DISABLED";
        }
        if (!missingConfigFields.isEmpty()) {
            return "CONFIG_INVALID";
        }
        if (Boolean.TRUE.equals(record.mockMode())) {
            return "DIAGNOSTIC_MOCK";
        }
        if (latestLog == null) {
            return "READY";
        }
        return latestLog.success() ? "HEALTHY" : "DEGRADED";
    }

    private boolean resolveDiagnosticMockMode(NotificationChannelType type, Map<String, Object> config) {
        if (!List.of(NotificationChannelType.SMS, NotificationChannelType.WECHAT, NotificationChannelType.DINGTALK).contains(type)) {
            return false;
        }
        if (!allowDiagnosticMockProfile()) {
            return false;
        }
        Map<String, Object> normalized = normalizeConfig(config);
        Object enabled = normalized.get("diagnosticMockEnabled");
        if (!(enabled instanceof Boolean enabledValue) || !enabledValue) {
            return false;
        }
        Object endpointValue = normalized.containsKey("endpoint") ? normalized.get("endpoint") : normalized.get("url");
        String endpoint = endpointValue == null ? null : normalizeNullable(String.valueOf(endpointValue));
        return endpoint != null && isLocalEndpoint(endpoint);
    }

    private boolean isLocalEndpoint(String endpoint) {
        return endpoint.startsWith("http://127.0.0.1")
                || endpoint.startsWith("http://localhost")
                || endpoint.startsWith("https://127.0.0.1")
                || endpoint.startsWith("https://localhost");
    }

    private boolean allowDiagnosticMockProfile() {
        return environment.acceptsProfiles(Profiles.of("local", "test"));
    }

    // 统一构造请求非法异常。
    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }

    // 生成渠道主键。
    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Filters(
            Boolean enabled,
            String channelType
    ) {
    }
}
