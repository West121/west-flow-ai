package com.westflow.ai.service;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.common.error.ContractException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 工具注册与执行层测试。
 */
class AiToolExecutionServiceTest {

    private AiToolRegistry aiToolRegistry;
    private AiToolExecutionService aiToolExecutionService;

    @BeforeEach
    void setUp() {
        aiToolRegistry = new AiToolRegistry();
        aiToolExecutionService = new AiToolExecutionService(
                aiToolRegistry,
                Clock.fixed(Instant.parse("2026-03-23T02:10:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldDirectExecuteRegisteredReadTool() {
        AtomicInteger invocationCount = new AtomicInteger();
        aiToolRegistry.register(AiToolDefinition.read(
                "workflow.todo.list",
                AiToolSource.PLATFORM,
                "已返回 3 条待办",
                context -> {
                    invocationCount.incrementAndGet();
                    assertThat(context.conversationId()).isEqualTo("conv_001");
                    assertThat(context.request().arguments()).containsEntry("keyword", "请假");
                    return Map.of("count", 3, "items", List.of("task_001", "task_002", "task_003"));
                }
        ));

        AiToolCallResultResponse result = aiToolExecutionService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "workflow.todo.list",
                        AiToolType.READ,
                        AiToolSource.PLATFORM,
                        Map.of("keyword", "请假")
                )
        );

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.requiresConfirmation()).isFalse();
        assertThat(result.summary()).isEqualTo("已返回 3 条待办");
        assertThat(result.result()).containsEntry("count", 3);
        assertThat(result.completedAt()).isEqualTo(OffsetDateTime.parse("2026-03-23T10:10:00+08:00"));
        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    void shouldReturnPendingConfirmationForRegisteredWriteTool() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        aiToolRegistry.register(AiToolDefinition.write(
                "workflow.task.complete",
                AiToolSource.AGENT,
                "请确认是否完成当前待办",
                context -> {
                    invoked.set(true);
                    return Map.of("done", true);
                }
        ));

        AiToolCallResultResponse result = aiToolExecutionService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "workflow.task.complete",
                        AiToolType.WRITE,
                        AiToolSource.AGENT,
                        Map.of("taskId", "task_001")
                )
        );

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.requiresConfirmation()).isTrue();
        assertThat(result.summary()).isEqualTo("请确认是否完成当前待办");
        assertThat(result.confirmationId()).startsWith("confirm_");
        assertThat(result.result()).isEmpty();
        assertThat(result.completedAt()).isNull();
        assertThat(invoked).isFalse();
    }

    @Test
    void shouldRejectUnregisteredTool() {
        assertThatThrownBy(() -> aiToolExecutionService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "workflow.todo.list",
                        AiToolType.READ,
                        AiToolSource.PLATFORM,
                        Map.of("keyword", "请假")
                )
        ))
                .isInstanceOf(ContractException.class)
                .hasMessage("工具未注册");
    }

    @Test
    void shouldRejectToolTypeMismatch() {
        aiToolRegistry.register(AiToolDefinition.read(
                "workflow.todo.list",
                AiToolSource.PLATFORM,
                "已返回 3 条待办",
                context -> Map.of("count", 3)
        ));

        assertThatThrownBy(() -> aiToolExecutionService.executeToolCall(
                "conv_001",
                new AiToolCallRequest(
                        "workflow.todo.list",
                        AiToolType.WRITE,
                        AiToolSource.PLATFORM,
                        Map.of("keyword", "请假")
                )
        ))
                .isInstanceOf(ContractException.class)
                .hasMessage("工具类型与注册定义不一致");
    }
}
