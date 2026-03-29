package com.westflow.processruntime.service.append;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processruntime.service.append.DynamicBuildResolutionService.DynamicBuildResolutionResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 追加与动态构建运行时编排入口。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class DynamicBuildAppendRuntimeService {

    private final DynamicBuildAppendCreationService creationService;
    private final DynamicBuildResolutionService resolutionService;

    public void executeDynamicBuilder(String processInstanceId, String sourceNodeId) {
        if (processInstanceId == null || processInstanceId.isBlank() || sourceNodeId == null || sourceNodeId.isBlank()) {
            return;
        }
        Map<String, Object> processVariables = creationService.runtimeVariables(processInstanceId);
        PublishedProcessDefinition parentDefinition = creationService.resolveParentDefinition(processInstanceId, processVariables);
        ProcessDslPayload.Node node = findDynamicBuilderNode(parentDefinition, sourceNodeId);
        if (node == null) {
            return;
        }
        Map<String, Object> config = resolutionService.mapValue(node.config());
        String buildMode = resolutionService.normalizeBuildMode(resolutionService.stringValue(config.get("buildMode")));
        int maxGeneratedCount = resolutionService.integerValue(config.get("maxGeneratedCount"));
        if (!supportsBuildMode(buildMode) || maxGeneratedCount <= 0) {
            return;
        }
        String appendPolicy = resolutionService.normalizeAppendPolicy(resolutionService.stringValue(config.get("appendPolicy")));
        String operatorUserId = creationService.resolveRuntimeOperatorUserId(processVariables);
        try {
            DynamicBuildResolutionResult resolution = resolutionService.resolveDynamicBuilderItems(
                    buildMode,
                    config,
                    processVariables,
                    maxGeneratedCount
            );
            if (resolution.items().isEmpty()) {
                recordSkippedOutcome(processInstanceId, sourceNodeId, buildMode, appendPolicy, config, processVariables, resolution, operatorUserId);
                return;
            }
            if ("APPROVER_TASKS".equals(buildMode)) {
                creationService.createDynamicBuildTasks(processInstanceId, sourceNodeId, node.name(), appendPolicy, resolution, operatorUserId);
                return;
            }
            creationService.createDynamicBuildSubprocesses(
                    processInstanceId,
                    sourceNodeId,
                    node.name(),
                    parentDefinition,
                    appendPolicy,
                    processVariables,
                    resolution,
                    operatorUserId
            );
        } catch (RuntimeException exception) {
            creationService.createDynamicBuildOutcomeLink(
                    processInstanceId,
                    sourceNodeId,
                    buildMode,
                    appendPolicy,
                    config,
                    processVariables,
                    null,
                    null,
                    "FAILED",
                    "DYNAMIC_BUILD_FAILED",
                    resolutionService.buildDynamicBuildFailureReason(exception),
                    operatorUserId
            );
            throw exception;
        }
    }

    private ProcessDslPayload.Node findDynamicBuilderNode(PublishedProcessDefinition parentDefinition, String sourceNodeId) {
        return parentDefinition.dsl().nodes().stream()
                .filter(item -> sourceNodeId.equals(item.id()))
                .filter(item -> "dynamic-builder".equals(item.type()))
                .findFirst()
                .orElse(null);
    }

    private boolean supportsBuildMode(String buildMode) {
        return List.of("APPROVER_TASKS", "SUBPROCESS_CALLS").contains(buildMode);
    }

    private void recordSkippedOutcome(
            String processInstanceId,
            String sourceNodeId,
            String buildMode,
            String appendPolicy,
            Map<String, Object> config,
            Map<String, Object> processVariables,
            DynamicBuildResolutionResult resolution,
            String operatorUserId
    ) {
        creationService.createDynamicBuildOutcomeLink(
                processInstanceId,
                sourceNodeId,
                buildMode,
                appendPolicy,
                config,
                processVariables,
                null,
                null,
                "SKIPPED",
                "DYNAMIC_BUILD_SKIPPED",
                resolutionService.buildDynamicBuildSkipReason(resolution),
                operatorUserId
        );
    }
}
