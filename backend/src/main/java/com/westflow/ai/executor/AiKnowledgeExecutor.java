package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 普通知识执行器。
 */
public class AiKnowledgeExecutor extends AbstractAiExecutor {

    public AiKnowledgeExecutor() {
        super(AiExecutorType.KNOWLEDGE);
    }

    @Override
    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "进入功能问答与使用说明链路";
    }

    @Override
    protected Map<String, Object> payload(AiExecutionPlan plan, AiExecutionContext context) {
        return Map.of(
                "keyword", context.content(),
                "domain", plan.domain(),
                "pageRoute", context.pageRoute()
        );
    }

    @Override
    protected AiToolCallRequest toolCallRequest(AiExecutionPlan plan, AiExecutionContext context) {
        if (plan.toolCandidates().isEmpty()) {
            return null;
        }
        String toolKey = plan.toolCandidates().getFirst();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", context.content());
        arguments.put("domain", plan.domain());
        arguments.put("pageRoute", context.pageRoute());
        arguments.put("userId", context.userId());
        AiToolSource toolSource = "plm.change.summary".equals(toolKey) ? AiToolSource.SKILL : AiToolSource.PLATFORM;
        return new AiToolCallRequest(toolKey, AiToolType.READ, toolSource, arguments);
    }
}
