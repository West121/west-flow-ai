package com.westflow.aiadmin.conversation.service;

import com.westflow.aiadmin.conversation.api.AiConversationDetailResponse;
import com.westflow.aiadmin.conversation.api.AiConversationListItemResponse;
import com.westflow.aiadmin.conversation.mapper.AiConversationAdminMapper;
import com.westflow.aiadmin.conversation.model.AiConversationAdminRecord;
import com.westflow.aiadmin.support.AiAdminAccessService;
import com.westflow.aiadmin.support.AiAdminSupport;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 会话管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "title", "messageCount", "status");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiConversationAdminMapper aiConversationAdminMapper;

    /**
     * 分页查询会话。
     */
    public PageResponse<AiConversationListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiConversationAdminRecord> comparator = resolveComparator(request.sorts());
        List<AiConversationListItemResponse> matched = aiConversationAdminMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(record.status()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询会话详情。
     */
    public AiConversationDetailResponse detail(String conversationId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(conversationId));
    }

    private AiConversationListItemResponse toListItem(AiConversationAdminRecord record) {
        return new AiConversationListItemResponse(
                record.conversationId(),
                record.title(),
                record.preview(),
                record.status(),
                record.contextTagsJson(),
                record.messageCount(),
                record.operatorUserId(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiConversationDetailResponse toDetail(AiConversationAdminRecord record) {
        return new AiConversationDetailResponse(
                record.conversationId(),
                record.title(),
                record.preview(),
                record.status(),
                record.contextTagsJson(),
                record.messageCount(),
                record.operatorUserId(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("status".equals(filter.field())) {
                status = AiAdminSupport.normalize(value);
            }
        }
        return new Filters(status);
    }

    private Comparator<AiConversationAdminRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiConversationAdminRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiConversationAdminRecord> comparator = switch (sort.field()) {
            case "title" -> Comparator.comparing(AiConversationAdminRecord::title, Comparator.nullsLast(Comparator.naturalOrder()));
            case "messageCount" -> Comparator.comparingInt(AiConversationAdminRecord::messageCount);
            case "status" -> Comparator.comparing(AiConversationAdminRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(AiConversationAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiConversationAdminRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiConversationAdminRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.title().toLowerCase().contains(normalized)
                || (record.preview() != null && record.preview().toLowerCase().contains(normalized))
                || record.status().toLowerCase().contains(normalized)
                || (record.contextTagsJson() != null && record.contextTagsJson().toLowerCase().contains(normalized))
                || (record.operatorUserId() != null && record.operatorUserId().toLowerCase().contains(normalized));
    }

    private AiConversationAdminRecord requireRecord(String conversationId) {
        AiConversationAdminRecord record = aiConversationAdminMapper.selectById(conversationId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "会话记录不存在",
                    Map.of("conversationId", conversationId)
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
            String status
    ) {
    }
}
