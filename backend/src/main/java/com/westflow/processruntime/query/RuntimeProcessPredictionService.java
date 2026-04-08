package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessPredictionAutomationActionResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.response.ProcessPredictionFeatureSnapshotResponse;
import com.westflow.processruntime.api.response.ProcessPredictionNextNodeCandidateResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import com.westflow.processruntime.api.response.ProcessTaskTraceItemResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_NODE_SAMPLE_SIZE = 200;

    private final FlowableEngineFacade flowableEngineFacade;
    private final RuntimeProcessPredictionAutomationService runtimeProcessPredictionAutomationService;

    public ProcessPredictionResponse predict(
            String processKey,
            String instanceStatus,
            String currentNodeId,
            String currentNodeName,
            String currentTaskKind,
            String currentTaskSemanticMode,
            String currentAction,
            String currentActingMode,
            String currentActingForUserId,
            String currentDelegatedByUserId,
            String currentHandoverFromUserId,
            String assigneeUserId,
            String businessType,
            String organizationProfile,
            OffsetDateTime receiveTime,
            List<ProcessTaskTraceItemResponse> taskTrace,
            List<CountersignTaskGroupResponse> countersignGroups,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        OffsetDateTime now = OffsetDateTime.now(TIME_ZONE);
        if (currentNodeId == null || currentNodeId.isBlank()) {
            return buildUnavailablePrediction(
                    processKey,
                    currentNodeId,
                    currentNodeName,
                    assigneeUserId,
                    businessType,
                    organizationProfile,
                    now,
                    "未命中活动节点样本",
                    "当前实例没有明确的活动节点，暂时无法估算后续时长。"
            );
        }
        if ("COMPLETED".equalsIgnoreCase(instanceStatus)
                || "TERMINATED".equalsIgnoreCase(instanceStatus)
                || "REVOKED".equalsIgnoreCase(instanceStatus)) {
            return buildEndedPrediction(
                    processKey,
                    currentNodeId,
                    currentNodeName,
                    assigneeUserId,
                    businessType,
                    organizationProfile,
                    now
            );
        }

        Long currentElapsedMinutes = minutesBetween(receiveTime, now);
        PredictionScenarioSignals scenarioSignals = resolveScenarioSignals(
                currentTaskKind,
                currentTaskSemanticMode,
                currentAction,
                currentActingMode,
                currentActingForUserId,
                currentDelegatedByUserId,
                currentHandoverFromUserId,
                taskTrace,
                countersignGroups
        );
        PredictionSampleSet sampleSet = resolvePredictionSamples(
                processKey,
                currentNodeId,
                assigneeUserId,
                businessType,
                organizationProfile,
                now
        );
        Map<String, HistoricProcessInstance> processById = loadCompletedProcesses(sampleSet.tasks());
        Map<String, Map<String, Object>> processVariablesById = loadHistoricProcessVariables(processById.keySet());
        List<HistoricTaskContext> contexts = buildHistoricTaskContexts(sampleSet.tasks(), processById, processVariablesById);
        List<HistoricTaskContext> filteredContexts = contexts.stream()
                .filter(context -> matchesBusinessType(context, businessType))
                .filter(context -> matchesOrganizationProfile(context, organizationProfile))
                .toList();
        if (filteredContexts.isEmpty()) {
            filteredContexts = contexts;
        }

        List<Long> rawNodeDurationSamples = new ArrayList<>();
        List<Long> rawRemainingDurationSamples = new ArrayList<>();
        Map<String, TransitionStats> nextNodeStats = new LinkedHashMap<>();
        for (HistoricTaskContext context : filteredContexts) {
            OffsetDateTime startedAt = context.createdAt();
            OffsetDateTime taskEndedAt = toOffsetDateTime(context.task().getEndTime());
            OffsetDateTime processEndedAt = context.process() == null ? null : toOffsetDateTime(context.process().getEndTime());
            if (startedAt != null && taskEndedAt != null) {
                rawNodeDurationSamples.add(minutesBetween(startedAt, taskEndedAt));
            }
            if (startedAt != null && processEndedAt != null) {
                rawRemainingDurationSamples.add(minutesBetween(startedAt, processEndedAt));
            }
            resolveNextNode(context.task(), flowNodes, flowEdges).ifPresent(candidate -> {
                TransitionStats stats = nextNodeStats.computeIfAbsent(
                        candidate.nodeId(),
                        key -> new TransitionStats(candidate.nodeId(), candidate.nodeName())
                );
                stats.hitCount++;
                if (candidate.medianDurationMinutes() != null) {
                    stats.durations.add(candidate.medianDurationMinutes());
                }
            });
        }

        List<Long> nodeDurationSamples = cleanOutliers(rawNodeDurationSamples);
        List<Long> remainingDurationSamples = cleanOutliers(rawRemainingDurationSamples);
        int rawSampleSize = rawNodeDurationSamples.size();
        int filteredSampleSize = nodeDurationSamples.size();
        int outlierFilteredSampleSize = Math.max(0, rawSampleSize - filteredSampleSize);
        Long p50 = percentileOrNull(nodeDurationSamples, 0.50);
        Long p75 = percentileOrNull(nodeDurationSamples, 0.75);
        Long p90 = percentileOrNull(nodeDurationSamples, 0.90);
        String sampleTier = resolveSampleTier(filteredSampleSize, sampleSet.usedFallback());
        String workingDayProfile = resolveWorkingDayProfile(now);
        String resolvedOrganizationProfile = resolveOrganizationProfile(organizationProfile, filteredContexts);
        String sampleProfile = effectiveSampleProfile(
                sampleSet.profile(),
                businessType,
                filteredContexts.size(),
                sampleSet.tasks().size(),
                scenarioSignals
        );
        String riskLevel = resolveRiskLevel(currentElapsedMinutes, nodeDurationSamples, p50, p75, p90);
        List<ProcessPredictionNextNodeCandidateResponse> nextCandidates = nextNodeStats.isEmpty()
                ? fallbackCandidates(currentNodeId, flowNodes, flowEdges)
                : mapNextCandidates(nextNodeStats, filteredContexts.size(), p75);
        ProcessPredictionFeatureSnapshotResponse featureSnapshot = new ProcessPredictionFeatureSnapshotResponse(
                processKey,
                currentNodeId,
                businessType,
                assigneeUserId,
                resolvedOrganizationProfile,
                workingDayProfile,
                sampleTier,
                rawSampleSize,
                filteredSampleSize
        );

        if (remainingDurationSamples.isEmpty()) {
            ProcessPredictionResponse response = new ProcessPredictionResponse(
                    null,
                    null,
                    null,
                    currentElapsedMinutes,
                    p50,
                    p75,
                    p90,
                    riskLevel,
                    resolveConfidence(0, nextCandidates, true, sampleTier, scenarioSignals),
                    0,
                    outlierFilteredSampleSize,
                    sampleProfile,
                    sampleTier,
                    workingDayProfile,
                    resolvedOrganizationProfile,
                    "当前节点历史样本不足，已回退到流程图候选路径。",
                    "历史样本不足",
                    buildExplanation(currentNodeName, currentElapsedMinutes, null, riskLevel, nextCandidates, true, sampleProfile, scenarioSignals),
                    buildNarrativeExplanation(currentNodeName, currentElapsedMinutes, null, riskLevel, sampleProfile, scenarioSignals),
                    buildBottleneckAttribution(currentNodeName, currentElapsedMinutes, p75, p90, true, scenarioSignals),
                    buildDelayReasons(currentElapsedMinutes, nodeDurationSamples, true, scenarioSignals),
                    buildRecommendedActions(currentElapsedMinutes, riskLevel, nextCandidates, true, scenarioSignals),
                    buildOptimizationSuggestions(riskLevel, true, scenarioSignals),
                    List.of(),
                    featureSnapshot,
                    nextCandidates
            );
            return attachAutomationActions(response, currentNodeName);
        }

        long remainingMedian = applyScenarioBuffer(median(remainingDurationSamples), scenarioSignals);
        OffsetDateTime predictedFinishTime = now.plusMinutes(remainingMedian);
        OffsetDateTime predictedRiskThresholdTime = receiveTime == null || p75 == null
                ? null
                : receiveTime.plusMinutes(p75);
        ProcessPredictionResponse response = new ProcessPredictionResponse(
                predictedFinishTime,
                predictedRiskThresholdTime,
                remainingMedian,
                currentElapsedMinutes,
                p50,
                p75,
                p90,
                riskLevel,
                resolveConfidence(remainingDurationSamples.size(), nextCandidates, sampleSet.usedFallback(), sampleTier, scenarioSignals),
                remainingDurationSamples.size(),
                outlierFilteredSampleSize,
                sampleProfile,
                sampleTier,
                workingDayProfile,
                resolvedOrganizationProfile,
                buildBasisSummary(currentNodeName, sampleProfile, remainingDurationSamples.size(), remainingMedian, nextCandidates),
                null,
                buildExplanation(currentNodeName, currentElapsedMinutes, remainingMedian, riskLevel, nextCandidates, false, sampleProfile, scenarioSignals),
                buildNarrativeExplanation(currentNodeName, currentElapsedMinutes, remainingMedian, riskLevel, sampleProfile, scenarioSignals),
                buildBottleneckAttribution(currentNodeName, currentElapsedMinutes, p75, p90, false, scenarioSignals),
                buildDelayReasons(currentElapsedMinutes, nodeDurationSamples, false, scenarioSignals),
                buildRecommendedActions(currentElapsedMinutes, riskLevel, nextCandidates, false, scenarioSignals),
                buildOptimizationSuggestions(riskLevel, false, scenarioSignals),
                List.of(),
                featureSnapshot,
                nextCandidates
        );
        return attachAutomationActions(response, currentNodeName);
    }

    public ProcessPredictionResponse predictForActiveTaskListItem(
            String processKey,
            String currentNodeId,
            String currentNodeName,
            String currentTaskKind,
            String currentTaskSemanticMode,
            String currentAction,
            String currentActingMode,
            String currentActingForUserId,
            String currentDelegatedByUserId,
            String currentHandoverFromUserId,
            String assigneeUserId,
            String businessType,
            String organizationProfile,
            OffsetDateTime receiveTime,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        return predict(
                processKey,
                "RUNNING",
                currentNodeId,
                currentNodeName,
                currentTaskKind,
                currentTaskSemanticMode,
                currentAction,
                currentActingMode,
                currentActingForUserId,
                currentDelegatedByUserId,
                currentHandoverFromUserId,
                assigneeUserId,
                businessType,
                organizationProfile,
                receiveTime,
                List.of(),
                List.of(),
                flowNodes,
                flowEdges
        );
    }

    private ProcessPredictionResponse buildUnavailablePrediction(
            String processKey,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String businessType,
            String organizationProfile,
            OffsetDateTime now,
            String sampleProfile,
            String reason
    ) {
        ProcessPredictionResponse response = new ProcessPredictionResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "LOW",
                "LOW",
                0,
                0,
                sampleProfile,
                "WEAK",
                resolveWorkingDayProfile(now),
                stringValue(organizationProfile, "未命中组织画像"),
                reason,
                reason,
                reason,
                reason,
                "当前没有活动节点，暂无瓶颈归因。",
                List.of("当前实例没有明确的活动节点。"),
                List.of("等待新的运行节点激活后再查看预测。"),
                List.of("先查看流程图回顾和时间轴，再判断是否需要人工介入。"),
                List.of(),
                new ProcessPredictionFeatureSnapshotResponse(
                        processKey,
                        currentNodeId,
                        businessType,
                        assigneeUserId,
                        stringValue(organizationProfile, "未命中组织画像"),
                        resolveWorkingDayProfile(now),
                        "WEAK",
                        0,
                        0
                ),
                List.of()
        );
        return attachAutomationActions(response, currentNodeName);
    }

    private ProcessPredictionResponse buildEndedPrediction(
            String processKey,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String businessType,
            String organizationProfile,
            OffsetDateTime now
    ) {
        ProcessPredictionResponse response = new ProcessPredictionResponse(
                null,
                null,
                0L,
                0L,
                null,
                null,
                null,
                "LOW",
                "HIGH",
                0,
                0,
                "流程已结束，无需样本预测",
                "ENDED",
                resolveWorkingDayProfile(now),
                stringValue(organizationProfile, "未命中组织画像"),
                "流程已结束，无需预测剩余审批时长。",
                "流程已结束",
                "流程已经结束，当前无需继续预测剩余节点和时长。",
                "流程已经结束，可转入回顾或归档视图。",
                "当前实例没有活跃瓶颈节点。",
                List.of("实例状态已结束。"),
                List.of("查看回顾时间轴和历史审批意见。"),
                List.of("把结束实例沉淀到复盘和统计面板。"),
                List.of(),
                new ProcessPredictionFeatureSnapshotResponse(
                        processKey,
                        currentNodeId,
                        businessType,
                        assigneeUserId,
                        stringValue(organizationProfile, "未命中组织画像"),
                        resolveWorkingDayProfile(now),
                        "ENDED",
                        0,
                        0
                ),
                List.of()
        );
        return attachAutomationActions(response, currentNodeName);
    }

    private PredictionSampleSet resolvePredictionSamples(
            String processKey,
            String currentNodeId,
            String assigneeUserId,
            String businessType,
            String organizationProfile,
            OffsetDateTime now
    ) {
        boolean workingDay = isWorkingDay(now);
        if (assigneeUserId != null && !assigneeUserId.isBlank()) {
            List<HistoricTaskInstance> assigneeWindowSamples = filterByWorkingDay(
                    baseHistoricTaskQuery(processKey, currentNodeId)
                            .taskAssignee(assigneeUserId)
                            .listPage(0, MAX_NODE_SAMPLE_SIZE),
                    workingDay
            );
            if (assigneeWindowSamples.size() >= 8) {
                return new PredictionSampleSet(assigneeWindowSamples, "同流程同节点同办理人（" + workingDayLabel(workingDay) + "）", false);
            }
        }

        List<HistoricTaskInstance> windowSamples = filterByWorkingDay(
                baseHistoricTaskQuery(processKey, currentNodeId).listPage(0, MAX_NODE_SAMPLE_SIZE),
                workingDay
        );
        if (windowSamples.size() >= 12) {
            return new PredictionSampleSet(windowSamples, "同流程同节点（" + workingDayLabel(workingDay) + "）", false);
        }

        List<HistoricTaskInstance> nodeSamples = baseHistoricTaskQuery(processKey, currentNodeId)
                .listPage(0, MAX_NODE_SAMPLE_SIZE);
        String profile = "同流程同节点（全量样本回退）";
        if (businessType != null && !businessType.isBlank()) {
            profile += " · 业务类型 " + businessType;
        }
        if (organizationProfile != null && !organizationProfile.isBlank()) {
            profile += " · 组织画像 " + organizationProfile;
        }
        return new PredictionSampleSet(nodeSamples, profile, true);
    }

    private org.flowable.task.api.history.HistoricTaskInstanceQuery baseHistoricTaskQuery(
            String processKey,
            String currentNodeId
    ) {
        return flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processDefinitionKey(processKey)
                .taskDefinitionKey(currentNodeId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc();
    }

    private Map<String, HistoricProcessInstance> loadCompletedProcesses(List<HistoricTaskInstance> tasks) {
        Set<String> processIds = new LinkedHashSet<>();
        for (HistoricTaskInstance task : tasks) {
            if (task.getProcessInstanceId() != null) {
                processIds.add(task.getProcessInstanceId());
            }
        }
        if (processIds.isEmpty()) {
            return Map.of();
        }
        List<HistoricProcessInstance> instances = flowableEngineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceIds(processIds)
                .includeProcessVariables()
                .finished()
                .list();
        Map<String, HistoricProcessInstance> mapping = new HashMap<>();
        for (HistoricProcessInstance instance : instances) {
            mapping.put(instance.getId(), instance);
        }
        return mapping;
    }

    private Map<String, Map<String, Object>> loadHistoricProcessVariables(Set<String> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String processId : processIds) {
            Map<String, Object> variables = new LinkedHashMap<>();
            List<HistoricVariableInstance> variableInstances = flowableEngineFacade.historyService()
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processId)
                    .list();
            for (HistoricVariableInstance variableInstance : variableInstances) {
                variables.put(variableInstance.getVariableName(), variableInstance.getValue());
            }
            result.put(processId, variables);
        }
        return result;
    }

    private List<HistoricTaskContext> buildHistoricTaskContexts(
            List<HistoricTaskInstance> tasks,
            Map<String, HistoricProcessInstance> processById,
            Map<String, Map<String, Object>> processVariablesById
    ) {
        List<HistoricTaskContext> contexts = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            Map<String, Object> variables = processVariablesById.getOrDefault(task.getProcessInstanceId(), Map.of());
            String deptName = stringValue(variables.get("westflowInitiatorDepartmentName"));
            String postName = stringValue(variables.get("westflowInitiatorPostName"));
            OffsetDateTime createdAt = toOffsetDateTime(task.getCreateTime());
            contexts.add(new HistoricTaskContext(
                    task,
                    processById.get(task.getProcessInstanceId()),
                    stringValue(variables.get("westflowBusinessType")),
                    resolveOrganizationProfile(deptName, postName),
                    createdAt == null ? null : resolveWorkingDayProfile(createdAt),
                    createdAt
            ));
        }
        return contexts;
    }

    private boolean matchesBusinessType(HistoricTaskContext context, String businessType) {
        if (businessType == null || businessType.isBlank()) {
            return true;
        }
        return businessType.equalsIgnoreCase(context.businessType());
    }

    private boolean matchesOrganizationProfile(HistoricTaskContext context, String organizationProfile) {
        if (organizationProfile == null || organizationProfile.isBlank()) {
            return true;
        }
        return organizationProfile.equalsIgnoreCase(context.organizationProfile());
    }

    private String effectiveSampleProfile(
            String baseProfile,
            String businessType,
            int filteredSize,
            int originalSize,
            PredictionScenarioSignals scenarioSignals
    ) {
        String scenarioSuffix = scenarioSignals.labels().isEmpty()
                ? ""
                : " · 场景 " + String.join(" / ", scenarioSignals.labels());
        if (businessType == null || businessType.isBlank()) {
            return stringValue(baseProfile, "默认样本口径") + scenarioSuffix;
        }
        if (filteredSize <= 0) {
            return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType + "（未命中专属样本）" + scenarioSuffix;
        }
        if (filteredSize < originalSize) {
            return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType + "（已过滤专属样本）" + scenarioSuffix;
        }
        return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType + scenarioSuffix;
    }

    private java.util.Optional<ProcessPredictionNextNodeCandidateResponse> resolveNextNode(
            HistoricTaskInstance sample,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        List<HistoricTaskInstance> instanceTasks = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(sample.getProcessInstanceId())
                .orderByTaskCreateTime()
                .asc()
                .list();
        HistoricTaskInstance next = null;
        for (HistoricTaskInstance item : instanceTasks) {
            if (Objects.equals(item.getId(), sample.getId())) {
                continue;
            }
            if (sample.getCreateTime() != null
                    && item.getCreateTime() != null
                    && item.getCreateTime().after(sample.getCreateTime())
                    && item.getTaskDefinitionKey() != null
                    && !Objects.equals(item.getTaskDefinitionKey(), sample.getTaskDefinitionKey())) {
                next = item;
                break;
            }
        }
        if (next == null) {
            List<ProcessPredictionNextNodeCandidateResponse> fallback = fallbackCandidates(
                    sample.getTaskDefinitionKey(),
                    flowNodes,
                    flowEdges
            );
            return fallback.stream().findFirst();
        }
        Long duration = null;
        if (next.getCreateTime() != null && next.getEndTime() != null) {
            duration = minutesBetween(toOffsetDateTime(next.getCreateTime()), toOffsetDateTime(next.getEndTime()));
        }
        return java.util.Optional.of(new ProcessPredictionNextNodeCandidateResponse(
                next.getTaskDefinitionKey(),
                next.getName(),
                0,
                1,
                duration,
                0,
                0,
                "LOW"
        ));
    }

    private List<ProcessPredictionNextNodeCandidateResponse> fallbackCandidates(
            String currentNodeId,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        Map<String, ProcessDslPayload.Node> nodeMap = new LinkedHashMap<>();
        for (ProcessDslPayload.Node node : flowNodes == null ? List.<ProcessDslPayload.Node>of() : flowNodes) {
            nodeMap.put(node.id(), node);
        }
        List<ProcessPredictionNextNodeCandidateResponse> candidates = new ArrayList<>();
        for (ProcessDslPayload.Edge edge : flowEdges == null ? List.<ProcessDslPayload.Edge>of() : flowEdges) {
            if (!Objects.equals(edge.source(), currentNodeId)) {
                continue;
            }
            ProcessDslPayload.Node target = nodeMap.get(edge.target());
            candidates.add(new ProcessPredictionNextNodeCandidateResponse(
                    edge.target(),
                    target == null ? edge.target() : target.name(),
                    0,
                    0,
                    null,
                    0,
                    candidates.size() + 1,
                    "LOW"
            ));
        }
        return candidates.stream().distinct().limit(3).toList();
    }

    private List<ProcessPredictionNextNodeCandidateResponse> mapNextCandidates(
            Map<String, TransitionStats> nextNodeStats,
            int sampleSize,
            Long p75
    ) {
        List<TransitionStats> ordered = nextNodeStats.values().stream()
                .sorted(Comparator.comparingInt((TransitionStats item) -> item.hitCount).reversed())
                .limit(3)
                .toList();
        List<ProcessPredictionNextNodeCandidateResponse> candidates = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            TransitionStats item = ordered.get(index);
            double probability = sampleSize == 0 ? 0 : roundRatio(item.hitCount, sampleSize);
            Long medianDuration = item.durations.isEmpty() ? null : median(item.durations);
            candidates.add(new ProcessPredictionNextNodeCandidateResponse(
                    item.nodeId,
                    item.nodeName,
                    probability,
                    item.hitCount,
                    medianDuration,
                    Math.max(1, (int) Math.round(probability * 100)) + resolveCandidateRiskWeight(medianDuration, p75),
                    index + 1,
                    probability >= 0.6D ? "HIGH" : probability >= 0.3D ? "MEDIUM" : "LOW"
            ));
        }
        return candidates;
    }

    private int resolveCandidateRiskWeight(Long medianDuration, Long p75) {
        if (medianDuration == null || p75 == null) {
            return 0;
        }
        if (medianDuration >= p75) {
            return 20;
        }
        return medianDuration >= (p75 / 2) ? 10 : 0;
    }

    private String resolveRiskLevel(Long currentElapsedMinutes, List<Long> nodeDurationSamples, Long p50, Long p75, Long p90) {
        if (currentElapsedMinutes == null || nodeDurationSamples.isEmpty()) {
            return "LOW";
        }
        long resolvedP50 = p50 == null ? percentile(nodeDurationSamples, 0.50) : p50;
        long resolvedP75 = p75 == null ? percentile(nodeDurationSamples, 0.75) : p75;
        long resolvedP90 = p90 == null ? percentile(nodeDurationSamples, 0.90) : p90;
        if (currentElapsedMinutes > resolvedP90 || currentElapsedMinutes > resolvedP75) {
            return "HIGH";
        }
        if (currentElapsedMinutes > resolvedP50) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveConfidence(
            int sampleSize,
            List<ProcessPredictionNextNodeCandidateResponse> nextCandidates,
            boolean usedFallback,
            String sampleTier,
            PredictionScenarioSignals scenarioSignals
    ) {
        double topProbability = nextCandidates.stream()
                .mapToDouble(ProcessPredictionNextNodeCandidateResponse::probability)
                .max()
                .orElse(0D);
        String confidence;
        if (!usedFallback && sampleSize >= 20 && topProbability >= 0.6D && "DENSE".equals(sampleTier)) {
            confidence = "HIGH";
        } else if (sampleSize >= 8) {
            confidence = "MEDIUM";
        } else {
            confidence = "LOW";
        }
        if (scenarioSignals.addSign() || scenarioSignals.transferFamily()) {
            confidence = degradeConfidence(confidence);
        }
        return confidence;
    }

    private String buildBasisSummary(
            String currentNodeName,
            String sampleProfile,
            int sampleSize,
            long remainingMedian,
            List<ProcessPredictionNextNodeCandidateResponse> nextCandidates
    ) {
        String nextNodeText = nextCandidates.isEmpty()
                ? "无明显候选后续节点"
                : "最可能流向 " + nextCandidates.get(0).nodeName();
        return "基于节点“" + stringValue(currentNodeName, "--")
                + "”的历史完成样本 " + sampleSize
                + " 条（" + stringValue(sampleProfile, "默认样本口径") + "）"
                + "，预测剩余时长中位数约 " + remainingMedian
                + " 分钟，" + nextNodeText + "。";
    }

    private String buildExplanation(
            String currentNodeName,
            Long currentElapsedMinutes,
            Long remainingMedian,
            String riskLevel,
            List<ProcessPredictionNextNodeCandidateResponse> nextCandidates,
            boolean fallbackOnly,
            String sampleProfile,
            PredictionScenarioSignals scenarioSignals
    ) {
        StringBuilder explanation = new StringBuilder();
        explanation.append(stringValue(currentNodeName, "当前节点"));
        if (currentElapsedMinutes != null) {
            explanation.append("已停留 ").append(currentElapsedMinutes).append(" 分钟");
        }
        if (remainingMedian != null) {
            explanation.append("，按历史中位样本预计还需 ").append(remainingMedian).append(" 分钟");
        }
        explanation.append(fallbackOnly ? "。历史样本不足，当前主要依据流程图结构推断后续走向" : "。该预测主要依据同流程历史实例和当前节点完成分布");
        if (sampleProfile != null && !sampleProfile.isBlank()) {
            explanation.append("，当前命中的样本口径为 ").append(sampleProfile);
        }
        if (!scenarioSignals.labels().isEmpty()) {
            explanation.append("，并结合了").append(String.join("、", scenarioSignals.labels())).append("场景的专门口径");
        }
        explanation.append("，超期风险为 ").append(resolveRiskLabel(riskLevel));
        if (nextCandidates != null && !nextCandidates.isEmpty()) {
            explanation.append("；下一步更可能进入 ")
                    .append(nextCandidates.stream()
                            .limit(2)
                            .map(ProcessPredictionNextNodeCandidateResponse::nodeName)
                            .filter(Objects::nonNull)
                            .reduce((left, right) -> left + "、" + right)
                            .orElse("后续节点"));
        }
        explanation.append("。");
        return explanation.toString();
    }

    private String buildNarrativeExplanation(
            String currentNodeName,
            Long currentElapsedMinutes,
            Long remainingMedian,
            String riskLevel,
            String sampleProfile,
            PredictionScenarioSignals scenarioSignals
    ) {
        return buildExplanation(
                currentNodeName,
                currentElapsedMinutes,
                remainingMedian,
                riskLevel,
                List.of(),
                remainingMedian == null,
                sampleProfile,
                scenarioSignals
        );
    }

    private String buildBottleneckAttribution(
            String currentNodeName,
            Long currentElapsedMinutes,
            Long p75,
            Long p90,
            boolean insufficientSamples,
            PredictionScenarioSignals scenarioSignals
    ) {
        if (insufficientSamples) {
            return "当前节点历史样本不足，主要瓶颈归因只能回退到流程结构和当前停留时长。";
        }
        if (scenarioSignals.countersign()) {
            return "当前节点属于会签场景，瓶颈通常来自成员意见汇聚和阈值达成速度。";
        }
        if (scenarioSignals.addSign()) {
            return "当前节点叠加了加签链路，瓶颈通常来自原审批人与加签人之间的串行等待。";
        }
        if (scenarioSignals.transferFamily()) {
            return "当前节点发生过转办/委派/离职转办，瓶颈通常来自责任人切换后的重新认领与信息交接。";
        }
        if (currentElapsedMinutes == null) {
            return "当前节点还没有足够的停留时长，暂无明显瓶颈。";
        }
        if (p90 != null && currentElapsedMinutes > p90) {
            return stringValue(currentNodeName, "当前节点") + " 已超过历史 p90，当前节点就是主要瓶颈。";
        }
        if (p75 != null && currentElapsedMinutes > p75) {
            return stringValue(currentNodeName, "当前节点") + " 已超过历史 p75，当前节点存在明显延迟压力。";
        }
        return "当前节点仍在历史可接受波动范围内，主要瓶颈暂未形成。";
    }

    private List<String> buildDelayReasons(
            Long currentElapsedMinutes,
            List<Long> nodeDurationSamples,
            boolean insufficientSamples,
            PredictionScenarioSignals scenarioSignals
    ) {
        List<String> reasons = new ArrayList<>();
        if (insufficientSamples) {
            reasons.add("当前节点历史样本不足，预测置信度降低。");
        }
        if (scenarioSignals.addSign()) {
            reasons.add("当前链路包含加签任务，原节点完成时间会被加签处理节奏拉长。");
        }
        if (scenarioSignals.countersign()) {
            reasons.add("当前处于会签节点，需要等待多位成员意见汇聚。");
        }
        if (scenarioSignals.transferFamily()) {
            reasons.add("当前任务发生过转办/委派/离职转办，责任人切换带来的交接会增加波动。");
        }
        if (currentElapsedMinutes != null && !nodeDurationSamples.isEmpty()) {
            long p75 = percentile(nodeDurationSamples, 0.75);
            if (currentElapsedMinutes > p75) {
                reasons.add("当前节点停留时间已经超过历史 75 分位。");
            }
        }
        if (reasons.isEmpty()) {
            reasons.add("当前流程走势与历史样本接近。");
        }
        return reasons;
    }

    private List<String> buildRecommendedActions(
            Long currentElapsedMinutes,
            String riskLevel,
            List<ProcessPredictionNextNodeCandidateResponse> nextCandidates,
            boolean insufficientSamples,
            PredictionScenarioSignals scenarioSignals
    ) {
        List<String> actions = new ArrayList<>();
        String normalizedRisk = riskLevel == null ? "" : riskLevel.toUpperCase();
        if ("HIGH".equals(normalizedRisk)) {
            actions.add("优先催办当前节点办理人，必要时升级给上级负责人。");
        } else if ("MEDIUM".equals(normalizedRisk)) {
            actions.add("建议关注当前节点办理进展，并提前同步下一审批人。");
        } else {
            actions.add("当前流程走势稳定，可按正常节奏跟进。");
        }
        if (currentElapsedMinutes != null && currentElapsedMinutes >= 240) {
            actions.add("当前节点已长时间停留，建议检查是否存在认领或表单补充阻塞。");
        }
        if (insufficientSamples) {
            actions.add("历史样本不足，建议结合流程规则和人工判断复核预测结果。");
        }
        if (scenarioSignals.addSign()) {
            actions.add("加签场景建议先确认加签人是否已认领，并提前同步原审批人预期恢复时间。");
        }
        if (scenarioSignals.countersign()) {
            actions.add("会签节点建议优先催办尚未表态的成员，并核对阈值是否接近达成。");
        }
        if (scenarioSignals.transferFamily()) {
            actions.add("转办/委派场景建议确认新责任人是否完成交接并已开始处理。");
        }
        if (nextCandidates != null && !nextCandidates.isEmpty()) {
            actions.add("可提前与下一节点“" + stringValue(nextCandidates.get(0).nodeName(), "后续节点") + "”的候选办理人沟通。");
        }
        return actions.stream().distinct().limit(3).toList();
    }

    private List<String> buildOptimizationSuggestions(
            String riskLevel,
            boolean insufficientSamples,
            PredictionScenarioSignals scenarioSignals
    ) {
        List<String> suggestions = new ArrayList<>();
        if ("HIGH".equalsIgnoreCase(riskLevel)) {
            suggestions.add("把当前节点的 SLA 预警阈值前移，并增加催办频率。");
            suggestions.add("评估是否为该节点补充候补办理人或共享候选组。");
        } else if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            suggestions.add("为当前节点补充前置材料检查，减少往返补件。");
        } else {
            suggestions.add("当前流程运行稳定，可继续观察样本是否持续收敛。");
        }
        if (insufficientSamples) {
            suggestions.add("优先积累该节点更多历史样本，再决定是否调整流程规则。");
        }
        if (scenarioSignals.addSign()) {
            suggestions.add("评估把高频加签改成固定并行评审节点，减少临时加签带来的波动。");
        }
        if (scenarioSignals.countersign()) {
            suggestions.add("会签节点可评估改成更明确的阈值配置或缩减参与人范围。");
        }
        if (scenarioSignals.transferFamily()) {
            suggestions.add("为转办/委派链路增加交接清单和预提醒，降低责任切换损耗。");
        }
        return suggestions.stream().distinct().limit(3).toList();
    }

    private PredictionScenarioSignals resolveScenarioSignals(
            String currentTaskKind,
            String currentTaskSemanticMode,
            String currentAction,
            String currentActingMode,
            String currentActingForUserId,
            String currentDelegatedByUserId,
            String currentHandoverFromUserId,
            List<ProcessTaskTraceItemResponse> taskTrace,
            List<CountersignTaskGroupResponse> countersignGroups
    ) {
        List<ProcessTaskTraceItemResponse> trace = taskTrace == null ? List.of() : taskTrace;
        List<CountersignTaskGroupResponse> groups = countersignGroups == null ? List.of() : countersignGroups;
        boolean countersign = isCountersignMode(currentTaskSemanticMode)
                || trace.stream().anyMatch(item -> isCountersignMode(item.taskSemanticMode()))
                || !groups.isEmpty();
        boolean addSign = "ADD_SIGN".equalsIgnoreCase(currentTaskKind)
                || trace.stream().anyMatch(item -> item.isAddSignTask() && !item.isRevoked());
        boolean delegated = "DELEGATE".equalsIgnoreCase(currentActingMode)
                || currentDelegatedByUserId != null
                || trace.stream().anyMatch(item -> "DELEGATE".equalsIgnoreCase(item.actingMode()) || item.delegatedByUserId() != null);
        boolean handover = "HANDOVER".equalsIgnoreCase(currentActingMode)
                || currentHandoverFromUserId != null
                || trace.stream().anyMatch(item -> "HANDOVER".equalsIgnoreCase(item.actingMode()) || item.handoverFromUserId() != null);
        boolean transferred = "TRANSFER".equalsIgnoreCase(currentAction)
                || "TRANSFER".equalsIgnoreCase(currentActingMode)
                || currentActingForUserId != null
                || trace.stream().anyMatch(item -> "TRANSFER".equalsIgnoreCase(item.action()));
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (countersign) {
            labels.add("会签");
        }
        if (addSign) {
            labels.add("加签");
        }
        if (handover) {
            labels.add("离职转办");
        } else if (delegated) {
            labels.add("委派");
        } else if (transferred) {
            labels.add("转办");
        }
        return new PredictionScenarioSignals(countersign, addSign, delegated || handover || transferred, List.copyOf(labels));
    }

    private boolean isCountersignMode(String taskSemanticMode) {
        if (taskSemanticMode == null || taskSemanticMode.isBlank()) {
            return false;
        }
        String normalized = taskSemanticMode.toUpperCase();
        return normalized.contains("SIGN") || normalized.contains("VOTE");
    }

    private long applyScenarioBuffer(long remainingMedian, PredictionScenarioSignals scenarioSignals) {
        double factor = 1.0D;
        if (scenarioSignals.countersign()) {
            factor += 0.15D;
        }
        if (scenarioSignals.addSign()) {
            factor += 0.20D;
        }
        if (scenarioSignals.transferFamily()) {
            factor += 0.10D;
        }
        return Math.max(remainingMedian, Math.round(remainingMedian * factor));
    }

    private String degradeConfidence(String confidence) {
        if ("HIGH".equalsIgnoreCase(confidence)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private ProcessPredictionResponse attachAutomationActions(
            ProcessPredictionResponse prediction,
            String currentNodeName
    ) {
        if (prediction == null) {
            return null;
        }
        List<ProcessPredictionAutomationActionResponse> actions =
                runtimeProcessPredictionAutomationService.evaluate(null, currentNodeName, prediction);
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
                actions,
                prediction.featureSnapshot(),
                prediction.nextNodeCandidates()
        );
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
    }

    private long minutesBetween(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(start, end).toMinutes());
    }

    private long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private Long percentileOrNull(List<Long> values, double percentile) {
        return values.isEmpty() ? null : percentile(values, percentile);
    }

    private double roundRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 1000D) / denominator) / 1000D;
    }

    private String resolveRiskLabel(String riskLevel) {
        return switch ((riskLevel == null ? "" : riskLevel).toUpperCase()) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            default -> "低";
        };
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String stringValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isWorkingDay(OffsetDateTime time) {
        if (time == null) {
            return true;
        }
        return switch (time.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> true;
        };
    }

    private String resolveWorkingDayProfile(OffsetDateTime time) {
        return isWorkingDay(time) ? "工作日" : "非工作日";
    }

    private String workingDayLabel(boolean workingDay) {
        return workingDay ? "工作日样本" : "非工作日样本";
    }

    private List<HistoricTaskInstance> filterByWorkingDay(List<HistoricTaskInstance> tasks, boolean workingDay) {
        return tasks.stream()
                .filter(task -> {
                    OffsetDateTime start = toOffsetDateTime(task.getCreateTime());
                    return start != null && isWorkingDay(start) == workingDay;
                })
                .limit(MAX_NODE_SAMPLE_SIZE)
                .toList();
    }

    private List<Long> cleanOutliers(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> positives = values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .sorted()
                .toList();
        if (positives.size() < 6) {
            return positives;
        }
        long q1 = percentile(positives, 0.25);
        long q3 = percentile(positives, 0.75);
        long iqr = Math.max(1L, q3 - q1);
        long lowerBound = Math.max(0L, q1 - iqr * 2L);
        long upperBound = q3 + iqr * 2L;
        List<Long> filtered = positives.stream()
                .filter(value -> value >= lowerBound && value <= upperBound)
                .toList();
        return filtered.isEmpty() ? positives : filtered;
    }

    private String resolveSampleTier(int sampleSize, boolean usedFallback) {
        if (sampleSize >= 24 && !usedFallback) {
            return "DENSE";
        }
        if (sampleSize >= 12) {
            return "BALANCED";
        }
        if (sampleSize >= 6) {
            return "SPARSE";
        }
        return "WEAK";
    }

    private String resolveOrganizationProfile(String organizationProfile, List<HistoricTaskContext> contexts) {
        if (organizationProfile != null && !organizationProfile.isBlank()) {
            return organizationProfile;
        }
        return contexts.stream()
                .map(HistoricTaskContext::organizationProfile)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("未命中组织画像");
    }

    private String resolveOrganizationProfile(String departmentName, String postName) {
        if (departmentName == null || departmentName.isBlank()) {
            return postName == null || postName.isBlank() ? null : postName;
        }
        if (postName == null || postName.isBlank()) {
            return departmentName;
        }
        return departmentName + " / " + postName;
    }

    private static final class TransitionStats {
        private final String nodeId;
        private final String nodeName;
        private int hitCount;
        private final List<Long> durations = new ArrayList<>();

        private TransitionStats(String nodeId, String nodeName) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
        }
    }

    private record PredictionSampleSet(
            List<HistoricTaskInstance> tasks,
            String profile,
            boolean usedFallback
    ) {
    }

    private record HistoricTaskContext(
            HistoricTaskInstance task,
            HistoricProcessInstance process,
            String businessType,
            String organizationProfile,
            String workingDayProfile,
            OffsetDateTime createdAt
    ) {
    }

    private record PredictionScenarioSignals(
            boolean countersign,
            boolean addSign,
            boolean transferFamily,
            List<String> labels
    ) {
    }
}
