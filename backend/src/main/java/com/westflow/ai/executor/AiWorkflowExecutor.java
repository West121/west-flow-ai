package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程解释执行器。
 */
public class AiWorkflowExecutor extends AbstractAiExecutor {

    public AiWorkflowExecutor() {
        super(AiExecutorType.WORKFLOW);
    }

    @Override
    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "进入流程解释与审批轨迹链路";
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
        String toolKey = resolveToolKey(plan);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", context.content());
        arguments.put("domain", plan.domain());
        arguments.put("view", inferView(context));
        arguments.put("pageSize", 100);
        arguments.putAll(plan.arguments());
        return new AiToolCallRequest(
                toolKey,
                AiToolType.READ,
                "workflow.trace.summary".equals(toolKey) ? AiToolSource.SKILL : AiToolSource.PLATFORM,
                arguments
        );
    }

    private String resolveToolKey(AiExecutionPlan plan) {
        if (plan.toolCandidates().isEmpty()) {
            return "task.query";
        }
        String candidate = plan.toolCandidates().getFirst();
        if ("approval.detail.query".equals(candidate) || "approval.trace.query".equals(candidate)) {
            return "task.query";
        }
        return candidate;
    }

    private String inferView(AiExecutionContext context) {
        if (context.pageRoute() != null && context.pageRoute().contains("/workbench/todos/")) {
            return "TODO";
        }
        String normalizedContent = context.content() == null ? "" : context.content();
        if (normalizedContent.contains("我发起")
                || normalizedContent.contains("发起了几个")
                || normalizedContent.contains("申请进度")
                || normalizedContent.contains("当前进度")) {
            return "INITIATED";
        }
        return "TODO";
    }
}
