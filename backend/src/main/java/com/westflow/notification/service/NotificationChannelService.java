package com.westflow.notification.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.notification.api.NotificationChannelDetailResponse;
import com.westflow.notification.api.NotificationChannelFormOptionsResponse;
import com.westflow.notification.api.NotificationChannelListItemResponse;
import com.westflow.notification.api.NotificationChannelMutationResponse;
import com.westflow.notification.api.SaveNotificationChannelRequest;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
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

@Service
@RequiredArgsConstructor
// 管理通知渠道的查询、配置校验和保存。
public class NotificationChannelService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "channelType", "mockMode");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "channelCode", "channelName", "lastSentAt");

    private final NotificationChannelMapper notificationChannelMapper;

    // 通知渠道列表支持关键字、状态、渠道类型和 mock 模式筛选。
    public PageResponse<NotificationChannelListItemResponse> page(PageRequest request) {
        // 渠道列表先走内存过滤，后续接数据库时只替换 mapper 即可。
        Filters filters = resolveFilters(request.filters());
        Comparator<NotificationChannelRecord> comparator = resolveComparator(request.sorts());
        List<NotificationChannelListItemResponse> records = notificationChannelMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .filter(record -> filters.channelType() == null || filters.channelType().equals(record.channelType()))
                .filter(record -> filters.mockMode() == null || filters.mockMode().equals(record.mockMode()))
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

    // 返回可用渠道类型枚举，供表单下拉框使用。
    public NotificationChannelFormOptionsResponse formOptions() {
        return new NotificationChannelFormOptionsResponse(
                NotificationChannelType.orderedValues().stream()
                        .map(type -> new NotificationChannelFormOptionsResponse.ChannelTypeOption(
                                type.name(),
                                type.label(),
                                type.realSend(),
                                type.mockProvider()
                        ))
                        .toList()
        );
    }

    @Transactional
    // 新建通知渠道并返回新主键。
    public NotificationChannelMutationResponse create(SaveNotificationChannelRequest request) {
        validateChannelCode(request.channelCode(), null);
        NotificationChannelType type = resolveType(request.channelType());
        validateConfig(type, request.config(), Boolean.TRUE.equals(request.mockMode()));
        String channelId = buildId("nch");
        Instant now = Instant.now();
        notificationChannelMapper.upsert(buildRecord(channelId, request, now, now, null));
        return new NotificationChannelMutationResponse(channelId);
    }

    @Transactional
    // 更新通知渠道并保留原始创建时间。
    public NotificationChannelMutationResponse update(String channelId, SaveNotificationChannelRequest request) {
        requireChannel(channelId);
        validateChannelCode(request.channelCode(), channelId);
        NotificationChannelType type = resolveType(request.channelType());
        validateConfig(type, request.config(), Boolean.TRUE.equals(request.mockMode()));
        NotificationChannelRecord existing = requireChannel(channelId);
        Instant now = Instant.now();
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                channelId,
                request.channelCode().trim(),
                type.name(),
                request.channelName().trim(),
                Boolean.TRUE.equals(request.enabled()),
                Boolean.TRUE.equals(request.mockMode()),
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
            Instant lastSentAt
    ) {
        return new NotificationChannelRecord(
                channelId,
                request.channelCode().trim(),
                resolveType(request.channelType()).name(),
                request.channelName().trim(),
                Boolean.TRUE.equals(request.enabled()),
                Boolean.TRUE.equals(request.mockMode()),
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
        Boolean mockMode = null;
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
                case "mockMode" -> mockMode = resolveBoolean(value, "mock 模式筛选值不合法");
                default -> {
                }
            }
        }
        return new Filters(enabled, channelType, mockMode);
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
    private void validateConfig(NotificationChannelType type, Map<String, Object> config, boolean mockMode) {
        Map<String, Object> normalized = normalizeConfig(config);
        if (mockMode) {
            return;
        }
        switch (type) {
            case EMAIL -> {
                requireField(normalized, "smtpHost");
                requireField(normalized, "smtpPort");
                requireField(normalized, "fromAddress");
            }
            case WEBHOOK -> requireField(normalized, "url");
            case SMS -> {
                requireField(normalized, "endpoint", "url");
                requireField(normalized, "accessToken");
            }
            case WECHAT -> {
                requireField(normalized, "endpoint", "url");
                requireField(normalized, "accessToken");
                requireField(normalized, "agentId");
                requireField(normalized, "corpId");
            }
            case DINGTALK -> {
                requireField(normalized, "endpoint", "url");
                requireField(normalized, "accessToken");
                requireField(normalized, "agentId");
                requireField(normalized, "appKey");
            }
            default -> {
            }
        }
    }

    // 读取必须配置项，缺失时直接报错。
    private void requireField(Map<String, Object> config, String key) {
        requireField(config, key, key);
    }

    // 允许 endpoint/url 这类兼容字段共用一个校验入口。
    private void requireField(Map<String, Object> config, String key, String alternateKey) {
        Object value = config.get(key);
        if ((value == null || String.valueOf(value).isBlank()) && !key.equals(alternateKey)) {
            value = config.get(alternateKey);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "通知渠道配置缺少必要参数",
                    Map.of("field", key)
            );
        }
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
                record.mockMode(),
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
                record.mockMode(),
                record.config(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.lastSentAt()
        );
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
            String channelType,
            Boolean mockMode
    ) {
    }
}
