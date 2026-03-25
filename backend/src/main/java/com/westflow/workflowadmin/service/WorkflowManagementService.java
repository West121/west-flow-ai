package com.westflow.workflowadmin.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDefinitionRecord;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.workflowadmin.api.response.WorkflowInstanceDetailResponse;
import com.westflow.workflowadmin.api.response.WorkflowInstanceListItemResponse;
import com.westflow.workflowadmin.api.response.WorkflowOperationLogDetailResponse;
import com.westflow.workflowadmin.api.response.WorkflowOperationLogListItemResponse;
import com.westflow.workflowadmin.api.response.WorkflowPublishRecordDetailResponse;
import com.westflow.workflowadmin.api.response.WorkflowPublishRecordListItemResponse;
import com.westflow.workflowadmin.api.response.WorkflowVersionDetailResponse;
import com.westflow.workflowadmin.api.response.WorkflowVersionListItemResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 流程管理后台查询服务。
 */
@Service
@RequiredArgsConstructor
public class WorkflowManagementService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProcessDefinitionMapper processDefinitionMapper;
    private final BusinessProcessLinkMapper businessProcessLinkMapper;
    private final WorkflowOperationLogService workflowOperationLogService;
    private final IdentityAuthService fixtureAuthService;
    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessLinkService processLinkService;
    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    public PageResponse<WorkflowVersionListItemResponse> pageVersions(PageRequest request) {
        ensureWorkflowAdminAccess();
        List<ProcessDefinitionRecord> published = processDefinitionMapper.selectAllPublished();
        List<WorkflowVersionListItemResponse> matched = published.stream()
                .filter(record -> matchesDefinitionKeyword(record, request.keyword()))
                .filter(record -> matchesCategoryFilter(record, request.filters()))
                .map(record -> toVersionListItem(record, published))
                .sorted(Comparator.comparing(WorkflowVersionListItemResponse::publishedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        return toPage(request, matched);
    }

    public WorkflowVersionDetailResponse versionDetail(String processDefinitionId) {
        ensureWorkflowAdminAccess();
        ProcessDefinitionRecord record = requirePublishedDefinition(processDefinitionId);
        return new WorkflowVersionDetailResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                record.deploymentId(),
                record.flowableDefinitionId(),
                record.publisherUserId(),
                toOffsetDateTime(record.createdAt()),
                toOffsetDateTime(record.updatedAt()),
                record.bpmnXml()
        );
    }

    public PageResponse<WorkflowPublishRecordListItemResponse> pagePublishRecords(PageRequest request) {
        ensureWorkflowAdminAccess();
        List<WorkflowPublishRecordListItemResponse> matched = processDefinitionMapper.selectAllPublished().stream()
                .filter(record -> matchesDefinitionKeyword(record, request.keyword()))
                .filter(record -> matchesCategoryFilter(record, request.filters()))
                .sorted(Comparator.comparing(ProcessDefinitionRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toPublishRecordListItem)
                .toList();
        return toPage(request, matched);
    }

    public WorkflowPublishRecordDetailResponse publishRecordDetail(String processDefinitionId) {
        ensureWorkflowAdminAccess();
        ProcessDefinitionRecord record = requirePublishedDefinition(processDefinitionId);
        return new WorkflowPublishRecordDetailResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.version(),
                record.category(),
                record.deploymentId(),
                record.flowableDefinitionId(),
                record.publisherUserId(),
                toOffsetDateTime(record.createdAt()),
                record.bpmnXml()
        );
    }

    public PageResponse<WorkflowInstanceListItemResponse> pageInstances(PageRequest request) {
        ensureWorkflowAdminAccess();
        Filters filters = resolveInstanceFilters(request.filters());
        List<HistoricProcessInstance> historicInstances = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .orderByProcessInstanceStartTime()
                .desc()
                .list();
        List<WorkflowInstanceListItemResponse> matched = historicInstances.stream()
                .map(this::toInstanceListItem)
                .filter(item -> item != null)
                .filter(item -> matchesInstanceKeyword(item, request.keyword()))
                .filter(item -> filters.businessType() == null || filters.businessType().equalsIgnoreCase(item.businessType()))
                .filter(item -> filters.status() == null || filters.status().equalsIgnoreCase(item.status()))
                .toList();
        return toPage(request, matched);
    }

    public WorkflowInstanceDetailResponse instanceDetail(String instanceId) {
        ensureWorkflowAdminAccess();
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (historicInstance == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程实例不存在",
                    Map.of("instanceId", instanceId)
            );
        }
        InstanceContext context = buildInstanceContext(historicInstance);
        List<ProcessInstanceLinkResponse> processLinks = processLinks(instanceId);
        return new WorkflowInstanceDetailResponse(
                instanceId,
                context.platformProcessDefinitionId(),
                historicInstance.getProcessDefinitionId(),
                context.processKey(),
                context.processName(),
                context.businessType(),
                context.businessId(),
                historicInstance.getStartUserId(),
                context.status(),
                context.suspended(),
                context.currentTaskNames(),
                toOffsetDateTime(historicInstance.getStartTime()),
                toOffsetDateTime(historicInstance.getEndTime()),
                context.variables(),
                flowableProcessRuntimeService.inclusiveGatewayHits(instanceId),
                processLinks,
                waitingParentConfirmLinks(processLinks),
                flowableProcessRuntimeService.appendLinks(instanceId)
        );
    }

    private List<ProcessInstanceLinkResponse> processLinks(String instanceId) {
        return flowableProcessRuntimeService.links(processLinkService.resolveRootInstanceId(instanceId));
    }

    private List<ProcessInstanceLinkResponse> waitingParentConfirmLinks(List<ProcessInstanceLinkResponse> processLinks) {
        return processLinks.stream()
                .filter(ProcessInstanceLinkResponse::parentConfirmationRequired)
                .toList();
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, TIME_ZONE);
    }

    public PageResponse<WorkflowOperationLogListItemResponse> pageOperationLogs(PageRequest request) {
        return workflowOperationLogService.page(request);
    }

    public WorkflowOperationLogDetailResponse operationLogDetail(String logId) {
        return workflowOperationLogService.detail(logId);
    }

    private WorkflowVersionListItemResponse toVersionListItem(
            ProcessDefinitionRecord record,
            List<ProcessDefinitionRecord> published
    ) {
        int maxVersion = published.stream()
                .filter(candidate -> candidate.processKey().equals(record.processKey()))
                .map(ProcessDefinitionRecord::version)
                .max(Integer::compareTo)
                .orElse(record.version());
        return new WorkflowVersionListItemResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                record.version() == maxVersion,
                record.deploymentId(),
                record.flowableDefinitionId(),
                record.publisherUserId(),
                toOffsetDateTime(record.createdAt())
        );
    }

    private WorkflowPublishRecordListItemResponse toPublishRecordListItem(ProcessDefinitionRecord record) {
        return new WorkflowPublishRecordListItemResponse(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.version(),
                record.category(),
                record.deploymentId(),
                record.flowableDefinitionId(),
                record.publisherUserId(),
                toOffsetDateTime(record.createdAt())
        );
    }

    private WorkflowInstanceListItemResponse toInstanceListItem(HistoricProcessInstance historicInstance) {
        InstanceContext context = buildInstanceContext(historicInstance);
        return new WorkflowInstanceListItemResponse(
                historicInstance.getId(),
                context.platformProcessDefinitionId(),
                historicInstance.getProcessDefinitionId(),
                context.processKey(),
                context.processName(),
                context.businessType(),
                context.businessId(),
                historicInstance.getStartUserId(),
                context.status(),
                context.suspended(),
                context.currentTaskNames(),
                toOffsetDateTime(historicInstance.getStartTime()),
                toOffsetDateTime(historicInstance.getEndTime())
        );
    }

    private InstanceContext buildInstanceContext(HistoricProcessInstance historicInstance) {
        Map<String, Object> variables = readVariables(historicInstance.getId());
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(historicInstance.getId())
                .singleResult();
        List<String> currentTaskNames = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(historicInstance.getId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .map(Task::getName)
                .toList();
        String platformProcessDefinitionId = stringValue(variables.get("westflowProcessDefinitionId"));
        BusinessProcessLinkRecord link = businessProcessLinkMapper.selectByProcessInstanceId(historicInstance.getId());
        if (platformProcessDefinitionId == null && link != null) {
            platformProcessDefinitionId = link.processDefinitionId();
        }
        String processKey = stringValue(variables.get("westflowProcessKey"));
        String processName = stringValue(variables.get("westflowProcessName"));
        if ((processKey == null || processName == null) && platformProcessDefinitionId != null) {
            ProcessDefinitionRecord definition = processDefinitionMapper.selectById(platformProcessDefinitionId);
            if (definition != null) {
                processKey = processKey == null ? definition.processKey() : processKey;
                processName = processName == null ? definition.processName() : processName;
            }
        }
        return new InstanceContext(
                platformProcessDefinitionId,
                processKey,
                processName,
                link == null ? stringValue(variables.get("westflowBusinessType")) : link.businessType(),
                link == null ? stringValue(variables.get("westflowBusinessKey")) : link.businessId(),
                runtimeInstance != null ? runtimeInstance.isSuspended() : false,
                resolveInstanceStatus(historicInstance, runtimeInstance),
                currentTaskNames,
                variables
        );
    }

    private Map<String, Object> readVariables(String processInstanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return new LinkedHashMap<>(flowableEngineFacade.runtimeService().getVariables(processInstanceId));
        }
        return flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .stream()
                .collect(LinkedHashMap::new,
                        (map, variable) -> map.put(variable.getVariableName(), variable.getValue()),
                        LinkedHashMap::putAll);
    }

    private String resolveInstanceStatus(HistoricProcessInstance historicInstance, ProcessInstance runtimeInstance) {
        if (historicInstance.getEndTime() != null) {
            return historicInstance.getDeleteReason() == null ? "COMPLETED" : "TERMINATED";
        }
        if (runtimeInstance != null && runtimeInstance.isSuspended()) {
            return "SUSPENDED";
        }
        return "RUNNING";
    }

    private boolean matchesDefinitionKeyword(ProcessDefinitionRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(record.processDefinitionId(), normalized)
                || contains(record.processKey(), normalized)
                || contains(record.processName(), normalized)
                || contains(record.category(), normalized)
                || contains(record.publisherUserId(), normalized);
    }

    private boolean matchesCategoryFilter(ProcessDefinitionRecord record, List<FilterItem> filters) {
        String category = filters.stream()
                .filter(filter -> "category".equals(filter.field()) && "eq".equalsIgnoreCase(filter.operator()))
                .map(filter -> filter.value() == null ? null : filter.value().asText())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        return category == null || category.equalsIgnoreCase(record.category());
    }

    private boolean matchesInstanceKeyword(WorkflowInstanceListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.processInstanceId(), normalized)
                || contains(item.processKey(), normalized)
                || contains(item.processName(), normalized)
                || contains(item.businessType(), normalized)
                || contains(item.businessId(), normalized)
                || contains(item.startUserId(), normalized);
    }

    private Filters resolveInstanceFilters(List<FilterItem> filters) {
        String businessType = null;
        String status = null;
        for (FilterItem filter : filters) {
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                continue;
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("businessType".equals(filter.field())) {
                businessType = value;
            }
            if ("status".equals(filter.field())) {
                status = value;
            }
        }
        return new Filters(businessType, status);
    }

    private <T> PageResponse<T> toPage(PageRequest request, List<T> matched) {
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<T> records = fromIndex >= matched.size() ? List.of() : matched.subList(fromIndex, toIndex);
        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private ProcessDefinitionRecord requirePublishedDefinition(String processDefinitionId) {
        ProcessDefinitionRecord record = processDefinitionMapper.selectById(processDefinitionId);
        if (record == null || !"PUBLISHED".equals(record.status())) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "流程版本不存在",
                    Map.of("processDefinitionId", processDefinitionId)
            );
        }
        return record;
    }

    private void ensureWorkflowAdminAccess() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        if (!fixtureAuthService.isProcessAdmin(userId) && !fixtureAuthService.isSystemAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问流程管理后台",
                    Map.of("userId", userId)
            );
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        return date == null ? null : date.toInstant().atZone(TIME_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(TIME_ZONE).toOffsetDateTime();
    }

    private record Filters(String businessType, String status) {
    }

    private record InstanceContext(
            String platformProcessDefinitionId,
            String processKey,
            String processName,
            String businessType,
            String businessId,
            boolean suspended,
            String status,
            List<String> currentTaskNames,
            Map<String, Object> variables
    ) {
    }
}
