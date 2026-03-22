package com.westflow.workflowadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import com.westflow.workflowadmin.api.WorkflowOperationLogDetailResponse;
import com.westflow.workflowadmin.api.WorkflowOperationLogListItemResponse;
import com.westflow.workflowadmin.mapper.WorkflowOperationLogMapper;
import com.westflow.workflowadmin.model.WorkflowOperationLogRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 流程操作日志服务。
 */
@Service
@RequiredArgsConstructor
public class WorkflowOperationLogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WorkflowOperationLogMapper workflowOperationLogMapper;
    private final BusinessProcessLinkMapper businessProcessLinkMapper;
    private final IdentityAuthService fixtureAuthService;
    private final ObjectMapper objectMapper;

    /**
     * 记录流程平台动作日志，供后台查询和实例追踪复用。
     */
    public void record(RecordCommand command) {
        BusinessProcessLinkRecord link = command.processInstanceId() == null
                ? null
                : businessProcessLinkMapper.selectByProcessInstanceId(command.processInstanceId());
        workflowOperationLogMapper.insert(new WorkflowOperationLogRecord(
                buildId("wflog"),
                command.processInstanceId(),
                command.processDefinitionId() == null ? link == null ? null : link.processDefinitionId() : command.processDefinitionId(),
                command.flowableDefinitionId(),
                command.businessType() == null ? link == null ? null : link.businessType() : command.businessType(),
                command.businessId() == null ? link == null ? null : link.businessId() : command.businessId(),
                command.taskId(),
                command.nodeId(),
                command.actionType(),
                command.actionName(),
                command.actionCategory(),
                command.operatorUserId(),
                command.targetUserId(),
                command.sourceTaskId(),
                command.targetTaskId(),
                normalizeNullable(command.commentText()),
                serializeDetails(command.details()),
                command.createdAt() == null ? Instant.now() : command.createdAt()
        ));
    }

    /**
     * 分页查询流程操作日志。
     */
    public PageResponse<WorkflowOperationLogListItemResponse> page(PageRequest request) {
        ensureWorkflowAdminAccess();
        Filters filters = resolveFilters(request.filters());
        List<WorkflowOperationLogRecord> matched = workflowOperationLogMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.actionType() == null || filters.actionType().equalsIgnoreCase(record.actionType()))
                .filter(record -> filters.businessType() == null || filters.businessType().equalsIgnoreCase(record.businessType()))
                .sorted(Comparator.comparing(WorkflowOperationLogRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        return toPage(request, matched, this::toListItem);
    }

    /**
     * 读取单条流程操作日志详情。
     */
    public WorkflowOperationLogDetailResponse detail(String logId) {
        ensureWorkflowAdminAccess();
        WorkflowOperationLogRecord record = workflowOperationLogMapper.selectById(logId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程操作日志不存在",
                    Map.of("logId", logId)
            );
        }
        return new WorkflowOperationLogDetailResponse(
                record.logId(),
                record.processInstanceId(),
                record.processDefinitionId(),
                record.flowableDefinitionId(),
                record.businessType(),
                record.businessId(),
                record.taskId(),
                record.nodeId(),
                record.actionType(),
                record.actionName(),
                record.actionCategory(),
                record.operatorUserId(),
                record.targetUserId(),
                record.sourceTaskId(),
                record.targetTaskId(),
                record.commentText(),
                deserializeDetails(record.detailJson()),
                record.createdAt()
        );
    }

    private WorkflowOperationLogListItemResponse toListItem(WorkflowOperationLogRecord record) {
        return new WorkflowOperationLogListItemResponse(
                record.logId(),
                record.processInstanceId(),
                record.businessType(),
                record.businessId(),
                record.actionType(),
                record.actionName(),
                record.actionCategory(),
                record.operatorUserId(),
                record.targetUserId(),
                record.createdAt()
        );
    }

    private boolean matchesKeyword(WorkflowOperationLogRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(record.logId(), normalized)
                || contains(record.processInstanceId(), normalized)
                || contains(record.businessType(), normalized)
                || contains(record.businessId(), normalized)
                || contains(record.actionType(), normalized)
                || contains(record.actionName(), normalized)
                || contains(record.operatorUserId(), normalized)
                || contains(record.targetUserId(), normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String actionType = null;
        String businessType = null;
        for (FilterItem filter : filters) {
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                continue;
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("actionType".equals(filter.field())) {
                actionType = normalizeNullable(value);
            }
            if ("businessType".equals(filter.field())) {
                businessType = normalizeNullable(value);
            }
        }
        return new Filters(actionType, businessType);
    }

    private <T> PageResponse<T> toPage(PageRequest request, List<WorkflowOperationLogRecord> matched, java.util.function.Function<WorkflowOperationLogRecord, T> mapper) {
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<T> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream().map(mapper).toList();
        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private String serializeDetails(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化流程操作日志明细", exception);
        }
    }

    private Map<String, Object> deserializeDetails(String detailJson) {
        try {
            return detailJson == null || detailJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(detailJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化流程操作日志明细", exception);
        }
    }

    private void ensureWorkflowAdminAccess() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        if (!fixtureAuthService.isProcessAdmin(userId) && !fixtureAuthService.isSystemAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问流程操作日志",
                    Map.of("userId", userId)
            );
        }
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private record Filters(String actionType, String businessType) {
    }

    /**
     * 写日志命令。
     */
    public record RecordCommand(
            String processInstanceId,
            String processDefinitionId,
            String flowableDefinitionId,
            String businessType,
            String businessId,
            String taskId,
            String nodeId,
            String actionType,
            String actionName,
            String actionCategory,
            String operatorUserId,
            String targetUserId,
            String sourceTaskId,
            String targetTaskId,
            String commentText,
            Map<String, Object> details,
            Instant createdAt
    ) {
    }
}
