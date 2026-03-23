package com.westflow.ai.api;

import com.westflow.ai.model.AiAuditEntryResponse;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConversationDetailResponse;
import com.westflow.ai.model.AiConversationSummaryResponse;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiMessageBlockResponse;
import com.westflow.ai.model.AiMessageResponse;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.service.AiCopilotService;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AI Copilot 控制器契约测试。
 */
@ExtendWith(MockitoExtension.class)
class AiCopilotControllerTest {

    @Mock
    private AiCopilotService aiCopilotService;

    @InjectMocks
    private AiCopilotController aiCopilotController;

    @Test
    void shouldListConversationsAndCreateConversation() {
        when(aiCopilotService.pageConversations(any())).thenReturn(new PageResponse<>(
                1,
                20,
                1,
                1,
                List.of(new AiConversationSummaryResponse(
                        "conv_001",
                        "PLM 变更审批助手",
                        "用户正在追问 ECR 审批建议",
                        "active",
                        OffsetDateTime.parse("2026-03-23T10:00:00+08:00"),
                        3,
                        List.of("PLM", "审批")
                )),
                List.of()
        ));
        when(aiCopilotService.createConversation(any())).thenReturn(new AiConversationDetailResponse(
                "conv_002",
                "新建 Copilot 会话",
                "刚刚创建",
                "active",
                OffsetDateTime.parse("2026-03-23T10:05:00+08:00"),
                0,
                List.of("AI Copilot"),
                List.of(),
                List.of(),
                List.of()
        ));

        ApiResponse<PageResponse<AiConversationSummaryResponse>> pageResponse = aiCopilotController.pageConversations(
                new PageRequest(1, 20, "PLM", List.of(), List.of(), List.of())
        );
        assertThat(pageResponse.code()).isEqualTo("OK");
        assertThat(pageResponse.data().records()).hasSize(1);
        assertThat(pageResponse.data().records().get(0).title()).isEqualTo("PLM 变更审批助手");

        ApiResponse<AiConversationDetailResponse> createResponse = aiCopilotController.createConversation(
                new com.westflow.ai.model.AiConversationCreateRequest("新建 Copilot 会话", List.of("AI Copilot"))
        );
        assertThat(createResponse.code()).isEqualTo("OK");
        assertThat(createResponse.data().conversationId()).isEqualTo("conv_002");
        assertThat(createResponse.data().title()).isEqualTo("新建 Copilot 会话");
    }

    @Test
    void shouldDirectExecuteReadToolAndStageWriteToolForConfirmation() {
        when(aiCopilotService.executeToolCall(eq("conv_001"), any())).thenAnswer(invocation -> {
            AiToolCallRequest request = invocation.getArgument(1);
            if (request.toolType() == AiToolType.READ) {
                return new AiToolCallResultResponse(
                        "tool_001",
                        "conv_001",
                        "workflow.todo.list",
                        AiToolType.READ,
                        AiToolSource.PLATFORM,
                        "EXECUTED",
                        false,
                        null,
                        "已返回 3 条待办",
                        Map.of("count", 3),
                        Map.of("items", List.of()),
                        OffsetDateTime.parse("2026-03-23T10:10:00+08:00"),
                        OffsetDateTime.parse("2026-03-23T10:10:00+08:00")
                );
            }
            return new AiToolCallResultResponse(
                    "tool_002",
                    "conv_001",
                    "workflow.task.complete",
                    AiToolType.WRITE,
                    AiToolSource.AGENT,
                    "PENDING_CONFIRMATION",
                    true,
                    "confirm_001",
                    "请确认是否完成当前待办",
                    Map.of("taskId", "task_001"),
                    Map.of(),
                    OffsetDateTime.parse("2026-03-23T10:10:00+08:00"),
                    OffsetDateTime.parse("2026-03-23T10:10:00+08:00")
            );
        });
        when(aiCopilotService.confirmToolCall(eq("tool_002"), any())).thenReturn(new AiToolCallResultResponse(
                "tool_002",
                "conv_001",
                "workflow.task.complete",
                AiToolType.WRITE,
                AiToolSource.AGENT,
                "CONFIRMED",
                false,
                "confirm_001",
                "已确认并完成待办",
                Map.of("taskId", "task_001"),
                Map.of("done", true),
                OffsetDateTime.parse("2026-03-23T10:10:00+08:00"),
                OffsetDateTime.parse("2026-03-23T10:11:00+08:00")
        ));

        ApiResponse<AiToolCallResultResponse> readResponse = aiCopilotController.executeToolCall(
                "conv_001",
                new AiToolCallRequest("workflow.todo.list", AiToolType.READ, AiToolSource.PLATFORM, Map.of("keyword", "请假"))
        );
        assertThat(readResponse.data().status()).isEqualTo("EXECUTED");
        assertThat(readResponse.data().requiresConfirmation()).isFalse();

        ApiResponse<AiToolCallResultResponse> writeResponse = aiCopilotController.executeToolCall(
                "conv_001",
                new AiToolCallRequest("workflow.task.complete", AiToolType.WRITE, AiToolSource.AGENT, Map.of("taskId", "task_001"))
        );
        assertThat(writeResponse.data().status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(writeResponse.data().requiresConfirmation()).isTrue();

        ApiResponse<AiToolCallResultResponse> confirmResponse = aiCopilotController.confirmToolCall(
                "tool_002",
                new AiConfirmToolCallRequest(true, "确认执行", Map.of())
        );
        assertThat(confirmResponse.data().status()).isEqualTo("CONFIRMED");
        assertThat(confirmResponse.data().requiresConfirmation()).isFalse();
    }

    @Test
    void shouldReturnConversationDetailAndAuditTrail() {
        when(aiCopilotService.getConversation("conv_001")).thenReturn(new AiConversationDetailResponse(
                "conv_001",
                "PLM 变更审批助手",
                "用户正在追问 ECR 审批建议",
                "active",
                OffsetDateTime.parse("2026-03-23T10:00:00+08:00"),
                3,
                List.of("PLM", "审批"),
                List.of(new AiMessageResponse(
                        "msg_001",
                        "system",
                        "系统",
                        OffsetDateTime.parse("2026-03-23T10:00:00+08:00"),
                        "已接入工作空间",
                        List.of(new AiMessageBlockResponse(
                                "text",
                                "上下文已就绪",
                                "可以继续追问待办",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of()
                        ))
                )),
                List.of(),
                List.of(new AiAuditEntryResponse(
                        "audit_001",
                        "conv_001",
                        "tool_001",
                        "READ",
                        "读取待办列表",
                        OffsetDateTime.parse("2026-03-23T10:02:00+08:00")
                ))
        ));

        ApiResponse<AiConversationDetailResponse> response = aiCopilotController.getConversation("conv_001");
        assertThat(response.data().history()).hasSize(1);
        assertThat(response.data().audit()).hasSize(1);
    }

    @Test
    void shouldAppendMessageToConversation() {
        when(aiCopilotService.appendMessage(eq("conv_001"), any())).thenReturn(new AiConversationDetailResponse(
                "conv_001",
                "PLM 变更审批助手",
                "用户刚刚追加了一条消息",
                "active",
                OffsetDateTime.parse("2026-03-23T10:00:00+08:00"),
                4,
                List.of("PLM", "审批"),
                List.of(new AiMessageResponse(
                        "msg_002",
                        "user",
                        "你",
                        OffsetDateTime.parse("2026-03-23T10:12:00+08:00"),
                        "请继续给我审批建议",
                        List.of()
                )),
                List.of(),
                List.of()
        ));

        ApiResponse<AiConversationDetailResponse> response = aiCopilotController.appendMessage(
                "conv_001",
                new AiMessageAppendRequest("请继续给我审批建议")
        );
        assertThat(response.data().messageCount()).isEqualTo(4);
        assertThat(response.data().history().get(0).content()).isEqualTo("请继续给我审批建议");
    }
}
