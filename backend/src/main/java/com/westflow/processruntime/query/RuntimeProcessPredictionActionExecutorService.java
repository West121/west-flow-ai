package com.westflow.processruntime.query;

import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.service.NotificationDispatchService;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.processruntime.api.response.ProcessPredictionAutomationActionResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import com.westflow.processruntime.trace.RuntimeInstanceEventRecorder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionActionExecutorService {

    private static final String DEFAULT_CHANNEL_CODE = "in_app_default";
    private static final String SYSTEM_OPERATOR = "system";

    private final OrchestratorExecutionRepository orchestratorExecutionRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final RuntimeInstanceEventRecorder runtimeInstanceEventRecorder;
    private final RuntimeProcessPredictionGovernanceService runtimeProcessPredictionGovernanceService;
    private final RuntimeProcessPredictionAutomationProperties automationProperties;

    public ProcessPredictionResponse execute(
            String processInstanceId,
            String taskId,
            String processName,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String initiatorUserId,
            ProcessPredictionResponse prediction
    ) {
        if (prediction == null || prediction.automationActions() == null || prediction.automationActions().isEmpty()) {
            return prediction;
        }
        List<ProcessPredictionAutomationActionResponse> resolvedActions = new ArrayList<>();
        for (ProcessPredictionAutomationActionResponse action : prediction.automationActions()) {
            if (!"READY".equalsIgnoreCase(action.status())) {
                resolvedActions.add(action);
                continue;
            }
            resolvedActions.add(executeAction(
                    processInstanceId,
                    taskId,
                    processName,
                    currentNodeId,
                    currentNodeName,
                    assigneeUserId,
                    initiatorUserId,
                    action
            ));
        }
        return copyWithActions(prediction, resolvedActions);
    }

    private ProcessPredictionAutomationActionResponse executeAction(
            String processInstanceId,
            String taskId,
            String processName,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String initiatorUserId,
            ProcessPredictionAutomationActionResponse action
    ) {
        if (!runtimeProcessPredictionGovernanceService.isActionEnabled(action.actionType())) {
            return skippedAction(action, "当前自动动作已被治理开关禁用");
        }
        if (runtimeProcessPredictionGovernanceService.isInQuietHoursNow()) {
            return skippedAction(action, "当前处于静默时间窗，自动动作已跳过");
        }
        String targetId = buildTargetId(processInstanceId, currentNodeId, action.actionType());
        Instant now = Instant.now();
        Instant dedupCutoff = now.minusSeconds(Math.max(1, automationProperties.getDedupWindowMinutes()) * 60L);
        if (orchestratorExecutionRepository.countSucceededByTargetIdSince(targetId, dedupCutoff) > 0) {
            return new ProcessPredictionAutomationActionResponse(
                    action.actionType(),
                    action.mode(),
                    "EXECUTED",
                    action.title(),
                    action.detail() + "（节流窗口内已执行）"
            );
        }
        String recipient = resolveRecipient(action.actionType(), assigneeUserId, initiatorUserId);
        if (requiresNotification(action.actionType()) && (recipient == null || recipient.isBlank())) {
            orchestratorExecutionRepository.insert(new OrchestratorScanExecutionRecord(
                    newId("orc_exec"),
                    "prediction_v3_auto",
                    targetId,
                    resolveAutomationType(action.actionType()),
                    OrchestratorExecutionStatus.SKIPPED,
                    "缺少有效接收人，跳过自动动作",
                    now
            ));
            return new ProcessPredictionAutomationActionResponse(
                    action.actionType(),
                    action.mode(),
                    "SKIPPED",
                    action.title(),
                    action.detail() + "（未找到有效接收人，自动动作已跳过）"
            );
        }
        try {
            if (recipient != null) {
                notificationDispatchService.dispatchByChannelCode(
                        effectiveChannelCode(),
                        new NotificationDispatchRequest(
                                recipient,
                                processName == null || processName.isBlank() ? action.title() : processName + " · " + action.title(),
                                action.detail(),
                                Map.of(
                                        "processInstanceId", safe(processInstanceId),
                                        "taskId", safe(taskId),
                                        "nodeId", safe(currentNodeId),
                                        "actionType", safe(action.actionType())
                                )
                        )
                );
            }
            runtimeInstanceEventRecorder.appendInstanceEvent(
                    processInstanceId,
                    taskId,
                    currentNodeId,
                    resolveEventType(action.actionType()),
                    action.title(),
                    "AUTOMATION",
                    taskId,
                    taskId,
                    recipient,
                    runtimeInstanceEventRecorder.eventDetails(
                            "comment", action.detail(),
                            "automationType", action.actionType(),
                            "nodeName", currentNodeName,
                            "mode", action.mode()
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    SYSTEM_OPERATOR
            );
            orchestratorExecutionRepository.insert(new OrchestratorScanExecutionRecord(
                    newId("orc_exec"),
                    "prediction_v3_auto",
                    targetId,
                    resolveAutomationType(action.actionType()),
                    OrchestratorExecutionStatus.SUCCEEDED,
                    action.detail(),
                    now
            ));
            return new ProcessPredictionAutomationActionResponse(
                    action.actionType(),
                    action.mode(),
                    "EXECUTED",
                    action.title(),
                    action.detail()
            );
        } catch (Exception exception) {
            orchestratorExecutionRepository.insert(new OrchestratorScanExecutionRecord(
                    newId("orc_exec"),
                    "prediction_v3_auto",
                    targetId,
                    resolveAutomationType(action.actionType()),
                    OrchestratorExecutionStatus.FAILED,
                    exception.getMessage(),
                    now
            ));
            return new ProcessPredictionAutomationActionResponse(
                    action.actionType(),
                    action.mode(),
                    "FAILED",
                    action.title(),
                    action.detail() + "（执行失败：" + safe(exception.getMessage()) + "）"
            );
        }
    }

    private ProcessPredictionAutomationActionResponse skippedAction(
            ProcessPredictionAutomationActionResponse action,
            String reason
    ) {
        return new ProcessPredictionAutomationActionResponse(
                action.actionType(),
                action.mode(),
                "SKIPPED",
                action.title(),
                action.detail() + "（" + reason + "）"
        );
    }

    private ProcessPredictionResponse copyWithActions(
            ProcessPredictionResponse prediction,
            List<ProcessPredictionAutomationActionResponse> automationActions
    ) {
        return new ProcessPredictionResponse(
                prediction.predictedFinishTime(),
                prediction.predictedRiskThresholdTime(),
                prediction.remainingDurationMinutes(),
                prediction.currentElapsedMinutes(),
                prediction.currentNodeDurationP50Minutes(),
                prediction.currentNodeDurationP75Minutes(),
                prediction.currentNodeDurationP90Minutes(),
                prediction.overdueRiskLevel(),
                prediction.confidence(),
                prediction.historicalSampleSize(),
                prediction.outlierFilteredSampleSize(),
                prediction.sampleProfile(),
                prediction.sampleTier(),
                prediction.workingDayProfile(),
                prediction.organizationProfile(),
                prediction.basisSummary(),
                prediction.noPredictionReason(),
                prediction.explanation(),
                prediction.narrativeExplanation(),
                prediction.bottleneckAttribution(),
                prediction.topDelayReasons(),
                prediction.recommendedActions(),
                prediction.optimizationSuggestions(),
                automationActions,
                prediction.featureSnapshot(),
                prediction.nextNodeCandidates(),
                prediction.sampleLayer(),
                prediction.predictedPathRemainingMinutes(),
                prediction.predictedPathTotalDurationMinutes(),
                prediction.predictedPathRiskLevel(),
                prediction.predictedPathConfidence(),
                prediction.predictedPathNodeIds(),
                prediction.predictedPathNodeNames(),
                prediction.evaluationReport()
        );
    }

    private String resolveRecipient(String actionType, String assigneeUserId, String initiatorUserId) {
        return switch (actionType) {
            case "AUTO_URGE", "SLA_REMINDER" -> nonBlankOrNull(assigneeUserId);
            case "NEXT_NODE_PRE_NOTIFY", "COLLABORATION_ACTION" -> firstNonBlank(initiatorUserId, assigneeUserId);
            default -> firstNonBlank(assigneeUserId, initiatorUserId);
        };
    }

    private boolean requiresNotification(String actionType) {
        return switch (actionType) {
            case "AUTO_URGE", "SLA_REMINDER", "NEXT_NODE_PRE_NOTIFY" -> true;
            default -> false;
        };
    }

    private String effectiveChannelCode() {
        String configured = automationProperties.getChannelCode();
        return configured == null || configured.isBlank() ? DEFAULT_CHANNEL_CODE : configured.trim();
    }

    private OrchestratorAutomationType resolveAutomationType(String actionType) {
        return switch (actionType) {
            case "AUTO_URGE" -> OrchestratorAutomationType.PREDICTION_AUTO_URGE;
            case "SLA_REMINDER" -> OrchestratorAutomationType.PREDICTION_SLA_REMINDER;
            case "NEXT_NODE_PRE_NOTIFY" -> OrchestratorAutomationType.PREDICTION_NEXT_NODE_PRE_NOTIFY;
            case "COLLABORATION_ACTION" -> OrchestratorAutomationType.PREDICTION_COLLABORATION_ACTION;
            default -> OrchestratorAutomationType.PREDICTION_COLLABORATION_ACTION;
        };
    }

    private String resolveEventType(String actionType) {
        return switch (actionType) {
            case "AUTO_URGE" -> "PREDICTION_AUTO_URGED";
            case "SLA_REMINDER" -> "PREDICTION_SLA_REMINDER";
            case "NEXT_NODE_PRE_NOTIFY" -> "PREDICTION_NEXT_NODE_PRE_NOTIFY";
            case "COLLABORATION_ACTION" -> "PREDICTION_COLLABORATION";
            default -> "PREDICTION_AUTOMATION";
        };
    }

    private String buildTargetId(String processInstanceId, String currentNodeId, String actionType) {
        return "orc_target_" + compactInstanceKey(processInstanceId)
                + "_" + safe(currentNodeId, "process")
                + "_" + normalizeActionSegment(actionType);
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String safe(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return nonBlankOrNull(fallback);
    }

    private String nonBlankOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeActionSegment(String actionType) {
        return switch (actionType == null ? "" : actionType.trim().toUpperCase()) {
            case "AUTO_URGE" -> "au";
            case "SLA_REMINDER" -> "sr";
            case "NEXT_NODE_PRE_NOTIFY" -> "np";
            case "COLLABORATION_ACTION" -> "ca";
            default -> "pd";
        };
    }

    private String compactInstanceKey(String processInstanceId) {
        String value = processInstanceId == null || processInstanceId.isBlank() ? "unknown" : processInstanceId.trim();
        return Integer.toHexString(value.hashCode());
    }
}
