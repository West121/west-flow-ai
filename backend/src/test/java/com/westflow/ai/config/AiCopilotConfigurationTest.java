package com.westflow.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.FlowableRuntimeStartService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

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
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);

        ChatClient chatClient = configuration.aiCopilotChatClient(chatModel);
        AiToolRegistry aiToolRegistry = configuration.aiToolRegistry(
                mock(ProcessDefinitionService.class),
                mock(FlowableProcessRuntimeService.class),
                mock(FlowableRuntimeStartService.class),
                mock(JdbcTemplate.class)
        );
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
                configuration.aiOrchestrationPlanner(aiRegistryCatalogService)
        );
        AiCopilotRuntimeService runtimeService = configuration.aiCopilotRuntimeService(
                chatClient,
                supervisorAgent,
                routingAgent,
                aiRegistryCatalogService
        );

        assertThat(chatClient).isNotNull();
        assertThat(aiToolRegistry).isNotNull();
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
