package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作执行器。
 */
public class AiActionExecutor extends AbstractAiExecutor {

    public AiActionExecutor() {
        super(AiExecutorType.ACTION);
    }

    @Override
    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "进入写操作规划链路";
    }

    @Override
    protected Map<String, Object> payload(AiExecutionPlan plan, AiExecutionContext context) {
        return Map.of(
                "arguments", plan.arguments(),
                "pageRoute", context.pageRoute(),
                "domain", plan.domain()
        );
    }

    @Override
    protected AiToolCallRequest toolCallRequest(AiExecutionPlan plan, AiExecutionContext context) {
        String toolKey = plan.toolCandidates().isEmpty() ? "process.start" : plan.toolCandidates().getFirst();
        Map<String, Object> arguments = new LinkedHashMap<>(plan.arguments());
        arguments.putIfAbsent("domain", plan.domain());
        arguments.putIfAbsent("routePath", context.pageRoute());
        return new AiToolCallRequest(
                toolKey,
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                arguments
        );
    }
}
