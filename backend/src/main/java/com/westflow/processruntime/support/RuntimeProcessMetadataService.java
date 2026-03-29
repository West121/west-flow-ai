package com.westflow.processruntime.support;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.link.RuntimeAppendLinkService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessMetadataService {

    public record NodeMetadata(String nodeName, String nodeType) {
    }

    public record DefinitionMetadata(String processName, Integer version) {
    }

    public record SubprocessStructureMetadata(
            String callScope,
            String joinMode,
            String childStartStrategy,
            String parentResumeStrategy
    ) {
    }

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessDefinitionService processDefinitionService;
    private final ProcessLinkService processLinkService;
    private final RuntimeAppendLinkService runtimeAppendLinkService;

    public PublishedProcessDefinition resolvePublishedDefinition(
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

    public Optional<PublishedProcessDefinition> resolvePublishedDefinitionByInstance(String processInstanceId) {
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
                // Fall through to process key resolution below.
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

    public NodeMetadata resolveNodeMetadata(String processInstanceId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return new NodeMetadata(null, null);
        }
        return resolvePublishedDefinitionByInstance(processInstanceId)
                .map(definition -> definition.dsl().nodes().stream()
                        .filter(node -> nodeId.equals(node.id()))
                        .findFirst()
                        .map(node -> new NodeMetadata(
                                node.name() == null || node.name().isBlank() ? nodeId : node.name(),
                                node.type()
                        ))
                        .orElse(new NodeMetadata(nodeId, null)))
                .orElse(new NodeMetadata(nodeId, null));
    }

    public Map<String, Object> resolveNodeConfig(String processInstanceId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Map.of();
        }
        return resolvePublishedDefinitionByInstance(processInstanceId)
                .map(definition -> nodeConfig(definition.dsl(), nodeId))
                .orElse(Map.of());
    }

    public DefinitionMetadata resolveDefinitionMetadata(String processInstanceId, String definitionId, String processKey) {
        try {
            PublishedProcessDefinition definition;
            if (definitionId != null && !definitionId.isBlank()) {
                try {
                    definition = processDefinitionService.getById(definitionId);
                } catch (RuntimeException ignored) {
                    definition = processDefinitionService.getByFlowableDefinitionId(definitionId);
                }
            } else {
                Map<String, Object> variables = runtimeOrHistoricVariables(processInstanceId);
                definition = resolvePublishedDefinition(
                        stringValue(variables.get("westflowProcessDefinitionId")),
                        stringValue(variables.get("westflowProcessDefinitionId")),
                        processKey,
                        processInstanceId
                );
            }
            return new DefinitionMetadata(definition.processName(), definition.version());
        } catch (RuntimeException exception) {
            return new DefinitionMetadata(null, null);
        }
    }

    public DefinitionMetadata resolveTargetProcessDefinition(RuntimeAppendLinkRecord record) {
        if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
            return resolveDefinitionMetadata(record.targetInstanceId(), record.calledDefinitionId(), record.calledProcessKey());
        }
        return resolveDefinitionMetadata(record.parentInstanceId(), record.calledDefinitionId(), record.calledProcessKey());
    }

    public SubprocessStructureMetadata resolveSubprocessStructureMetadata(String processInstanceId, String nodeId) {
        Map<String, Object> config = resolveNodeConfig(processInstanceId, nodeId);
        return new SubprocessStructureMetadata(
                stringValueOrDefault(config.get("callScope"), "CHILD_ONLY"),
                stringValueOrDefault(config.get("joinMode"), "AUTO_RETURN"),
                stringValueOrDefault(config.get("childStartStrategy"), "LATEST_PUBLISHED"),
                stringValueOrDefault(config.get("parentResumeStrategy"), "AUTO_RETURN")
        );
    }

    public SubprocessStructureMetadata resolveSubprocessStructureMetadata(
            com.westflow.processruntime.model.ProcessLinkRecord record
    ) {
        SubprocessStructureMetadata fallback = resolveSubprocessStructureMetadata(
                record.parentInstanceId(),
                record.parentNodeId()
        );
        return new SubprocessStructureMetadata(
                stringValueOrDefault(record.callScope(), fallback.callScope()),
                stringValueOrDefault(record.joinMode(), fallback.joinMode()),
                stringValueOrDefault(record.childStartStrategy(), fallback.childStartStrategy()),
                stringValueOrDefault(record.parentResumeStrategy(), fallback.parentResumeStrategy())
        );
    }

    public boolean requiresParentConfirmation(SubprocessStructureMetadata structureMetadata) {
        return "WAIT_PARENT_CONFIRM".equals(structureMetadata.parentResumeStrategy())
                || "WAIT_PARENT_CONFIRM".equals(structureMetadata.joinMode());
    }

    public String resolveTaskName(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        Task runtimeTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (runtimeTask != null) {
            return runtimeTask.getName();
        }
        HistoricTaskInstance historicTask = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        return historicTask == null ? null : historicTask.getName();
    }

    public Map<String, Object> runtimeVariables(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.runtimeService().getVariables(processInstanceId).forEach((key, value) -> {
            if (value != null) {
                variables.put(key, value);
            }
        });
        return variables;
    }

    public Map<String, Object> runtimeOrHistoricVariables(String processInstanceId) {
        try {
            Map<String, Object> runtimeValues = runtimeVariables(processInstanceId);
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
        } catch (FlowableObjectNotFoundException ignored) {
            // Fall through to historic variables.
        }
        return historicVariables(processInstanceId);
    }

    public String activeFlowableDefinitionId(String processInstanceId) {
        try {
            HistoricProcessInstance historic = flowableEngineFacade.historyService()
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historic != null) {
                return historic.getProcessDefinitionId();
            }
        } catch (RuntimeException ignored) {
            // Fall back to runtime query below.
        }
        var runtime = flowableEngineFacade.runtimeService().createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return runtime == null ? null : runtime.getProcessDefinitionId();
    }

    public String activeProcessKey(String processInstanceId) {
        String definitionId = activeFlowableDefinitionId(processInstanceId);
        if (definitionId == null || definitionId.isBlank()) {
            return null;
        }
        try {
            return processDefinitionService.getByFlowableDefinitionId(definitionId).processKey();
        } catch (RuntimeException ignored) {
            HistoricProcessInstance historic = requireHistoricProcessInstance(processInstanceId);
            String fallbackKey = historic.getProcessDefinitionKey();
            return fallbackKey == null || fallbackKey.isBlank() ? null : fallbackKey;
        }
    }

    public Map<String, Object> historicVariables(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        flowableEngineFacade.historyService().createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    public HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
        HistoricProcessInstance instance = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw resourceNotFound("流程实例不存在", Map.of("instanceId", processInstanceId));
        }
        return instance;
    }

    public Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String resolveRuntimeTreeRootInstanceId(String instanceId) {
        String processLinkRootInstanceId = processLinkService.resolveRootInstanceId(instanceId);
        if (!instanceId.equals(processLinkRootInstanceId)) {
            return processLinkRootInstanceId;
        }
        RuntimeAppendLinkRecord appendLink = runtimeAppendLinkService.getByTargetInstanceId(instanceId);
        if (appendLink != null && appendLink.rootInstanceId() != null && !appendLink.rootInstanceId().isBlank()) {
            return appendLink.rootInstanceId();
        }
        return instanceId;
    }

    private ContractException resourceNotFound(String message, Map<String, Object> details) {
        return new ContractException(
                "PROCESS.RUNTIME_RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                details
        );
    }

    private Map<String, Object> nodeConfig(ProcessDslPayload payload, String nodeId) {
        return payload.nodes().stream()
                .filter(node -> Objects.equals(node.id(), nodeId))
                .findFirst()
                .map(node -> node.config() == null ? Map.<String, Object>of() : Map.copyOf(node.config()))
                .orElse(Map.of());
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String stringValueOrDefault(Object value, String defaultValue) {
        String text = stringValue(value);
        return text == null ? defaultValue : text;
    }
}
