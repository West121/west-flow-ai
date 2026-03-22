package com.westflow.processdef.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.processdef.api.ProcessDefinitionDetailResponse;
import com.westflow.processdef.api.ProcessDefinitionListItemResponse;
import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDefinitionRecord;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessDefinitionService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String DRAFT_PROCESS_DEFINITION_ID_SUFFIX = ":draft";
    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "category");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of(
            "createdAt",
            "version",
            "processName",
            "processKey",
            "category"
    );

    private final ProcessDefinitionMapper processDefinitionMapper;
    private final ProcessDslValidator processDslValidator;
    private final ProcessDslToBpmnService processDslToBpmnService;
    private final ObjectMapper objectMapper;

    public ProcessDefinitionService(
            ProcessDefinitionMapper processDefinitionMapper,
            ProcessDslValidator processDslValidator,
            ProcessDslToBpmnService processDslToBpmnService,
            ObjectMapper objectMapper
    ) {
        this.processDefinitionMapper = processDefinitionMapper;
        this.processDslValidator = processDslValidator;
        this.processDslToBpmnService = processDslToBpmnService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized ProcessDefinitionDetailResponse saveDraft(ProcessDslPayload payload) {
        String processDefinitionId = draftProcessDefinitionId(payload.processKey());
        LocalDateTime now = now();
        ProcessDefinitionRecord existingDraft = processDefinitionMapper.selectDraftByProcessKey(payload.processKey());

        ProcessDefinitionRecord draftRecord = new ProcessDefinitionRecord(
                processDefinitionId,
                payload.processKey(),
                payload.processName(),
                normalizeCategory(payload.category()),
                draftVersion(),
                STATUS_DRAFT,
                serializeDsl(payload),
                "",
                existingDraft == null ? now : existingDraft.createdAt(),
                now
        );

        if (existingDraft == null) {
            processDefinitionMapper.insertDefinition(draftRecord);
        } else {
            processDefinitionMapper.updateDefinition(draftRecord);
        }

        return toDetailResponse(draftRecord, payload);
    }

    @Transactional
    public synchronized ProcessDefinitionDetailResponse publish(ProcessDslPayload payload) {
        processDslValidator.validate(payload);

        int version = nextPublishedVersion(payload.processKey());
        String processDefinitionId = publishProcessDefinitionId(payload.processKey(), version);
        String bpmnXml = processDslToBpmnService.convert(payload, processDefinitionId, version);
        LocalDateTime now = now();

        ProcessDefinitionRecord publishedRecord = new ProcessDefinitionRecord(
                processDefinitionId,
                payload.processKey(),
                payload.processName(),
                normalizeCategory(payload.category()),
                version,
                STATUS_PUBLISHED,
                serializeDsl(payload),
                bpmnXml,
                now,
                now
        );

        processDefinitionMapper.insertDefinition(publishedRecord);
        return toDetailResponse(publishedRecord, payload);
    }

    public ProcessDefinitionDetailResponse detail(String processDefinitionId) {
        return toDetailResponse(getRecordById(processDefinitionId));
    }

    public PublishedProcessDefinition getLatestByProcessKey(String processKey) {
        ProcessDefinitionRecord record = processDefinitionMapper.selectLatestPublishedByProcessKey(processKey);
        if (record == null) {
            throw resourceNotFound("processKey", processKey);
        }
        return toPublishedProcessDefinition(record);
    }

    public PublishedProcessDefinition getById(String processDefinitionId) {
        return toPublishedProcessDefinition(getRecordById(processDefinitionId));
    }

    public PageResponse<ProcessDefinitionListItemResponse> page(PageRequest request) {
        QueryCriteria criteria = resolveCriteria(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = processDefinitionMapper.countPage(request.keyword(), criteria.status(), criteria.category());
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<ProcessDefinitionListItemResponse> records = total == 0
                ? List.of()
                : processDefinitionMapper.selectPage(
                        request.keyword(),
                        criteria.status(),
                        criteria.category(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                ).stream().map(this::toListItem).toList();

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private ProcessDefinitionRecord getRecordById(String processDefinitionId) {
        ProcessDefinitionRecord record = processDefinitionMapper.selectById(processDefinitionId);
        if (record == null) {
            throw resourceNotFound("processDefinitionId", processDefinitionId);
        }
        return record;
    }

    private ProcessDefinitionDetailResponse toDetailResponse(ProcessDefinitionRecord record) {
        return toDetailResponse(record, deserializeDsl(record.dslJson()));
    }

    private ProcessDefinitionDetailResponse toDetailResponse(
            ProcessDefinitionRecord record,
            ProcessDslPayload payload
    ) {
        return new ProcessDefinitionDetailResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                toOffsetDateTime(record.createdAt()),
                toOffsetDateTime(record.updatedAt()),
                payload,
                record.bpmnXml()
        );
    }

    private PublishedProcessDefinition toPublishedProcessDefinition(ProcessDefinitionRecord record) {
        return new PublishedProcessDefinition(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                toOffsetDateTime(record.createdAt()),
                deserializeDsl(record.dslJson()),
                record.bpmnXml()
        );
    }

    private ProcessDefinitionListItemResponse toListItem(ProcessDefinitionRecord record) {
        return new ProcessDefinitionListItemResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                toOffsetDateTime(record.createdAt())
        );
    }

    private QueryCriteria resolveCriteria(List<FilterItem> filters) {
        String status = null;
        String category = null;

        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupportedField("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupportedField("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }

            String value = filter.value() == null ? null : filter.value().asText();
            if ("status".equals(filter.field())) {
                status = normalizeNullable(value);
            } else if ("category".equals(filter.field())) {
                category = normalizeNullable(value);
            }
        }

        return new QueryCriteria(status, category);
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "created_at";
        }

        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupportedField("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }

        return switch (sort.field()) {
            case "createdAt" -> "created_at";
            case "version" -> "version";
            case "processName" -> "process_name";
            case "processKey" -> "process_key";
            case "category" -> "category";
            default -> throw unsupportedField("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        return "asc".equalsIgnoreCase(sorts.get(0).direction()) ? "ASC" : "DESC";
    }

    private int nextPublishedVersion(String processKey) {
        Integer currentMaxVersion = processDefinitionMapper.selectMaxVersionByProcessKey(processKey);
        return (currentMaxVersion == null ? 0 : currentMaxVersion) + 1;
    }

    private String serializeDsl(ProcessDslPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化流程 DSL", exception);
        }
    }

    private ProcessDslPayload deserializeDsl(String dslJson) {
        try {
            return objectMapper.readValue(dslJson, ProcessDslPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化流程 DSL", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }

    private String normalizeCategory(String category) {
        return normalizeNullable(category);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String draftProcessDefinitionId(String processKey) {
        return processKey + DRAFT_PROCESS_DEFINITION_ID_SUFFIX;
    }

    private String publishProcessDefinitionId(String processKey, int version) {
        return processKey + ":" + version;
    }

    private int draftVersion() {
        return 0;
    }

    private ContractException resourceNotFound(String field, String value) {
        return new ContractException(
                "BIZ.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "流程定义不存在",
                Map.of(field, value)
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

    private record QueryCriteria(
            String status,
            String category
    ) {
    }
}
