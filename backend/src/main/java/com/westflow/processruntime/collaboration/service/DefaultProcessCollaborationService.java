package com.westflow.processruntime.collaboration.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.collaboration.api.CreateProcessCollaborationEventRequest;
import com.westflow.processruntime.collaboration.api.ProcessCollaborationEventResponse;
import com.westflow.processruntime.collaboration.api.ProcessCollaborationQueryRequest;
import com.westflow.processruntime.collaboration.model.ProcessCollaborationEventRecord;
import com.westflow.processruntime.model.ProcessRuntimeSpecialPermissions;
import com.westflow.workflowadmin.mapper.WorkflowOperationLogMapper;
import com.westflow.workflowadmin.model.WorkflowOperationLogRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协同事件服务实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class DefaultProcessCollaborationService implements ProcessCollaborationService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTION_CATEGORY = "COLLABORATION";
    private static final String ACTION_CREATE = "COLLABORATION_EVENT_CREATED";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final FlowableEngineFacade flowableEngineFacade;
    private final WorkflowOperationLogMapper workflowOperationLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProcessCollaborationEventResponse createEvent(CreateProcessCollaborationEventRequest request) {
        String instanceId = normalize(request.instanceId());
        String taskId = normalize(request.taskId());
        if (instanceId == null && taskId == null) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "instanceId 与 taskId 至少需要一个",
                    Map.of()
            );
        }

        Task task = taskId == null ? null : requireTask(taskId);
        if (instanceId == null && task != null) {
            instanceId = task.getProcessInstanceId();
        }
        if (instanceId != null && task != null && !instanceId.equals(task.getProcessInstanceId())) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "taskId 不属于指定实例",
                    Map.of("instanceId", instanceId, "taskId", taskId)
            );
        }
        requireProcessInstance(instanceId);

        String operatorUserId = currentUserId();
        String eventId = buildId("col");
        Instant createdAt = Instant.now();
        List<String> mentionedUserIds = normalizeList(request.mentionedUserIds());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("permissionCode", ProcessRuntimeSpecialPermissions.COLLABORATION_CREATE);
        details.put("eventType", normalizeEventType(request.eventType()));
        details.put("subject", normalizeText(request.subject()));
        details.put("content", normalizeText(request.content()));
        details.put("mentionedUserIds", mentionedUserIds);
        details.put("taskResolved", task != null);
        if (task != null) {
            details.put("taskDefinitionKey", task.getTaskDefinitionKey());
            details.put("taskName", task.getName());
        }

        workflowOperationLogMapper.insert(new WorkflowOperationLogRecord(
                eventId,
                instanceId,
                task == null ? null : task.getProcessDefinitionId(),
                task == null ? null : task.getProcessDefinitionId(),
                resolveBusinessType(instanceId),
                resolveBusinessId(instanceId),
                taskId,
                task == null ? null : task.getTaskDefinitionKey(),
                ACTION_CREATE,
                "创建协同事件",
                ACTION_CATEGORY,
                operatorUserId,
                mentionedUserIds.isEmpty() ? null : mentionedUserIds.get(0),
                taskId,
                taskId,
                normalizeText(request.content()),
                toJson(details),
                createdAt
        ));

        return toResponse(new ProcessCollaborationEventRecord(
                eventId,
                instanceId,
                taskId,
                normalizeEventType(request.eventType()),
                normalizeText(request.subject()),
                normalizeText(request.content()),
                mentionedUserIds,
                ProcessRuntimeSpecialPermissions.COLLABORATION_CREATE,
                ACTION_CREATE,
                ACTION_CATEGORY,
                operatorUserId,
                createdAt,
                details
        ));
    }

    @Override
    public PageResponse<ProcessCollaborationEventResponse> page(ProcessCollaborationQueryRequest request) {
        List<ProcessCollaborationEventResponse> matched = loadAll().stream()
                .filter(item -> request.instanceId() == null || request.instanceId().isBlank() || request.instanceId().equals(item.instanceId()))
                .filter(item -> request.taskId() == null || request.taskId().isBlank() || request.taskId().equals(item.taskId()))
                .filter(item -> request.eventType() == null || request.eventType().isBlank() || request.eventType().equalsIgnoreCase(item.eventType()))
                .filter(item -> matchesKeyword(item, request.keyword()))
                .sorted(Comparator.comparing(ProcessCollaborationEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        return toPage(matched, request.page(), request.pageSize());
    }

    @Override
    public List<ProcessCollaborationEventResponse> trace(String instanceId) {
        return loadAll().stream()
                .filter(item -> instanceId.equals(item.instanceId()))
                .sorted(Comparator.comparing(ProcessCollaborationEventResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<ProcessCollaborationEventResponse> loadAll() {
        return workflowOperationLogMapper.selectAll().stream()
                .filter(record -> ACTION_CATEGORY.equalsIgnoreCase(record.actionCategory()))
                .map(this::toResponse)
                .toList();
    }

    private ProcessCollaborationEventResponse toResponse(WorkflowOperationLogRecord record) {
        Map<String, Object> details = deserialize(record.detailJson());
        return new ProcessCollaborationEventResponse(
                record.logId(),
                record.processInstanceId(),
                record.taskId(),
                stringValue(details.get("eventType"), record.actionType()),
                stringValue(details.get("subject"), record.actionName()),
                stringValue(details.get("content"), record.commentText()),
                toStringList(details.get("mentionedUserIds")),
                ProcessRuntimeSpecialPermissions.COLLABORATION_VIEW,
                record.actionType(),
                record.actionCategory(),
                record.operatorUserId(),
                toDateTime(record.createdAt()),
                details
        );
    }

    private ProcessCollaborationEventResponse toResponse(ProcessCollaborationEventRecord record) {
        return new ProcessCollaborationEventResponse(
                record.eventId(),
                record.instanceId(),
                record.taskId(),
                record.eventType(),
                record.subject(),
                record.content(),
                record.mentionedUserIds(),
                record.permissionCode(),
                record.actionType(),
                record.actionCategory(),
                record.operatorUserId(),
                toDateTime(record.occurredAt()),
                record.details()
        );
    }

    private boolean matchesKeyword(ProcessCollaborationEventResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.eventId(), normalized)
                || contains(item.instanceId(), normalized)
                || contains(item.taskId(), normalized)
                || contains(item.eventType(), normalized)
                || contains(item.subject(), normalized)
                || contains(item.content(), normalized)
                || contains(item.operatorUserId(), normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private PageResponse<ProcessCollaborationEventResponse> toPage(List<ProcessCollaborationEventResponse> records, int page, int pageSize) {
        long total = records.size();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        int toIndex = Math.min(records.size(), fromIndex + pageSize);
        List<ProcessCollaborationEventResponse> pageRecords = fromIndex >= records.size()
                ? List.of()
                : records.subList(fromIndex, toIndex);
        return new PageResponse<>(page, pageSize, total, pages, pageRecords, List.of());
    }

    private Task requireTask(String taskId) {
        Task task = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            return task;
        }
        throw new ContractException(
                "PROCESS.TASK_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "任务不存在",
                Map.of("taskId", taskId)
        );
    }

    private void requireProcessInstance(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new ContractException(
                    "PROCESS.ACTION_NOT_ALLOWED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "instanceId 不能为空",
                    Map.of()
            );
        }
        boolean runtimeExists = flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(instanceId).count() > 0;
        boolean historicExists = flowableEngineFacade.historyService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).count() > 0;
        if (!runtimeExists && !historicExists) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程实例不存在",
                    Map.of("instanceId", instanceId)
            );
        }
    }

    private String resolveBusinessType(String instanceId) {
        return resolveProcessInstanceBusinessValue(instanceId, true);
    }

    private String resolveBusinessId(String instanceId) {
        return resolveProcessInstanceBusinessValue(instanceId, false);
    }

    private String resolveProcessInstanceBusinessValue(String instanceId, boolean businessType) {
        var runtimeInstance = flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (runtimeInstance != null) {
            return businessType ? stringValue(runtimeInstance.getProcessDefinitionKey()) : runtimeInstance.getBusinessKey();
        }
        var historicInstance = flowableEngineFacade.historyService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (historicInstance != null) {
            return businessType ? stringValue(historicInstance.getProcessDefinitionKey()) : historicInstance.getBusinessKey();
        }
        return null;
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String stringValue(String value) {
        return normalizeText(value);
    }

    private String normalizeEventType(String eventType) {
        return normalizeText(eventType == null ? "COMMENT" : eventType).toUpperCase();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> deserialize(String detailJson) {
        try {
            return detailJson == null || detailJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(detailJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化协同事件详情", exception);
        }
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化协同事件详情", exception);
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? fallback : stringValue;
    }

    private String stringValue(Object value) {
        return stringValue(value, null);
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private OffsetDateTime toDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, TIME_ZONE);
    }

}
