package com.westflow.processruntime.service.append;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.action.FlowableTaskActionService;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.append.DynamicBuildResolutionService.DynamicBuildResolutionResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicBuildAppendCreationService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final FlowableTaskActionService flowableTaskActionService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;
    private final DynamicBuildOutcomeRecorder dynamicBuildOutcomeRecorder;
    private final ProcessLinkService processLinkService;

    public PublishedProcessDefinition resolveParentDefinition(String processInstanceId, Map<String, Object> processVariables) {
        String platformDefinitionId = stringValue(processVariables.get("westflowProcessDefinitionId"));
        if (platformDefinitionId != null && !platformDefinitionId.isBlank()) {
            return processDefinitionService.getById(platformDefinitionId);
        }
        String processKey = stringValue(processVariables.get("westflowProcessKey"));
        if (processKey != null && !processKey.isBlank()) {
            return processDefinitionService.getLatestByProcessKey(processKey);
        }
        return processDefinitionService.getById(activeFlowableDefinitionId(processInstanceId));
    }

    public Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    public String resolveRuntimeOperatorUserId(Map<String, Object> processVariables) {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        String initiatorUserId = stringValue(processVariables.get("westflowInitiatorUserId"));
        return initiatorUserId == null ? "SYSTEM" : initiatorUserId;
    }

    public void createDynamicBuildTasks(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            String appendPolicy,
            DynamicBuildResolutionResult resolution,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String processDefinitionId = activeFlowableDefinitionId(processInstanceId);
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < resolution.items().size(); index++) {
            Map<String, Object> item = resolution.items().get(index);
            String targetUserId = stringValue(item.get("userId"));
            if (targetUserId == null) {
                targetUserId = stringValue(item.get("targetUserId"));
            }
            if (targetUserId == null || targetUserId.isBlank()) {
                continue;
            }
            String generatedNodeId = sourceNodeId + "__dynamic_task_" + (index + 1);
            Map<String, Object> localVariables = new LinkedHashMap<>(Map.of(
                    "westflowTaskKind", "APPEND",
                    "westflowAppendType", "TASK",
                    "westflowAppendPolicy", appendPolicy,
                    "westflowTriggerMode", "DYNAMIC_BUILD",
                    "westflowSourceTaskId", sourceStructureId,
                    "westflowSourceNodeId", sourceNodeId,
                    "westflowOperatorUserId", operatorUserId
            ));
            localVariables.putAll(resolution.runtimeMetadata());
            Task generatedTask = flowableTaskActionService.createAdhocTask(
                    processInstanceId,
                    processDefinitionId,
                    generatedNodeId,
                    nodeName + " / 动态生成审批",
                    "APPEND",
                    targetUserId,
                    List.of(),
                    null,
                    localVariables
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "TASK",
                    "ADHOC_TASK",
                    appendPolicy,
                    generatedTask.getId(),
                    null,
                    targetUserId,
                    null,
                    null,
                    null,
                    null,
                    "USER",
                    null,
                    null,
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
        }
    }

    public void createDynamicBuildSubprocesses(
            String processInstanceId,
            String sourceNodeId,
            String nodeName,
            PublishedProcessDefinition parentDefinition,
            String appendPolicy,
            Map<String, Object> processVariables,
            DynamicBuildResolutionResult resolution,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String parentBusinessKey = stringValue(processVariables.get("westflowBusinessKey"));
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        for (int index = 0; index < resolution.items().size(); index++) {
            Map<String, Object> item = resolution.items().get(index);
            String calledProcessKey = stringValue(item.get("calledProcessKey"));
            if (calledProcessKey == null || calledProcessKey.isBlank()) {
                continue;
            }
            String versionPolicy = Optional.ofNullable(stringValue(item.get("calledVersionPolicy"))).orElse("LATEST_PUBLISHED");
            Integer calledVersion = item.get("calledVersion") instanceof Number number ? number.intValue() : null;
            PublishedProcessDefinition childDefinition = "FIXED_VERSION".equals(versionPolicy)
                    ? processDefinitionService.getPublishedByProcessKeyAndVersion(calledProcessKey, calledVersion)
                    : processDefinitionService.getLatestByProcessKey(calledProcessKey);

            Map<String, Object> childVariables = new LinkedHashMap<>();
            childVariables.put("westflowProcessDefinitionId", childDefinition.processDefinitionId());
            childVariables.put("westflowProcessKey", childDefinition.processKey());
            childVariables.put("westflowProcessName", childDefinition.processName());
            childVariables.put("westflowBusinessType", stringValue(processVariables.get("westflowBusinessType")));
            childVariables.put("westflowBusinessKey", parentBusinessKey);
            childVariables.put("westflowInitiatorUserId", stringValue(processVariables.get("westflowInitiatorUserId")));
            childVariables.put("westflowParentInstanceId", processInstanceId);
            childVariables.put("westflowRootInstanceId", rootInstanceId);
            childVariables.put("westflowAppendType", "SUBPROCESS");
            childVariables.put("westflowAppendPolicy", appendPolicy);
            childVariables.put("westflowAppendTriggerMode", "DYNAMIC_BUILD");
            childVariables.put("westflowAppendSourceTaskId", sourceStructureId);
            childVariables.put("westflowAppendSourceNodeId", sourceNodeId);
            childVariables.put("westflowAppendOperatorUserId", operatorUserId);
            childVariables.putAll(resolution.runtimeMetadata());
            Object appendVariables = item.get("appendVariables");
            if (appendVariables instanceof Map<?, ?> appendVariablesMap) {
                appendVariablesMap.forEach((key, value) -> childVariables.put(String.valueOf(key), value));
            }
            ProcessInstance childInstance = flowableEngineFacade.runtimeService().startProcessInstanceByKey(
                    childDefinition.processKey(),
                    buildGeneratedSubprocessRuntimeBusinessKey(parentBusinessKey, sourceNodeId, index),
                    childVariables
            );
            RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                    UUID.randomUUID().toString(),
                    rootInstanceId,
                    processInstanceId,
                    sourceStructureId,
                    sourceNodeId,
                    "SUBPROCESS",
                    "ADHOC_SUBPROCESS",
                    appendPolicy,
                    null,
                    childInstance.getProcessInstanceId(),
                    null,
                    childDefinition.processKey(),
                    childDefinition.processDefinitionId(),
                    versionPolicy,
                    calledVersion,
                    "PROCESS_KEY",
                    stringValue(processVariables.get("westflowBusinessType")),
                    stringValue(item.get("sceneCode")),
                    "RUNNING",
                    "DYNAMIC_BUILD",
                    operatorUserId,
                    stringValue(item.get("comment")),
                    Instant.now(),
                    null
            );
            runtimeAppendLinkService.createLink(appendLink);
        }
    }

    public void createDynamicBuildOutcomeLink(
            String processInstanceId,
            String sourceNodeId,
            String buildMode,
            String appendPolicy,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            String targetTaskId,
            String targetInstanceId,
            String status,
            String runtimeLinkType,
            String commentText,
            String operatorUserId
    ) {
        String rootInstanceId = resolveRuntimeTreeRootInstanceId(processInstanceId);
        String sourceStructureId = buildDynamicBuildSourceTaskId(processInstanceId, sourceNodeId);
        String calledProcessKey = "SUBPROCESS_CALLS".equals(buildMode) ? stringValue(config.get("calledProcessKey")) : null;
        String calledDefinitionId = null;
        String calledVersionPolicy = "SUBPROCESS_CALLS".equals(buildMode) ? stringValue(config.get("calledVersionPolicy")) : null;
        Integer calledVersion = null;
        if ("SUBPROCESS_CALLS".equals(buildMode)) {
            Object calledVersionValue = config.get("calledVersion");
            if (calledVersionValue instanceof Number number) {
                calledVersion = number.intValue();
            } else if (calledVersionValue instanceof String stringValue && !stringValue.isBlank()) {
                try {
                    calledVersion = Integer.parseInt(stringValue.trim());
                } catch (NumberFormatException ignored) {
                    calledVersion = null;
                }
            }
        }
        RuntimeAppendLinkRecord appendLink = new RuntimeAppendLinkRecord(
                UUID.randomUUID().toString(),
                rootInstanceId,
                processInstanceId,
                sourceStructureId,
                sourceNodeId,
                "SUBPROCESS_CALLS".equals(buildMode) ? "SUBPROCESS" : "TASK",
                runtimeLinkType,
                appendPolicy,
                targetTaskId,
                targetInstanceId,
                null,
                calledProcessKey,
                calledDefinitionId,
                calledVersionPolicy,
                calledVersion,
                "SUBPROCESS_CALLS".equals(buildMode) ? "PROCESS_KEY" : "USER",
                stringValue(processVariables.get("westflowBusinessType")),
                stringValue(config.get("sceneCode")),
                status,
                "DYNAMIC_BUILD",
                operatorUserId,
                commentText,
                Instant.now(),
                Instant.now()
        );
        dynamicBuildOutcomeRecorder.record(appendLink);
    }

    private String buildGeneratedSubprocessRuntimeBusinessKey(String parentBusinessKey, String sourceNodeId, int index) {
        String normalizedParentBusinessKey = parentBusinessKey == null || parentBusinessKey.isBlank()
                ? "instance"
                : parentBusinessKey;
        return normalizedParentBusinessKey + "::dynamic-build::" + sourceNodeId + "::" + (index + 1);
    }

    private String buildDynamicBuildSourceTaskId(String processInstanceId, String sourceNodeId) {
        return processInstanceId + "::dynamic-build::" + sourceNodeId;
    }

    private String activeFlowableDefinitionId(String processInstanceId) {
        ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionId();
        }
        throw new ContractException(
                "PROCESS.INSTANCE_NOT_RUNNING",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "流程实例未运行",
                Map.of("processInstanceId", processInstanceId)
        );
    }

    private String resolveRuntimeTreeRootInstanceId(String instanceId) {
        ProcessLinkRecord processLink = processLinkService.getByChildInstanceId(instanceId);
        if (processLink != null) {
            return processLink.rootInstanceId();
        }
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetInstanceId(instanceId);
        if (appendLink != null && appendLink.rootInstanceId() != null && !appendLink.rootInstanceId().isBlank()) {
            return appendLink.rootInstanceId();
        }
        return instanceId;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }
}
