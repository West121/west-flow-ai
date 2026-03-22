package com.westflow.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AI Copilot 配置装配测试。
 */
class AiCopilotConfigurationTest {

    private final AiCopilotConfiguration configuration = new AiCopilotConfiguration();

    @Test
    void shouldCreateSpringAiAndAlibabaRuntimeBeans() {
        ChatModel chatModel = mock(ChatModel.class);

        ChatClient chatClient = configuration.aiCopilotChatClient(chatModel);
        ReactAgent workflowDesignAgent = configuration.workflowDesignAgent(chatModel);
        ReactAgent taskHandleAgent = configuration.taskHandleAgent(chatModel);
        ReactAgent statsAgent = configuration.statsAgent(chatModel);
        ReactAgent plmAssistantAgent = configuration.plmAssistantAgent(chatModel);
        LlmRoutingAgent routingAgent = configuration.routingAgent(
                chatModel,
                workflowDesignAgent,
                taskHandleAgent,
                statsAgent,
                plmAssistantAgent
        );
        SupervisorAgent supervisorAgent = configuration.supervisorAgent(
                chatModel,
                routingAgent,
                workflowDesignAgent,
                taskHandleAgent,
                statsAgent,
                plmAssistantAgent
        );
        AiGatewayService aiGatewayService = configuration.aiGatewayService(
                configuration.aiOrchestrationPlanner(configuration.aiAgentRegistry(), configuration.aiSkillRegistry())
        );
        AiCopilotRuntimeService runtimeService = configuration.aiCopilotRuntimeService(chatClient, supervisorAgent, routingAgent);

        assertThat(chatClient).isNotNull();
        assertThat(workflowDesignAgent).isNotNull();
        assertThat(taskHandleAgent).isNotNull();
        assertThat(statsAgent).isNotNull();
        assertThat(plmAssistantAgent).isNotNull();
        assertThat(routingAgent).isNotNull();
        assertThat(supervisorAgent).isNotNull();
        assertThat(aiGatewayService).isNotNull();
        assertThat(runtimeService).isNotNull();
    }
}
