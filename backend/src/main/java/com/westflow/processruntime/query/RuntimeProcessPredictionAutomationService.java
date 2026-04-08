package com.westflow.processruntime.query;

import com.westflow.processruntime.api.response.ProcessPredictionAutomationActionResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuntimeProcessPredictionAutomationService {

    public List<ProcessPredictionAutomationActionResponse> evaluate(
            String processName,
            String currentNodeName,
            ProcessPredictionResponse prediction
    ) {
        if (prediction == null) {
            return List.of();
        }
        List<ProcessPredictionAutomationActionResponse> actions = new ArrayList<>();
        String riskLevel = normalize(prediction.overdueRiskLevel());
        if ("HIGH".equals(riskLevel)) {
            actions.add(new ProcessPredictionAutomationActionResponse(
                    "AUTO_URGE",
                    "AUTO_URGE",
                    "READY",
                    "高风险自动催办",
                    "“" + safe(currentNodeName, "当前节点") + "”已进入高风险区间，建议立即催办并同步负责人。"
            ));
        } else if ("MEDIUM".equals(riskLevel)) {
            actions.add(new ProcessPredictionAutomationActionResponse(
                    "SLA_REMINDER",
                    "NOTIFY",
                    "READY",
                    "SLA 临近提醒",
                    "“" + safe(processName, "当前流程") + "”已接近历史高风险阈值，建议提前提醒当前办理人。"
            ));
        } else {
            actions.add(new ProcessPredictionAutomationActionResponse(
                    "SLA_REMINDER",
                    "NOTIFY",
                    "SKIPPED",
                    "SLA 临近提醒",
                    "当前流程走势稳定，暂不触发自动提醒。"
            ));
        }
        if (prediction.nextNodeCandidates() != null && !prediction.nextNodeCandidates().isEmpty()) {
            actions.add(new ProcessPredictionAutomationActionResponse(
                    "NEXT_NODE_PRE_NOTIFY",
                    "NOTIFY",
                    "READY",
                    "下一审批人预提醒",
                    "建议提前同步下一节点“" + safe(prediction.nextNodeCandidates().get(0).nodeName(), "后续节点") + "”的候选办理人。"
            ));
        }
        if ("HIGH".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
            actions.add(new ProcessPredictionAutomationActionResponse(
                    "COLLABORATION_ACTION",
                    "COORDINATE",
                    "READY",
                    "预测触发协同动作",
                    "建议在协同区补充卡点原因、所需材料或预先说明，减少下一节点等待。"
            ));
        }
        return actions.stream().distinct().limit(4).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
