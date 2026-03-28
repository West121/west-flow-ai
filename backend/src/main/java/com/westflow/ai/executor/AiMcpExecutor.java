package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 执行器。
 */
public class AiMcpExecutor extends AbstractAiExecutor {

    public AiMcpExecutor() {
        super(AiExecutorType.MCP);
    }

    @Override
    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "进入外部 MCP 能力链路";
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
        String toolKey = plan.toolCandidates().isEmpty() ? "" : plan.toolCandidates().getFirst();
        if (toolKey.isBlank()) {
            return null;
        }
        Map<String, Object> arguments = new LinkedHashMap<>(plan.arguments());
        arguments.putIfAbsent("keyword", context.content());
        arguments.putIfAbsent("domain", plan.domain());
        return new AiToolCallRequest(toolKey, AiToolType.READ, AiToolSource.MCP, arguments);
    }
}
