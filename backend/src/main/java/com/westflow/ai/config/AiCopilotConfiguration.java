package com.westflow.ai.config;

import com.westflow.ai.agent.AiAgentDescriptor;
import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.service.AiToolExecutionService;
import com.westflow.ai.skill.AiSkillDescriptor;
import com.westflow.ai.skill.AiSkillRegistry;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Copilot 默认组件装配。
 */
@Configuration
public class AiCopilotConfiguration {

    /**
     * 注册默认智能体元数据。
     */
    @Bean
    public AiAgentRegistry aiAgentRegistry() {
        return new AiAgentRegistry(List.of(
                new AiAgentDescriptor("supervisor", "Supervisor", "SUPERVISOR", List.of("OA", "PLM", "GENERAL"), true, 100),
                new AiAgentDescriptor("routing", "Routing", "ROUTING", List.of("OA", "PLM", "GENERAL"), false, 80),
                new AiAgentDescriptor("workflow-designer", "流程设计智能体", "ROUTING", List.of("OA", "PLM"), false, 70),
                new AiAgentDescriptor("plm-assistant", "PLM 助手", "ROUTING", List.of("PLM"), false, 60)
        ));
    }

    /**
     * 注册默认 Skill 元数据。
     */
    @Bean
    public AiSkillRegistry aiSkillRegistry() {
        return new AiSkillRegistry(List.of(
                new AiSkillDescriptor("approval-trace", "审批轨迹解释", List.of("OA", "PLM"), List.of("轨迹", "trace", "路径"), false, 90),
                new AiSkillDescriptor("submission-advice", "发起参数建议", List.of("OA", "PLM"), List.of("发起", "submit", "建议"), false, 80),
                new AiSkillDescriptor("plm-change-summary", "PLM 变更摘要", List.of("PLM"), List.of("PLM", "ECR", "ECO", "物料"), false, 85)
        ));
    }

    /**
     * 注册默认工具定义。
     */
    @Bean
    public AiToolRegistry aiToolRegistry() {
        return new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "workflow.todo.list",
                        AiToolSource.PLATFORM,
                        "已返回待办列表",
                        context -> Map.of(
                                "count", 0,
                                "items", List.of(),
                                "keyword", context.request().arguments().getOrDefault("keyword", "")
                        )
                ),
                AiToolDefinition.read(
                        "workflow.trace.summary",
                        AiToolSource.SKILL,
                        "已返回审批轨迹摘要",
                        context -> Map.of(
                                "summary", "当前流程已进入轨迹分析模式，可继续查看会签进度和审批路径。",
                                "domain", context.request().arguments().getOrDefault("domain", "GENERAL")
                        )
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "已返回 PLM 变更摘要",
                        context -> Map.of(
                                "summary", "当前 PLM 变更申请已生成业务摘要，可继续追问 ECR/ECO 影响范围。",
                                "businessType", context.request().arguments().getOrDefault("businessType", "PLM")
                        )
                ),
                AiToolDefinition.write(
                        "process.start",
                        AiToolSource.AGENT,
                        "请确认是否发起流程",
                        context -> Map.of("accepted", true)
                ),
                AiToolDefinition.write(
                        "workflow.task.complete",
                        AiToolSource.AGENT,
                        "请确认是否完成当前待办",
                        context -> Map.of("accepted", true)
                ),
                AiToolDefinition.write(
                        "workflow.task.reject",
                        AiToolSource.AGENT,
                        "请确认是否退回当前待办",
                        context -> Map.of("accepted", true)
                )
        ));
    }

    /**
     * 装配 AI 编排规划器。
     */
    @Bean
    public AiOrchestrationPlanner aiOrchestrationPlanner(
            AiAgentRegistry aiAgentRegistry,
            AiSkillRegistry aiSkillRegistry
    ) {
        return new AiOrchestrationPlanner(aiAgentRegistry, aiSkillRegistry);
    }

    /**
     * 装配 AI 网关。
     */
    @Bean
    public AiGatewayService aiGatewayService(AiOrchestrationPlanner aiOrchestrationPlanner) {
        return new AiGatewayService(aiOrchestrationPlanner);
    }

    /**
     * 装配工具执行服务。
     */
    @Bean
    public AiToolExecutionService aiToolExecutionService(AiToolRegistry aiToolRegistry) {
        return new AiToolExecutionService(aiToolRegistry);
    }
}
