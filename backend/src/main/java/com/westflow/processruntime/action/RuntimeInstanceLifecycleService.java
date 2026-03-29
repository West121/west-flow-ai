package com.westflow.processruntime.action;

import com.westflow.common.error.ContractException;
import com.westflow.processruntime.api.request.TerminateProcessInstanceRequest;
import com.westflow.processruntime.api.request.WakeUpInstanceRequest;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.link.BusinessLinkSnapshot;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.query.RuntimeProcessLinkQueryService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeInstanceLifecycleService {

    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeTaskActionSupportService taskActionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final ProcessLinkService processLinkService;
    private final RuntimeProcessLinkQueryService runtimeProcessLinkQueryService;

    @Transactional
    public CompleteTaskResponse terminate(String instanceId, TerminateProcessInstanceRequest request) {
        String terminateScope = normalizeTerminateScope(request.terminateScope());
        if ("ROOT".equals(terminateScope)) {
            return terminateRootProcess(instanceId, request.reason());
        }
        if ("CHILD".equals(terminateScope) || "SUBPROCESS".equals(terminateScope)) {
            return terminateChildProcess(instanceId, request.childInstanceId(), request.reason());
        }
        throw actionSupportService.actionNotAllowed("当前仅支持终止根流程或子流程", Map.of("terminateScope", terminateScope));
    }

    @Transactional
    public CompleteTaskResponse wakeUp(String instanceId, WakeUpInstanceRequest request) {
        HistoricProcessInstance historicInstance = processActionSupportService.requireHistoricProcessInstance(instanceId);
        if (historicInstance.getEndTime() == null && historicInstance.getDeleteReason() == null) {
            throw actionSupportService.actionNotAllowed("仅已结束的流程实例支持唤醒", Map.of("instanceId", instanceId));
        }

        HistoricTaskInstance resolvedSourceTask = processActionSupportService.requireHistoricTaskSource(instanceId, request.sourceTaskId());
        Map<String, Object> variables = new LinkedHashMap<>(processActionSupportService.historicVariables(instanceId));
        String processKey = actionSupportService.stringValue(variables.get("westflowProcessKey"));
        String platformProcessDefinitionId = actionSupportService.stringValue(variables.get("westflowProcessDefinitionId"));
        if (processKey == null || processKey.isBlank()) {
            throw actionSupportService.resourceNotFound("流程定义不存在", Map.of("instanceId", instanceId));
        }
        String businessKey = historicInstance.getBusinessKey();
        ProcessInstance newInstance = processActionSupportService.startHistoricProcessInstance(processKey, businessKey, variables);

        Task firstActiveTask = processActionSupportService.firstActiveTask(newInstance.getProcessInstanceId());
        if (firstActiveTask != null && !Objects.equals(firstActiveTask.getTaskDefinitionKey(), resolvedSourceTask.getTaskDefinitionKey())) {
            flowableTaskActionService.moveToActivity(firstActiveTask.getId(), resolvedSourceTask.getTaskDefinitionKey(), Map.of());
        }

        processActionSupportService.transferMatchingTasks(newInstance.getProcessInstanceId(), resolvedSourceTask);
        processActionSupportService.copyBusinessLinkOnWakeUp(instanceId, newInstance.getProcessInstanceId(), platformProcessDefinitionId);
        processActionSupportService.appendInstanceEvent(
                newInstance.getProcessInstanceId(),
                null,
                resolvedSourceTask.getTaskDefinitionKey(),
                "INSTANCE_WOKEN_UP",
                "流程实例已唤醒",
                "INSTANCE",
                request.sourceTaskId(),
                null,
                resolvedSourceTask.getAssignee(),
                processActionSupportService.eventDetails(
                        "comment", request.comment(),
                        "sourceInstanceId", instanceId,
                        "sourceTaskId", request.sourceTaskId(),
                        "targetNodeId", resolvedSourceTask.getTaskDefinitionKey(),
                        "targetUserId", resolvedSourceTask.getAssignee()
                ),
                null,
                resolvedSourceTask.getTaskDefinitionKey(),
                null,
                null,
                null,
                null,
                null
        );
        return taskActionSupportService.nextTaskResponse(newInstance.getProcessInstanceId(), request.sourceTaskId());
    }

    private CompleteTaskResponse terminateRootProcess(String instanceId, String reason) {
        processActionSupportService.requireRunningInstance(instanceId);
        Instant terminatedAt = Instant.now();
        List<com.westflow.processruntime.model.ProcessLinkRecord> childLinks = processLinkService.listByRootInstanceId(instanceId);
        runtimeAppendLinkService.markTerminatedByRootInstanceId(instanceId, terminatedAt);
        flowableTaskActionService.revokeProcessInstance(instanceId, "WESTFLOW_TERMINATE:" + reason);
        childLinks.forEach(link -> processLinkService.updateStatus(link.childInstanceId(), "TERMINATED", terminatedAt));
        processActionSupportService.appendInstanceEvent(
                instanceId,
                null,
                null,
                "INSTANCE_TERMINATED",
                "流程已终止",
                "INSTANCE",
                null,
                null,
                null,
                processActionSupportService.eventDetails(
                        "terminateScope", "ROOT",
                        "reason", reason,
                        "terminatedChildCount", childLinks.size()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new CompleteTaskResponse(instanceId, null, "TERMINATED", List.of());
    }

    private CompleteTaskResponse terminateChildProcess(String instanceId, String childInstanceId, String reason) {
        String resolvedChildInstanceId = childInstanceId == null || childInstanceId.isBlank()
                ? instanceId
                : childInstanceId.trim();
        var link = processLinkService.getByChildInstanceId(resolvedChildInstanceId);
        RuntimeAppendLinkRecord appendLink = null;
        if (link == null) {
            appendLink = runtimeAppendLinkService.getByTargetInstanceId(resolvedChildInstanceId);
            if (appendLink == null) {
                throw actionSupportService.actionNotAllowed(
                        "目标子流程实例不存在",
                        Map.of("instanceId", instanceId, "childInstanceId", resolvedChildInstanceId)
                );
            }
        }
        processActionSupportService.requireRunningInstance(resolvedChildInstanceId);
        Instant terminatedAt = Instant.now();
        List<com.westflow.processruntime.model.ProcessLinkRecord> descendantLinks = link == null
                ? List.of()
                : runtimeProcessLinkQueryService.descendantProcessLinks(link.rootInstanceId(), resolvedChildInstanceId);
        List<String> descendantInstanceIds = descendantLinks.stream()
                .map(com.westflow.processruntime.model.ProcessLinkRecord::childInstanceId)
                .distinct()
                .toList();
        runtimeAppendLinkService.markTerminatedByParentInstanceId(resolvedChildInstanceId, terminatedAt);
        descendantInstanceIds.forEach(descendantInstanceId ->
                runtimeAppendLinkService.markTerminatedByParentInstanceId(descendantInstanceId, terminatedAt)
        );
        processActionSupportService.revokeProcessInstanceQuietly(flowableTaskActionService, resolvedChildInstanceId, reason);
        descendantInstanceIds.stream()
                .filter(descendantInstanceId -> !resolvedChildInstanceId.equals(descendantInstanceId))
                .forEach(descendantInstanceId -> processActionSupportService.revokeProcessInstanceQuietly(flowableTaskActionService, descendantInstanceId, reason));
        if (link != null) {
            processLinkService.updateStatus(resolvedChildInstanceId, "TERMINATED", terminatedAt);
            descendantLinks.forEach(descendantLink ->
                    processLinkService.updateStatus(descendantLink.childInstanceId(), "TERMINATED", terminatedAt)
            );
            processActionSupportService.appendInstanceEvent(
                    link.parentInstanceId(),
                    null,
                    link.parentNodeId(),
                    "SUBPROCESS_TERMINATED",
                    "子流程已终止",
                    "INSTANCE",
                    null,
                    resolvedChildInstanceId,
                    null,
                    processActionSupportService.eventDetails(
                            "terminateScope", "CHILD",
                            "reason", reason,
                            "childInstanceId", resolvedChildInstanceId,
                            "parentNodeId", link.parentNodeId(),
                            "terminatedDescendantCount", descendantInstanceIds.size()
                    ),
                    null,
                    link.parentNodeId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } else {
            runtimeAppendLinkService.updateStatusByTargetInstanceId(resolvedChildInstanceId, "TERMINATED", terminatedAt);
            processActionSupportService.appendInstanceEvent(
                    appendLink.parentInstanceId(),
                    null,
                    appendLink.sourceNodeId(),
                    "APPEND_TERMINATED",
                    "追加子流程已终止",
                    "INSTANCE",
                    appendLink.sourceTaskId(),
                    resolvedChildInstanceId,
                    null,
                    processActionSupportService.eventDetails(
                            "terminateScope", "CHILD",
                            "reason", reason,
                            "childInstanceId", resolvedChildInstanceId,
                            "sourceTaskId", appendLink.sourceTaskId(),
                            "sourceNodeId", appendLink.sourceNodeId(),
                            "appendLinkId", appendLink.id()
                    ),
                    null,
                    appendLink.sourceNodeId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        String resolvedRootInstanceId = link != null ? link.rootInstanceId() : appendLink.rootInstanceId();
        String resolvedParentInstanceId = link != null ? link.parentInstanceId() : appendLink.parentInstanceId();
        processActionSupportService.appendInstanceEvent(
                resolvedChildInstanceId,
                null,
                null,
                "INSTANCE_TERMINATED",
                "流程已终止",
                "INSTANCE",
                null,
                null,
                null,
                processActionSupportService.eventDetails(
                        "terminateScope", "CHILD",
                        "reason", reason,
                        "rootInstanceId", resolvedRootInstanceId,
                        "parentInstanceId", resolvedParentInstanceId,
                        "terminatedDescendantCount", descendantInstanceIds.size()
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        String appendTerminatePolicy = appendLink == null ? null : resolveDynamicBuildTerminatePolicy(appendLink);
        if (appendLink != null && shouldCascadeTerminateAppendParent(appendLink, appendTerminatePolicy, resolvedChildInstanceId)) {
            processActionSupportService.appendInstanceEvent(
                    resolvedParentInstanceId,
                    null,
                    appendLink.sourceNodeId(),
                    "APPEND_TERMINATE_POLICY_TRIGGERED",
                    "附属子流程终止已触发父流程级联终止",
                    "INSTANCE",
                    appendLink.sourceTaskId(),
                    resolvedChildInstanceId,
                    actionSupportService.currentUserId(),
                    processActionSupportService.eventDetails(
                            "appendLinkId", appendLink.id(),
                            "appendType", appendLink.appendType(),
                            "policy", appendLink.policy(),
                            "terminatePolicy", appendTerminatePolicy,
                            "reason", reason,
                            "parentInstanceId", resolvedParentInstanceId,
                            "rootInstanceId", resolvedRootInstanceId
                    ),
                    null,
                    appendLink.sourceNodeId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            if (Objects.equals(resolvedRootInstanceId, resolvedParentInstanceId)) {
                return terminateRootProcess(resolvedParentInstanceId, reason + "（附属结构终止策略触发）");
            }
            return terminateChildProcess(resolvedRootInstanceId, resolvedParentInstanceId, reason + "（附属结构终止策略触发）");
        }
        return taskActionSupportService.nextTaskResponse(resolvedParentInstanceId, null);
    }

    private boolean shouldCascadeTerminateAppendParent(
            RuntimeAppendLinkRecord appendLink,
            String terminatePolicy,
            String resolvedChildInstanceId
    ) {
        if (appendLink == null) {
            return false;
        }
        if (!"TERMINATE_PARENT_AND_GENERATED".equals(terminatePolicy)) {
            return false;
        }
        String parentInstanceId = appendLink.parentInstanceId();
        return parentInstanceId != null
                && !parentInstanceId.isBlank()
                && !Objects.equals(parentInstanceId, resolvedChildInstanceId);
    }

    private String resolveDynamicBuildTerminatePolicy(RuntimeAppendLinkRecord appendLink) {
        if (appendLink == null || appendLink.sourceNodeId() == null || appendLink.sourceNodeId().isBlank()) {
            return null;
        }
        Map<String, Object> nodeConfig = processActionSupportService.resolveNodeConfig(appendLink.parentInstanceId(), appendLink.sourceNodeId());
        return actionSupportService.stringValue(nodeConfig.get("terminatePolicy"));
    }

    private String normalizeTerminateScope(String terminateScope) {
        if (terminateScope == null || terminateScope.isBlank()) {
            return "ROOT";
        }
        return terminateScope.trim().toUpperCase();
    }
}
