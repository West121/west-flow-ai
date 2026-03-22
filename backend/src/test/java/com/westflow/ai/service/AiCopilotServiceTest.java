package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.mapper.AiAuditMapper;
import com.westflow.ai.mapper.AiConfirmationMapper;
import com.westflow.ai.mapper.AiConversationMapper;
import com.westflow.ai.mapper.AiMessageMapper;
import com.westflow.ai.mapper.AiToolCallMapper;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConversationRecord;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallRecord;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
        aiCopilotService = new DbAiCopilotService(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                new ObjectMapper()
        );
    }

    @Test
    void shouldDirectExecuteReadToolCall() {
        when(aiConversationMapper.selectById("conv_001")).thenReturn(conversation());

        AiToolCallResultResponse result = aiCopilotService.executeToolCall(
                "conv_001",
                new AiToolCallRequest("workflow.todo.list", AiToolType.READ, AiToolSource.PLATFORM, Map.of("keyword", "请假"))
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
                new AiToolCallRequest("workflow.task.complete", AiToolType.WRITE, AiToolSource.AGENT, Map.of("taskId", "task_001"))
        );
        assertThat(pending.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(pending.requiresConfirmation()).isTrue();

        when(aiToolCallMapper.selectById(pending.toolCallId())).thenReturn(new AiToolCallRecord(
                pending.toolCallId(),
                "conv_001",
                "workflow.task.complete",
                AiToolType.WRITE,
                AiToolSource.AGENT,
                "PENDING_CONFIRMATION",
                true,
                "{\"taskId\":\"task_001\"}",
                "{}",
                "请确认是否完成当前待办",
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
        verify(aiConfirmationMapper).insertConfirmation(any());
        verify(aiAuditMapper, times(2)).insertAudit(any());
    }

    private AiConversationRecord conversation() {
        return new AiConversationRecord(
                "conv_001",
                "PLM 变更审批助手",
                "用户正在追问 ECR 审批建议",
                "active",
                "[\"PLM\",\"审批\"]",
                3,
                "usr_001",
                LocalDateTime.of(2026, 3, 23, 10, 0),
                LocalDateTime.of(2026, 3, 23, 10, 0)
        );
    }
}
