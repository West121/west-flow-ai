package com.westflow.ai.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.service.AiRuntimeToolCallbackProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.tool.ToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spring AI Alibaba 运行时编排测试。
 */
class SpringAiAlibabaCopilotRuntimeServiceTest {

    private SupervisorAgent supervisorAgent;
    private LlmRoutingAgent routingAgent;
    private ChatClient chatClient;
    private AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider;
    private AiCopilotRuntimeService runtimeService;

    @BeforeEach
    void setUp() throws Exception {
        supervisorAgent = mock(SupervisorAgent.class);
        routingAgent = mock(LlmRoutingAgent.class);
        chatClient = mock(ChatClient.class);
        aiRuntimeToolCallbackProvider = mock(AiRuntimeToolCallbackProvider.class);
        when(aiRuntimeToolCallbackProvider.createProvider(anyString(), anyString()))
                .thenReturn(ToolCallbackProvider.from());
        runtimeService = new SpringAiAlibabaCopilotRuntimeService(
                chatClient,
                supervisorAgent,
                routingAgent,
                null,
                aiRuntimeToolCallbackProvider
        );
    }

    @Test
    void shouldUseSupervisorAgentForWriteRoute() throws Exception {
        when(supervisorAgent.invoke(anyString()))
                .thenReturn(Optional.of(new OverAllState(java.util.Map.of("output", "supervisor-result"))));

        String result = runtimeService.generateReply(
                new AiGatewayRequest("conv_001", "usr_001", "请发起流程", "OA", true, List.of(), List.of("OA")),
                new AiGatewayResponse("SUPERVISOR", "supervisor", true, List.of(), null, null, List.of(), null)
        );

        assertThat(result).isEqualTo("supervisor-result");
    }

    @Test
    void shouldUseRoutingAgentForRoutingRoute() throws Exception {
        when(routingAgent.invoke(anyString()))
                .thenReturn(Optional.of(new OverAllState(java.util.Map.of("output", "routing-result"))));

        String result = runtimeService.generateReply(
                new AiGatewayRequest("conv_002", "usr_001", "帮我解释待办", "PLM", false, List.of(), List.of("PLM")),
                new AiGatewayResponse("ROUTING", "routing", false, List.of(), null, null, List.of(), null)
        );

        assertThat(result).isEqualTo("routing-result");
    }

    @Test
    void shouldFallbackToChatClientWhenAgentDoesNotReturnText() throws Exception {
        when(routingAgent.invoke(anyString()))
                .thenReturn(Optional.of(new OverAllState(java.util.Map.of())));
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallbackProvider.class))).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("chat-client-result");

        String result = runtimeService.generateReply(
                new AiGatewayRequest("conv_003", "usr_001", "总结审批轨迹", "OA", false, List.of("approval-trace"), List.of("OA")),
                new AiGatewayResponse("SKILL", "routing", false, List.of("approval-trace"), null, null, List.of(), null)
        );

        assertThat(result).isEqualTo("chat-client-result");
        verify(aiRuntimeToolCallbackProvider).createProvider("usr_001", "OA");
    }

    @Test
    void shouldIgnoreOpaqueAgentIdsAndFallbackToChatClient() throws Exception {
        when(routingAgent.invoke(anyString()))
                .thenReturn(Optional.of(new OverAllState(java.util.Map.of(
                        "messageId", "9e9e1966-b0c2-495a-bbb2-61f162f34351"
                ))));
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallbackProvider.class))).thenReturn(requestSpec);
        when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("当前没有待办。");

        String result = runtimeService.generateReply(
                new AiGatewayRequest("conv_004", "usr_001", "帮我看看当前待办", "OA", false, List.of(), List.of("OA")),
                new AiGatewayResponse("ROUTING", "routing", false, List.of(), null, null, List.of(), null)
        );

        assertThat(result).isEqualTo("当前没有待办。");
    }

    @Test
    void shouldGeneratePlannedReplyDirectlyByChatClient() {
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("张三当前在 PLM产品组，岗位是 PLM产品经理。");

        String result = runtimeService.generatePlannedReply(
                new AiGatewayRequest("conv_005", "usr_001", "张三是什么部门 岗位", "SYSTEM", false, List.of(), List.of("SYSTEM")),
                new AiGatewayResponse("ROUTING", "knowledge-agent", false, List.of(), null, null, List.of(), null),
                "KNOWLEDGE",
                "{\"keyword\":\"张三是什么部门 岗位\"}",
                "fallback"
        );

        assertThat(result).isEqualTo("张三当前在 PLM产品组，岗位是 PLM产品经理。");
    }
}
