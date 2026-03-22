package com.westflow.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.westflow.ai.model.AiMessageRecord;
import com.westflow.ai.model.AiMessageResponse;
import com.westflow.ai.model.AiToolCallRecord;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
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

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiToolCallMapper aiToolCallMapper;
    private final AiConfirmationMapper aiConfirmationMapper;
    private final AiAuditMapper aiAuditMapper;
    private final ObjectMapper objectMapper;

    public DbAiCopilotService(
            AiConversationMapper aiConversationMapper,
            AiMessageMapper aiMessageMapper,
            AiToolCallMapper aiToolCallMapper,
            AiConfirmationMapper aiConfirmationMapper,
            AiAuditMapper aiAuditMapper,
            ObjectMapper objectMapper
    ) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.aiToolCallMapper = aiToolCallMapper;
        this.aiConfirmationMapper = aiConfirmationMapper;
        this.aiAuditMapper = aiAuditMapper;
        this.objectMapper = objectMapper;
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
        return toDetail(
                conversation,
                aiMessageMapper.selectByConversationId(conversationId).stream().map(this::toMessage).toList(),
                aiToolCallMapper.selectByConversationId(conversationId).stream().map(this::toToolCallResult).toList(),
                aiAuditMapper.selectByConversationId(conversationId).stream().map(this::toAudit).toList()
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
        AiMessageRecord message = new AiMessageRecord(
                newId("msg"),
                conversationId,
                "user",
                "你",
                request.content(),
                toJson(List.of(defaultTextBlock(request.content()))),
                currentUserId(),
                now,
                now
        );
        aiMessageMapper.insertMessage(message);
        String preview = request.content() == null || request.content().isBlank() ? conversation.preview() : request.content();
        insertAudit(conversationId, null, "MESSAGE_APPEND", preview);
        createAssistantReply(conversationId, request.content(), now);
        long messageCount = aiMessageMapper.countByConversationId(conversationId);
        aiConversationMapper.updateConversationSnapshot(conversationId, preview, STATUS_ACTIVE, Math.toIntExact(messageCount), now);
        return getConversation(conversationId);
    }

    /**
     * 执行工具调用。
     */
    @Override
    @Transactional
    public AiToolCallResultResponse executeToolCall(String conversationId, AiToolCallRequest request) {
        requireConversation(conversationId);
        LocalDateTime now = now();
        String toolCallId = newId("tool");
        boolean requiresConfirmation = request.toolType() == AiToolType.WRITE;
        String status = requiresConfirmation ? TOOL_STATUS_PENDING_CONFIRMATION : TOOL_STATUS_EXECUTED;
        String summary = requiresConfirmation ? confirmationSummary(request.toolKey()) : executionSummary(request.toolKey());
        String confirmationId = requiresConfirmation ? newId("confirm") : null;
        Map<String, Object> result = requiresConfirmation
                ? Map.of()
                : Map.of("executed", true, "toolKey", request.toolKey());
        AiToolCallRecord record = new AiToolCallRecord(
                toolCallId,
                conversationId,
                request.toolKey(),
                request.toolType(),
                request.toolSource(),
                status,
                requiresConfirmation,
                toJson(request.arguments()),
                toJson(result),
                summary,
                confirmationId,
                currentUserId(),
                now,
                requiresConfirmation ? null : now
        );
        aiToolCallMapper.insertToolCall(record);
        if (requiresConfirmation) {
            insertAudit(conversationId, toolCallId, "WRITE", summary);
        } else {
            insertAudit(conversationId, toolCallId, "READ", summary);
        }
        return toToolCallResult(record);
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
        String resultJson = request.approved()
                ? toJson(Map.of("approved", true, "comment", request.comment()))
                : toJson(Map.of("approved", false, "comment", request.comment()));
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
        aiToolCallMapper.updateToolCallResult(
                toolCallId,
                status,
                false,
                confirmation.confirmationId(),
                resultJson,
                request.approved() ? "已确认并完成工具调用" : "已取消工具调用",
                now
        );
        insertAudit(toolCall.conversationId(), toolCallId, "WRITE", request.approved() ? "确认写操作" : "取消写操作");
        appendConfirmationResultMessage(toolCall, confirmation, request, now);
        return new AiToolCallResultResponse(
                toolCallId,
                toolCall.conversationId(),
                toolCall.toolKey(),
                toolCall.toolType(),
                toolCall.toolSource(),
                status,
                false,
                confirmation.confirmationId(),
                request.approved() ? "已确认并完成工具调用" : "已取消工具调用",
                parseJsonMap(toolCall.argumentsJson()),
                parseJsonMap(resultJson),
                offsetNow(now),
                offsetNow(now)
        );
    }

    private void createAssistantReply(String conversationId, String content, LocalDateTime now) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            return;
        }

        List<AiMessageBlockResponse> blocks;
        String assistantContent;
        if (requiresWriteConfirmation(normalizedContent)) {
            PendingToolCall pendingToolCall = stagePendingToolCall(conversationId, normalizedContent, now);
            assistantContent = "我已经整理出建议动作，确认后才会真正执行写操作。";
            blocks = List.of(
                    defaultTextBlock("检测到写操作意图，系统已切换为确认执行模式。"),
                    new AiMessageBlockResponse(
                            "confirm",
                            "请确认是否继续执行",
                            null,
                            pendingToolCall.confirmationId(),
                            pendingToolCall.summary(),
                            "当前默认策略是读操作直执、写操作必须确认。",
                            "确认处理",
                            "暂不执行",
                            "pending",
                            null
                    )
            );
        } else {
            assistantContent = "我已经根据当前问题整理出下一步建议，你可以继续追问或改写指令。";
            blocks = List.of(defaultTextBlock("这是一次只读分析，不会触发任何写操作。"));
        }

        aiMessageMapper.insertMessage(new AiMessageRecord(
                newId("msg"),
                conversationId,
                "assistant",
                "AI Copilot",
                assistantContent,
                toJson(blocks),
                currentUserId(),
                now,
                now
        ));
    }

    private PendingToolCall stagePendingToolCall(String conversationId, String content, LocalDateTime now) {
        String toolCallId = newId("tool");
        String confirmationId = newId("confirm");
        String toolKey = detectToolKey(content);
        String summary = confirmationSummary(toolKey);
        aiToolCallMapper.insertToolCall(new AiToolCallRecord(
                toolCallId,
                conversationId,
                toolKey,
                AiToolType.WRITE,
                AiToolSource.AGENT,
                TOOL_STATUS_PENDING_CONFIRMATION,
                true,
                toJson(Map.of("content", content)),
                toJson(Map.of()),
                summary,
                confirmationId,
                currentUserId(),
                now,
                null
        ));
        aiConfirmationMapper.insertConfirmation(new AiConfirmationRecord(
                confirmationId,
                toolCallId,
                "pending",
                false,
                null,
                null,
                now,
                null,
                now
        ));
        insertAudit(conversationId, toolCallId, "WRITE", "生成待确认操作卡");
        return new PendingToolCall(toolCallId, confirmationId, summary);
    }

    private void appendConfirmationResultMessage(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            LocalDateTime now
    ) {
        String status = request.approved() ? "confirmed" : "cancelled";
        String content = request.approved() ? "操作确认成功，系统已记录执行结果。" : "已取消本次操作，流程状态保持不变。";
        aiMessageMapper.insertMessage(new AiMessageRecord(
                newId("msg"),
                toolCall.conversationId(),
                "assistant",
                "AI Copilot",
                content,
                toJson(List.of(new AiMessageBlockResponse(
                        "confirm",
                        "操作确认结果",
                        null,
                        confirmation.confirmationId(),
                        request.approved() ? "写操作已确认执行" : "写操作已取消",
                        request.comment(),
                        "确认处理",
                        "暂不执行",
                        status,
                        offsetNow(now) == null ? null : offsetNow(now).toString()
                ))),
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

    private boolean requiresWriteConfirmation(String content) {
        return List.of("完成", "审批", "处理", "发起", "提交", "驳回", "退回", "转办", "加签", "减签")
                .stream()
                .anyMatch(content::contains);
    }

    private String detectToolKey(String content) {
        if (content.contains("发起")) {
            return "process.start";
        }
        if (content.contains("驳回") || content.contains("退回")) {
            return "workflow.task.reject";
        }
        return "workflow.task.complete";
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
        return new AiMessageBlockResponse("text", null, content, null, null, null, null, null, null, null);
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

    private String defaultTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新建 Copilot 会话";
        }
        return title.trim();
    }

    private String executionSummary(String toolKey) {
        if ("workflow.todo.list".equals(toolKey)) {
            return "已返回待办列表";
        }
        return "已执行工具调用";
    }

    private String confirmationSummary(String toolKey) {
        if (toolKey != null && toolKey.contains("complete")) {
            return "请确认是否完成当前待办";
        }
        return "请确认是否执行该操作";
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record PendingToolCall(
            String toolCallId,
            String confirmationId,
            String summary
    ) {
    }
}
