package com.westflow.processruntime.query;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 将 Flowable 任务组装为运行态列表和详情摘要。
public class RuntimeTaskAssembler {

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;
    private final RuntimeProcessPredictionService runtimeProcessPredictionService;

    // 将单个任务转换为列表项。
    public ProcessTaskListItemResponse toTaskListItem(
            Task task,
            RuntimeTaskProjectionContext projectionContext,
            RuntimeTaskQueryContext queryContext
    ) {
        String processInstanceId = task.getProcessInstanceId();
        Map<String, Object> variables = projectionContext.runtimeVariablesByInstanceId().computeIfAbsent(processInstanceId, this::runtimeVariables);
        PublishedProcessDefinition definition = projectionContext.definitionByInstanceId().computeIfAbsent(
                processInstanceId,
                instanceId -> resolvePublishedDefinitionForActiveTask(task, variables)
                        .orElseGet(() -> doResolvePublishedDefinitionByInstance(instanceId)
                                .orElseThrow(() -> resourceNotFound("流程定义不存在", Map.of("processInstanceId", instanceId))))
        );
        List<IdentityLink> identityLinks = projectionContext.identityLinksByTaskId().computeIfAbsent(task.getId(), runtimeTaskSupportService::identityLinksForTask);
        List<String> candidateUserIds = runtimeTaskSupportService.candidateUsers(identityLinks);
        List<String> candidateGroupIds = runtimeTaskSupportService.candidateGroups(identityLinks);
        String taskKind = runtimeTaskVisibilityService.resolveTaskKind(task, queryContext);
        ProcessPredictionResponse prediction = runtimeProcessPredictionService.predictForActiveTaskListItem(
                definition.processKey(),
                task.getTaskDefinitionKey(),
                task.getName(),
                task.getAssignee(),
                stringValue(variables.get("westflowBusinessType")),
                OffsetDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.of("Asia/Shanghai")),
                definition.dsl().nodes(),
                definition.dsl().edges()
        );
        return new ProcessTaskListItemResponse(
                task.getId(),
                processInstanceId,
                definition.processDefinitionId(),
                definition.processKey(),
                definition.processName(),
                stringValue(variables.get("westflowBusinessKey")),
                stringValue(variables.get("westflowBusinessType")),
                stringValue(variables.get("westflowInitiatorUserId")),
                stringValue(variables.get("westflowInitiatorPostId")),
                stringValue(variables.get("westflowInitiatorPostName")),
                stringValue(variables.get("westflowInitiatorDepartmentId")),
                stringValue(variables.get("westflowInitiatorDepartmentName")),
                task.getTaskDefinitionKey(),
                task.getName(),
                taskKind,
                runtimeTaskSupportService.resolveTaskStatus(task, identityLinks, taskKind, queryContext.taskLocalVariablesByTaskId()),
                runtimeTaskSupportService.resolveAssignmentMode(candidateUserIds, candidateGroupIds, task.getAssignee()),
                candidateUserIds,
                candidateGroupIds,
                task.getAssignee(),
                OffsetDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.of("Asia/Shanghai")),
                OffsetDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.of("Asia/Shanghai")),
                null,
                prediction
        );
    }

    // 按流程实例解析对应的已发布流程定义。
    public Optional<PublishedProcessDefinition> resolvePublishedDefinitionByInstance(String processInstanceId) {
        return doResolvePublishedDefinitionByInstance(processInstanceId);
    }

    // 判断任务是否匹配关键字。
    public boolean matchesTaskKeyword(ProcessTaskListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.processName(), normalized)
                || contains(item.processKey(), normalized)
                || contains(item.nodeName(), normalized)
                || contains(item.businessKey(), normalized)
                || contains(item.businessType(), normalized);
    }

    private Optional<PublishedProcessDefinition> doResolvePublishedDefinitionByInstance(String processInstanceId) {
        Map<String, Object> variables = runtimeOrHistoricVariables(processInstanceId);
        String platformDefinitionId = stringValue(variables.get("westflowProcessDefinitionId"));
        String processKey = stringValue(variables.get("westflowProcessKey"));
        String flowableDefinitionId = activeFlowableDefinitionId(processInstanceId);
        if ((platformDefinitionId == null || platformDefinitionId.isBlank())
                && flowableDefinitionId != null
                && !flowableDefinitionId.isBlank()) {
            try {
                return Optional.of(processDefinitionService.getByFlowableDefinitionId(flowableDefinitionId));
            } catch (RuntimeException ignored) {
                // 继续回退到流程实例级解析。
            }
        }
        if (processKey == null || processKey.isBlank()) {
            processKey = activeProcessKey(processInstanceId);
        }
        try {
            return Optional.of(resolvePublishedDefinition(platformDefinitionId, platformDefinitionId, processKey, processInstanceId));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<PublishedProcessDefinition> resolvePublishedDefinitionForActiveTask(Task task, Map<String, Object> variables) {
        String platformDefinitionId = stringValue(variables.get("westflowProcessDefinitionId"));
        if (platformDefinitionId != null && !platformDefinitionId.isBlank()) {
            return Optional.of(processDefinitionService.getById(platformDefinitionId));
        }
        String flowableDefinitionId = task.getProcessDefinitionId();
        if (flowableDefinitionId != null && !flowableDefinitionId.isBlank()) {
            try {
                return Optional.of(processDefinitionService.getByFlowableDefinitionId(flowableDefinitionId));
            } catch (RuntimeException ignored) {
                // 回退到更慢的流程实例级解析。
            }
        }
        return Optional.empty();
    }

    private PublishedProcessDefinition resolvePublishedDefinition(
            String preferredPlatformDefinitionId,
            String variablePlatformDefinitionId,
            String processKey,
            String processInstanceId
    ) {
        String platformDefinitionId = preferredPlatformDefinitionId != null && !preferredPlatformDefinitionId.isBlank()
                ? preferredPlatformDefinitionId
                : variablePlatformDefinitionId;
        if (platformDefinitionId != null && !platformDefinitionId.isBlank()) {
            return processDefinitionService.getById(platformDefinitionId);
        }
        if (processKey == null || processKey.isBlank()) {
            throw resourceNotFound("流程定义不存在", Map.of("processInstanceId", processInstanceId));
        }
        return processDefinitionService.getLatestByProcessKey(processKey);
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = flowableEngineFacade.runtimeService().getVariables(processInstanceId);
        return variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    private Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        try {
            Map<String, Object> runtimeValues = runtimeVariables(processInstanceId);
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
        } catch (FlowableObjectNotFoundException exception) {
            // 继续使用历史变量兜底。
        }
        return historicVariables(processInstanceId);
    }

    private String activeFlowableDefinitionId(String processInstanceId) {
        org.flowable.engine.runtime.ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionId();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return historicInstance == null ? null : historicInstance.getProcessDefinitionId();
    }

    private String activeProcessKey(String processInstanceId) {
        org.flowable.engine.runtime.ProcessInstance runtimeInstance = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance != null) {
            return runtimeInstance.getProcessDefinitionKey();
        }
        HistoricProcessInstance historicInstance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return historicInstance == null ? null : historicInstance.getProcessDefinitionKey();
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

    private ContractException resourceNotFound(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                details
        );
    }
}
