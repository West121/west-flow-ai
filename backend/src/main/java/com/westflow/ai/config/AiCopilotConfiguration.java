package com.westflow.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.agent.AiAgentDescriptor;
import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.runtime.SpringAiAlibabaCopilotRuntimeService;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.service.AiToolExecutionService;
import com.westflow.ai.skill.AiSkillDescriptor;
import com.westflow.ai.skill.AiSkillRegistry;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Copilot 默认组件装配。
 */
@Configuration
public class AiCopilotConfiguration {

    /**
     * 装配 Spring AI ChatClient。
     */
    @Bean
    public ChatClient aiCopilotChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 West Flow AI Copilot，请始终使用简洁中文回答。")
                .build();
    }

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

    /**
     * 流程设计智能体。
     */
    @Bean
    public ReactAgent workflowDesignAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("workflow-design-agent")
                .description("负责流程定义、节点建议和发布前检查")
                .model(chatModel)
                .instruction("你是流程设计智能体，请结合输入生成流程设计建议。")
                .build();
    }

    /**
     * 待办处理智能体。
     */
    @Bean
    public ReactAgent taskHandleAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("task-handle-agent")
                .description("负责解释待办、审批动作与处理建议")
                .model(chatModel)
                .instruction("你是待办处理智能体，请结合任务上下文生成处理建议。")
                .build();
    }

    /**
     * 统计问答智能体。
     */
    @Bean
    public ReactAgent statsAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("stats-agent")
                .description("负责流程、OA、PLM 统计问答")
                .model(chatModel)
                .instruction("你是统计问答智能体，请根据业务域回答统计问题。")
                .build();
    }

    /**
     * PLM 助手。
     */
    @Bean
    public ReactAgent plmAssistantAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("plm-assistant-agent")
                .description("负责 PLM 变更、ECR/ECO 和物料变更问题")
                .model(chatModel)
                .instruction("你是 PLM 助手，请回答 ECR、ECO 和物料变更相关问题。")
                .build();
    }

    /**
     * Routing Agent。
     */
    @Bean
    public LlmRoutingAgent routingAgent(
            ChatModel chatModel,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return LlmRoutingAgent.builder()
                .name("copilot-routing-agent")
                .description("根据用户意图把问题路由到具体业务智能体")
                .model(chatModel)
                .systemPrompt("""
                        你是 West Flow AI Routing Agent。
                        请根据输入把请求路由到最合适的子智能体。
                        - workflow-design-agent: 流程设计、流程定义、节点配置
                        - task-handle-agent: 待办、审批、轨迹、动作建议
                        - stats-agent: 统计、报表、趋势、指标
                        - plm-assistant-agent: ECR、ECO、物料变更、PLM 流程
                        """)
                .fallbackAgent("task-handle-agent")
                .subAgents(List.of(workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * Supervisor Agent。
     */
    @Bean
    public SupervisorAgent supervisorAgent(
            ChatModel chatModel,
            LlmRoutingAgent routingAgent,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return SupervisorAgent.builder()
                .name("copilot-supervisor-agent")
                .description("负责协调 Copilot 多智能体和写操作确认前的监督")
                .model(chatModel)
                .mainAgent(taskHandleAgent)
                .systemPrompt("""
                        你是 West Flow AI Supervisor。
                        你需要协调 Routing Agent 与各业务智能体，并确保写操作遵守“必须确认”策略。
                        如果请求是查询或分析，允许直接交给 Routing Agent。
                        如果请求涉及发起、审批、退回、驳回等写动作，只输出确认前说明。
                        """)
                .subAgents(List.of(routingAgent, workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * 装配 Spring AI Alibaba 运行时服务。
     */
    @Bean
    public AiCopilotRuntimeService aiCopilotRuntimeService(
            ChatClient aiCopilotChatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent
    ) {
        return new SpringAiAlibabaCopilotRuntimeService(aiCopilotChatClient, supervisorAgent, routingAgent);
    }
}
