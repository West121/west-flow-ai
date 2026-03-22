package com.westflow.processdef.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.processdef.api.ProcessDefinitionListItemResponse;
import com.westflow.processdef.api.PublishProcessDefinitionResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProcessDefinitionService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "category");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of(
            "createdAt",
            "version",
            "processName",
            "processKey",
            "category"
    );

    private final ProcessDslValidator processDslValidator;
    private final ProcessDslToBpmnService processDslToBpmnService;
    private final Map<String, AtomicInteger> versionsByProcessKey = new ConcurrentHashMap<>();
    private final Map<String, PublishedProcessDefinition> definitionsById = new ConcurrentHashMap<>();
    private final Map<String, PublishedProcessDefinition> latestDefinitionsByKey = new ConcurrentHashMap<>();
    private final AtomicLong publicationSequence = new AtomicLong();

    public ProcessDefinitionService(
            ProcessDslValidator processDslValidator,
            ProcessDslToBpmnService processDslToBpmnService
    ) {
        this.processDslValidator = processDslValidator;
        this.processDslToBpmnService = processDslToBpmnService;
    }

    public synchronized PublishProcessDefinitionResponse publish(ProcessDslPayload payload) {
        processDslValidator.validate(payload);

        int version = versionsByProcessKey
                .computeIfAbsent(payload.processKey(), ignored -> new AtomicInteger())
                .incrementAndGet();
        String processDefinitionId = payload.processKey() + ":" + version;
        String bpmnXml = processDslToBpmnService.convert(payload, processDefinitionId, version);
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))
                .plusNanos(publicationSequence.getAndIncrement());

        PublishedProcessDefinition published = new PublishedProcessDefinition(
                processDefinitionId,
                payload.processKey(),
                payload.processName(),
                payload.category(),
                version,
                STATUS_PUBLISHED,
                createdAt,
                payload,
                bpmnXml
        );

        definitionsById.put(processDefinitionId, published);
        latestDefinitionsByKey.put(payload.processKey(), published);

        return new PublishProcessDefinitionResponse(processDefinitionId, payload.processKey(), version, bpmnXml);
    }

    public PublishedProcessDefinition getLatestByProcessKey(String processKey) {
        PublishedProcessDefinition definition = latestDefinitionsByKey.get(processKey);
        if (definition == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程定义不存在",
                    Map.of("processKey", processKey)
            );
        }
        return definition;
    }

    public PublishedProcessDefinition getById(String processDefinitionId) {
        PublishedProcessDefinition definition = definitionsById.get(processDefinitionId);
        if (definition == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程定义不存在",
                    Map.of("processDefinitionId", processDefinitionId)
            );
        }
        return definition;
    }

    public PageResponse<ProcessDefinitionListItemResponse> page(PageRequest request) {
        List<PublishedProcessDefinition> filtered = definitionsById.values().stream()
                .filter(definition -> matchesKeyword(definition, request.keyword()))
                .filter(definition -> matchesFilters(definition, request.filters()))
                .sorted(buildComparator(request.sorts()))
                .toList();

        long total = filtered.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long start = (long) (request.page() - 1) * pageSize;
        if (start >= total) {
            return new PageResponse<>(request.page(), pageSize, total, pages, List.of(), List.of());
        }

        int fromIndex = Math.toIntExact(start);
        int toIndex = (int) Math.min(total, start + pageSize);
        List<ProcessDefinitionListItemResponse> records = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toListItem)
                .toList();

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private boolean matchesKeyword(PublishedProcessDefinition definition, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = keyword.trim().toLowerCase();
        return containsIgnoreCase(definition.processDefinitionId(), normalizedKeyword)
                || containsIgnoreCase(definition.processKey(), normalizedKeyword)
                || containsIgnoreCase(definition.processName(), normalizedKeyword)
                || containsIgnoreCase(definition.category(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private boolean matchesFilters(PublishedProcessDefinition definition, List<FilterItem> filters) {
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupportedField("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }

            if ("status".equals(filter.field()) && !matchesStringFilter(definition.status(), filter)) {
                return false;
            }

            if ("category".equals(filter.field()) && !matchesStringFilter(definition.category(), filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesStringFilter(String actualValue, FilterItem filter) {
        String operator = filter.operator() == null ? "eq" : filter.operator().trim().toLowerCase();
        List<String> values = extractFilterValues(filter.value());

        return switch (operator) {
            case "eq" -> values.stream().anyMatch(value -> equalsIgnoreCase(actualValue, value));
            case "ne" -> values.isEmpty() || values.stream().noneMatch(value -> equalsIgnoreCase(actualValue, value));
            case "in" -> values.stream().anyMatch(value -> equalsIgnoreCase(actualValue, value));
            case "not_in" -> values.stream().noneMatch(value -> equalsIgnoreCase(actualValue, value));
            case "like" -> values.stream().anyMatch(value -> containsIgnoreCase(actualValue, normalizeKeyword(value)));
            case "prefix_like" -> values.stream().anyMatch(value -> startsWithIgnoreCase(actualValue, normalizeKeyword(value)));
            case "suffix_like" -> values.stream().anyMatch(value -> endsWithIgnoreCase(actualValue, normalizeKeyword(value)));
            case "is_null" -> actualValue == null || actualValue.isBlank();
            case "is_not_null" -> actualValue != null && !actualValue.isBlank();
            default -> throw unsupportedField("不支持的筛选操作符", operator, List.of("eq", "ne", "in", "not_in", "like", "prefix_like", "suffix_like", "is_null", "is_not_null"));
        };
    }

    private List<String> extractFilterValues(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            return StreamSupport.stream(value.spliterator(), false)
                    .map(JsonNode::asText)
                    .toList();
        }
        return List.of(value.asText());
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null && value.toLowerCase().startsWith(prefix.toLowerCase());
    }

    private boolean endsWithIgnoreCase(String value, String suffix) {
        return value != null && suffix != null && value.toLowerCase().endsWith(suffix.toLowerCase());
    }

    private String normalizeKeyword(String value) {
        return value == null ? null : value.trim();
    }

    private Comparator<PublishedProcessDefinition> buildComparator(List<SortItem> sorts) {
        Comparator<PublishedProcessDefinition> comparator;
        if (sorts == null || sorts.isEmpty()) {
            comparator = Comparator.comparing(PublishedProcessDefinition::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PublishedProcessDefinition::version, Comparator.reverseOrder())
                    .thenComparing(PublishedProcessDefinition::processDefinitionId, Comparator.reverseOrder());
            return comparator;
        }

        comparator = null;
        for (SortItem sort : sorts) {
            if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
                throw unsupportedField("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
            }
            Comparator<PublishedProcessDefinition> fieldComparator = comparatorFor(sort.field(), sort.direction());
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        Comparator<PublishedProcessDefinition> fallback = Comparator
                .comparing(PublishedProcessDefinition::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublishedProcessDefinition::version, Comparator.reverseOrder())
                .thenComparing(PublishedProcessDefinition::processDefinitionId, Comparator.reverseOrder());

        return comparator.thenComparing(fallback);
    }

    private Comparator<PublishedProcessDefinition> comparatorFor(String field, String direction) {
        boolean desc = direction == null || direction.isBlank() || "desc".equalsIgnoreCase(direction);
        Comparator<PublishedProcessDefinition> comparator = switch (field) {
            case "createdAt" -> Comparator.comparing(
                    PublishedProcessDefinition::createdAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "version" -> Comparator.comparingInt(PublishedProcessDefinition::version);
            case "processName" -> Comparator.comparing(
                    PublishedProcessDefinition::processName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "processKey" -> Comparator.comparing(
                    PublishedProcessDefinition::processKey,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "category" -> Comparator.comparing(
                    PublishedProcessDefinition::category,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            default -> throw unsupportedField("不支持的排序字段", field, SUPPORTED_SORT_FIELDS);
        };
        return desc ? comparator.reversed() : comparator;
    }

    private ProcessDefinitionListItemResponse toListItem(PublishedProcessDefinition definition) {
        return new ProcessDefinitionListItemResponse(
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                definition.category(),
                definition.version(),
                definition.status(),
                definition.createdAt()
        );
    }

    private ContractException unsupportedField(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }
}
