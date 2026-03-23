package com.westflow.aiadmin.confirmation.service;

import com.westflow.aiadmin.confirmation.api.AiConfirmationDetailResponse;
import com.westflow.aiadmin.confirmation.api.AiConfirmationListItemResponse;
import com.westflow.aiadmin.confirmation.mapper.AiConfirmationAdminMapper;
import com.westflow.aiadmin.confirmation.model.AiConfirmationAdminRecord;
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
 * AI 确认记录管理服务。
 */
@Service
@RequiredArgsConstructor
public class AiConfirmationService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "approved");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "resolvedAt", "updatedAt", "status");
    private static final List<String> SUPPORTED_SORT_DIRECTIONS = List.of("asc", "desc");

    private final AiAdminAccessService aiAdminAccessService;
    private final AiConfirmationAdminMapper aiConfirmationAdminMapper;

    /**
     * 分页查询确认记录。
     */
    public PageResponse<AiConfirmationListItemResponse> page(PageRequest request) {
        aiAdminAccessService.ensureAiAdminAccess();
        Filters filters = resolveFilters(request.filters());
        Comparator<AiConfirmationAdminRecord> comparator = resolveComparator(request.sorts());
        List<AiConfirmationListItemResponse> matched = aiConfirmationAdminMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.status() == null || filters.status().equals(record.status()))
                .filter(record -> filters.approved() == null || filters.approved().equals(record.approved()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();
        return AiAdminSupport.toPage(request, matched);
    }

    /**
     * 查询确认记录详情。
     */
    public AiConfirmationDetailResponse detail(String confirmationId) {
        aiAdminAccessService.ensureAiAdminAccess();
        return toDetail(requireRecord(confirmationId));
    }

    private AiConfirmationListItemResponse toListItem(AiConfirmationAdminRecord record) {
        return new AiConfirmationListItemResponse(
                record.confirmationId(),
                record.toolCallId(),
                record.status(),
                record.approved(),
                record.comment(),
                record.resolvedBy(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.resolvedAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private AiConfirmationDetailResponse toDetail(AiConfirmationAdminRecord record) {
        return new AiConfirmationDetailResponse(
                record.confirmationId(),
                record.toolCallId(),
                record.status(),
                record.approved(),
                record.comment(),
                record.resolvedBy(),
                AiAdminSupport.toOffsetDateTime(record.createdAt()),
                AiAdminSupport.toOffsetDateTime(record.resolvedAt()),
                AiAdminSupport.toOffsetDateTime(record.updatedAt())
        );
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String status = null;
        Boolean approved = null;
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
                case "approved" -> approved = Boolean.valueOf(value);
                default -> {
                }
            }
        }
        return new Filters(status, approved);
    }

    private Comparator<AiConfirmationAdminRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(AiConfirmationAdminRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        if (!SUPPORTED_SORT_DIRECTIONS.contains(sort.direction())) {
            throw unsupported("不支持的排序方向", sort.direction(), SUPPORTED_SORT_DIRECTIONS);
        }
        Comparator<AiConfirmationAdminRecord> comparator = switch (sort.field()) {
            case "status" -> Comparator.comparing(AiConfirmationAdminRecord::status, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(AiConfirmationAdminRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "resolvedAt" -> Comparator.comparing(AiConfirmationAdminRecord::resolvedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AiConfirmationAdminRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private boolean matchesKeyword(AiConfirmationAdminRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.status().toLowerCase().contains(normalized)
                || (record.toolCallId() != null && record.toolCallId().toLowerCase().contains(normalized))
                || (record.comment() != null && record.comment().toLowerCase().contains(normalized))
                || (record.resolvedBy() != null && record.resolvedBy().toLowerCase().contains(normalized));
    }

    private AiConfirmationAdminRecord requireRecord(String confirmationId) {
        AiConfirmationAdminRecord record = aiConfirmationAdminMapper.selectById(confirmationId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "确认记录不存在",
                    Map.of("confirmationId", confirmationId)
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
            Boolean approved
    ) {
    }
}
