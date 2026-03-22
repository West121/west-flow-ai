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
    private final AiGatewayService aiGatewayService;
    private final AiToolExecutionService aiToolExecutionService;

    public DbAiCopilotService(
            AiConversationMapper aiConversationMapper,
            AiMessageMapper aiMessageMapper,
            AiToolCallMapper aiToolCallMapper,
            AiConfirmationMapper aiConfirmationMapper,
            AiAuditMapper aiAuditMapper,
            ObjectMapper objectMapper,
            AiGatewayService aiGatewayService,
            AiToolExecutionService aiToolExecutionService
    ) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.aiToolCallMapper = aiToolCallMapper;
        this.aiConfirmationMapper = aiConfirmationMapper;
        this.aiAuditMapper = aiAuditMapper;
        this.objectMapper = objectMapper;
        this.aiGatewayService = aiGatewayService;
        this.aiToolExecutionService = aiToolExecutionService;
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
        AiToolCallResultResponse result = aiToolExecutionService.executeToolCall(conversationId, request);
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

    private AssistantReply buildAssistantReply(AiConversationRecord conversation, String content, LocalDateTime now) {
        if (content.isBlank()) {
            return new AssistantReply("请输入更明确的问题或操作意图。", List.of(defaultTextBlock("当前消息为空，未触发 AI 编排。")));
        }

        String domain = resolveDomain(conversation);
        AiGatewayResponse gatewayResponse = aiGatewayService.route(new AiGatewayRequest(
                conversation.conversationId(),
                currentUserId(),
                content,
                domain,
                false,
                List.of(),
                parseJsonStringList(conversation.contextTagsJson())
        ));

        if (gatewayResponse.requiresConfirmation()) {
            AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                    conversation.conversationId(),
                    buildWriteToolCall(content, domain)
            );
            persistToolCall(toolResult);
            insertAudit(conversation.conversationId(), toolResult.toolCallId(), "WRITE", toolResult.summary());
            return new AssistantReply(
                    "我已经整理出建议动作，确认后才会真正执行写操作。",
                    List.of(
                            defaultTextBlock("当前请求已进入 Supervisor 审核流程。"),
                            confirmationBlock(toolResult)
                    )
            );
        }

        AiToolCallRequest readToolCall = buildReadToolCall(content, domain, gatewayResponse.skillIds());
        if (readToolCall != null) {
            AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(conversation.conversationId(), readToolCall);
            persistToolCall(toolResult);
            insertAudit(conversation.conversationId(), toolResult.toolCallId(), "READ", toolResult.summary());
            return new AssistantReply(buildReadSummary(toolResult), buildReadBlocks(toolResult, gatewayResponse));
        }

        return new AssistantReply(
                buildAssistantSummary(gatewayResponse, domain),
                List.of(defaultTextBlock(buildAssistantSummary(gatewayResponse, domain)))
        );
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
        if (contextTags.stream().anyMatch(tag -> "PLM".equalsIgnoreCase(tag))) {
            return "PLM";
        }
        if (contextTags.stream().anyMatch(tag -> "OA".equalsIgnoreCase(tag) || "审批".equalsIgnoreCase(tag))) {
            return "OA";
        }
        return "GENERAL";
    }

    private AiToolCallRequest buildWriteToolCall(String content, String domain) {
        if (content.contains("发起") || content.contains("提交")) {
            return new AiToolCallRequest(
                    "process.start",
                    AiToolType.WRITE,
                    AiToolSource.AGENT,
                    Map.of("content", content, "domain", domain)
            );
        }
        if (content.contains("驳回") || content.contains("退回")) {
            return new AiToolCallRequest(
                    "workflow.task.reject",
                    AiToolType.WRITE,
                    AiToolSource.AGENT,
                    Map.of("content", content, "domain", domain)
            );
        }
        return new AiToolCallRequest(
                "workflow.task.complete",
                AiToolType.WRITE,
                AiToolSource.AGENT,
                Map.of("content", content, "domain", domain)
        );
    }

    private AiToolCallRequest buildReadToolCall(String content, String domain, List<String> skillIds) {
        if (skillIds.contains("approval-trace") || content.contains("轨迹") || content.contains("路径")) {
            return new AiToolCallRequest(
                    "workflow.trace.summary",
                    AiToolType.READ,
                    AiToolSource.SKILL,
                    Map.of("content", content, "domain", domain)
            );
        }
        if (skillIds.contains("plm-change-summary") || "PLM".equalsIgnoreCase(domain)
                || content.contains("PLM") || content.contains("ECR") || content.contains("ECO") || content.contains("物料")) {
            return new AiToolCallRequest(
                    "plm.change.summary",
                    AiToolType.READ,
                    AiToolSource.SKILL,
                    Map.of("content", content, "domain", domain, "businessType", "PLM")
            );
        }
        if (content.contains("待办")) {
            return new AiToolCallRequest(
                    "workflow.todo.list",
                    AiToolType.READ,
                    AiToolSource.PLATFORM,
                    Map.of("keyword", content, "domain", domain)
            );
        }
        return null;
    }

    private AiMessageBlockResponse confirmationBlock(AiToolCallResultResponse result) {
        return new AiMessageBlockResponse(
                "confirm",
                "请确认是否继续执行",
                null,
                result.confirmationId(),
                result.summary(),
                "当前默认策略是读操作直执、写操作必须确认。",
                "确认处理",
                "暂不执行",
                "pending",
                null
        );
    }

    private List<AiMessageBlockResponse> buildReadBlocks(AiToolCallResultResponse result, AiGatewayResponse gatewayResponse) {
        if ("workflow.todo.list".equals(result.toolKey())) {
            Object count = result.result().getOrDefault("count", 0);
            return List.of(
                    defaultTextBlock("已通过平台内置工具读取待办数据。"),
                    defaultTextBlock("当前路由模式：" + gatewayResponse.routeMode()),
                    defaultTextBlock("当前待办数量：" + count)
            );
        }
        return List.of(
                defaultTextBlock("已通过 " + gatewayResponse.routeMode() + " 路由完成只读分析。"),
                defaultTextBlock(buildReadSummary(result))
        );
    }

    private String buildReadSummary(AiToolCallResultResponse result) {
        if ("workflow.trace.summary".equals(result.toolKey())) {
            return "我已经汇总当前审批轨迹，可继续追问节点处理路径。";
        }
        if ("plm.change.summary".equals(result.toolKey())) {
            return "我已经生成当前 PLM 变更摘要，可继续追问 ECR/ECO 影响范围。";
        }
        if ("workflow.todo.list".equals(result.toolKey())) {
            return "我已经读取当前待办列表，可继续缩小筛选范围。";
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

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record AssistantReply(
            String content,
            List<AiMessageBlockResponse> blocks
    ) {
    }
}
