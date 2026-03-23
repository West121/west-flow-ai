package com.westflow.aiadmin.toolcall.service;

import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminObservabilityService;
import com.westflow.aiadmin.support.AiAdminSupport;
import com.westflow.aiadmin.toolcall.api.AiToolCallDetailResponse;
import com.westflow.aiadmin.toolcall.api.AiToolCallListItemResponse;
import com.westflow.aiadmin.toolcall.mapper.AiToolCallAdminMapper;
import com.westflow.aiadmin.toolcall.model.AiToolCallAdminRecord;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 工具调用管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiToolCallService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "toolType", "toolSource", "hitSource", "requiresConfirmation");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "completedAt", "toolKey", "status", "toolType", "toolSource");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiToolCallAdminMapper aiToolCallAdminMapper;
    private final AiAdminObservabilityService aiAdminObservabilityService;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询工具调用记录。
     */
    public PageResponse<AiToolCallListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiToolCallAdminRecord> comparator = resolveComparator(request.sorts());
        List<AiToolCallListItemResponse> matched = aiToolCallAdminMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(record.status()))
                .filter(record -> filters.toolType() == null || filters.toolType().equals(record.toolType()))
                .filter(record -> filters.toolSource() == null || filters.toolSource().equals(record.toolSource()))
                .filter(record -> filters.requiresConfirmation() == null || filters.requiresConfirmation().equals(record.requiresConfirmation()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询工具调用详情。
     */
    public AiToolCallDetailResponse detail(String toolCallId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(toolCallId));
    }

    private AiToolCallListItemResponse toListItem(AiToolCallAdminRecord record) {
        return new AiToolCallListItemResponse(
                record.toolCallId(),
                record.conversationId(),
                record.toolKey(),
                record.toolType(),
                record.toolSource(),
                record.toolSource(),
                record.status(),
                record.requiresConfirmation(),
                record.summary(),
                record.confirmationId(),
                record.operatorUserId(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.completedAt()),
                resolveExecutionDurationMillis(record),
                resolveFailureReason(record)
        );
    }

    private AiToolCallDetailResponse toDetail(AiToolCallAdminRecord record) {
        AiAdminObservabilityService.ToolCallDiagnostics diagnostics = aiAdminObservabilityService.describeToolCall(record);
        return new AiToolCallDetailResponse(
                record.toolCallId(),
                record.conversationId(),
                record.toolKey(),
                record.toolType(),
                record.toolSource(),
                record.toolSource(),
                record.status(),
                record.requiresConfirmation(),
                record.argumentsJson(),
                record.resultJson(),
                record.summary(),
                record.confirmationId(),
                record.operatorUserId(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.completedAt()),
                resolveExecutionDurationMillis(record),
                diagnostics.failureReason(),
                diagnostics.failureCode(),
                diagnostics.conversationTitle(),
                diagnostics.confirmationStatus(),
                diagnostics.confirmationApproved(),
                diagnostics.confirmationResolvedBy(),
                diagnostics.confirmationComment(),
                diagnostics.linkedTool(),
                diagnostics.linkedSkill(),
                diagnostics.linkedMcp(),
                diagnostics.linkedAgents()
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        String toolType = null;
        String toolSource = null;
        Boolean requiresConfirmation = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> status = AiAdminSupport.normalize(value);
                case "toolType" -> toolType = AiAdminSupport.normalize(value);
                case "toolSource" -> toolSource = AiAdminSupport.normalize(value);
                case "hitSource" -> toolSource = AiAdminSupport.normalize(value);
                case "requiresConfirmation" -> requiresConfirmation = Boolean.valueOf(value);
                default -> {
                }
            }
        }
        return new Filters(status, toolType, toolSource, requiresConfirmation);
    }

    private Comparator<AiToolCallAdminRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiToolCallAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiToolCallAdminRecord> comparator = switch (sort.field()) {
            case "toolKey" -> Comparator.comparing(AiToolCallAdminRecord::toolKey, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(AiToolCallAdminRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "toolType" -> Comparator.comparing(AiToolCallAdminRecord::toolType, Comparator.nullsLast(Comparator.naturalOrder()));
            case "toolSource" -> Comparator.comparing(AiToolCallAdminRecord::toolSource, Comparator.nullsLast(Comparator.naturalOrder()));
            case "completedAt" -> Comparator.comparing(AiToolCallAdminRecord::completedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiToolCallAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiToolCallAdminRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.toolKey().toLowerCase().contains(normalized)
                || record.toolType().toLowerCase().contains(normalized)
                || record.toolSource().toLowerCase().contains(normalized)
                || record.status().toLowerCase().contains(normalized)
                || (record.summary() != null && record.summary().toLowerCase().contains(normalized))
                || (resolveFailureReason(record) != null && resolveFailureReason(record).toLowerCase().contains(normalized))
                || (record.argumentsJson() != null && record.argumentsJson().toLowerCase().contains(normalized))
                || (record.resultJson() != null && record.resultJson().toLowerCase().contains(normalized));
    }

    private Long resolveExecutionDurationMillis(AiToolCallAdminRecord record) {
        if (record.createdAt() == null || record.completedAt() == null) {
            return null;
        }
        long duration = java.time.Duration.between(record.createdAt(), record.completedAt()).toMillis();
        return Math.max(duration, 0L);
    }

    private String resolveFailureReason(AiToolCallAdminRecord record) {
        if (!"FAILED".equalsIgnoreCase(record.status()) || record.resultJson() == null || record.resultJson().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(record.resultJson());
            if (root.hasNonNull("message")) {
                return root.path("message").asText();
            }
            if (root.hasNonNull("error")) {
                return root.path("error").asText();
            }
            if (root.hasNonNull("reason")) {
                return root.path("reason").asText();
            }
        } catch (Exception ignored) {
            // 保持降级兼容，继续尝试从原始结果中提取失败原因。
        }
        return record.resultJson();
    }

    private AiToolCallAdminRecord requireRecord(String toolCallId) {
        AiToolCallAdminRecord record = aiToolCallAdminMapper.selectById(toolCallId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "工具调用记录不存在",
                    Map.of("toolCallId", toolCallId)
            );
        }
        return record;
    }

    private ContractException unsupported(String message, String value, List<String> allowedValues) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("value", value, "allowedValues", allowedValues)
        );
    }

    private record Filters(
            String status,
            String toolType,
            String toolSource,
            Boolean requiresConfirmation
    ) {
    }
}
