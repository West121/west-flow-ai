package com.westflow.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.mapper.AiAuditMapper;
import com.westflow.ai.mapper.AiConfirmationMapper;
import com.westflow.ai.mapper.AiConversationMapper;
import com.westflow.ai.mapper.AiMessageMapper;
import com.westflow.ai.mapper.AiToolCallMapper;
import com.westflow.ai.model.AiAuditEntryResponse;
import com.westflow.ai.model.AiAuditRecord;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConfirmationRecord;
import com.westflow.ai.model.AiConversationCreateRequest;
import com.westflow.ai.model.AiConversationDetailResponse;
import com.westflow.ai.model.AiConversationRecord;
import com.westflow.ai.model.AiConversationSummaryResponse;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiMessageBlockResponse;
import com.westflow.ai.model.AiMessageBlockResponse.Field;
import com.westflow.ai.model.AiMessageBlockResponse.Failure;
import com.westflow.ai.model.AiMessageBlockResponse.Metric;
import com.westflow.ai.model.AiMessageBlockResponse.TraceStep;
import com.westflow.ai.model.AiMessageRecord;
import com.westflow.ai.model.AiMessageResponse;
import com.westflow.ai.model.AiToolCallRecord;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库持久化的 AI Copilot 实现。
 */
@Service
public class DbAiCopilotService implements AiCopilotService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_ACTIVE = "active";
    private static final String TOOL_STATUS_EXECUTED = "EXECUTED";
    private static final String TOOL_STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String TOOL_STATUS_CONFIRMED = "CONFIRMED";
    private static final String TOOL_STATUS_CANCELLED = "CANCELLED";
    private static final String TOOL_STATUS_FAILED = "FAILED";

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiToolCallMapper aiToolCallMapper;
    private final AiConfirmationMapper aiConfirmationMapper;
    private final AiAuditMapper aiAuditMapper;
    private final ObjectMapper objectMapper;
    private final AiGatewayService aiGatewayService;
    private final AiToolExecutionService aiToolExecutionService;
    private final AiCopilotRuntimeService aiCopilotRuntimeService;
    private final AiRegistryCatalogService aiRegistryCatalogService;

    public DbAiCopilotService(
            AiConversationMapper aiConversationMapper,
            AiMessageMapper aiMessageMapper,
            AiToolCallMapper aiToolCallMapper,
            AiConfirmationMapper aiConfirmationMapper,
            AiAuditMapper aiAuditMapper,
            ObjectMapper objectMapper,
            AiGatewayService aiGatewayService,
            AiToolExecutionService aiToolExecutionService,
            AiCopilotRuntimeService aiCopilotRuntimeService,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.aiToolCallMapper = aiToolCallMapper;
        this.aiConfirmationMapper = aiConfirmationMapper;
        this.aiAuditMapper = aiAuditMapper;
        this.objectMapper = objectMapper;
        this.aiGatewayService = aiGatewayService;
        this.aiToolExecutionService = aiToolExecutionService;
        this.aiCopilotRuntimeService = aiCopilotRuntimeService;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
    }

    /**
     * 分页查询会话摘要。
     */
    @Override
    public PageResponse<AiConversationSummaryResponse> pageConversations(PageRequest request) {
        long total = aiConversationMapper.countPage(request.keyword());
        long pageSize = request.pageSize();
        long offset = Math.max(0L, (long) (request.page() - 1) * pageSize);
        List<AiConversationSummaryResponse> records = aiConversationMapper.selectPage(request.keyword(), pageSize, offset).stream()
                .map(this::toSummary)
                .toList();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResponse<>(request.page(), request.pageSize(), total, pages, records, List.of());
    }

    /**
     * 新建会话。
     */
    @Override
    @Transactional
    public AiConversationDetailResponse createConversation(AiConversationCreateRequest request) {
        LocalDateTime now = now();
        AiConversationRecord record = new AiConversationRecord(
                newId("conv"),
                defaultTitle(request.title()),
                "刚刚创建",
                STATUS_ACTIVE,
                toJson(request.contextTags()),
                0,
                currentUserId(),
                now,
                now
        );
        aiConversationMapper.insertConversation(record);
        insertAudit(record.conversationId(), null, "CONVERSATION_CREATE", "创建会话");
        return toDetail(record, List.of(), List.of(), List.of());
    }

    /**
     * 查询会话详情。
     */
    @Override
    public AiConversationDetailResponse getConversation(String conversationId) {
        AiConversationRecord conversation = requireConversation(conversationId);
        List<AiMessageRecord> messageRecords = aiMessageMapper.selectByConversationId(conversationId);
        List<AiToolCallRecord> toolCallRecords = aiToolCallMapper.selectByConversationId(conversationId);
        List<AiAuditRecord> auditRecords = aiAuditMapper.selectByConversationId(conversationId);
        return toDetail(
                conversation,
                messageRecords == null ? List.of() : messageRecords.stream().map(this::toMessage).toList(),
                toolCallRecords == null ? List.of() : toolCallRecords.stream().map(this::toToolCallResult).toList(),
                auditRecords == null ? List.of() : auditRecords.stream().map(this::toAudit).toList()
        );
    }

    /**
     * 追加会话消息。
     */
    @Override
    @Transactional
    public AiConversationDetailResponse appendMessage(String conversationId, AiMessageAppendRequest request) {
        AiConversationRecord conversation = requireConversation(conversationId);
        LocalDateTime now = now();
        String normalizedContent = request.content() == null ? "" : request.content().trim();
        AiMessageRecord message = new AiMessageRecord(
                newId("msg"),
                conversationId,
                "user",
                "你",
                normalizedContent,
                toJson(List.of(defaultTextBlock(normalizedContent))),
                currentUserId(),
                now,
                now
        );
        aiMessageMapper.insertMessage(message);
        String preview = normalizedContent.isBlank() ? conversation.preview() : normalizedContent;
        insertAudit(conversationId, null, "MESSAGE_APPEND", preview == null ? "" : preview);
        AssistantReply assistantReply = buildAssistantReply(conversation, normalizedContent, now);
        aiMessageMapper.insertMessage(new AiMessageRecord(
                newId("msg"),
                conversationId,
                "assistant",
                "AI Copilot",
                assistantReply.content(),
                toJson(assistantReply.blocks()),
                currentUserId(),
                now,
                now
        ));
        long messageCount = aiMessageMapper.countByConversationId(conversationId);
        aiConversationMapper.updateConversationSnapshot(
                conversationId,
                assistantReply.content(),
                STATUS_ACTIVE,
                Math.toIntExact(messageCount),
                now
        );
        return getConversation(conversationId);
    }

    /**
     * 执行工具调用。
     */
    @Override
    @Transactional
    public AiToolCallResultResponse executeToolCall(String conversationId, AiToolCallRequest request) {
        requireConversation(conversationId);
        AiToolCallResultResponse result = aiToolExecutionService.executeToolCall(conversationId, request, currentUserId());
        persistToolCall(result);
        insertAudit(conversationId, result.toolCallId(), request.toolType() == AiToolType.WRITE ? "WRITE" : "READ", result.summary());
        return result;
    }

    /**
     * 确认写操作工具调用。
     */
    @Override
    @Transactional
    public AiToolCallResultResponse confirmToolCall(String toolCallId, AiConfirmToolCallRequest request) {
        AiToolCallRecord toolCall = requireToolCall(toolCallId);
        if (toolCall.toolType() != AiToolType.WRITE) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "只允许确认写操作工具调用");
        }
        LocalDateTime now = now();
        String status = request.approved() ? TOOL_STATUS_CONFIRMED : TOOL_STATUS_CANCELLED;
        AiToolCallRequest toolCallRequest = new AiToolCallRequest(
                toolCall.toolKey(),
                toolCall.toolType(),
                toolCall.toolSource(),
                parseJsonMap(toolCall.argumentsJson())
        );
        String resultJson = request.approved()
                ? toJson(Map.of("approved", true, "comment", request.comment()))
                : toJson(Map.of("approved", false, "comment", request.comment()));
        String summary = request.approved() ? "已确认并完成工具调用" : "已取消工具调用";
        String confirmationId = toolCall.confirmationId() == null ? newId("confirm") : toolCall.confirmationId();
        AiConfirmationRecord existingConfirmation = aiConfirmationMapper.selectById(confirmationId);
        AiConfirmationRecord confirmation = new AiConfirmationRecord(
                confirmationId,
                toolCallId,
                status,
                request.approved(),
                request.comment(),
                currentUserId(),
                existingConfirmation == null ? now : existingConfirmation.createdAt(),
                now,
                now
        );
        if (existingConfirmation == null) {
            aiConfirmationMapper.insertConfirmation(confirmation);
        } else {
            aiConfirmationMapper.updateConfirmation(confirmation);
        }
        if (request.approved()) {
            try {
                AiToolCallResultResponse executedResult = aiToolExecutionService.executeConfirmedWriteToolCall(
                        toolCall.conversationId(),
                        toolCallRequest,
                        currentUserId(),
                        toolCallId,
                        confirmationId
                );
                resultJson = toJson(executedResult.result());
                summary = executedResult.summary();
            } catch (RuntimeException exception) {
                status = TOOL_STATUS_FAILED;
                resultJson = toJson(Map.of(
                        "approved", true,
                        "comment", request.comment(),
                        "error", exception.getMessage()
                ));
                summary = "写操作执行失败";
            }
        }
        aiToolCallMapper.updateToolCallResult(
                toolCallId,
                status,
                false,
                confirmation.confirmationId(),
                resultJson,
                summary,
                now
        );
        AiToolCallResultResponse finalResult = new AiToolCallResultResponse(
                toolCallId,
                toolCall.conversationId(),
                toolCall.toolKey(),
                toolCall.toolType(),
                toolCall.toolSource(),
                status,
                false,
                confirmation.confirmationId(),
                summary,
                toolCallRequest.arguments(),
                parseJsonMap(resultJson),
                offsetNow(now),
                offsetNow(now)
        );
        insertAudit(
                toolCall.conversationId(),
                toolCallId,
                "WRITE",
                request.approved() ? (TOOL_STATUS_FAILED.equals(status) ? "确认写操作失败" : "确认写操作") : "取消写操作"
        );
        appendConfirmationResultMessage(toolCall, confirmation, request, now, finalResult);
        return finalResult;
    }

    private AssistantReply buildAssistantReply(AiConversationRecord conversation, String content, LocalDateTime now) {
        if (content.isBlank()) {
            return new AssistantReply("请输入更明确的问题或操作意图。", List.of(defaultTextBlock("当前消息为空，未触发 AI 编排。")));
        }

        String domain = resolveDomain(conversation);
        String routePath = resolveRoutePath(conversation);
        AiGatewayResponse gatewayResponse = aiGatewayService.route(new AiGatewayRequest(
                conversation.conversationId(),
                currentUserId(),
                content,
                domain,
                false,
                List.of(),
                parseJsonStringList(conversation.contextTagsJson()),
                routePath
        ));

        if (gatewayResponse.requiresConfirmation()) {
            AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                    conversation.conversationId(),
                    buildWriteToolCall(content, domain, routePath),
                    currentUserId()
            );
            persistToolCall(toolResult);
            insertAudit(conversation.conversationId(), toolResult.toolCallId(), "WRITE", toolResult.summary());
            return new AssistantReply(
                    "我已经整理出建议动作，确认后才会真正执行写操作。",
                    List.of(
                            defaultTextBlock("当前请求已进入 Supervisor 审核流程。"),
                            traceBlock(
                                    gatewayResponse.routeMode(),
                                    gatewayResponse.agentId(),
                                    gatewayResponse.agentKey(),
                                    "已完成写操作路由规划，等待用户确认后执行。",
                                    "pending",
                                    buildRouteTrace(gatewayResponse, domain, routePath, toolResult.toolCallId())
                            ),
                            buildProcessStartPreviewBlock(content, domain, routePath),
                            resultBlock(
                                    toolResult.toolSource().name(),
                                    toolResult.toolKey(),
                                    toolResult.toolKey(),
                                    toolResult.toolType().name(),
                                    toolResult.summary(),
                                    Map.of(
                                            "toolCallId", toolResult.toolCallId(),
                                            "confirmationId", toolResult.confirmationId(),
                                            "status", toolResult.status(),
                                            "requiresConfirmation", toolResult.requiresConfirmation(),
                                            "arguments", toolResult.arguments(),
                                            "domain", domain,
                                            "routePath", routePath
                                    ),
                                    buildToolResultTrace(gatewayResponse, toolResult, "waiting-confirmation"),
                                    buildToolContextFields(
                                            toolResult.toolSource().name(),
                                            toolResult.toolKey(),
                                            toolResult.toolKey(),
                                            toolResult.toolType().name(),
                                            domain,
                                            routePath,
                                            toolResult.toolCallId(),
                                            toolResult.confirmationId(),
                                            toolResult.summary(),
                                            "等待确认"
                                    ),
                                    List.of()
                            ),
                            confirmationBlock(toolResult, gatewayResponse, domain, routePath)
                    )
            );
        }

        AiToolCallRequest readToolCall = buildReadToolCall(content, domain, gatewayResponse.skillIds(), routePath);
        if (readToolCall != null) {
            AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                    conversation.conversationId(),
                    readToolCall,
                    currentUserId()
            );
            persistToolCall(toolResult);
            insertAudit(conversation.conversationId(), toolResult.toolCallId(), "READ", toolResult.summary());
            List<TraceStep> trace = buildToolResultTrace(gatewayResponse, toolResult, "executed");
            return new AssistantReply(
                    resolveRuntimeReply(content, gatewayResponse, domain, buildReadSummary(toolResult)),
                    buildReadBlocks(content, routePath, toolResult, gatewayResponse, trace)
            );
        }

        String runtimeReply = resolveRuntimeReply(content, gatewayResponse, domain, buildAssistantSummary(gatewayResponse, domain));
        return new AssistantReply(
                runtimeReply,
                List.of(
                        defaultTextBlock(runtimeReply),
                        traceBlock(
                                gatewayResponse.routeMode(),
                                gatewayResponse.agentId(),
                                gatewayResponse.agentKey(),
                                buildAssistantSummary(gatewayResponse, domain),
                                "executed",
                                buildRouteTrace(gatewayResponse, domain, routePath, null)
                        )
                )
        );
    }

    private void appendConfirmationResultMessage(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            LocalDateTime now,
            AiToolCallResultResponse finalResult
    ) {
        String status = request.approved() ? "confirmed" : "cancelled";
        String content = request.approved() ? "操作确认成功，系统已记录执行结果。" : "已取消本次操作，流程状态保持不变。";
        List<TraceStep> trace = buildToolResultTrace(
                toolCall.toolSource().name(),
                toolCall.toolKey(),
                toolCall.toolKey(),
                finalResult.status(),
                request.approved() ? "写操作确认完成" : "写操作已取消"
        );
        boolean failed = TOOL_STATUS_FAILED.equals(finalResult.status());
        aiMessageMapper.insertMessage(new AiMessageRecord(
                newId("msg"),
                toolCall.conversationId(),
                "assistant",
                "AI Copilot",
                content,
                toJson(List.of(
                        traceBlock(
                                toolCall.toolSource().name(),
                                toolCall.toolKey(),
                                toolCall.toolKey(),
                                request.approved() ? "写操作确认成功" : "写操作已取消",
                                status,
                                trace
                        ),
                        failed
                                ? failureBlock(
                                toolCall.toolSource().name(),
                                toolCall.toolKey(),
                                toolCall.toolKey(),
                                toolCall.toolType().name(),
                                "写操作执行失败",
                                "AI.TOOL_EXECUTE_FAILED",
                                "写操作执行失败",
                                finalResult.summary(),
                                trace
                        )
                                : resultBlock(
                                toolCall.toolSource().name(),
                                toolCall.toolKey(),
                                toolCall.toolKey(),
                                toolCall.toolType().name(),
                                finalResult.summary(),
                                Map.of(
                                        "approved", request.approved(),
                                        "comment", request.comment(),
                                        "status", finalResult.status(),
                                        "result", finalResult.result(),
                                        "confirmationId", confirmation.confirmationId(),
                                        "toolCallId", toolCall.toolCallId(),
                                        "toolType", toolCall.toolType().name()
                                ),
                                trace,
                                buildToolContextFields(
                                        toolCall.toolSource().name(),
                                        toolCall.toolKey(),
                                        toolCall.toolKey(),
                                        toolCall.toolType().name(),
                                        null,
                                        null,
                                        toolCall.toolCallId(),
                                        confirmation.confirmationId(),
                                        finalResult.summary(),
                                        request.approved() ? "确认执行" : "已取消"
                                ),
                                List.of()
                        ),
                        confirmationBlock(toolCall, confirmation, request, finalResult, status, now)
                )),
                currentUserId(),
                now,
                now
        ));
        long messageCount = aiMessageMapper.countByConversationId(toolCall.conversationId());
        aiConversationMapper.updateConversationSnapshot(
                toolCall.conversationId(),
                content,
                STATUS_ACTIVE,
                Math.toIntExact(messageCount),
                now
        );
    }

    private AiConversationRecord requireConversation(String conversationId) {
        AiConversationRecord conversation = aiConversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ContractException("AI.CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private AiToolCallRecord requireToolCall(String toolCallId) {
        AiToolCallRecord toolCall = aiToolCallMapper.selectById(toolCallId);
        if (toolCall == null) {
            throw new ContractException("AI.TOOL_CALL_NOT_FOUND", HttpStatus.NOT_FOUND, "工具调用不存在");
        }
        return toolCall;
    }

    private AiConversationSummaryResponse toSummary(AiConversationRecord record) {
        return new AiConversationSummaryResponse(
                record.conversationId(),
                record.title(),
                record.preview(),
                record.status(),
                offsetNow(record.updatedAt()),
                record.messageCount(),
                parseJsonStringList(record.contextTagsJson())
        );
    }

    private AiConversationDetailResponse toDetail(
            AiConversationRecord conversation,
            List<AiMessageResponse> history,
            List<AiToolCallResultResponse> toolCalls,
            List<AiAuditEntryResponse> audit
    ) {
        return new AiConversationDetailResponse(
                conversation.conversationId(),
                conversation.title(),
                conversation.preview(),
                conversation.status(),
                offsetNow(conversation.updatedAt()),
                conversation.messageCount(),
                parseJsonStringList(conversation.contextTagsJson()),
                history,
                toolCalls,
                audit
        );
    }

    private AiMessageResponse toMessage(AiMessageRecord record) {
        return new AiMessageResponse(
                record.messageId(),
                record.role(),
                record.authorName(),
                offsetNow(record.createdAt()),
                record.content(),
                parseMessageBlocks(record.blocksJson())
        );
    }

    private AiToolCallResultResponse toToolCallResult(AiToolCallRecord record) {
        return new AiToolCallResultResponse(
                record.toolCallId(),
                record.conversationId(),
                record.toolKey(),
                record.toolType(),
                record.toolSource(),
                record.status(),
                record.requiresConfirmation(),
                record.confirmationId(),
                record.summary(),
                parseJsonMap(record.argumentsJson()),
                parseJsonMap(record.resultJson()),
                offsetNow(record.createdAt()),
                offsetNow(record.completedAt())
        );
    }

    private AiAuditEntryResponse toAudit(AiAuditRecord record) {
        return new AiAuditEntryResponse(
                record.auditId(),
                record.conversationId(),
                record.toolCallId(),
                record.actionType(),
                record.summary(),
                offsetNow(record.occurredAt())
        );
    }

    private AiMessageBlockResponse defaultTextBlock(String content) {
        return new AiMessageBlockResponse("text", null, content, null, null, null, null, null, null, null, null, null, List.of(), List.of());
    }

    /**
     * 构建工具/智能体命中轨迹块。
     */
    private AiMessageBlockResponse traceBlock(
            String sourceType,
            String sourceKey,
            String sourceName,
            String detail,
            String status,
            List<TraceStep> trace
    ) {
        return new AiMessageBlockResponse(
                "trace",
                "命中轨迹",
                null,
                null,
                status,
                detail,
                null,
                null,
                status,
                null,
                currentUserId(),
                null,
                sourceType,
                sourceKey,
                sourceName,
                null,
                Map.of(),
                null,
                trace,
                List.of(),
                List.of()
        );
    }

    /**
     * 构建工具执行结果块。
     */
    private AiMessageBlockResponse resultBlock(
            String sourceType,
            String sourceKey,
            String sourceName,
            String toolType,
            String summary,
            Map<String, Object> result,
            List<TraceStep> trace,
            List<Field> fields,
            List<Metric> metrics
    ) {
        return new AiMessageBlockResponse(
                "result",
                "执行结果",
                null,
                null,
                summary,
                summary,
                null,
                null,
                "ok",
                null,
                currentUserId(),
                null,
                sourceType,
                sourceKey,
                sourceName,
                toolType,
                result,
                null,
                trace,
                fields,
                metrics
        );
    }

    /**
     * 构建失败块。
     */
    private AiMessageBlockResponse failureBlock(
            String sourceType,
            String sourceKey,
            String sourceName,
            String toolType,
            String summary,
            String code,
            String message,
            String detail,
            List<TraceStep> trace
    ) {
        return new AiMessageBlockResponse(
                "failure",
                "执行失败",
                null,
                null,
                summary,
                detail,
                null,
                null,
                "failed",
                null,
                currentUserId(),
                null,
                sourceType,
                sourceKey,
                sourceName,
                toolType,
                Map.of(),
                new Failure(code, message, detail),
                trace,
                List.of(),
                List.of()
        );
    }

    private List<AiMessageBlockResponse> parseMessageBlocks(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(blocksJson, new TypeReference<List<AiMessageBlockResponse>>() { });
        } catch (JsonProcessingException exception) {
            throw new ContractException("AI.BLOCKS_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "消息块解析失败", Map.of("blocksJson", blocksJson));
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new ContractException("AI.JSON_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "JSON 解析失败", Map.of("json", json));
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new ContractException("AI.JSON_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, "JSON 解析失败", Map.of("json", json));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ContractException("AI.JSON_SERIALIZE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "JSON 序列化失败");
        }
    }

    private void insertAudit(String conversationId, String toolCallId, String actionType, String summary) {
        aiAuditMapper.insertAudit(new AiAuditRecord(
                newId("audit"),
                conversationId,
                toolCallId,
                actionType,
                summary,
                currentUserId(),
                now()
        ));
    }

    private String currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "system";
        } catch (RuntimeException ignored) {
            return "system";
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(TIME_ZONE);
    }

    private OffsetDateTime offsetNow(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(TIME_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime offsetNow(OffsetDateTime offsetDateTime) {
        return offsetDateTime;
    }

    private void persistToolCall(AiToolCallResultResponse result) {
        aiToolCallMapper.insertToolCall(new AiToolCallRecord(
                result.toolCallId(),
                result.conversationId(),
                result.toolKey(),
                result.toolType(),
                result.toolSource(),
                result.status(),
                result.requiresConfirmation(),
                toJson(result.arguments()),
                toJson(result.result()),
                result.summary(),
                result.confirmationId(),
                currentUserId(),
                toLocalDateTime(result.createdAt()),
                toLocalDateTime(result.completedAt())
        ));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.atZoneSameInstant(TIME_ZONE).toLocalDateTime();
    }

    private String defaultTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新建 Copilot 会话";
        }
        return title.trim();
    }

    private String resolveDomain(AiConversationRecord conversation) {
        List<String> contextTags = parseJsonStringList(conversation.contextTagsJson());
        if (contextTags.stream().anyMatch(tag -> tag != null && tag.startsWith("route:/plm/"))) {
            return "PLM";
        }
        if (contextTags.stream().anyMatch(tag -> tag != null && (tag.startsWith("route:/oa/") || tag.startsWith("route:/workbench/")))) {
            return "OA";
        }
        if (contextTags.stream().anyMatch(tag -> "PLM".equalsIgnoreCase(tag))) {
            return "PLM";
        }
        if (contextTags.stream().anyMatch(tag -> "OA".equalsIgnoreCase(tag) || "审批".equalsIgnoreCase(tag))) {
            return "OA";
        }
        return "GENERAL";
    }

    private AiToolCallRequest buildWriteToolCall(String content, String domain, String routePath) {
        String taskId = extractTaskId(routePath);
        if (content.contains("发起") || content.contains("提交")) {
            return new AiToolCallRequest(
                    "process.start",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    Map.of("content", content, "domain", domain, "routePath", routePath)
            );
        }
        if (content.contains("认领")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    Map.of("content", content, "domain", domain, "routePath", routePath, "taskId", taskId, "action", "CLAIM")
            );
        }
        if (content.contains("驳回") || content.contains("退回")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    Map.of("content", content, "domain", domain, "routePath", routePath, "taskId", taskId, "action", "REJECT")
            );
        }
        if (content.contains("已读") || content.contains("已阅")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    Map.of("content", content, "domain", domain, "routePath", routePath, "taskId", taskId, "action", "READ")
            );
        }
        return new AiToolCallRequest(
                "task.handle",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                Map.of("content", content, "domain", domain, "routePath", routePath, "taskId", taskId, "action", "COMPLETE")
        );
    }

    private AiToolCallRequest buildReadToolCall(String content, String domain, List<String> skillIds, String routePath) {
        return aiRegistryCatalogService.matchReadTool(
                        currentUserId(),
                        content,
                        domain,
                        skillIds,
                        routePath
                )
                .map(tool -> switch (tool.toolCode()) {
                    case "workflow.definition.list" -> new AiToolCallRequest(
                            tool.toolCode(),
                            tool.toolType(),
                            tool.toolSource(),
                            Map.of("keyword", content == null ? "" : content)
                    );
                    case "stats.query" -> new AiToolCallRequest(
                            tool.toolCode(),
                            tool.toolType(),
                            tool.toolSource(),
                            Map.of("keyword", content == null ? "" : content, "domain", domain)
                    );
                    case "plm.bill.query", "plm.change.summary" -> new AiToolCallRequest(
                            tool.toolCode(),
                            tool.toolType(),
                            tool.toolSource(),
                            Map.of("keyword", extractBillKeyword(routePath, content == null ? "" : content), "domain", domain)
                    );
                    default -> new AiToolCallRequest(
                            tool.toolCode(),
                            tool.toolType(),
                            tool.toolSource(),
                            Map.of("keyword", extractTaskKeyword(routePath, content == null ? "" : content), "domain", domain, "view", "TODO")
                    );
                })
                .orElse(null);
    }

    private AiMessageBlockResponse confirmationBlock(
            AiToolCallResultResponse result,
            AiGatewayResponse gatewayResponse,
            String domain,
            String routePath
    ) {
        return new AiMessageBlockResponse(
                "confirm",
                "请确认是否继续执行",
                null,
                result.confirmationId(),
                "当前默认策略是读操作直执、写操作必须确认。",
                "当前写操作已进入 " + gatewayResponse.routeMode() + " 编排链路，请核对业务域、来源页面和参数后再确认。",
                "确认处理",
                "暂不执行",
                "pending",
                null,
                null,
                null,
                result.toolSource().name(),
                result.toolKey(),
                result.toolKey(),
                result.toolType().name(),
                Map.of(
                        "toolCallId", result.toolCallId(),
                        "confirmationId", result.confirmationId(),
                        "status", result.status(),
                        "requiresConfirmation", result.requiresConfirmation(),
                        "toolType", result.toolType().name(),
                        "toolSource", result.toolSource().name(),
                        "domain", domain,
                        "routePath", routePath,
                        "arguments", result.arguments()
                ),
                null,
                List.of(
                        new TraceStep("route", "路由模式", gatewayResponse.routeMode(), "pending"),
                        new TraceStep("agent", "命中智能体", gatewayResponse.agentId(), "pending"),
                        new TraceStep("context", "业务域", domain, "pending"),
                        new TraceStep("context", "来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, "pending"),
                        new TraceStep("tool", "工具调用", result.toolCallId(), "pending")
                ),
                List.of(
                        new Field("业务域", domain, null),
                        new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                        new Field("工具名称", result.toolKey(), null),
                        new Field("工具类型", result.toolType().name(), null),
                        new Field("确认单编号", result.confirmationId(), null)
                ),
                List.of(new Metric("确认状态", result.requiresConfirmation() ? "待确认" : "已确认", null, "warning"))
        );
    }

    private AiMessageBlockResponse confirmationBlock(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            AiToolCallResultResponse finalResult,
            String status,
            LocalDateTime now
    ) {
        return new AiMessageBlockResponse(
                "confirm",
                "操作确认结果",
                null,
                confirmation.confirmationId(),
                request.approved() ? "写操作已确认执行" : "已取消本次写操作",
                request.comment(),
                "确认处理",
                "暂不执行",
                status,
                offsetNow(now) == null ? null : offsetNow(now).toString(),
                currentUserId(),
                request.comment(),
                toolCall.toolSource().name(),
                toolCall.toolKey(),
                toolCall.toolKey(),
                toolCall.toolType().name(),
                Map.of(
                        "toolCallId", toolCall.toolCallId(),
                        "confirmationId", confirmation.confirmationId(),
                        "status", finalResult.status(),
                        "approved", request.approved(),
                        "comment", request.comment(),
                        "toolType", toolCall.toolType().name(),
                        "toolSource", toolCall.toolSource().name(),
                        "toolKey", toolCall.toolKey(),
                        "result", finalResult.result(),
                        "summary", finalResult.summary()
                ),
                null,
                List.of(
                        new TraceStep("tool", "工具调用", toolCall.toolCallId(), status),
                        new TraceStep("confirmation", "确认单", confirmation.confirmationId(), status),
                        new TraceStep("result", "执行状态", finalResult.status(), status)
                ),
                List.of(
                        new Field("工具名称", toolCall.toolKey(), null),
                        new Field("工具类型", toolCall.toolType().name(), null),
                        new Field("确认人", currentUserId(), null),
                        new Field("确认意见", request.comment() == null || request.comment().isBlank() ? "无" : request.comment(), null)
                ),
                List.of(new Metric("执行状态", finalResult.status(), null, "neutral"))
        );
    }

    private List<Field> buildToolContextFields(
            String sourceType,
            String sourceKey,
            String sourceName,
            String toolType,
            String domain,
            String routePath,
            String toolCallId,
            String confirmationId,
            String summary,
            String status
    ) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        fields.add(new Field("来源类型", sourceType, null));
        fields.add(new Field("工具编码", sourceKey, null));
        fields.add(new Field("工具名称", sourceName, null));
        fields.add(new Field("工具类型", toolType, null));
        if (domain != null && !domain.isBlank()) {
            fields.add(new Field("业务域", domain, null));
        }
        if (routePath != null && !routePath.isBlank()) {
            fields.add(new Field("来源页面", routePath, null));
        }
        if (toolCallId != null && !toolCallId.isBlank()) {
            fields.add(new Field("工具调用编号", toolCallId, null));
        }
        if (confirmationId != null && !confirmationId.isBlank()) {
            fields.add(new Field("确认单编号", confirmationId, null));
        }
        fields.add(new Field("摘要", summary, null));
        fields.add(new Field("状态", status, null));
        return List.copyOf(fields);
    }

    private List<AiMessageBlockResponse> buildReadBlocks(
            String content,
            String routePath,
            AiToolCallResultResponse result,
            AiGatewayResponse gatewayResponse,
            List<TraceStep> trace
    ) {
        if ("workflow.definition.list".equals(result.toolKey())) {
            return List.of(
                    defaultTextBlock("已通过平台工具读取流程定义。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中平台流程定义查询工具。",
                            "executed",
                            trace
                    ),
                    resultBlock(
                            result.toolSource().name(),
                            result.toolKey(),
                            result.toolKey(),
                            result.toolType().name(),
                            result.summary(),
                            result.result(),
                            trace,
                            buildToolContextFields(
                                    result.toolSource().name(),
                                    result.toolKey(),
                                    result.toolKey(),
                                    result.toolType().name(),
                                    resolveDomainFromRoute(routePath),
                                    routePath,
                                    result.toolCallId(),
                                    result.confirmationId(),
                                    result.summary(),
                                    "已执行"
                            ),
                            List.of()
                    ),
                    defaultTextBlock("你可以继续追问某条流程的设计和发布状态。")
            );
        }
        if ("task.query".equals(result.toolKey()) || "workflow.todo.list".equals(result.toolKey())) {
            Object count = result.result().getOrDefault("count", 0);
            java.util.ArrayList<AiMessageBlockResponse> blocks = new java.util.ArrayList<>(List.of(
                    defaultTextBlock("已通过平台内置工具读取待办数据。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中待办查询链路。",
                            "executed",
                            trace
                    ),
                    resultBlock(
                            result.toolSource().name(),
                            result.toolKey(),
                            result.toolKey(),
                            result.toolType().name(),
                            result.summary(),
                            result.result(),
                            trace,
                            buildToolContextFields(
                                    result.toolSource().name(),
                                    result.toolKey(),
                                    result.toolKey(),
                                    result.toolType().name(),
                                    resolveDomainFromRoute(routePath),
                                    routePath,
                                    result.toolCallId(),
                                    result.confirmationId(),
                                    result.summary(),
                                    "已执行"
                            ),
                            List.of(
                                    new Metric("当前待办数", String.valueOf(count), null, "neutral")
                            )
                    ),
                    buildTodoStatsBlock(routePath, count)
            ));
            if (content != null && (content.contains("解释") || content.contains("路径") || content.contains("为什么"))) {
                blocks.add(defaultTextBlock(buildTodoExplanation(routePath, result.result())));
            }
            return List.copyOf(blocks);
        }
        if ("stats.query".equals(result.toolKey())) {
            return List.of(
                    defaultTextBlock("已通过平台统计工具生成指标摘要。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中统计查询链路。",
                            "executed",
                            trace
                    ),
                    resultBlock(
                            result.toolSource().name(),
                            result.toolKey(),
                            result.toolKey(),
                            result.toolType().name(),
                            result.summary(),
                            result.result(),
                            trace,
                            buildToolContextFields(
                                    result.toolSource().name(),
                                    result.toolKey(),
                                    result.toolKey(),
                                    result.toolType().name(),
                                    resolveDomainFromRoute(routePath),
                                    routePath,
                                    result.toolCallId(),
                                    result.confirmationId(),
                                    result.summary(),
                                    "已执行"
                            ),
                            List.of(
                                    new Metric("总量", String.valueOf(result.result().getOrDefault("total", 0)), null, "neutral"),
                                    new Metric("已完成", String.valueOf(result.result().getOrDefault("completed", 0)), null, "positive"),
                                    new Metric("待处理", String.valueOf(result.result().getOrDefault("pending", 0)), null, "warning"),
                                    new Metric("完成率", String.valueOf(result.result().getOrDefault("completionRate", "--")), null, "positive")
                            )
                    ),
                    buildStatsAnswerBlock(result.result())
            );
        }
        return List.of(
                defaultTextBlock("已通过 " + gatewayResponse.routeMode() + " 路由完成只读分析。"),
                traceBlock(
                        gatewayResponse.routeMode(),
                        gatewayResponse.agentId(),
                        gatewayResponse.agentKey(),
                        buildReadSummary(result),
                        "executed",
                        trace
                ),
                resultBlock(
                        result.toolSource().name(),
                        result.toolKey(),
                        result.toolKey(),
                        result.toolType().name(),
                        result.summary(),
                        result.result(),
                        trace,
                        buildToolContextFields(
                                result.toolSource().name(),
                                result.toolKey(),
                                result.toolKey(),
                                result.toolType().name(),
                                resolveDomainFromRoute(routePath),
                                routePath,
                                result.toolCallId(),
                                result.confirmationId(),
                                result.summary(),
                                "已执行"
                        ),
                        List.of()
                ),
                defaultTextBlock(buildReadSummary(result))
        );
    }

    private String buildReadSummary(AiToolCallResultResponse result) {
        if ("workflow.definition.list".equals(result.toolKey())) {
            return "我已经读取流程定义列表，可继续按流程名或流程键深入查看。";
        }
        if ("task.query".equals(result.toolKey()) || "workflow.trace.summary".equals(result.toolKey())) {
            return "我已经汇总当前审批轨迹，可继续追问节点处理路径。";
        }
        if ("plm.bill.query".equals(result.toolKey()) || "plm.change.summary".equals(result.toolKey())) {
            return "我已经生成当前 PLM 变更摘要，可继续追问 ECR/ECO 影响范围。";
        }
        if ("stats.query".equals(result.toolKey())) {
            return "我已经整理出当前统计摘要，可继续追问时间范围或业务域。";
        }
        return "我已经完成本次只读分析。";
    }

    private String buildAssistantSummary(AiGatewayResponse gatewayResponse, String domain) {
        if ("SKILL".equals(gatewayResponse.routeMode())) {
            return "已切换到可复用 Skill 分析链路，当前业务域为 " + domain + "。";
        }
        if ("ROUTING".equals(gatewayResponse.routeMode())) {
            return "已通过 Routing 智能体整理当前问题，可继续补充上下文。";
        }
        return "已进入 Supervisor 编排链路。";
    }

    private String resolveRuntimeReply(
            String content,
            AiGatewayResponse gatewayResponse,
            String domain,
            String fallback
    ) {
        try {
            String runtimeReply = aiCopilotRuntimeService.generateReply(
                    new AiGatewayRequest(
                            null,
                            currentUserId(),
                            content,
                            domain,
                            gatewayResponse.requiresConfirmation(),
                            gatewayResponse.skillIds(),
                            List.of()
                    ),
                    gatewayResponse
            );
            if (runtimeReply == null || runtimeReply.isBlank()) {
                return fallback;
            }
            return runtimeReply;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveRoutePath(AiConversationRecord conversation) {
        return parseJsonStringList(conversation.contextTagsJson()).stream()
                .filter(tag -> tag != null && tag.startsWith("route:"))
                .map(tag -> tag.substring("route:".length()))
                .findFirst()
                .orElse("");
    }

    private String extractTaskId(String routePath) {
        if (routePath == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/workbench/todos/([^/?]+)").matcher(routePath);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractTaskKeyword(String routePath, String fallbackKeyword) {
        String taskId = extractTaskId(routePath);
        return taskId.isBlank() ? fallbackKeyword : taskId;
    }

    private String extractBillKeyword(String routePath, String fallbackKeyword) {
        if (routePath == null || routePath.isBlank()) {
            return fallbackKeyword;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/(?:oa|plm)/[^/]+/([^/?]+)").matcher(routePath);
        return matcher.find() ? matcher.group(1) : fallbackKeyword;
    }

    private String resolveDomainFromRoute(String routePath) {
        if (routePath == null || routePath.isBlank()) {
            return "GENERAL";
        }
        if (routePath.startsWith("/oa/")) {
            return "OA";
        }
        if (routePath.startsWith("/plm/")) {
            return "PLM";
        }
        if (routePath.startsWith("/workflow/")) {
            return "WORKFLOW";
        }
        if (routePath.startsWith("/system/")) {
            return "SYSTEM";
        }
        if (routePath.startsWith("/workbench/")) {
            return "WORKBENCH";
        }
        return "GENERAL";
    }

    private AiMessageBlockResponse buildProcessStartPreviewBlock(String content, String domain, String routePath) {
        return new AiMessageBlockResponse(
                "form-preview",
                "拟发起流程预览",
                null,
                null,
                null,
                "确认前可先核对业务域、来源页面和用户指令。",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field("业务域", domain, null),
                        new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                        new Field("用户指令", content, "确认后进入实际流程发起")
                ),
                List.of()
        );
    }

    private AiMessageBlockResponse buildTodoStatsBlock(String routePath, Object count) {
        String taskId = extractTaskId(routePath);
        return new AiMessageBlockResponse(
                "stats",
                "待办摘要",
                null,
                null,
                null,
                "当前待办和路由上下文已经同步到 Copilot。",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field("待办编号", taskId.isBlank() ? "未定位" : taskId, null),
                        new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                        new Field("视图", "TODO", "来自平台待办查询")
                ),
                List.of(
                        new Metric("当前待办数", String.valueOf(count), null, "neutral"),
                        new Metric("待办编号", taskId.isBlank() ? "未定位" : taskId, null, "warning"),
                        new Metric("视图", "TODO", "来自平台待办查询", "positive")
                )
        );
    }

    private String buildTodoExplanation(String routePath, Map<String, Object> result) {
        String taskId = extractTaskId(routePath);
        Object items = result.get("items");
        if (!taskId.isBlank()) {
            return "待办 " + taskId + " 已命中当前上下文，Copilot 会优先围绕该事项解释处理路径。";
        }
        return "当前已读取待办列表，命中结果：" + (items == null ? "[]" : items);
    }

    private AiMessageBlockResponse buildStatsAnswerBlock(Map<String, Object> result) {
        Object total = result.getOrDefault("total", 0);
        Object completed = result.getOrDefault("completed", 0);
        Object pending = result.getOrDefault("pending", 0);
        Object completionRate = result.getOrDefault("completionRate", "--");
        return new AiMessageBlockResponse(
                "stats",
                "统计问答结果",
                null,
                null,
                null,
                "当前回答来自平台统计工具，可继续细分到流程、OA 或 PLM 维度。",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field("统计口径", "流程平台统计", "来自平台统计工具"),
                        new Field("统计主题", "当前流程运营指标", null),
                        new Field("摘要", "已返回真实统计数据", null)
                ),
                List.of(
                        new Metric("总量", String.valueOf(total), null, "neutral"),
                        new Metric("已完成", String.valueOf(completed), null, "positive"),
                        new Metric("待处理", String.valueOf(pending), null, "warning"),
                        new Metric("完成率", String.valueOf(completionRate), null, "positive")
                )
        );
    }

    /**
     * 构建路由轨迹。
     */
    private List<TraceStep> buildRouteTrace(
            AiGatewayResponse gatewayResponse,
            String domain,
            String routePath,
            String toolCallId
    ) {
        java.util.ArrayList<TraceStep> trace = new java.util.ArrayList<>();
        trace.add(new TraceStep("route", "路由模式", gatewayResponse.routeMode(), "executed"));
        trace.add(new TraceStep("agent", "命中智能体", gatewayResponse.agentId(), "executed"));
        if (gatewayResponse.skillIds() != null && !gatewayResponse.skillIds().isEmpty()) {
            trace.add(new TraceStep("skill", "命中技能", String.join(",", gatewayResponse.skillIds()), "executed"));
        }
        trace.add(new TraceStep("context", "业务域", domain, "executed"));
        if (routePath != null && !routePath.isBlank()) {
            trace.add(new TraceStep("context", "页面路径", routePath, "executed"));
        }
        if (toolCallId != null && !toolCallId.isBlank()) {
            trace.add(new TraceStep("tool", "预生成工具调用", toolCallId, "pending"));
        }
        return List.copyOf(trace);
    }

    /**
     * 构建工具执行轨迹。
     */
    private List<TraceStep> buildToolResultTrace(
            AiGatewayResponse gatewayResponse,
            AiToolCallResultResponse result,
            String phase
    ) {
        java.util.ArrayList<TraceStep> trace = new java.util.ArrayList<>();
        trace.add(new TraceStep("route", "路由模式", gatewayResponse.routeMode(), "executed"));
        trace.add(new TraceStep("agent", "命中智能体", gatewayResponse.agentId(), "executed"));
        if (gatewayResponse.skillIds() != null && !gatewayResponse.skillIds().isEmpty()) {
            trace.add(new TraceStep("skill", "命中技能", String.join(",", gatewayResponse.skillIds()), "executed"));
        }
        trace.add(new TraceStep("tool", "工具编码", result.toolKey(), result.status()));
        trace.add(new TraceStep("result", "执行阶段", phase, result.status()));
        return List.copyOf(trace);
    }

    /**
     * 构建确认后的工具执行轨迹。
     */
    private List<TraceStep> buildToolResultTrace(
            String sourceType,
            String sourceKey,
            String sourceName,
            String status,
            String detail
    ) {
        return List.of(
                new TraceStep("source", "来源类型", sourceType, status),
                new TraceStep("source", "来源编码", sourceKey, status),
                new TraceStep("source", "来源名称", sourceName, status),
                new TraceStep("result", "执行状态", status, status),
                new TraceStep("result", "执行说明", detail, status)
        );
    }

    private record AssistantReply(
            String content,
            List<AiMessageBlockResponse> blocks
    ) {
    }
}
