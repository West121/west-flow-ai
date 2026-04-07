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
        String toolKey = resolveToolKey(plan);
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

    private String resolveToolKey(AiExecutionPlan plan) {
        if (!plan.toolCandidates().isEmpty()) {
            String candidate = normalize(plan.toolCandidates().getFirst());
            if (isRegisteredStyleToolKey(candidate)) {
                return candidate;
            }
            String lowered = candidate.toLowerCase();
            if (lowered.contains("task")) {
                return "task.handle";
            }
            if (
                    lowered.contains("process")
                            || lowered.contains("workflow")
                            || lowered.contains("instance")
                            || lowered.contains("start")
                            || lowered.contains("create")
            ) {
                return "process.start";
            }
        }
        return plan.arguments().containsKey("taskId") ? "task.handle" : "process.start";
    }

    private boolean isRegisteredStyleToolKey(String candidate) {
        return "process.start".equals(candidate)
                || "task.handle".equals(candidate)
                || candidate.startsWith("workflow.task.");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
