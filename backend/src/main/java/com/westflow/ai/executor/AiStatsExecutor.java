package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统计执行器。
 */
public class AiStatsExecutor extends AbstractAiExecutor {

    public AiStatsExecutor() {
        super(AiExecutorType.STATS);
    }

    @Override
    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "进入统计分析链路";
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
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", context.content());
        arguments.put("domain", plan.domain());
        arguments.putAll(plan.arguments());
        return new AiToolCallRequest(
                "stats.query",
                AiToolType.READ,
                AiToolSource.PLATFORM,
                arguments
        );
    }
}
