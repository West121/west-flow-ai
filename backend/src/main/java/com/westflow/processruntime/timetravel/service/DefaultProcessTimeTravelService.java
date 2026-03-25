package com.westflow.processruntime.timetravel.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.ExecuteProcessTimeTravelRequest;
import com.westflow.processruntime.api.response.ProcessTimeTravelExecutionResponse;
import com.westflow.processruntime.api.request.ProcessTimeTravelQueryRequest;
import com.westflow.processruntime.model.ProcessRuntimeSpecialPermissions;
import com.westflow.processruntime.timetravel.model.ProcessTimeTravelExecutionRecord;
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
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 穿越时空执行服务实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class DefaultProcessTimeTravelService implements ProcessTimeTravelService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTION_CATEGORY = "TIME_TRAVEL";
    private static final String ACTION_BACK = "TIME_TRAVEL_BACK_TO_NODE";
    private static final String ACTION_REOPEN = "TIME_TRAVEL_REOPEN_INSTANCE";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final FlowableEngineFacade flowableEngineFacade;
    private final WorkflowOperationLogMapper workflowOperationLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProcessTimeTravelExecutionResponse execute(ExecuteProcessTimeTravelRequest request) {
        String instanceId = normalize(request.instanceId());
        if (instanceId == null) {
            throw invalid("instanceId 不能为空", Map.of());
        }
        String strategy = normalizeStrategy(request.strategy());
        String operatorUserId = currentUserId();
        String executionId = buildId("tt");
        Instant occurredAt = Instant.now();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("permissionCode", ProcessRuntimeSpecialPermissions.TIME_TRAVEL_EXECUTE);
        details.put("strategy", strategy);
        details.put("reason", normalize(request.reason()));
        details.put("taskId", normalize(request.taskId()));
        details.put("targetNodeId", normalize(request.targetNodeId()));

        ProcessTimeTravelExecutionRecord executionRecord;
        if ("BACK_TO_NODE".equals(strategy)) {
            executionRecord = executeBackToNode(executionId, instanceId, operatorUserId, occurredAt, request, details);
        } else if ("REOPEN_INSTANCE".equals(strategy)) {
            executionRecord = executeReopenInstance(executionId, instanceId, operatorUserId, occurredAt, request, details);
        } else {
            throw invalid("不支持的穿越时空策略", Map.of("strategy", strategy));
        }

        workflowOperationLogMapper.insert(new WorkflowOperationLogRecord(
                executionRecord.executionId(),
                executionRecord.instanceId(),
                resolveProcessDefinitionId(executionRecord.instanceId()),
                resolveProcessDefinitionId(executionRecord.instanceId()),
                resolveBusinessType(executionRecord.instanceId()),
                resolveBusinessId(executionRecord.instanceId()),
                executionRecord.taskId(),
                executionRecord.targetNodeId(),
                executionRecord.actionType(),
                strategyName(executionRecord.strategy()),
                executionRecord.actionCategory(),
                executionRecord.operatorUserId(),
                null,
                executionRecord.taskId(),
                executionRecord.targetTaskId(),
                normalize(request.reason()),
                toJson(executionRecord.details()),
                occurredAt
        ));

        return toResponse(executionRecord);
    }

    @Override
    public PageResponse<ProcessTimeTravelExecutionResponse> page(ProcessTimeTravelQueryRequest request) {
        List<ProcessTimeTravelExecutionResponse> matched = loadAll().stream()
                .filter(item -> request.instanceId() == null || request.instanceId().isBlank() || request.instanceId().equals(item.instanceId()))
                .filter(item -> request.strategy() == null || request.strategy().isBlank() || request.strategy().equalsIgnoreCase(item.strategy()))
                .filter(item -> matchesKeyword(item, request.keyword()))
                .sorted(Comparator.comparing(ProcessTimeTravelExecutionResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        return toPage(matched, request.page(), request.pageSize());
    }

    @Override
    public List<ProcessTimeTravelExecutionResponse> trace(String instanceId) {
        return loadAll().stream()
                .filter(item -> instanceId.equals(item.instanceId()))
                .sorted(Comparator.comparing(ProcessTimeTravelExecutionResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private ProcessTimeTravelExecutionRecord executeBackToNode(
            String executionId,
            String instanceId,
            String operatorUserId,
            Instant occurredAt,
            ExecuteProcessTimeTravelRequest request,
            Map<String, Object> details
    ) {
        Task task = resolveTargetTask(instanceId, request.taskId());
        String targetNodeId = normalize(request.targetNodeId());
        if (targetNodeId == null) {
            throw invalid("BACK_TO_NODE 策略需要 targetNodeId", Map.of("instanceId", instanceId));
        }
        flowableEngineFacade.runtimeService()
                .createChangeActivityStateBuilder()
                .processInstanceId(instanceId)
                .moveExecutionToActivityId(task.getExecutionId(), targetNodeId)
                .changeState();
        details.put("targetTaskId", task.getId());
        details.put("sourceExecutionId", task.getExecutionId());
        details.put("newActiveTaskIds", resolveActiveTaskIds(instanceId));
        details.put("executed", true);
        return new ProcessTimeTravelExecutionRecord(
                executionId,
                instanceId,
                "BACK_TO_NODE",
                task.getId(),
                targetNodeId,
                task.getId(),
                instanceId,
                ProcessRuntimeSpecialPermissions.TIME_TRAVEL_EXECUTE,
                ACTION_BACK,
                ACTION_CATEGORY,
                operatorUserId,
                occurredAt,
                details
        );
    }

    private ProcessTimeTravelExecutionRecord executeReopenInstance(
            String executionId,
            String instanceId,
            String operatorUserId,
            Instant occurredAt,
            ExecuteProcessTimeTravelRequest request,
            Map<String, Object> details
    ) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (runtimeInstance != null) {
            throw invalid("REOPEN_INSTANCE 仅支持已结束实例", Map.of("instanceId", instanceId));
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (historicInstance == null) {
            throw notFound("流程实例不存在", Map.of("instanceId", instanceId));
        }
        Map<String, Object> variables = normalizeVariables(request.variables());
        ProcessInstance reopenedInstance = flowableEngineFacade.runtimeService().startProcessInstanceById(
                historicInstance.getProcessDefinitionId(),
                historicInstance.getBusinessKey(),
                variables
        );
        details.put("newInstanceId", reopenedInstance.getProcessInstanceId());
        details.put("reopenedFromProcessDefinitionId", historicInstance.getProcessDefinitionId());
        details.put("reopenedBusinessKey", historicInstance.getBusinessKey());
        details.put("executed", true);
        return new ProcessTimeTravelExecutionRecord(
                executionId,
                instanceId,
                "REOPEN_INSTANCE",
                null,
                null,
                null,
                reopenedInstance.getProcessInstanceId(),
                ProcessRuntimeSpecialPermissions.TIME_TRAVEL_EXECUTE,
                ACTION_REOPEN,
                ACTION_CATEGORY,
                operatorUserId,
                occurredAt,
                details
        );
    }

    private List<ProcessTimeTravelExecutionResponse> loadAll() {
        return workflowOperationLogMapper.selectAll().stream()
                .filter(record -> ACTION_CATEGORY.equalsIgnoreCase(record.actionCategory()))
                .map(this::toResponse)
                .toList();
    }

    private ProcessTimeTravelExecutionResponse toResponse(WorkflowOperationLogRecord record) {
        Map<String, Object> details = deserialize(record.detailJson());
        return new ProcessTimeTravelExecutionResponse(
                record.logId(),
                record.processInstanceId(),
                normalizeStrategy(stringValue(details.get("strategy"), record.actionType())),
                record.taskId(),
                stringValue(details.get("targetNodeId"), record.nodeId()),
                stringValue(details.get("targetTaskId"), record.targetTaskId()),
                stringValue(details.get("newInstanceId"), null),
                ProcessRuntimeSpecialPermissions.TIME_TRAVEL_VIEW,
                record.actionType(),
                record.actionCategory(),
                record.operatorUserId(),
                toDateTime(record.createdAt()),
                details
        );
    }

    private ProcessTimeTravelExecutionResponse toResponse(ProcessTimeTravelExecutionRecord record) {
        return new ProcessTimeTravelExecutionResponse(
                record.executionId(),
                record.instanceId(),
                record.strategy(),
                record.taskId(),
                record.targetNodeId(),
                record.targetTaskId(),
                record.newInstanceId(),
                record.permissionCode(),
                record.actionType(),
                record.actionCategory(),
                record.operatorUserId(),
                toDateTime(record.occurredAt()),
                record.details()
        );
    }

    private boolean matchesKeyword(ProcessTimeTravelExecutionResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.executionId(), normalized)
                || contains(item.instanceId(), normalized)
                || contains(item.strategy(), normalized)
                || contains(item.taskId(), normalized)
                || contains(item.targetNodeId(), normalized)
                || contains(item.targetTaskId(), normalized)
                || contains(item.newInstanceId(), normalized)
                || contains(item.operatorUserId(), normalized)
                || contains(item.actionType(), normalized)
                || contains(item.actionCategory(), normalized)
                || contains(detailText(item.details()), normalized);
    }

    private List<String> resolveActiveTaskIds(String instanceId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instanceId)
                .active()
                .list()
                .stream()
                .map(Task::getId)
                .toList();
    }

    private Task resolveTargetTask(String instanceId, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            Task task = flowableEngineFacade.taskService().createTaskQuery().taskId(taskId).singleResult();
            if (task == null || !instanceId.equals(task.getProcessInstanceId())) {
                throw notFound("任务不存在或不属于当前实例", Map.of("instanceId", instanceId, "taskId", taskId));
            }
            return task;
        }
        List<Task> tasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instanceId)
                .active()
                .list();
        if (tasks.isEmpty()) {
            throw invalid("当前实例不存在可回退的活动任务", Map.of("instanceId", instanceId));
        }
        if (tasks.size() > 1) {
            throw invalid("当前实例存在多个活动任务，请指定 taskId", Map.of("instanceId", instanceId, "activeTaskCount", tasks.size()));
        }
        return tasks.get(0);
    }

    private String resolveProcessDefinitionId(String instanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionId();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        return historicInstance == null ? null : historicInstance.getProcessDefinitionId();
    }

    private String resolveBusinessType(String instanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionKey();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        return historicInstance == null ? null : historicInstance.getProcessDefinitionKey();
    }

    private String resolveBusinessId(String instanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getBusinessKey();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        return historicInstance == null ? null : historicInstance.getBusinessKey();
    }

    private PageResponse<ProcessTimeTravelExecutionResponse> toPage(List<ProcessTimeTravelExecutionResponse> records, int page, int pageSize) {
        long total = records.size();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        int toIndex = Math.min(records.size(), fromIndex + pageSize);
        List<ProcessTimeTravelExecutionResponse> pageRecords = fromIndex >= records.size()
                ? List.of()
                : records.subList(fromIndex, toIndex);
        return new PageResponse<>(page, pageSize, total, pages, pageRecords, List.of());
    }

    private Map<String, Object> normalizeVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(variables);
    }

    private String normalizeStrategy(String strategy) {
        String normalized = normalize(strategy);
        return normalized == null ? "BACK_TO_NODE" : normalized.toUpperCase();
    }

    private String strategyName(String strategy) {
        return switch (strategy) {
            case "BACK_TO_NODE" -> "回退到节点";
            case "REOPEN_INSTANCE" -> "重开实例";
            default -> strategy;
        };
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private String detailText(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ";" + right)
                .orElse(null);
    }

    private Map<String, Object> deserialize(String detailJson) {
        try {
            return detailJson == null || detailJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(detailJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化穿越时空详情", exception);
        }
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化穿越时空详情", exception);
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? fallback : stringValue;
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

    private OffsetDateTime toDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, TIME_ZONE);
    }

    private ContractException invalid(String message, Map<String, Object> details) {
        return new ContractException("PROCESS.ACTION_NOT_ALLOWED", HttpStatus.UNPROCESSABLE_ENTITY, message, details);
    }

    private ContractException notFound(String message, Map<String, Object> details) {
        return new ContractException("PROCESS.RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message, details);
    }
}
