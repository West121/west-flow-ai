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
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 负责流程定义的草稿保存、发布、查询和分页检索。
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
    private final RepositoryService repositoryService;

    @Transactional
    public synchronized ProcessDefinitionDetailResponse saveDraft(ProcessDslPayload payload) {
        // 草稿只更新当前流程键对应的最后一个草稿版本。
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
        // 发布前先做 DSL 校验，再生成新的正式版本和 BPMN。
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
        deployToFlowable(publishedRecord);
        return toDetailResponse(publishedRecord, payload);
    }

    // 返回流程定义详情，包含 DSL 和 BPMN 片段。
    public ProcessDefinitionDetailResponse detail(String processDefinitionId) {
        return toDetailResponse(getRecordById(processDefinitionId));
    }

    // 按流程键获取最近一次已发布版本，供运行时启动流程使用。
    public PublishedProcessDefinition getLatestByProcessKey(String processKey) {
        ProcessDefinitionRecord record = processDefinitionMapper.selectLatestPublishedByProcessKey(processKey);
        if (record == null) {
            throw resourceNotFound("processKey", processKey);
        }
        return toPublishedProcessDefinition(record);
    }

    // 按主键获取已保存的流程定义，发布后和草稿态都适用。
    public PublishedProcessDefinition getById(String processDefinitionId) {
        return toPublishedProcessDefinition(getRecordById(processDefinitionId));
    }

    // 按关键字、状态和分类组合分页查询流程定义列表。
    public PageResponse<ProcessDefinitionListItemResponse> page(PageRequest request) {
        // 流程定义列表同时支持关键词、状态和分类筛选。
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

    // 根据主键读取流程定义，缺失时抛出资源不存在异常。
    private ProcessDefinitionRecord getRecordById(String processDefinitionId) {
        ProcessDefinitionRecord record = processDefinitionMapper.selectById(processDefinitionId);
        if (record == null) {
            throw resourceNotFound("processDefinitionId", processDefinitionId);
        }
        return record;
    }

    // 把数据库记录和 DSL 反序列化结果组装成详情返回值。
    private ProcessDefinitionDetailResponse toDetailResponse(ProcessDefinitionRecord record) {
        return toDetailResponse(record, deserializeDsl(record.dslJson()));
    }

    // 带入外部传入的 DSL 对象，避免重复反序列化。
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

    // 把数据库记录转换成运行时可直接使用的已发布定义对象。
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

    // 将已发布的 BPMN 同步部署到真实 Flowable 引擎。
    private void deployToFlowable(ProcessDefinitionRecord record) {
        repositoryService.createDeployment()
                .name(record.processDefinitionId())
                .key(record.processKey())
                .category(record.category())
                .addString(record.processKey() + ".bpmn20.xml", record.bpmnXml())
                .deploy();
    }

    // 组装列表页的单行摘要数据。
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

    // 解析分页筛选条件，当前只支持状态和分类。
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

    // 解析排序字段到数据库列名。
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

    // 解析排序方向，默认按时间倒序。
    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        return "asc".equalsIgnoreCase(sorts.get(0).direction()) ? "ASC" : "DESC";
    }

    // 计算下一个正式版本号，保证同一流程键下版本递增。
    private int nextPublishedVersion(String processKey) {
        Integer currentMaxVersion = processDefinitionMapper.selectMaxVersionByProcessKey(processKey);
        return (currentMaxVersion == null ? 0 : currentMaxVersion) + 1;
    }

    // 将 DSL 序列化后写入数据库。
    private String serializeDsl(ProcessDslPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化流程 DSL", exception);
        }
    }

    // 将数据库中的 DSL JSON 反序列化为对象。
    private ProcessDslPayload deserializeDsl(String dslJson) {
        try {
            return objectMapper.readValue(dslJson, ProcessDslPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化流程 DSL", exception);
        }
    }

    // 统一使用当前时区时间，避免数据库和业务时区不一致。
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    // 把本地时间转换为带时区的返回值。
    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }

    // 分类字段空值归一为 null，方便持久化和筛选。
    private String normalizeCategory(String category) {
        return normalizeNullable(category);
    }

    // 把空白字符串归一为 null，避免脏数据进入数据库。
    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 草稿定义使用固定后缀，便于同一流程键覆盖更新。
    private String draftProcessDefinitionId(String processKey) {
        return processKey + DRAFT_PROCESS_DEFINITION_ID_SUFFIX;
    }

    // 发布定义的主键包含版本号，便于历史追溯。
    private String publishProcessDefinitionId(String processKey, int version) {
        return processKey + ":" + version;
    }

    // 草稿版本固定为 0，和正式版本区分开。
    private int draftVersion() {
        return 0;
    }

    // 统一构造资源不存在异常。
    private ContractException resourceNotFound(String field, String value) {
        return new ContractException(
                "BIZ.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "流程定义不存在",
                Map.of(field, value)
        );
    }

    // 统一构造不支持字段异常，提示允许值。
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
