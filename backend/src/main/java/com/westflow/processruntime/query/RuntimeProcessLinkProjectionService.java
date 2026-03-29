package com.westflow.processruntime.query;

import com.westflow.common.error.ContractException;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.response.ProcessTaskSnapshot;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.link.ProcessLinkService;
import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessLinkProjectionService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final ProcessLinkService processLinkService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskSupportService runtimeTaskSupportService;

    public ProcessInstanceLinkResponse requireLinkResponse(String linkId) {
        return toProcessInstanceLinkResponse(requireProcessLink(linkId), 0, 0);
    }

    public ProcessInstanceLinkResponse toProcessInstanceLinkResponse(
            ProcessLinkRecord record,
            int descendantCount,
            int runningDescendantCount
    ) {
        RuntimeProcessMetadataService.NodeMetadata parentNode =
                runtimeProcessMetadataService.resolveNodeMetadata(record.parentInstanceId(), record.parentNodeId());
        Map<String, Object> nodeConfig =
                runtimeProcessMetadataService.resolveNodeConfig(record.parentInstanceId(), record.parentNodeId());
        RuntimeProcessMetadataService.DefinitionMetadata childDefinition =
                runtimeProcessMetadataService.resolveDefinitionMetadata(
                        record.childInstanceId(),
                        record.calledDefinitionId(),
                        record.calledProcessKey()
                );
        RuntimeProcessMetadataService.SubprocessStructureMetadata structureMetadata =
                runtimeProcessMetadataService.resolveSubprocessStructureMetadata(record);
        Integer calledVersion = runtimeProcessMetadataService.integerValue(nodeConfig.get("calledVersion"));
        boolean parentConfirmationRequired = "WAIT_PARENT_CONFIRM".equals(record.status());
        return new ProcessInstanceLinkResponse(
                record.id(),
                record.rootInstanceId(),
                record.parentInstanceId(),
                record.childInstanceId(),
                record.parentNodeId(),
                parentNode.nodeName(),
                parentNode.nodeType(),
                record.calledProcessKey(),
                record.calledDefinitionId(),
                stringValueOrDefault(nodeConfig.get("calledVersionPolicy"), "LATEST_PUBLISHED"),
                calledVersion == null || calledVersion <= 0 ? null : calledVersion,
                childDefinition.processName(),
                childDefinition.version(),
                record.linkType(),
                record.status(),
                record.terminatePolicy(),
                record.childFinishPolicy(),
                structureMetadata.callScope(),
                structureMetadata.joinMode(),
                structureMetadata.childStartStrategy(),
                record.childStartDecisionReason(),
                structureMetadata.parentResumeStrategy(),
                resolveSubprocessResumeDecisionReason(record, structureMetadata),
                parentConfirmationRequired,
                descendantCount,
                runningDescendantCount,
                record.createdAt() == null ? null : OffsetDateTime.ofInstant(record.createdAt(), TIME_ZONE),
                record.finishedAt() == null ? null : OffsetDateTime.ofInstant(record.finishedAt(), TIME_ZONE)
        );
    }

    public RuntimeAppendLinkResponse toRuntimeAppendLinkResponse(RuntimeAppendLinkRecord record) {
        RuntimeProcessMetadataService.NodeMetadata sourceNode =
                runtimeProcessMetadataService.resolveNodeMetadata(record.parentInstanceId(), record.sourceNodeId());
        Map<String, Object> sourceNodeConfig =
                runtimeProcessMetadataService.resolveNodeConfig(record.parentInstanceId(), record.sourceNodeId());
        RuntimeProcessMetadataService.DefinitionMetadata targetDefinition =
                runtimeProcessMetadataService.resolveTargetProcessDefinition(record);
        String targetTaskName = runtimeProcessMetadataService.resolveTaskName(record.targetTaskId());
        Map<String, Object> runtimeMetadata = resolveDynamicBuildRuntimeMetadata(record);
        return new RuntimeAppendLinkResponse(
                record.id(),
                record.rootInstanceId(),
                record.parentInstanceId(),
                record.sourceTaskId(),
                record.sourceNodeId(),
                sourceNode.nodeName(),
                sourceNode.nodeType(),
                record.appendType(),
                record.runtimeLinkType(),
                record.policy(),
                record.targetTaskId(),
                targetTaskName,
                record.targetInstanceId(),
                record.targetUserId(),
                record.calledProcessKey(),
                record.calledDefinitionId(),
                record.calledVersionPolicy(),
                record.calledVersion(),
                targetDefinition.processName(),
                targetDefinition.version(),
                record.status(),
                record.triggerMode(),
                stringValue(sourceNodeConfig.get("buildMode")),
                normalizeDynamicBuilderSourceMode(stringValue(sourceNodeConfig.get("sourceMode"))),
                stringValueOrDefault(record.resolvedTargetMode(), resolveDynamicBuilderTargetMode(record, sourceNodeConfig)),
                stringValueOrDefault(
                        record.targetBusinessType(),
                        stringValue(runtimeProcessMetadataService.runtimeOrHistoricVariables(record.parentInstanceId()).get("westflowBusinessType"))
                ),
                stringValueOrDefault(record.targetSceneCode(), stringValue(sourceNodeConfig.get("sceneCode"))),
                stringValue(sourceNodeConfig.get("ruleExpression")),
                stringValue(sourceNodeConfig.get("manualTemplateCode")),
                stringValue(sourceNodeConfig.get("sceneCode")),
                stringValueOrDefault(
                        stringValue(runtimeMetadata.get("westflowDynamicExecutionStrategy")),
                        stringValue(sourceNodeConfig.get("executionStrategy"))
                ),
                stringValueOrDefault(
                        stringValue(runtimeMetadata.get("westflowDynamicFallbackStrategy")),
                        stringValue(sourceNodeConfig.get("fallbackStrategy"))
                ),
                runtimeProcessMetadataService.integerValue(runtimeMetadata.get("westflowDynamicMaxGeneratedCount")),
                runtimeProcessMetadataService.integerValue(runtimeMetadata.get("westflowDynamicGeneratedCount")),
                booleanValue(runtimeMetadata.get("westflowDynamicGenerationTruncated")),
                stringValue(runtimeMetadata.get("westflowDynamicResolvedSourceMode")),
                stringValue(runtimeMetadata.get("westflowDynamicResolutionPath")),
                stringValue(runtimeMetadata.get("westflowDynamicTemplateSource")),
                record.operatorUserId(),
                record.commentText(),
                resolveDynamicBuildResolutionStatus(record),
                resolveDynamicBuildResolutionReason(record),
                record.createdAt() == null ? null : OffsetDateTime.ofInstant(record.createdAt(), TIME_ZONE),
                record.finishedAt() == null ? null : OffsetDateTime.ofInstant(record.finishedAt(), TIME_ZONE)
        );
    }

    public ProcessTaskSnapshot toAppendTaskView(Task task) {
        return new ProcessTaskSnapshot(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create()),
                runtimeTaskSupportService.resolveTaskStatus(task),
                runtimeTaskSupportService.resolveAssignmentMode(
                        runtimeTaskSupportService.candidateUsers(task.getId()),
                        runtimeTaskSupportService.candidateGroups(task.getId()),
                        task.getAssignee()
                ),
                runtimeTaskSupportService.candidateUsers(task.getId()),
                runtimeTaskSupportService.candidateGroups(task.getId()),
                task.getAssignee(),
                runtimeTaskSupportService.resolveActingMode(task, null),
                resolveActingForUserId(task, null),
                resolveDelegatedByUserId(task, null),
                null
        );
    }

    private ProcessLinkRecord requireProcessLink(String linkId) {
        ProcessLinkRecord link = processLinkService.getById(linkId);
        if (link == null) {
            throw new ContractException(
                    "PROCESS.SUBPROCESS_LINK_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "主子流程关联不存在",
                    Map.of("linkId", linkId)
            );
        }
        return link;
    }

    private String resolveSubprocessResumeDecisionReason(
            ProcessLinkRecord record,
            RuntimeProcessMetadataService.SubprocessStructureMetadata structureMetadata
    ) {
        if ("WAIT_PARENT_CONFIRM".equals(record.status())) {
            return "WAIT_PARENT_CONFIRM";
        }
        if ("TERMINATED".equals(record.status())) {
            return "CHILD_TERMINATED";
        }
        if ("TERMINATE_PARENT".equals(record.childFinishPolicy())) {
            return "CHILD_FINISH_TERMINATES_PARENT";
        }
        if ("WAIT_PARENT_CONFIRM".equals(structureMetadata.parentResumeStrategy())) {
            return "PARENT_CONFIRM_RESUMED";
        }
        return "AUTO_RETURN";
    }

    private Map<String, Object> resolveDynamicBuildRuntimeMetadata(RuntimeAppendLinkRecord record) {
        if (record.targetTaskId() != null && !record.targetTaskId().isBlank()) {
            Map<String, Object> runtimeValues = taskLocalVariables(record.targetTaskId());
            if (!runtimeValues.isEmpty()) {
                return runtimeValues;
            }
            Map<String, Object> historicValues = historicTaskLocalVariables(record.targetTaskId());
            return historicValues.isEmpty() ? Map.of() : historicValues;
        }
        if (record.targetInstanceId() != null && !record.targetInstanceId().isBlank()) {
            Map<String, Object> values = runtimeProcessMetadataService.runtimeOrHistoricVariables(record.targetInstanceId());
            return values.isEmpty() ? Map.of() : values;
        }
        if ("DYNAMIC_BUILD".equals(record.triggerMode())) {
            Map<String, Object> sourceNodeConfig =
                    runtimeProcessMetadataService.resolveNodeConfig(record.parentInstanceId(), record.sourceNodeId());
            if (!sourceNodeConfig.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("westflowDynamicResolvedSourceMode", normalizeDynamicBuilderSourceMode(stringValue(sourceNodeConfig.get("sourceMode"))));
                metadata.put("westflowDynamicResolutionPath", record.status());
                metadata.put("westflowDynamicExecutionStrategy", stringValue(sourceNodeConfig.get("executionStrategy")));
                metadata.put("westflowDynamicFallbackStrategy", stringValue(sourceNodeConfig.get("fallbackStrategy")));
                metadata.put("westflowDynamicMaxGeneratedCount", runtimeProcessMetadataService.integerValue(sourceNodeConfig.get("maxGeneratedCount")));
                metadata.put("westflowDynamicGeneratedCount", 0);
                metadata.put("westflowDynamicGenerationTruncated", false);
                String templateSource = stringValue(sourceNodeConfig.get("manualTemplateCode"));
                if (templateSource == null || templateSource.isBlank()) {
                    templateSource = stringValue(sourceNodeConfig.get("sceneCode"));
                }
                if (templateSource != null && !templateSource.isBlank()) {
                    metadata.put("westflowDynamicTemplateSource", templateSource);
                }
                return metadata;
            }
        }
        return Map.of();
    }

    private Map<String, Object> taskLocalVariables(String taskId) {
        return runtimeTaskVisibilityService.taskLocalVariables(taskId, RuntimeTaskQueryContext.create());
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

    private String resolveDynamicBuildResolutionStatus(RuntimeAppendLinkRecord record) {
        if (record == null || record.triggerMode() == null || !"DYNAMIC_BUILD".equals(record.triggerMode())) {
            return null;
        }
        return switch (record.status()) {
            case "FAILED", "SKIPPED" -> record.status();
            default -> "SUCCESS";
        };
    }

    private String resolveDynamicBuildResolutionReason(RuntimeAppendLinkRecord record) {
        if (record == null || record.triggerMode() == null || !"DYNAMIC_BUILD".equals(record.triggerMode())) {
            return null;
        }
        if ("FAILED".equals(record.status()) || "SKIPPED".equals(record.status())) {
            return record.commentText();
        }
        return null;
    }

    private String resolveDynamicBuilderTargetMode(RuntimeAppendLinkRecord record, Map<String, Object> sourceNodeConfig) {
        if (record.resolvedTargetMode() != null && !record.resolvedTargetMode().isBlank()) {
            return record.resolvedTargetMode();
        }
        if (record.targetUserId() != null && !record.targetUserId().isBlank()) {
            return "USER";
        }
        if (record.calledProcessKey() != null && !record.calledProcessKey().isBlank()) {
            return "PROCESS_KEY";
        }
        return normalizeDynamicBuilderSourceMode(stringValue(sourceNodeConfig.get("sourceMode")));
    }

    private String resolveActingForUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowActingForUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        String ownerUserId = activeTask != null ? activeTask.getOwner() : historicTask == null ? null : historicTask.getOwner();
        String assigneeUserId = activeTask != null ? activeTask.getAssignee() : historicTask == null ? null : historicTask.getAssignee();
        if (ownerUserId != null && assigneeUserId != null && !ownerUserId.equals(assigneeUserId)) {
            return ownerUserId;
        }
        return null;
    }

    private String resolveDelegatedByUserId(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? taskLocalVariables(activeTask.getId())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitUserId = stringValue(localVariables.get("westflowDelegatedByUserId"));
        if (explicitUserId != null) {
            return explicitUserId;
        }
        return null;
    }

    private String normalizeDynamicBuilderSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return "RULE_DRIVEN";
        }
        return switch (sourceMode.trim().toUpperCase()) {
            case "RULE", "RULE_DRIVEN" -> "RULE_DRIVEN";
            case "MANUAL_TEMPLATE" -> "MANUAL_TEMPLATE";
            case "MODEL_DRIVEN" -> "MODEL_DRIVEN";
            default -> "RULE_DRIVEN";
        };
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.parseBoolean(stringValue.trim());
        }
        return null;
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
