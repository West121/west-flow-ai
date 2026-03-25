package com.westflow.ai.service;

import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolExecutionContext;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.common.error.ContractException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * 统一 AI 工具执行服务。
 */
public class AiToolExecutionService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiToolRegistry aiToolRegistry;
    private final Clock clock;
    private final AiRegistryCatalogService aiRegistryCatalogService;

    public AiToolExecutionService(AiToolRegistry aiToolRegistry) {
        this(aiToolRegistry, null, Clock.system(TIME_ZONE));
    }

    public AiToolExecutionService(AiToolRegistry aiToolRegistry, AiRegistryCatalogService aiRegistryCatalogService) {
        this(aiToolRegistry, aiRegistryCatalogService, Clock.system(TIME_ZONE));
    }

    public AiToolExecutionService(AiToolRegistry aiToolRegistry, Clock clock) {
        this(aiToolRegistry, null, clock);
    }

    public AiToolExecutionService(
            AiToolRegistry aiToolRegistry,
            AiRegistryCatalogService aiRegistryCatalogService,
            Clock clock
    ) {
        this.aiToolRegistry = Objects.requireNonNull(aiToolRegistry, "aiToolRegistry");
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 执行工具调用。
     */
    public AiToolCallResultResponse executeToolCall(String conversationId, AiToolCallRequest request) {
        return executeToolCall(conversationId, request, null);
    }

    /**
     * 按指定用户权限执行工具调用。
     */
    public AiToolCallResultResponse executeToolCall(String conversationId, AiToolCallRequest request, String userId) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(request, "request");
        AiToolDefinition definition = resolveDefinition(request, userId);
        validateDefinition(request, definition);

        OffsetDateTime now = now();
        String toolCallId = newId("tool");
        if (request.toolType() == AiToolType.WRITE) {
            return new AiToolCallResultResponse(
                    toolCallId,
                    conversationId,
                    request.toolKey(),
                    request.toolType(),
                    request.toolSource(),
                    "PENDING_CONFIRMATION",
                    true,
                    newId("confirm"),
                    definition.summary(),
                    request.arguments(),
                    Map.of(),
                    now,
                    null
            );
        }

        Map<String, Object> result = definition.handler().execute(
                new AiToolExecutionContext(conversationId, request, definition, now)
        );
        return new AiToolCallResultResponse(
                toolCallId,
                conversationId,
                request.toolKey(),
                request.toolType(),
                request.toolSource(),
                "EXECUTED",
                false,
                null,
                definition.summary(),
                request.arguments(),
                result,
                now,
                now
        );
    }

    /**
     * 在确认写操作后真正执行工具处理器。
     */
    public AiToolCallResultResponse executeConfirmedWriteToolCall(
            String conversationId,
            AiToolCallRequest request,
            String userId,
            String toolCallId,
            String confirmationId
    ) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(request, "request");
        AiToolDefinition definition = resolveDefinition(request, userId);
        validateDefinition(request, definition);
        if (definition.toolType() != AiToolType.WRITE) {
            throw new ContractException(
                    "VALIDATION.FIELD_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "只允许执行已确认的写操作工具"
            );
        }

        OffsetDateTime now = now();
        Map<String, Object> result = definition.handler().execute(
                new AiToolExecutionContext(conversationId, request, definition, now)
        );
        return new AiToolCallResultResponse(
                toolCallId,
                conversationId,
                request.toolKey(),
                request.toolType(),
                request.toolSource(),
                "CONFIRMED",
                false,
                confirmationId,
                definition.summary(),
                request.arguments(),
                result,
                now,
                now
        );
    }

    private void validateDefinition(AiToolCallRequest request, AiToolDefinition definition) {
        if (request.toolType() != definition.toolType()) {
            throw new ContractException(
                    "VALIDATION.FIELD_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "工具类型与注册定义不一致",
                    Map.of("toolKey", request.toolKey(), "expectedType", definition.toolType(), "actualType", request.toolType())
            );
        }
        if (request.toolSource() != definition.toolSource()) {
            throw new ContractException(
                    "VALIDATION.FIELD_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "工具来源与注册定义不一致",
                    Map.of("toolKey", request.toolKey(), "expectedSource", definition.toolSource(), "actualSource", request.toolSource())
            );
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.ofHours(8));
    }

    private AiToolDefinition resolveDefinition(AiToolCallRequest request, String userId) {
        AiToolDefinition registeredDefinition = aiToolRegistry.require(request.toolKey());
        if (aiRegistryCatalogService == null) {
            return registeredDefinition;
        }
        AiRegistryCatalogService.AiToolCatalogItem catalogItem = aiRegistryCatalogService.findTool(userId, request.toolKey())
                .orElseThrow(() -> new ContractException(
                        "VALIDATION.FIELD_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "工具未授权或未注册",
                        Map.of("toolKey", request.toolKey(), "userId", userId == null ? "" : userId)
                ));
        return new AiToolDefinition(
                catalogItem.toolCode(),
                catalogItem.toolSource(),
                catalogItem.toolType(),
                catalogItem.toolName(),
                registeredDefinition.handler()
        );
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
