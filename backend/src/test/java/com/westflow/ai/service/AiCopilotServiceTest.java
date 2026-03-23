package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.agent.AiAgentDescriptor;
import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.mapper.AiAuditMapper;
import com.westflow.ai.mapper.AiConfirmationMapper;
import com.westflow.ai.mapper.AiConversationMapper;
import com.westflow.ai.mapper.AiMessageMapper;
import com.westflow.ai.mapper.AiToolCallMapper;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConversationDetailResponse;
import com.westflow.ai.model.AiConversationRecord;
import com.westflow.ai.model.AiMessageBlockResponse;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiMessageResponse;
import com.westflow.ai.model.AiMessageRecord;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallRecord;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.runtime.SpringAiAlibabaCopilotRuntimeService;
import com.westflow.ai.skill.AiSkillDescriptor;
import com.westflow.ai.skill.AiSkillRegistry;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

/**
 * AI Copilot 服务层测试。
 */
@ExtendWith(MockitoExtension.class)
class AiCopilotServiceTest {

    @Mock
    private AiConversationMapper aiConversationMapper;

    @Mock
    private AiMessageMapper aiMessageMapper;

    @Mock
    private AiToolCallMapper aiToolCallMapper;

    @Mock
    private AiConfirmationMapper aiConfirmationMapper;

    @Mock
    private AiAuditMapper aiAuditMapper;

    private AiCopilotService aiCopilotService;
    private AtomicBoolean confirmedWriteExecuted;

    @BeforeEach
    void setUp() {
        confirmedWriteExecuted = new AtomicBoolean(false);
        AiAgentRegistry aiAgentRegistry = new AiAgentRegistry(List.of(
                new AiAgentDescriptor("supervisor", "Supervisor", "SUPERVISOR", List.of("OA", "PLM", "GENERAL"), true, 100),
                new AiAgentDescriptor("routing", "Routing", "ROUTING", List.of("OA", "PLM", "GENERAL"), false, 80)
        ));
        AiSkillRegistry aiSkillRegistry = new AiSkillRegistry(List.of(
                new AiSkillDescriptor("approval-trace", "审批轨迹解释", List.of("OA", "PLM"), List.of("轨迹", "路径"), false, 90),
                new AiSkillDescriptor("plm-change-summary", "PLM 变更摘要", List.of("PLM"), List.of("PLM", "ECR", "ECO"), false, 80)
        ));
        AiToolRegistry aiToolRegistry = new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "task.query",
                        AiToolSource.PLATFORM,
                        "已返回待办列表",
                        context -> Map.of("count", 1, "items", List.of("task_001"))
                ),
                AiToolDefinition.read(
                        "workflow.todo.list",
                        AiToolSource.PLATFORM,
                        "已返回待办列表",
                        context -> Map.of("count", 1, "items", List.of("task_001"))
                ),
                AiToolDefinition.read(
                        "workflow.trace.summary",
                        AiToolSource.SKILL,
                        "已返回审批轨迹摘要",
                        context -> Map.of("summary", "当前审批轨迹正常")
                ),
                AiToolDefinition.read(
                        "stats.query",
                        AiToolSource.PLATFORM,
                        "已返回统计摘要",
                        context -> Map.of(
                                "total", 12,
                                "completed", 9,
                                "pending", 3,
                                "completionRate", "75%"
                        )
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "已返回 PLM 变更摘要",
                        context -> Map.of("summary", "PLM 变更摘要已生成")
                ),
                AiToolDefinition.write(
                        "process.start",
                        AiToolSource.PLATFORM,
                        "请确认是否发起流程",
                        context -> Map.of("accepted", true)
                ),
                AiToolDefinition.write(
                        "task.handle",
                        AiToolSource.PLATFORM,
                        "请确认是否处理当前待办",
                        context -> {
                            confirmedWriteExecuted.set(true);
                            return Map.of("accepted", true, "done", true);
                        }
                ),
                AiToolDefinition.write(
                        "workflow.task.complete",
                        AiToolSource.AGENT,
                        "请确认是否完成当前待办",
                        context -> Map.of("accepted", true, "done", true)
                )
        ));
        ChatClient chatClient = org.mockito.Mockito.mock(ChatClient.class);
        AiCopilotRuntimeService runtimeService = new SpringAiAlibabaCopilotRuntimeService(
                chatClient,
                mockSupervisorAgent("supervisor-reply"),
                mockRoutingAgent("routing-reply")
        );
        AiRegistryCatalogService aiRegistryCatalogService = mock(AiRegistryCatalogService.class);
        lenient().when(aiRegistryCatalogService.matchReadTool(anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String content = invocation.getArgument(1, String.class);
                    if (content != null && content.contains("轨迹")) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "task.query",
                                "查询待办",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("OA", "PLM"),
                                List.of("轨迹"),
                                List.of("/workbench/"),
                                "westflow-internal-mcp",
                                "",
                                95,
                                Map.of()
                        ));
                    }
                    if (content != null && (content.contains("待办") || content.contains("路径"))) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "task.query",
                                "查询待办",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:copilot:open",
                                List.of("OA", "PLM"),
                                List.of("待办", "路径"),
                                List.of("/workbench/"),
                                "westflow-internal-mcp",
                                "",
                                95,
                                Map.of()
                        ));
                    }
                    if (content != null && content.contains("统计")) {
                        return java.util.Optional.of(new AiRegistryCatalogService.AiToolCatalogItem(
                                "stats.query",
                                "查询统计",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "ai:stats:query",
                                List.of("OA", "PLM", "GENERAL"),
                                List.of("统计"),
                                List.of("/system/", "/workflow/"),
                                "westflow-internal-mcp",
                                "",
                                90,
                                Map.of()
                        ));
                    }
                    return java.util.Optional.empty();
                });
        aiCopilotService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                new ObjectMapper(),
                new AiGatewayService(new AiOrchestrationPlanner(aiAgentRegistry, aiSkillRegistry)),
                new AiToolExecutionService(aiToolRegistry),
                runtimeService,
                aiRegistryCatalogService
        );
    }

    @Test
    void shouldDirectExecuteReadToolCall() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse result = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest("task.query", AiToolType.READ, AiToolSource.PLATFORM, Map.of("keyword", "请假"))
        );

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.requiresConfirmation()).isFalse();
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiAuditMapper).insertAudit(any());
    }

    @Test
    void shouldStageWriteToolCallForConfirmationAndConfirmIt() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse pending = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest("task.handle", AiToolType.WRITE, AiToolSource.PLATFORM, Map.of("taskId", "task_001", "action", "COMPLETE"))
        );
        assertThat(pending.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(pending.requiresConfirmation()).isTrue();

        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "task.handle",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                "PENDING_CONFIRMATION",
                true,
                "{\"taskId\":\"task_001\",\"action\":\"COMPLETE\"}",
                "{}",
                "请确认是否处理当前待办",
                pending.confirmationId(),
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 10),
                LocalDateTime.of(2026, 3, 23, 10, 10)
        ));

        AiToolCallResultResponse confirmed = aiCopilotService.confirmToolCall(
                pending.toolCallId(),
                new AiConfirmToolCallRequest(true, "确认执行")
        );

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.result()).containsEntry("done", true);
        assertThat(confirmedWriteExecuted.get()).isTrue();
        verify(aiConfirmationMapper).insertConfirmation(any());
        verify(aiAuditMapper, times(2)).insertAudit(any());
    }

    @Test
    void shouldAppendWriteIntentMessageAndStageConfirmationCard() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("请直接发起这个流程")
        );

        assertThat(detail.conversationId()).isEqualTo("conv_001");
        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("form-preview", "confirm");
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiAuditMapper, times(2)).insertAudit(any());
    }

    @Test
    void shouldAppendReadIntentMessageThroughSkillRouting() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());
        List<AiMessageRecord> storedMessages = mockStoredMessages(
                new AiMessageRecord(
                        "msg_001",
                        "conv_001",
                        "assistant",
                        "AI Copilot",
                        "我已经汇总当前审批轨迹，可继续追问节点处理路径。",
                        "[]",
                        "usr_001",
                        LocalDateTime.of(2026, 3, 23, 10, 12),
                        LocalDateTime.of(2026, 3, 23, 10, 12)
                )
        );
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我总结一下审批轨迹")
        );

        assertThat(detail.conversationId()).isEqualTo("conv_001");
        verify(aiToolCallMapper).insertToolCall(any());
        verify(aiConfirmationMapper, never()).insertConfirmation(any());
    }

    @Test
    void shouldAppendTodoExplanationWithStatsBlock() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("OA", "待办", "route:/workbench/todos/task_001"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("帮我解释这个待办的处理路径")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("stats");
    }

    @Test
    void shouldAppendStatsAnswerWithStatsBlock() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversationWithTags("OA", "统计", "route:/system/dashboard"));
        List<AiMessageRecord> storedMessages = mockStoredMessages();
        when(aiToolCallMapper.selectByConversationId("conv_001")).thenReturn(List.of());
        when(aiAuditMapper.selectByConversationId("conv_001")).thenReturn(List.of());

        AiConversationDetailResponse detail = aiCopilotService.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("给我看一下本周流程统计")
        );

        AiMessageResponse assistantMessage = detail.history().get(detail.history().size() - 1);
        assertThat(assistantMessage.blocks())
                .extracting(AiMessageBlockResponse::type)
                .contains("stats");
    }

    private AiConversationRecord conversation() {
        return conversationWithTags("PLM", "审批");
    }

    private AiConversationRecord conversationWithTags(String... tags) {
        return new AiConversationRecord(
                "conv_001",
                "PLM 变更审批助手",
                "用户正在追问 ECR 审批建议",
                "active",
                toJson(tags),
                3,
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 0),
                LocalDateTime.of(2026, 3, 23, 10, 0)
        );
    }

    private String toJson(String... tags) {
        try {
            return new ObjectMapper().writeValueAsString(List.of(tags));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private List<AiMessageRecord> mockStoredMessages(AiMessageRecord... initialMessages) {
        java.util.ArrayList<AiMessageRecord> storedMessages = new java.util.ArrayList<>(List.of(initialMessages));
        AtomicLong initialCount = new AtomicLong(storedMessages.size());
        when(aiMessageMapper.countByConversationId("conv_001")).thenAnswer(invocation -> {
            if (storedMessages.isEmpty()) {
                return initialCount.get();
            }
            return (long) storedMessages.size();
        });
        when(aiMessageMapper.selectByConversationId("conv_001")).thenAnswer(invocation -> List.copyOf(storedMessages));
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0, AiMessageRecord.class));
            return null;
        }).when(aiMessageMapper).insertMessage(any());
        return storedMessages;
    }

    private com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent mockSupervisorAgent(String reply) {
        try {
            com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent agent =
                    org.mockito.Mockito.mock(com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent.class);
            lenient().when(agent.invoke(anyString()))
                    .thenReturn(java.util.Optional.of(new com.alibaba.cloud.ai.graph.OverAllState(Map.of("output", reply))));
            return agent;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent mockRoutingAgent(String reply) {
        try {
            com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent agent =
                    org.mockito.Mockito.mock(com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent.class);
            lenient().when(agent.invoke(anyString()))
                    .thenReturn(java.util.Optional.of(new com.alibaba.cloud.ai.graph.OverAllState(Map.of("output", reply))));
            return agent;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
