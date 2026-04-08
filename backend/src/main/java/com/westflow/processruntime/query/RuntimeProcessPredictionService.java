package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_NODE_SAMPLE_SIZE = 200;

    private final FlowableEngineFacade flowableEngineFacade;

    public ProcessPredictionResponse predict(
            String processKey,
            String instanceStatus,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String businessType,
            OffsetDateTime receiveTime,
            List<ProcessTaskTraceItemResponse> taskTrace,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        if (currentNodeId == null || currentNodeId.isBlank()) {
            return new ProcessPredictionResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "LOW",
                    "LOW",
                    0,
                    "未命中活动节点样本",
                    "当前没有可预测的活动节点。",
                    "无活动节点",
                    "当前实例没有明确的活动节点，暂时无法估算后续时长。",
                    List.of(),
                    List.of("等待新的运行节点激活后再查看预测。"),
                    List.of()
            );
        }
        if ("COMPLETED".equalsIgnoreCase(instanceStatus)
                || "TERMINATED".equalsIgnoreCase(instanceStatus)
                || "REVOKED".equalsIgnoreCase(instanceStatus)) {
            return new ProcessPredictionResponse(
                    null,
                    null,
                    0L,
                    0L,
                    null,
                    null,
                    "LOW",
                    "HIGH",
                    0,
                    "流程已结束，无需样本预测",
                    "流程已结束，无需预测剩余审批时长。",
                    "流程已结束",
                    "流程已经结束，当前无需继续预测剩余节点和时长。",
                    List.of("实例状态已结束"),
                    List.of("该实例已结束，可转为回顾或归档查看。"),
                    List.of()
            );
        }

        OffsetDateTime now = OffsetDateTime.now(TIME_ZONE);
        Long currentElapsedMinutes = minutesBetween(receiveTime, now);
        PredictionSampleSet sampleSet = resolvePredictionSamples(processKey, currentNodeId, assigneeUserId, now);
        List<HistoricTaskInstance> nodeSamples = sampleSet.tasks();

        Map<String, HistoricProcessInstance> processById = loadCompletedProcesses(nodeSamples);
        List<HistoricTaskInstance> filteredNodeSamples = filterSamplesByBusinessType(nodeSamples, processById, businessType);
        String effectiveSampleProfile = effectiveSampleProfile(
                sampleSet.profile(),
                businessType,
                filteredNodeSamples.size(),
                nodeSamples.size()
        );
        List<Long> remainingMinutesSamples = new ArrayList<>();
        List<Long> nodeDurationSamples = new ArrayList<>();
        Map<String, TransitionStats> nextNodeStats = new LinkedHashMap<>();

        for (HistoricTaskInstance sample : filteredNodeSamples) {
            OffsetDateTime sampleStart = toOffsetDateTime(sample.getCreateTime());
            OffsetDateTime sampleEnd = toOffsetDateTime(sample.getEndTime());
            HistoricProcessInstance process = processById.get(sample.getProcessInstanceId());
            OffsetDateTime processEnd = process == null ? null : toOffsetDateTime(process.getEndTime());
            if (sampleStart != null && sampleEnd != null) {
                nodeDurationSamples.add(minutesBetween(sampleStart, sampleEnd));
            }
            if (sampleStart != null && processEnd != null) {
                remainingMinutesSamples.add(minutesBetween(sampleStart, processEnd));
            }
            resolveNextNode(sample, flowNodes, flowEdges).ifPresent(candidate -> {
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

        Long p50 = percentileOrNull(nodeDurationSamples, 0.50);
        Long p75 = percentileOrNull(nodeDurationSamples, 0.75);
        String riskLevel = resolveRiskLevel(currentElapsedMinutes, nodeDurationSamples);

        if (remainingMinutesSamples.isEmpty()) {
            List<ProcessPredictionNextNodeCandidateResponse> fallbackCandidates = fallbackCandidates(currentNodeId, flowNodes, flowEdges);
            return new ProcessPredictionResponse(
                    null,
                    null,
                    null,
                    currentElapsedMinutes,
                    p50,
                    p75,
                    riskLevel,
                    resolveConfidence(0, fallbackCandidates, true),
                    0,
                    effectiveSampleProfile,
                    "当前节点历史样本不足，已回退到流程图候选路径。",
                    "历史样本不足",
                    buildExplanation(currentNodeName, currentElapsedMinutes, null, riskLevel, fallbackCandidates, true, effectiveSampleProfile),
                    buildDelayReasons(currentElapsedMinutes, nodeDurationSamples, true),
                    buildRecommendedActions(currentElapsedMinutes, riskLevel, fallbackCandidates, true),
                    fallbackCandidates
            );
        }

        long remainingMedian = median(remainingMinutesSamples);
        OffsetDateTime predictedFinishTime = now.plusMinutes(remainingMedian);
        OffsetDateTime predictedRiskThresholdTime = receiveTime == null || p75 == null
                ? null
                : receiveTime.plusMinutes(p75);
        List<ProcessPredictionNextNodeCandidateResponse> nextCandidates = nextNodeStats.isEmpty()
                ? fallbackCandidates(currentNodeId, flowNodes, flowEdges)
                : mapNextCandidates(nextNodeStats, filteredNodeSamples.size());

        return new ProcessPredictionResponse(
                predictedFinishTime,
                predictedRiskThresholdTime,
                remainingMedian,
                currentElapsedMinutes,
                p50,
                p75,
                riskLevel,
                resolveConfidence(remainingMinutesSamples.size(), nextCandidates, sampleSet.usedFallback()),
                remainingMinutesSamples.size(),
                effectiveSampleProfile,
                buildBasisSummary(currentNodeName, effectiveSampleProfile, remainingMinutesSamples.size(), remainingMedian, nextCandidates),
                null,
                buildExplanation(currentNodeName, currentElapsedMinutes, remainingMedian, riskLevel, nextCandidates, false, effectiveSampleProfile),
                buildDelayReasons(currentElapsedMinutes, nodeDurationSamples, false),
                buildRecommendedActions(currentElapsedMinutes, riskLevel, nextCandidates, false),
                nextCandidates
        );
    }

    public ProcessPredictionResponse predictForActiveTaskListItem(
            String processKey,
            String currentNodeId,
            String currentNodeName,
            String assigneeUserId,
            String businessType,
            OffsetDateTime receiveTime,
            List<ProcessDslPayload.Node> flowNodes,
            List<ProcessDslPayload.Edge> flowEdges
    ) {
        return predict(
                processKey,
                "RUNNING",
                currentNodeId,
                currentNodeName,
                assigneeUserId,
                businessType,
                receiveTime,
                List.of(),
                flowNodes,
                flowEdges
        );
    }

    private PredictionSampleSet resolvePredictionSamples(
            String processKey,
            String currentNodeId,
            String assigneeUserId,
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
            if (assigneeWindowSamples.size() >= 6) {
                return new PredictionSampleSet(assigneeWindowSamples, "同流程同节点同办理人（" + workingDayLabel(workingDay) + "）", false);
            }
        }

        List<HistoricTaskInstance> windowSamples = filterByWorkingDay(
                baseHistoricTaskQuery(processKey, currentNodeId).listPage(0, MAX_NODE_SAMPLE_SIZE),
                workingDay
        );
        if (windowSamples.size() >= 8) {
            return new PredictionSampleSet(windowSamples, "同流程同节点（" + workingDayLabel(workingDay) + "）", false);
        }

        List<HistoricTaskInstance> nodeSamples = baseHistoricTaskQuery(processKey, currentNodeId)
                .listPage(0, MAX_NODE_SAMPLE_SIZE);
        return new PredictionSampleSet(nodeSamples, "同流程同节点（全量样本回退）", true);
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

    private List<HistoricTaskInstance> filterSamplesByBusinessType(
            List<HistoricTaskInstance> tasks,
            Map<String, HistoricProcessInstance> processById,
            String businessType
    ) {
        if (businessType == null || businessType.isBlank()) {
            return tasks;
        }
        return tasks.stream()
                .filter(task -> {
                    HistoricProcessInstance process = processById.get(task.getProcessInstanceId());
                    if (process == null || process.getProcessVariables() == null) {
                        return false;
                    }
                    Object value = process.getProcessVariables().get("westflowBusinessType");
                    return businessType.equalsIgnoreCase(value == null ? "" : String.valueOf(value));
                })
                .toList();
    }

    private String effectiveSampleProfile(
            String baseProfile,
            String businessType,
            int filteredSize,
            int originalSize
    ) {
        if (businessType == null || businessType.isBlank()) {
            return baseProfile;
        }
        if (filteredSize <= 0) {
            return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType + "（未命中专属样本）";
        }
        if (filteredSize < originalSize) {
            return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType + "（已过滤专属样本）";
        }
        return stringValue(baseProfile, "默认样本口径") + " · 业务类型 " + businessType;
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
            if (sample.getCreateTime() != null && item.getCreateTime() != null
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
            int sampleSize
    ) {
        List<TransitionStats> ordered = nextNodeStats.values().stream()
                .sorted(Comparator.comparingInt((TransitionStats item) -> item.hitCount).reversed())
                .limit(3)
                .toList();
        List<ProcessPredictionNextNodeCandidateResponse> candidates = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            TransitionStats item = ordered.get(index);
            double probability = sampleSize == 0 ? 0 : roundRatio(item.hitCount, sampleSize);
            candidates.add(new ProcessPredictionNextNodeCandidateResponse(
                    item.nodeId,
                    item.nodeName,
                    probability,
                    item.hitCount,
                    item.durations.isEmpty() ? null : median(item.durations),
                    Math.max(1, (int) Math.round(probability * 100)),
                    index + 1,
                    probability >= 0.6D ? "HIGH" : probability >= 0.3D ? "MEDIUM" : "LOW"
            ));
        }
        return candidates;
    }

    private String resolveRiskLevel(Long currentElapsedMinutes, List<Long> nodeDurationSamples) {
        if (currentElapsedMinutes == null || nodeDurationSamples.isEmpty()) {
            return "LOW";
        }
        long p50 = percentile(nodeDurationSamples, 0.50);
        long p75 = percentile(nodeDurationSamples, 0.75);
        if (currentElapsedMinutes > p75) {
            return "HIGH";
        }
        if (currentElapsedMinutes > p50) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveConfidence(int sampleSize, List<ProcessPredictionNextNodeCandidateResponse> nextCandidates, boolean usedFallback) {
        double topProbability = nextCandidates.stream()
                .mapToDouble(ProcessPredictionNextNodeCandidateResponse::probability)
                .max()
                .orElse(0D);
        if (!usedFallback && sampleSize >= 20 && topProbability >= 0.6D) {
            return "HIGH";
        }
        if (sampleSize >= 8) {
            return "MEDIUM";
        }
        return "LOW";
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
            String sampleProfile
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

    private List<String> buildDelayReasons(Long currentElapsedMinutes, List<Long> nodeDurationSamples, boolean insufficientSamples) {
        List<String> reasons = new ArrayList<>();
        if (insufficientSamples) {
            reasons.add("当前节点历史样本不足，预测置信度降低。");
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
            boolean insufficientSamples
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
        if (nextCandidates != null && !nextCandidates.isEmpty()) {
            actions.add("可提前与下一节点“" + stringValue(nextCandidates.get(0).nodeName(), "后续节点") + "”的候选办理人沟通。");
        }
        return actions.stream().distinct().limit(3).toList();
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
}
