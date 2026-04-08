package com.westflow.processruntime.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RuntimeProcessPredictionAiNarrationService {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;
    private final String fastChatModel;

    public RuntimeProcessPredictionAiNarrationService(
            ObjectProvider<ChatClient> chatClientProvider,
            ObjectMapper objectMapper,
            @Value("${westflow.ai.copilot.fast-chat-model:${DASHSCOPE_FAST_CHAT_MODEL:qwen-turbo-latest}}") String fastChatModel
    ) {
        this.chatClientProvider = chatClientProvider;
        this.objectMapper = objectMapper;
        this.fastChatModel = fastChatModel == null ? "" : fastChatModel.trim();
    }

    public ProcessPredictionResponse enhanceDetailPrediction(
            String processName,
            String businessType,
            String currentNodeName,
            ProcessPredictionResponse prediction
    ) {
        if (!shouldEnhance(prediction)) {
            return prediction;
        }
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return prediction;
        }
        try {
            ChatClient.ChatClientRequestSpec prompt = chatClient.prompt();
            if (!fastChatModel.isBlank()) {
                prompt = prompt.options(OpenAiChatOptions.builder().model(fastChatModel).build());
            }
            String content = prompt
                    .system("""
                            你是企业流程预测解释助手。
                            只能解释已经给出的预测结果，不得改写任何时间、时长、风险、置信度和候选节点数值。
                            仅输出 JSON，对象结构固定为：
                            {"explanation":"...","recommendedActions":["...","..."]}
                            - explanation 用简洁中文解释为什么会有当前风险。
                            - recommendedActions 最多 3 条，给出可执行催办/预同步建议。
                            - 不要输出 Markdown，不要输出代码块。
                            """)
                    .user("""
                            流程名称：%s
                            业务类型：%s
                            当前节点：%s
                            超期风险：%s
                            置信度：%s
                            历史样本数：%s
                            样本口径：%s
                            预计完成时间：%s
                            预计剩余时长（分钟）：%s
                            当前已停留（分钟）：%s
                            当前节点历史 p50（分钟）：%s
                            当前节点历史 p75（分钟）：%s
                            预计进入高风险阈值：%s
                            延迟因素：%s
                            候选下一节点：%s
                            现有规则解释：%s
                            现有建议动作：%s
                            """.formatted(
                            safe(processName),
                            safe(businessType),
                            safe(currentNodeName),
                            safe(prediction.overdueRiskLevel()),
                            safe(prediction.confidence()),
                            prediction.historicalSampleSize(),
                            safe(prediction.sampleProfile()),
                            safe(prediction.predictedFinishTime()),
                            safe(prediction.remainingDurationMinutes()),
                            safe(prediction.currentElapsedMinutes()),
                            safe(prediction.currentNodeDurationP50Minutes()),
                            safe(prediction.currentNodeDurationP75Minutes()),
                            safe(prediction.predictedRiskThresholdTime()),
                            safe(prediction.topDelayReasons()),
                            safe(prediction.nextNodeCandidates()),
                            safe(prediction.explanation()),
                            safe(prediction.recommendedActions())
                    ))
                    .call()
                    .content();
            JsonNode json = objectMapper.readTree(extractJson(content));
            String explanation = stringValue(json.path("explanation").asText());
            List<String> actions = new ArrayList<>();
            if (json.path("recommendedActions").isArray()) {
                json.path("recommendedActions").forEach(item -> {
                    String value = stringValue(item.asText());
                    if (!value.isBlank()) {
                        actions.add(value);
                    }
                });
            }
            if (explanation.isBlank() && actions.isEmpty()) {
                return prediction;
            }
            return new ProcessPredictionResponse(
                    prediction.predictedFinishTime(),
                    prediction.predictedRiskThresholdTime(),
                    prediction.remainingDurationMinutes(),
                    prediction.currentElapsedMinutes(),
                    prediction.currentNodeDurationP50Minutes(),
                    prediction.currentNodeDurationP75Minutes(),
                    prediction.overdueRiskLevel(),
                    prediction.confidence(),
                    prediction.historicalSampleSize(),
                    prediction.sampleProfile(),
                    prediction.basisSummary(),
                    prediction.noPredictionReason(),
                    explanation.isBlank() ? prediction.explanation() : explanation,
                    prediction.topDelayReasons(),
                    actions.isEmpty() ? prediction.recommendedActions() : List.copyOf(actions.stream().distinct().limit(3).toList()),
                    prediction.nextNodeCandidates()
            );
        } catch (Exception exception) {
            log.debug("process prediction AI narration fallback process={} node={}", processName, currentNodeName, exception);
            return prediction;
        }
    }

    private boolean shouldEnhance(ProcessPredictionResponse prediction) {
        if (prediction == null) {
            return false;
        }
        String riskLevel = stringValue(prediction.overdueRiskLevel()).toUpperCase();
        if (!"HIGH".equals(riskLevel) && !"MEDIUM".equals(riskLevel)) {
            return false;
        }
        return prediction.historicalSampleSize() >= 6;
    }

    private String extractJson(String content) {
        String normalized = stringValue(content).trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
