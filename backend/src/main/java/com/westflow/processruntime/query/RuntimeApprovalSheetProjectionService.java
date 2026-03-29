package com.westflow.processruntime.query;

import com.westflow.approval.service.ApprovalSheetQueryService;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import com.westflow.processruntime.link.BusinessLinkSnapshot;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.support.RuntimeParticipantDirectoryService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeApprovalSheetProjectionService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskAssembler runtimeTaskAssembler;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final RuntimeParticipantDirectoryService runtimeParticipantDirectoryService;

    public ApprovalSheetProjectionContext createProjectionContext() {
        return new ApprovalSheetProjectionContext(new HashMap<>(), new HashMap<>());
    }

    public ApprovalSheetListItemResponse fromCopiedTask(Task task) {
        Map<String, Object> variables = runtimeVariables(task.getProcessInstanceId());
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessKey")),
                task.getProcessInstanceId()
        );
        String businessType = stringValue(variables.get("westflowBusinessType"));
        String businessKey = stringValue(variables.get("westflowBusinessKey"));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        String taskStatus = runtimeTaskSupportService.resolveTaskStatus(task);
        return new ApprovalSheetListItemResponse(
                task.getProcessInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(businessType, businessData),
                stringValue(variables.get("westflowInitiatorUserId")),
                resolveUserDisplayName(stringValue(variables.get("westflowInitiatorUserId"))),
                stringValue(variables.get("westflowInitiatorPostId")),
                stringValue(variables.get("westflowInitiatorPostName")),
                stringValue(variables.get("westflowInitiatorDepartmentId")),
                stringValue(variables.get("westflowInitiatorDepartmentName")),
                task.getName(),
                task.getId(),
                taskStatus,
                task.getAssignee(),
                "COMPLETED",
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                "READ".equals(taskStatus) || "CC_READ".equals(taskStatus) ? "READ" : "UNREAD",
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getCreateTime()),
                null
        );
    }

    public ApprovalSheetListItemResponse fromCopiedHistoricTask(HistoricTaskInstance task) {
        Map<String, Object> variables = historicVariables(task.getProcessInstanceId());
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                stringValue(variables.get("westflowProcessDefinitionId")),
                stringValue(variables.get("westflowProcessKey")),
                task.getProcessInstanceId()
        );
        String businessType = stringValue(variables.get("westflowBusinessType"));
        String businessKey = stringValue(variables.get("westflowBusinessKey"));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new ApprovalSheetListItemResponse(
                task.getProcessInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                businessKey,
                businessType,
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(businessType, businessData),
                stringValue(variables.get("westflowInitiatorUserId")),
                resolveUserDisplayName(stringValue(variables.get("westflowInitiatorUserId"))),
                stringValue(variables.get("westflowInitiatorPostId")),
                stringValue(variables.get("westflowInitiatorPostName")),
                stringValue(variables.get("westflowInitiatorDepartmentId")),
                stringValue(variables.get("westflowInitiatorDepartmentName")),
                task.getName(),
                task.getId(),
                runtimeTaskSupportService.resolveHistoricTaskStatus(task, requireHistoricProcessInstance(task.getProcessInstanceId())),
                task.getAssignee(),
                "COMPLETED",
                stringValue(variables.get("westflowLastAction")),
                stringValue(variables.get("westflowLastOperatorUserId")),
                "CC_READ",
                toOffsetDateTime(task.getCreateTime()),
                toOffsetDateTime(task.getEndTime()),
                toOffsetDateTime(task.getEndTime())
        );
    }

    public ApprovalSheetListItemResponse fromTask(ProcessTaskListItemResponse task, ApprovalSheetProjectionContext projectionContext) {
        Map<String, Object> businessData = projectionContext.businessDataByKey().computeIfAbsent(
                task.businessType() + "::" + task.businessKey(),
                ignored -> approvalSheetQueryService.resolveBusinessData(task.businessType(), task.businessKey())
        );
        return new ApprovalSheetListItemResponse(
                task.instanceId(),
                task.processDefinitionId(),
                task.processKey(),
                task.processName(),
                task.businessKey(),
                task.businessType(),
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(task.businessType(), businessData),
                task.applicantUserId(),
                projectionContext.userDisplayNameById().computeIfAbsent(task.applicantUserId(), this::resolveUserDisplayName),
                task.initiatorPostId(),
                task.initiatorPostName(),
                task.initiatorDepartmentId(),
                task.initiatorDepartmentName(),
                task.nodeName(),
                task.taskId(),
                task.status(),
                task.assigneeUserId(),
                "RUNNING",
                null,
                null,
                "PENDING",
                task.createdAt(),
                task.updatedAt(),
                task.completedAt()
        );
    }

    public ApprovalSheetListItemResponse fromLink(BusinessLinkSnapshot link) {
        HistoricProcessInstance historicProcessInstance = requireHistoricProcessInstance(link.processInstanceId());
        Map<String, Object> variables = runtimeOrHistoricVariables(link.processInstanceId());
        PublishedProcessDefinition definition = resolvePublishedDefinition(
                link.processDefinitionId(),
                stringValue(variables.get("westflowProcessKey")),
                link.processInstanceId()
        );
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(link.processInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        HistoricTaskInstance latestHistoricTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(link.processInstanceId())
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(link.businessType(), link.businessId());
        Task currentTask = activeTasks.stream().findFirst().orElse(null);
        String latestAction = stringValue(variables.get("westflowLastAction"));
        String instanceStatus = resolveApprovalSheetInstanceStatus(link, historicProcessInstance, activeTasks, latestAction);
        OffsetDateTime updatedAt = currentTask != null
                ? toOffsetDateTime(currentTask.getCreateTime())
                : latestHistoricTask == null ? toOffsetDateTime(historicProcessInstance.getStartTime()) : toOffsetDateTime(latestHistoricTask.getEndTime());
        return new ApprovalSheetListItemResponse(
                link.processInstanceId(),
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                link.businessId(),
                link.businessType(),
                stringValue(businessData.get("billNo")),
                resolveBusinessTitle(link.businessType(), businessData),
                link.startUserId(),
                resolveUserDisplayName(link.startUserId()),
                stringValue(variables.get("westflowInitiatorPostId")),
                stringValue(variables.get("westflowInitiatorPostName")),
                stringValue(variables.get("westflowInitiatorDepartmentId")),
                stringValue(variables.get("westflowInitiatorDepartmentName")),
                currentTask != null ? currentTask.getName() : latestHistoricTask == null ? null : latestHistoricTask.getName(),
                currentTask != null ? currentTask.getId() : latestHistoricTask == null ? null : latestHistoricTask.getId(),
                currentTask != null
                        ? runtimeTaskSupportService.resolveTaskStatus(currentTask)
                        : latestHistoricTask == null ? null : runtimeTaskSupportService.resolveHistoricTaskStatus(latestHistoricTask, historicProcessInstance),
                currentTask != null ? currentTask.getAssignee() : latestHistoricTask == null ? null : latestHistoricTask.getAssignee(),
                instanceStatus,
                latestAction,
                stringValue(variables.get("westflowLastOperatorUserId")),
                currentTask == null ? ("REVOKED".equals(instanceStatus) ? "REVOKED" : "SUCCESS") : "PENDING",
                toOffsetDateTime(historicProcessInstance.getStartTime()),
                updatedAt,
                toOffsetDateTime(historicProcessInstance.getEndTime())
        );
    }

    public boolean matchesKeyword(ApprovalSheetListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.processName(), normalized)
                || contains(item.billNo(), normalized)
                || contains(item.businessTitle(), normalized)
                || contains(item.currentNodeName(), normalized)
                || contains(item.businessType(), normalized);
    }

    public String resolveHistoricTaskKind(HistoricTaskInstance task) {
        String historicTaskKind = stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (historicTaskKind != null) {
            return historicTaskKind;
        }
        return runtimeTaskVisibilityService.resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), RuntimeTaskQueryContext.create());
    }

    private String resolveApprovalSheetInstanceStatus(
            BusinessLinkSnapshot link,
            HistoricProcessInstance historicProcessInstance,
            List<Task> activeTasks,
            String latestAction
    ) {
        String status = resolveInstanceStatus(historicProcessInstance, activeTasks);
        if ("REVOKED".equals(status)) {
            return status;
        }
        if ("REVOKED".equals(link.status()) || "REVOKE".equals(latestAction)) {
            return "REVOKED";
        }
        return status;
    }

    private String resolveInstanceStatus(HistoricProcessInstance processInstance, List<Task> activeTasks) {
        if (processInstance.getDeleteReason() != null) {
            if ("WESTFLOW_REVOKED".equals(processInstance.getDeleteReason())) {
                return "REVOKED";
            }
            return "WESTFLOW_TERMINATED".equals(processInstance.getDeleteReason()) ? "TERMINATED" : "COMPLETED";
        }
        return activeTasks.stream().anyMatch(task -> !"CC".equals(runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create()))
                && runtimeTaskVisibilityService.isVisibleTask(task, RuntimeTaskQueryContext.create(), runtimeTaskAssembler::resolvePublishedDefinitionByInstance))
                || hasRunningAppendStructures(processInstance.getId())
                ? "RUNNING"
                : "COMPLETED";
    }

    private boolean hasRunningAppendStructures(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        return runtimeAppendLinkService.listByParentInstanceId(processInstanceId).stream()
                .anyMatch(link -> "RUNNING".equals(link.status()));
    }

    private String resolveBusinessTitle(String businessType, Map<String, Object> businessData) {
        if (businessData == null || businessData.isEmpty()) {
            return null;
        }
        return switch (businessType) {
            case "OA_LEAVE", "OA_EXPENSE" -> stringValue(businessData.get("reason"));
            case "OA_COMMON" -> stringValue(businessData.get("title"));
            default -> stringValue(businessData.get("billNo"));
        };
    }

    private String resolveUserDisplayName(String userId) {
        return runtimeParticipantDirectoryService.resolveUserDisplayName(userId);
    }

    private PublishedProcessDefinition resolvePublishedDefinition(String preferredPlatformDefinitionId, String processKey, String processInstanceId) {
        if (preferredPlatformDefinitionId != null && !preferredPlatformDefinitionId.isBlank()) {
            return processDefinitionService.getById(preferredPlatformDefinitionId);
        }
        Optional<PublishedProcessDefinition> byInstance = runtimeTaskAssembler.resolvePublishedDefinitionByInstance(processInstanceId);
        if (byInstance.isPresent()) {
            return byInstance.get();
        }
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalStateException("流程定义不存在: " + processInstanceId);
        }
        return processDefinitionService.getLatestByProcessKey(processKey);
    }

    private HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
        HistoricProcessInstance processInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            throw new IllegalStateException("流程实例不存在: " + processInstanceId);
        }
        return processInstance;
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : variables;
    }

    private Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        try {
            Map<String, Object> runtimeValues = runtimeVariables(processInstanceId);
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
        } catch (FlowableObjectNotFoundException ignored) {
            // Fall through to history fallback.
        }
        return historicVariables(processInstanceId);
    }

    private Map<String, Object> historicVariables(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private Map<String, Object> historicTaskLocalVariables(String taskId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .taskId(taskId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    public record ApprovalSheetProjectionContext(
            Map<String, Map<String, Object>> businessDataByKey,
            Map<String, String> userDisplayNameById
    ) {
    }
}
