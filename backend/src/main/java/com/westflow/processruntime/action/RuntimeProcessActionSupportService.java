package com.westflow.processruntime.action;

import com.westflow.approval.service.ApprovalSheetQueryService;
import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.HandoverExecutionTaskItemResponse;
import com.westflow.processruntime.api.response.HandoverPreviewTaskItemResponse;
import com.westflow.processruntime.link.BusinessLinkSnapshot;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import com.westflow.processruntime.support.RuntimeParticipantDirectoryService;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import com.westflow.processruntime.trace.RuntimeInstanceEventRecorder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessActionSupportService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeActionCoreSupportService coreSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;
    private final RuntimeInstanceEventRecorder runtimeInstanceEventRecorder;
    private final ApprovalSheetQueryService approvalSheetQueryService;
    private final RuntimeParticipantDirectoryService runtimeParticipantDirectoryService;
    private final JdbcTemplate jdbcTemplate;

    public HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
        return runtimeProcessMetadataService.requireHistoricProcessInstance(processInstanceId);
    }

    public ProcessInstance requireRunningInstance(String instanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (runtimeInstance == null) {
            throw new ContractException(
                    "PROCESS.INSTANCE_NOT_RUNNING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "流程实例未运行，不能终止",
                    Map.of("instanceId", instanceId)
            );
        }
        return runtimeInstance;
    }

    public Integer integerValue(Object value) {
        return runtimeProcessMetadataService.integerValue(value);
    }

    public Map<String, Object> runtimeVariables(String processInstanceId) {
        return runtimeProcessMetadataService.runtimeVariables(processInstanceId);
    }

    public Map<String, Object> resolveNodeConfig(String processInstanceId, String nodeId) {
        return runtimeProcessMetadataService.resolveNodeConfig(processInstanceId, nodeId);
    }

    public Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        return runtimeProcessMetadataService.runtimeOrHistoricVariables(processInstanceId);
    }

    public Map<String, Object> historicVariables(String processInstanceId) {
        return runtimeProcessMetadataService.historicVariables(processInstanceId);
    }

    public String activeFlowableDefinitionId(String processInstanceId) {
        return runtimeProcessMetadataService.activeFlowableDefinitionId(processInstanceId);
    }

    public String activeProcessKey(String processInstanceId) {
        return runtimeProcessMetadataService.activeProcessKey(processInstanceId);
    }

    public void appendInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId
    ) {
        appendInstanceEvent(
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId,
                coreSupportService.currentUserId()
        );
    }

    public void appendInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId,
            String operatorUserId
    ) {
        runtimeInstanceEventRecorder.appendInstanceEvent(
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId,
                operatorUserId
        );
    }

    public Map<String, Object> eventDetails(Object... keyValues) {
        return runtimeInstanceEventRecorder.eventDetails(keyValues);
    }

    public HandoverPreviewTaskItemResponse toHandoverPreviewTask(Task task, String sourceUserId) {
        Optional<BusinessLinkSnapshot> link = findBusinessLinkByInstanceId(task.getProcessInstanceId());
        String businessType = link.map(BusinessLinkSnapshot::businessType)
                .orElseGet(() -> coreSupportService.stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowBusinessType")));
        String businessKey = link.map(BusinessLinkSnapshot::businessId)
                .orElseGet(() -> coreSupportService.stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowBusinessKey")));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new HandoverPreviewTaskItemResponse(
                task.getId(),
                task.getProcessInstanceId(),
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(task.getProcessDefinitionId()),
                coreSupportService.stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowProcessKey")),
                coreSupportService.stringValue(runtimeVariables(task.getProcessInstanceId()).get("westflowProcessName")),
                businessKey,
                businessType,
                resolveBusinessTitle(businessType, businessData),
                coreSupportService.stringValue(businessData.get("billNo")),
                task.getTaskDefinitionKey(),
                task.getName(),
                sourceUserId,
                taskActionSupportService.toOffsetDateTime(task.getCreateTime()),
                taskActionSupportService.toOffsetDateTime(task.getCreateTime()),
                true,
                null
        );
    }

    public HandoverExecutionTaskItemResponse toHandoverExecutionTask(
            Task sourceTask,
            Task updatedTask,
            String sourceUserId,
            String targetUserId,
            String comment
    ) {
        Optional<BusinessLinkSnapshot> link = findBusinessLinkByInstanceId(updatedTask.getProcessInstanceId());
        String businessType = link.map(BusinessLinkSnapshot::businessType)
                .orElseGet(() -> coreSupportService.stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowBusinessType")));
        String businessKey = link.map(BusinessLinkSnapshot::businessId)
                .orElseGet(() -> coreSupportService.stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowBusinessKey")));
        Map<String, Object> businessData = approvalSheetQueryService.resolveBusinessData(businessType, businessKey);
        return new HandoverExecutionTaskItemResponse(
                sourceTask.getId(),
                updatedTask.getId(),
                updatedTask.getProcessInstanceId(),
                link.map(BusinessLinkSnapshot::processDefinitionId).orElse(updatedTask.getProcessDefinitionId()),
                coreSupportService.stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowProcessKey")),
                coreSupportService.stringValue(runtimeVariables(updatedTask.getProcessInstanceId()).get("westflowProcessName")),
                businessKey,
                businessType,
                resolveBusinessTitle(businessType, businessData),
                coreSupportService.stringValue(businessData.get("billNo")),
                updatedTask.getTaskDefinitionKey(),
                updatedTask.getName(),
                targetUserId,
                "HANDOVERED",
                updatedTask.getAssignee(),
                sourceUserId,
                comment,
                OffsetDateTime.now(TIME_ZONE),
                true,
                null
        );
    }

    public String resolveUserDisplayName(String userId) {
        return runtimeParticipantDirectoryService.resolveUserDisplayName(userId);
    }

    public String resolveGroupDisplayName(String groupId) {
        return runtimeParticipantDirectoryService.resolveGroupDisplayName(groupId);
    }

    public void updateBusinessProcessLink(String businessType, String businessId, String processInstanceId, String status) {
        switch (businessType) {
            case "OA_LEAVE" -> jdbcTemplate.update(
                    "UPDATE oa_leave_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            case "OA_EXPENSE" -> jdbcTemplate.update(
                    "UPDATE oa_expense_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            case "OA_COMMON" -> jdbcTemplate.update(
                    "UPDATE oa_common_request_bill SET process_instance_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    processInstanceId,
                    status,
                    businessId
            );
            default -> {
            }
        }
    }

    public String resolveBusinessTitle(String businessType, Map<String, Object> businessData) {
        if (businessData == null || businessData.isEmpty()) {
            return null;
        }
        return switch (businessType) {
            case "OA_LEAVE", "OA_EXPENSE" -> coreSupportService.stringValue(businessData.get("reason"));
            case "OA_COMMON" -> coreSupportService.stringValue(businessData.get("title"));
            default -> coreSupportService.stringValue(businessData.get("billNo"));
        };
    }

    public String resolveProcessInstanceIdByBusinessKey(String businessId) {
        List<org.flowable.engine.runtime.ProcessInstance> runtimeInstances = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .list();
        if (!runtimeInstances.isEmpty()) {
            return runtimeInstances.stream()
                    .filter(instance -> instance.getSuperExecutionId() == null
                            || instance.getRootProcessInstanceId() == null
                            || instance.getRootProcessInstanceId().equals(instance.getProcessInstanceId()))
                    .findFirst()
                    .orElse(runtimeInstances.get(0))
                    .getProcessInstanceId();
        }
        List<HistoricProcessInstance> historicInstances = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessId)
                .list();
        if (!historicInstances.isEmpty()) {
            return historicInstances.stream()
                    .filter(instance -> instance.getSuperProcessInstanceId() == null)
                    .findFirst()
                    .orElse(historicInstances.get(0))
                    .getId();
        }
        throw coreSupportService.resourceNotFound("审批单不存在", Map.of("businessId", businessId));
    }

    public String resolveStartBusinessType(StartProcessRequest request) {
        if (request.businessType() != null && !request.businessType().isBlank()) {
            return request.businessType();
        }
        return switch (request.processKey()) {
            case "oa_leave" -> "OA_LEAVE";
            case "oa_expense" -> "OA_EXPENSE";
            case "oa_common" -> "OA_COMMON";
            case "plm_ecr" -> "PLM_ECR";
            case "plm_eco" -> "PLM_ECO";
            case "plm_material" -> "PLM_MATERIAL";
            default -> request.businessType();
        };
    }

    public void revokeProcessInstanceQuietly(FlowableTaskActionService flowableTaskActionService, String processInstanceId, String reason) {
        try {
            flowableTaskActionService.revokeProcessInstance(processInstanceId, "WESTFLOW_TERMINATE:" + reason);
        } catch (FlowableObjectNotFoundException ignored) {
            // 级联终止时后代实例可能已被父实例删除。
        }
    }

    public HistoricTaskInstance requireHistoricTaskSource(String instanceId, String sourceTaskId) {
        HistoricTaskInstance sourceTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(sourceTaskId)
                .singleResult();
        if (sourceTask == null || !instanceId.equals(sourceTask.getProcessInstanceId())) {
            throw coreSupportService.actionNotAllowed(
                    "唤醒来源任务不存在",
                    Map.of("instanceId", instanceId, "sourceTaskId", sourceTaskId)
            );
        }
        return sourceTask;
    }

    public ProcessInstance startHistoricProcessInstance(String processKey, String businessKey, Map<String, Object> variables) {
        return flowableEngineFacade.runtimeService().startProcessInstanceByKey(processKey, businessKey, variables);
    }

    public Task firstActiveTask(String processInstanceId) {
        return flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public void transferMatchingTasks(String processInstanceId, HistoricTaskInstance sourceTask) {
        flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .filter(task -> sourceTask.getTaskDefinitionKey().equals(task.getTaskDefinitionKey()))
                .filter(task -> sourceTask.getAssignee() != null && !sourceTask.getAssignee().isBlank())
                .forEach(task -> flowableEngineFacade.taskService().setAssignee(task.getId(), sourceTask.getAssignee()));
    }

    public void copyBusinessLinkOnWakeUp(String sourceInstanceId, String targetInstanceId, String platformProcessDefinitionId) {
        runtimeBusinessLinkService.findByInstanceId(sourceInstanceId).ifPresent(link -> {
            runtimeBusinessLinkService.insertLink(
                    link.businessType(),
                    link.businessId(),
                    targetInstanceId,
                    platformProcessDefinitionId == null ? link.processDefinitionId() : platformProcessDefinitionId,
                    link.startUserId(),
                    "RUNNING"
            );
            updateBusinessProcessLink(link.businessType(), link.businessId(), targetInstanceId, "RUNNING");
        });
    }

    private Optional<BusinessLinkSnapshot> findBusinessLinkByInstanceId(String processInstanceId) {
        return runtimeBusinessLinkService.findByInstanceId(processInstanceId);
    }
}
