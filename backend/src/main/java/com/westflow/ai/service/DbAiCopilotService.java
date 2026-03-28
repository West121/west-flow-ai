package com.westflow.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.executor.AiExecutionContext;
import com.westflow.ai.executor.AiExecutionPlan;
import com.westflow.ai.executor.AiExecutionResult;
import com.westflow.ai.executor.AiExecutionRouter;
import com.westflow.ai.executor.AiExecutorType;
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
import com.westflow.ai.planner.AiCopilotPlan;
import com.westflow.ai.planner.AiPlanAgentService;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库持久化的 AI Copilot 实现。
 */
@Service
public class DbAiCopilotService implements AiCopilotService {

    private static final Logger log = LoggerFactory.getLogger(DbAiCopilotService.class);

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
    private final ProcessDefinitionService processDefinitionService;
    private final AiPlanAgentService aiPlanAgentService;
    private final AiExecutionRouter aiExecutionRouter;

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
            AiRegistryCatalogService aiRegistryCatalogService,
            ProcessDefinitionService processDefinitionService
    ) {
        this(
                aiConversationMapper,
                aiMessageMapper,
                aiToolCallMapper,
                aiConfirmationMapper,
                aiAuditMapper,
                objectMapper,
                aiGatewayService,
                aiToolExecutionService,
                aiCopilotRuntimeService,
                aiRegistryCatalogService,
                processDefinitionService,
                null,
                null
        );
    }

    @Autowired
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
            AiRegistryCatalogService aiRegistryCatalogService,
            ProcessDefinitionService processDefinitionService,
            AiPlanAgentService aiPlanAgentService,
            AiExecutionRouter aiExecutionRouter
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
        this.processDefinitionService = processDefinitionService;
        this.aiPlanAgentService = aiPlanAgentService;
        this.aiExecutionRouter = aiExecutionRouter;
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
     * 删除会话及其关联记录。
     */
    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        AiConversationRecord conversation = requireConversation(conversationId);
        String userId = currentUserId();
        if (!userId.equals(conversation.operatorUserId())) {
            throw new ContractException("AI.CONVERSATION_FORBIDDEN", HttpStatus.FORBIDDEN, "无权删除该会话");
        }
        aiAuditMapper.deleteByConversationId(conversationId);
        aiConfirmationMapper.deleteByConversationId(conversationId);
        aiMessageMapper.deleteByConversationId(conversationId);
        aiToolCallMapper.deleteByConversationId(conversationId);
        aiConversationMapper.deleteConversation(conversationId);
    }

    /**
     * 追加会话消息。
     */
    @Override
    @Transactional
    public AiConversationDetailResponse appendMessage(String conversationId, AiMessageAppendRequest request) {
        AiConversationRecord conversation = requireConversation(conversationId);
        LocalDateTime now = now();
        LocalDateTime assistantTime = now.plusSeconds(1);
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
                assistantTime,
                assistantTime
        ));
        long messageCount = aiMessageMapper.countByConversationId(conversationId);
        aiConversationMapper.updateConversationSnapshot(
                conversationId,
                assistantReply.content(),
                STATUS_ACTIVE,
                Math.toIntExact(messageCount),
                assistantTime
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
        Map<String, Object> mergedArguments = mergeArguments(
                parseJsonMap(toolCall.argumentsJson()),
                request.argumentsOverride()
        );
        AiToolCallRequest toolCallRequest = new AiToolCallRequest(
                toolCall.toolKey(),
                toolCall.toolType(),
                toolCall.toolSource(),
                mergedArguments
        );
        String resultJson = toJson(buildConfirmationPayload(request.approved(), request.comment()));
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
                Map<String, Object> failurePayload = new LinkedHashMap<>(buildConfirmationPayload(true, request.comment()));
                failurePayload.put("error", exception.getMessage());
                resultJson = toJson(failurePayload);
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

    private Map<String, Object> buildConfirmationPayload(boolean approved, String comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        if (comment != null && !comment.isBlank()) {
            payload.put("comment", comment);
        }
        return payload;
    }

    private AssistantReply buildAssistantReply(AiConversationRecord conversation, String content, LocalDateTime now) {
        if (content.isBlank()) {
            return new AssistantReply("请输入更明确的问题或操作意图。", List.of(defaultTextBlock("当前消息为空，未触发 AI 编排。")));
        }

        String domain = resolveDomain(conversation);
        String routePath = resolveRoutePath(conversation);
        AssistantReply plannedReply = tryBuildAssistantReplyByPlan(conversation, content, now, domain, routePath);
        if (plannedReply != null) {
            return plannedReply;
        }
        // Legacy fallback: only used when the new planner/executor mainline fails unexpectedly.
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

        boolean readOnlyIntent = isReadOnlyIntent(content, routePath);

        boolean writeIntent = isExplicitProcessStartIntent(content, routePath)
                || hasExplicitTaskHandleIntent(content);
        if ((gatewayResponse.requiresConfirmation() || writeIntent) && !readOnlyIntent) {
            AiToolCallRequest writeToolCall;
            try {
                writeToolCall = buildWriteToolCall(content, domain, routePath);
            } catch (ContractException exception) {
                if ("AI.UNSUPPORTED_WRITE_INTENT".equals(exception.getCode())) {
                    writeToolCall = null;
                } else if ("AI.PROCESS_START_UNAVAILABLE".equals(exception.getCode())) {
                    String message = exception.getMessage();
                    return new AssistantReply(message, List.of(defaultTextBlock(message)));
                } else {
                    throw exception;
                }
            }
            if (writeToolCall != null) {
                AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                        conversation.conversationId(),
                        writeToolCall,
                        currentUserId()
                );
                persistToolCall(toolResult);
                insertAudit(conversation.conversationId(), toolResult.toolCallId(), "WRITE", toolResult.summary());
                return new AssistantReply(
                        "我已经整理出建议动作，确认后才会真正执行写操作。",
                        List.of(
                                buildWritePreviewBlock(content, domain, routePath, toolResult),
                                confirmationBlock(toolResult, gatewayResponse, domain, routePath)
                        )
                );
            }
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
            String fallbackReply = buildReadFinalAnswer(content, routePath, toolResult);
            return new AssistantReply(
                    resolveReadRuntimeReply(content, domain, routePath, gatewayResponse, toolResult, fallbackReply),
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

    private AssistantReply tryBuildAssistantReplyByPlan(
            AiConversationRecord conversation,
            String content,
            LocalDateTime now,
            String domain,
            String routePath
    ) {
        if (aiPlanAgentService == null || aiExecutionRouter == null) {
            return null;
        }
        try {
            AiGatewayRequest request = new AiGatewayRequest(
                    conversation.conversationId(),
                    currentUserId(),
                    content,
                    domain,
                    false,
                    List.of(),
                    parseJsonStringList(conversation.contextTagsJson()),
                    routePath
            );
            AiCopilotPlan plan = aiPlanAgentService.plan(request);
            AiExecutionResult executionResult = aiExecutionRouter.route(
                    toExecutionPlan(plan),
                    new AiExecutionContext(
                            conversation.conversationId(),
                            currentUserId(),
                            content,
                            domain,
                            routePath,
                            parseJsonStringList(conversation.contextTagsJson())
                    )
            );
            return buildAssistantReplyFromExecution(conversation, content, domain, routePath, now, plan, executionResult);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI planner path failed, falling back to legacy conversationId={} domain={} routePath={} reason={}",
                    conversation.conversationId(),
                    domain,
                    routePath,
                    exception.getMessage()
            );
            return null;
        }
    }

    private AssistantReply buildAssistantReplyFromExecution(
            AiConversationRecord conversation,
            String content,
            String domain,
            String routePath,
            LocalDateTime now,
            AiCopilotPlan plan,
            AiExecutionResult executionResult
    ) {
        log.info(
                "AI planner execution conversationId={} intent={} executor={} presentation={} toolCall={}",
                conversation.conversationId(),
                plan.intent(),
                executionResult.executorType(),
                executionResult.presentation(),
                executionResult.toolCallRequest() == null ? "none" : executionResult.toolCallRequest().toolKey()
        );
        AiGatewayResponse gatewayResponse = gatewayResponseFromPlan(plan, executionResult, content, domain);
        if (executionResult.toolCallRequest() != null) {
            AiToolCallRequest toolCallRequest = preparePlannedToolCall(
                    content,
                    domain,
                    routePath,
                    executionResult.toolCallRequest()
            );
            if (toolCallRequest.toolType() == AiToolType.WRITE) {
                AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                        conversation.conversationId(),
                        toolCallRequest,
                        currentUserId()
                );
                persistToolCall(toolResult);
                insertAudit(conversation.conversationId(), toolResult.toolCallId(), "WRITE", toolResult.summary());
                if ("process.start".equals(toolCallRequest.toolKey())) {
                    return new AssistantReply(
                            "我已经整理出建议动作，确认后才会真正执行写操作。",
                            List.of(
                                    buildWritePreviewBlock(content, domain, routePath, toolResult),
                                    confirmationBlock(toolResult, gatewayResponse, domain, routePath)
                            )
                    );
                }
                return new AssistantReply(
                        "我已经整理出建议动作，确认后才会真正执行写操作。",
                        List.of(
                                buildWritePreviewBlock(content, domain, routePath, toolResult),
                                confirmationBlock(toolResult, gatewayResponse, domain, routePath)
                        )
                );
            }

            AiToolCallResultResponse toolResult = aiToolExecutionService.executeToolCall(
                    conversation.conversationId(),
                    toolCallRequest,
                    currentUserId()
            );
            persistToolCall(toolResult);
            insertAudit(conversation.conversationId(), toolResult.toolCallId(), "READ", toolResult.summary());
            List<TraceStep> trace = buildToolResultTrace(gatewayResponse, toolResult, "executed");
            String fallbackReply = buildReadFinalAnswer(content, routePath, toolResult);
            return new AssistantReply(
                    resolveReadRuntimeReply(content, domain, routePath, gatewayResponse, toolResult, fallbackReply),
                    buildReadBlocks(content, routePath, toolResult, gatewayResponse, trace)
            );
        }

        String fallback = executionResult.summary().isBlank() ? "已整理当前问题，可继续补充上下文。" : executionResult.summary();
        String runtimeReply = resolveRuntimeReply(content, gatewayResponse, domain, fallback);
        return new AssistantReply(
                runtimeReply,
                List.of(
                        defaultTextBlock(runtimeReply),
                        traceBlock(
                                gatewayResponse.routeMode(),
                                gatewayResponse.agentId(),
                                gatewayResponse.agentKey(),
                                fallback,
                                "executed",
                                buildRouteTrace(gatewayResponse, domain, routePath, null)
                        )
                )
        );
    }

    private AiExecutionPlan toExecutionPlan(AiCopilotPlan plan) {
        return new AiExecutionPlan(
                plan.intent().name(),
                plan.domain(),
                AiExecutorType.valueOf(plan.executor().name()),
                plan.toolCandidates(),
                plan.arguments(),
                plan.presentation().name().toLowerCase(),
                plan.needConfirmation(),
                plan.confidence()
        );
    }

    private AiGatewayResponse gatewayResponseFromPlan(
            AiCopilotPlan plan,
            AiExecutionResult executionResult,
            String content,
            String domain
    ) {
        String routeMode = plan.intent().name().equals("WRITE") ? "SUPERVISOR" : "ROUTING";
        String agentId = switch (executionResult.executorType()) {
            case KNOWLEDGE -> "knowledge-agent";
            case WORKFLOW -> "workflow-agent";
            case STATS -> "stats-agent";
            case ACTION -> "action-agent";
            case MCP -> "mcp-agent";
        };
        List<String> skillIds = aiRegistryCatalogService == null
                ? List.of()
                : java.util.Optional.ofNullable(aiRegistryCatalogService.matchSkills(
                        currentUserId(),
                        content,
                        domain,
                        List.of()
                )).orElse(List.of()).stream().map(AiRegistryCatalogService.AiSkillCatalogItem::skillCode).toList();
        return new AiGatewayResponse(
                routeMode,
                agentId,
                plan.needConfirmation(),
                skillIds,
                agentId,
                executionResult.summary(),
                List.of(),
                null
        );
    }

    private AiToolCallRequest preparePlannedToolCall(
            String content,
            String domain,
            String routePath,
            AiToolCallRequest toolCallRequest
    ) {
        if (toolCallRequest == null) {
            return null;
        }
        if ("process.start".equals(toolCallRequest.toolKey()) || "task.handle".equals(toolCallRequest.toolKey())) {
            AiToolCallRequest validated = buildWriteToolCall(content, domain, routePath);
            Map<String, Object> mergedArguments = new LinkedHashMap<>(validated.arguments());
            mergedArguments.putAll(toolCallRequest.arguments());
            if (mergedArguments.get("formData") instanceof Map<?, ?> incomingFormData
                    && validated.arguments().get("formData") instanceof Map<?, ?> validatedFormData) {
                Map<String, Object> mergedFormData = new LinkedHashMap<>();
                validatedFormData.forEach((key, value) -> mergedFormData.put(String.valueOf(key), value));
                incomingFormData.forEach((key, value) -> {
                    String field = String.valueOf(key);
                    Object current = mergedFormData.get(field);
                    if (shouldOverrideValidatedFormValue(current, value)) {
                        mergedFormData.put(field, value);
                    }
                });
                mergedArguments.put("formData", Map.copyOf(mergedFormData));
            }
            return new AiToolCallRequest(
                    validated.toolKey(),
                    validated.toolType(),
                    validated.toolSource(),
                    Map.copyOf(mergedArguments)
            );
        }
        return toolCallRequest;
    }

    private boolean shouldOverrideValidatedFormValue(Object current, Object incoming) {
        if (incoming == null) {
            return false;
        }
        if (incoming instanceof String incomingText) {
            String normalizedIncoming = incomingText.trim();
            if (normalizedIncoming.isBlank()) {
                return false;
            }
            if (current instanceof String currentText && !currentText.trim().isBlank()) {
                return false;
            }
            return true;
        }
        if (incoming instanceof Number incomingNumber) {
            if (current instanceof Number currentNumber && currentNumber.doubleValue() != 0d) {
                return false;
            }
            return incomingNumber.doubleValue() != 0d;
        }
        if (incoming instanceof Boolean incomingBoolean) {
            if (current instanceof Boolean) {
                return !Boolean.FALSE.equals(incomingBoolean);
            }
            return true;
        }
        return current == null;
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
        List<Field> executionFields = buildWriteExecutionFields(toolCall, confirmation, request, finalResult);
        List<Metric> executionMetrics = buildWriteExecutionMetrics(toolCall, finalResult);
        List<TraceStep> trace = buildWriteExecutionTrace(toolCall, finalResult, request.approved());
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
                                buildWriteFailureDetail(toolCall, finalResult),
                                trace,
                                buildWriteFailureFields(toolCall, confirmation, request, finalResult),
                                buildWriteFailureMetrics(toolCall, finalResult),
                                buildTaskHandleResultPayload(
                                        toolCall.toolCallId(),
                                        confirmation.confirmationId(),
                                        toolCall.toolKey(),
                                        toolCall.toolType().name(),
                                        finalResult.arguments(),
                                        Map.of("retryable", true)
                                )
                        )
                                : resultBlock(
                                toolCall.toolSource().name(),
                                toolCall.toolKey(),
                                toolCall.toolKey(),
                                toolCall.toolType().name(),
                                finalResult.summary(),
                                buildTaskHandleResultPayload(
                                        toolCall.toolCallId(),
                                        confirmation.confirmationId(),
                                        toolCall.toolKey(),
                                        toolCall.toolType().name(),
                                        finalResult.arguments(),
                                        Map.of(
                                                "approved", request.approved(),
                                                "comment", request.comment() == null ? "" : request.comment(),
                                                "status", finalResult.status(),
                                                "result", finalResult.result()
                                        )
                                ),
                                trace,
                                executionFields,
                                executionMetrics
                        ),
                        failed
                                ? retryBlock(
                                toolCall.toolSource().name(),
                                toolCall.toolKey(),
                                toolCall.toolKey(),
                                toolCall.toolType().name(),
                                finalResult.summary(),
                                "当前写操作执行失败，可以在修正参数后重新确认并重试。",
                                buildTaskHandleResultPayload(
                                        toolCall.toolCallId(),
                                        confirmation.confirmationId(),
                                        toolCall.toolKey(),
                                        toolCall.toolType().name(),
                                        finalResult.arguments(),
                                        Map.of("retryable", true)
                                ),
                                trace,
                                executionFields,
                                buildWriteFailureMetrics(toolCall, finalResult)
                        )
                                : defaultTextBlock("本次写操作已完成，可继续查看流程状态或发起下一步操作。"),
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
            List<TraceStep> trace,
            List<Field> fields,
            List<Metric> metrics,
            Map<String, Object> result
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
                result,
                new Failure(code, message, detail),
                trace,
                fields,
                metrics
        );
    }

    /**
     * 构建失败后的重试建议块。
     */
    private AiMessageBlockResponse retryBlock(
            String sourceType,
            String sourceKey,
            String sourceName,
            String toolType,
            String summary,
            String detail,
            Map<String, Object> result,
            List<TraceStep> trace,
            List<Field> fields,
            List<Metric> metrics
    ) {
        java.util.ArrayList<Metric> retryMetrics = new java.util.ArrayList<>();
        retryMetrics.add(new Metric("可重试", "是", null, "warning"));
        if (metrics != null) {
            retryMetrics.addAll(metrics);
        }
        return new AiMessageBlockResponse(
                "retry",
                "重试建议",
                null,
                null,
                summary,
                detail,
                null,
                null,
                "retryable",
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
                List.copyOf(retryMetrics)
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

    protected String currentUserId() {
        try {
            StpUtil.checkLogin();
            return StpUtil.getLoginIdAsString();
        } catch (RuntimeException exception) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "未登录或登录已失效");
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

    /**
     * legacy fallback / validated write preflight helper.
     */
    private AiToolCallRequest buildWriteToolCall(String content, String domain, String routePath) {
        if (isExplicitProcessStartIntent(content, routePath)) {
            Map<String, Object> startDraft = buildProcessStartDraft(content, domain, routePath);
            return new AiToolCallRequest(
                    "process.start",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    startDraft
            );
        }
        if (content.contains("认领")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    buildTaskHandleDraft(content, domain, routePath, "CLAIM")
            );
        }
        if (content.contains("驳回") || content.contains("退回")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    buildTaskHandleDraft(content, domain, routePath, "REJECT")
            );
        }
        if (content.contains("已读") || content.contains("已阅")) {
            return new AiToolCallRequest(
                    "task.handle",
                    AiToolType.WRITE,
                    AiToolSource.PLATFORM,
                    buildTaskHandleDraft(content, domain, routePath, "READ")
            );
        }
        if (!hasExplicitTaskHandleIntent(content)) {
            throw new ContractException("AI.UNSUPPORTED_WRITE_INTENT", HttpStatus.BAD_REQUEST, "当前请求更适合走分析或查询链路");
        }
        return new AiToolCallRequest(
                "task.handle",
                AiToolType.WRITE,
                AiToolSource.PLATFORM,
                buildTaskHandleDraft(content, domain, routePath, "COMPLETE")
        );
    }

    private Map<String, Object> buildTaskHandleDraft(String content, String domain, String routePath, String action) {
        Map<String, Object> draft = new java.util.LinkedHashMap<>();
        draft.put("content", content);
        draft.put("domain", domain);
        draft.put("routePath", routePath);
        draft.put("taskId", extractTaskId(routePath));
        draft.put("action", action);
        enrichBusinessContextFromRoute(routePath).forEach(draft::put);
        return Map.copyOf(draft);
    }

    private Map<String, Object> buildProcessStartDraft(String content, String domain, String routePath) {
        Map<String, Object> formData = new java.util.LinkedHashMap<>();
        Map<String, Object> draft = new java.util.LinkedHashMap<>();
        draft.put("content", content);
        draft.put("domain", domain);
        draft.put("routePath", routePath);
        draft.put("sceneCode", "default");

        switch (resolveProcessStartRouteHint(content, routePath)) {
            case "OA_LEAVE" -> {
                draft.put("processKey", "oa_leave");
                draft.put("businessType", "OA_LEAVE");
                formData.put("leaveType", extractLeaveType(content));
                formData.put("days", extractLeaveDays(content));
                formData.put("reason", extractReasonSeed(content, "请补充请假原因"));
                formData.put("urgent", content != null && content.contains("紧急"));
                formData.put("managerUserId", extractManagerUserId(content));
            }
            case "OA_EXPENSE" -> {
                draft.put("processKey", "oa_expense");
                draft.put("businessType", "OA_EXPENSE");
                formData.put("amount", extractExpenseAmount(content));
                formData.put("reason", extractReasonSeed(content, "请补充报销事由"));
            }
            case "OA_COMMON" -> {
                draft.put("processKey", "oa_common");
                draft.put("businessType", "OA_COMMON");
                formData.put("title", extractTitleSeed(content, "AI 发起的通用申请"));
                formData.put("content", extractReasonSeed(content, "请补充申请内容"));
            }
            case "PLM_ECR" -> {
                draft.put("processKey", "plm_ecr");
                draft.put("businessType", "PLM_ECR");
                formData.put("changeTitle", extractTitleSeed(content, "AI 发起的 ECR 变更"));
                formData.put("changeReason", extractReasonSeed(content, "请补充变更原因"));
                formData.put("affectedProductCode", "");
                formData.put("priorityLevel", "MEDIUM");
            }
            case "PLM_ECO" -> {
                draft.put("processKey", "plm_eco");
                draft.put("businessType", "PLM_ECO");
                formData.put("executionTitle", extractTitleSeed(content, "AI 发起的 ECO 执行"));
                formData.put("executionPlan", extractReasonSeed(content, "请补充执行说明"));
                formData.put("effectiveDate", "");
                formData.put("changeReason", extractReasonSeed(content, "请补充变更原因"));
            }
            case "PLM_MATERIAL" -> {
                draft.put("processKey", "plm_material");
                draft.put("businessType", "PLM_MATERIAL");
                formData.put("materialCode", "");
                formData.put("materialName", extractTitleSeed(content, "AI 发起的物料主数据变更"));
                formData.put("changeReason", extractReasonSeed(content, "请补充变更原因"));
                formData.put("changeType", "ATTRIBUTE_UPDATE");
            }
            default -> {
                draft.put("processKey", "");
                draft.put("businessType", "");
                formData.put("content", content);
            }
        }

        enrichValidatedProcessStartDefinition(draft);
        draft.put("formData", Map.copyOf(formData));
        return Map.copyOf(draft);
    }

    private void enrichValidatedProcessStartDefinition(Map<String, Object> draft) {
        String processKey = stringValue(draft.get("processKey"));
        if (processKey.isBlank()) {
            throw new ContractException(
                    "AI.PROCESS_START_UNAVAILABLE",
                    HttpStatus.BAD_REQUEST,
                    "当前未识别到可直接发起的系统流程，请明确业务类型后再试。"
            );
        }
        PublishedProcessDefinition definition;
        try {
            definition = processDefinitionService.getLatestByProcessKey(processKey);
        } catch (ContractException exception) {
            throw new ContractException(
                    "AI.PROCESS_START_UNAVAILABLE",
                    HttpStatus.BAD_REQUEST,
                    "系统中未找到可直接发起的已发布流程，请先确认流程已发布并对当前账号开放。"
            );
        }
        ProcessDslPayload payload = definition.dsl();
        String processFormKey = payload == null ? "" : stringValue(payload.processFormKey());
        String processFormVersion = payload == null ? "" : stringValue(payload.processFormVersion());
        if (processFormKey.isBlank() || processFormVersion.isBlank()) {
            throw new ContractException(
                    "AI.PROCESS_START_UNAVAILABLE",
                    HttpStatus.BAD_REQUEST,
                    "当前流程未绑定可直接发起的流程表单，暂时不能通过 AI 发起。"
            );
        }
        draft.put("processDefinitionId", definition.processDefinitionId());
        draft.put("processName", definition.processName());
        draft.put("processFormKey", processFormKey);
        draft.put("processFormVersion", processFormVersion);
    }

    private String resolveProcessStartRouteHint(String content, String routePath) {
        String normalizedRoute = routePath == null ? "" : routePath;
        return switch (normalizedRoute) {
            case "/oa/leave/create", "/oa/leave/list" -> "OA_LEAVE";
            case "/oa/expense/create", "/oa/expense/list" -> "OA_EXPENSE";
            case "/oa/common/create", "/oa/common/list" -> "OA_COMMON";
            case "/plm/ecr/create" -> "PLM_ECR";
            case "/plm/eco/create" -> "PLM_ECO";
            case "/plm/material-master/create" -> "PLM_MATERIAL";
            default -> inferBusinessTypeFromContent(content);
        };
    }

    private String inferBusinessTypeFromContent(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.contains("请假")
                || normalized.contains("事假")
                || normalized.contains("年假")
                || normalized.contains("病假")
                || normalized.contains("调休")
                || normalized.contains("婚假")
                || normalized.contains("产假")
                || normalized.contains("陪产假")
                || normalized.contains("丧假")) {
            return "OA_LEAVE";
        }
        if (normalized.contains("报销")) {
            return "OA_EXPENSE";
        }
        if (normalized.contains("通用申请")) {
            return "OA_COMMON";
        }
        if (normalized.contains("ECR")) {
            return "PLM_ECR";
        }
        if (normalized.contains("ECO")) {
            return "PLM_ECO";
        }
        if (normalized.contains("物料")) {
            return "PLM_MATERIAL";
        }
        return "";
    }

    /**
     * legacy fallback helper.
     */
    private boolean isReadOnlyIntent(String content, String routePath) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return true;
        }
        if (isExplicitProcessStartIntent(normalized, routePath) || hasExplicitTaskHandleIntent(normalized)) {
            return false;
        }
        return normalized.contains("看看")
                || normalized.contains("分析")
                || normalized.contains("解读")
                || normalized.contains("解释")
                || normalized.contains("进度")
                || normalized.contains("状态")
                || normalized.contains("怎么样")
                || normalized.contains("几个")
                || normalized.contains("多少")
                || normalized.contains("汇总")
                || normalized.contains("总结")
                || normalized.contains("查询")
                || normalized.contains("查看");
    }

    /**
     * legacy fallback / validated write preflight helper.
     */
    private boolean isExplicitProcessStartIntent(String content, String routePath) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return false;
        }
        boolean startVerb = normalized.contains("帮我发起")
                || normalized.contains("请帮我发起")
                || normalized.contains("直接发起")
                || normalized.contains("发起一个")
                || normalized.contains("提交一个")
                || normalized.contains("新建")
                || normalized.contains("创建")
                || normalized.contains("提交申请")
                || normalized.contains("发起申请");
        boolean readQualifier = normalized.contains("发起了几个")
                || normalized.contains("我发起了")
                || normalized.contains("目前进度")
                || normalized.contains("进度都怎么样")
                || normalized.contains("怎么样");
        boolean businessMentioned = !inferBusinessTypeFromContent(normalized).isBlank();
        boolean naturalStartVerb = normalized.contains("帮我请")
                || normalized.contains("请个")
                || normalized.contains("请一")
                || normalized.contains("想请")
                || normalized.contains("我要请")
                || normalized.contains("帮我报销")
                || normalized.contains("帮我提")
                || normalized.contains("帮我申请")
                || normalized.contains("帮我创建")
                || normalized.contains("帮我新建");
        return (startVerb || (businessMentioned && naturalStartVerb)) && !readQualifier;
    }

    /**
     * legacy fallback / validated write preflight helper.
     */
    private boolean hasExplicitTaskHandleIntent(String content) {
        String normalized = content == null ? "" : content.trim();
        return normalized.contains("认领")
                || normalized.contains("驳回")
                || normalized.contains("退回")
                || normalized.contains("已读")
                || normalized.contains("已阅")
                || normalized.contains("同意")
                || normalized.contains("通过")
                || normalized.contains("办理")
                || normalized.contains("处理这个")
                || normalized.contains("处理当前");
    }

    private String resolveApprovalSheetView(String content, String routePath) {
        String normalized = content == null ? "" : content.trim();
        if (!extractTaskId(routePath).isBlank()) {
            return "TODO";
        }
        boolean initiatedIntent = normalized.contains("我发起")
                || normalized.contains("我提交")
                || normalized.contains("发起了几个")
                || normalized.contains("提交了几个")
                || ((normalized.contains("发起") || normalized.contains("申请"))
                && (normalized.contains("进度")
                || normalized.contains("状态")
                || normalized.contains("几个")
                || normalized.contains("多少")
                || normalized.contains("怎么样")
                || normalized.contains("汇总")
                || normalized.contains("总结")
                || normalized.contains("查询")
                || normalized.contains("查看")
                || normalized.contains("看看")));
        return initiatedIntent ? "INITIATED" : "TODO";
    }

    private AiToolCallRequest buildReadToolCall(String content, String domain, List<String> skillIds, String routePath) {
        if (isStatsReadIntent(content)) {
            return new AiToolCallRequest(
                    "stats.query",
                    AiToolType.READ,
                    AiToolSource.PLATFORM,
                    Map.of("keyword", content == null ? "" : content, "domain", domain)
            );
        }
        return aiRegistryCatalogService.matchReadTool(
                        currentUserId(),
                        content,
                        domain,
                        skillIds,
                        routePath
                )
                .map(tool -> toReadToolCall(tool, content, domain, routePath))
                .orElseGet(() -> buildFallbackReadToolCall(content, domain, routePath));
    }

    private AiToolCallRequest toReadToolCall(
            AiRegistryCatalogService.AiToolCatalogItem tool,
            String content,
            String domain,
            String routePath
    ) {
        String approvalSheetView = resolveApprovalSheetView(content, routePath);
        return switch (tool.toolCode()) {
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
                    Map.of(
                            "keyword", extractApprovalSheetKeyword(routePath, content == null ? "" : content, approvalSheetView),
                            "domain", domain,
                            "view", approvalSheetView,
                            "pageSize", 100
                    )
            );
        };
    }

    private AiToolCallRequest buildFallbackReadToolCall(String content, String domain, String routePath) {
        String normalized = content == null ? "" : content;
        String approvalSheetView = resolveApprovalSheetView(normalized, routePath);
        if (isStatsReadIntent(normalized)) {
            return new AiToolCallRequest(
                    "stats.query",
                    AiToolType.READ,
                    AiToolSource.PLATFORM,
                    Map.of("keyword", normalized, "domain", domain)
            );
        }
        if (normalized.contains("待办")
                || normalized.contains("审批")
                || normalized.contains("认领")
                || normalized.contains("轨迹")
                || normalized.contains("路径")
                || normalized.contains("流程图")
                || normalized.contains("进度")
                || normalized.contains("申请")
                || normalized.contains("几个")
                || normalized.contains("怎么样")) {
            return new AiToolCallRequest(
                    "task.query",
                    AiToolType.READ,
                    AiToolSource.PLATFORM,
                    Map.of(
                            "keyword", extractApprovalSheetKeyword(routePath, normalized, approvalSheetView),
                            "domain", domain,
                            "view", approvalSheetView,
                            "pageSize", 100
                    )
            );
        }
        if (normalized.contains("PLM")
                || normalized.contains("ECR")
                || normalized.contains("ECO")
                || normalized.contains("物料")
                || normalized.contains("变更")) {
            return new AiToolCallRequest(
                    normalized.contains("摘要") || normalized.contains("总结") ? "plm.change.summary" : "plm.bill.query",
                    AiToolType.READ,
                    normalized.contains("摘要") || normalized.contains("总结") ? AiToolSource.SKILL : AiToolSource.PLATFORM,
                    Map.of("keyword", extractBillKeyword(routePath, normalized), "domain", domain)
            );
        }
        if (normalized.contains("流程定义")
                || normalized.contains("流程列表")
                || normalized.contains("流程版本")
                || normalized.contains("发布记录")) {
            return new AiToolCallRequest(
                    "workflow.definition.list",
                    AiToolType.READ,
                    AiToolSource.PLATFORM,
                    Map.of("keyword", normalized)
            );
        }
        return null;
    }

    private boolean isStatsReadIntent(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("统计")
                || normalized.contains("报表")
                || normalized.contains("指标")
                || normalized.contains("趋势")
                || normalized.contains("图表")) {
            return true;
        }
        return (normalized.contains("用户")
                || normalized.contains("员工")
                || normalized.contains("人员")
                || normalized.contains("角色")
                || normalized.contains("权限角色")
                || normalized.contains("字典")
                || normalized.contains("字典项")
                || normalized.contains("通知")
                || normalized.contains("通知模板")
                || normalized.contains("通知渠道")
                || normalized.contains("通知记录")
                || normalized.contains("通知日志")
                || normalized.contains("日志")
                || normalized.contains("请求日志")
                || normalized.contains("审计日志")
                || normalized.contains("操作日志")
                || normalized.contains("代理")
                || normalized.contains("委派")
                || normalized.contains("离职转办")
                || normalized.contains("系统消息")
                || normalized.contains("消息")
                || normalized.contains("文件")
                || normalized.contains("附件")
                || normalized.contains("触发器")
                || normalized.contains("触发执行")
                || normalized.contains("编排")
                || normalized.contains("监控")
                || normalized.contains("扫描")
                || normalized.contains("公司")
                || normalized.contains("部门")
                || normalized.contains("岗位")
                || normalized.contains("职位")
                || normalized.contains("菜单")
                || normalized.contains("权限点")
                || normalized.contains("功能项"))
                && (normalized.contains("几个")
                || normalized.contains("多少")
                || normalized.contains("数量")
                || normalized.contains("分布")
                || normalized.contains("占比")
                || normalized.contains("对比")
                || normalized.contains("列出")
                || normalized.contains("列出来")
                || normalized.contains("对应")
                || normalized.contains("停用")
                || normalized.contains("禁用")
                || normalized.contains("启用")
                || normalized.contains("状态")
                || normalized.contains("分类")
                || normalized.contains("岗位")
                || normalized.contains("公司")
                || normalized.contains("部门"));
    }

    private AiMessageBlockResponse confirmationBlock(
            AiToolCallResultResponse result,
            AiGatewayResponse gatewayResponse,
            String domain,
            String routePath
    ) {
        List<Field> fields = new java.util.ArrayList<>(List.of(
                new Field("业务域", domain, null),
                new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                new Field("工具名称", result.toolKey(), null),
                new Field("工具类型", result.toolType().name(), null),
                new Field("确认单编号", result.confirmationId(), null)
        ));
        List<Metric> metrics = new java.util.ArrayList<>(List.of(
                new Metric("确认状态", result.requiresConfirmation() ? "待确认" : "已确认", null, "warning")
        ));
        if (isTaskHandleTool(result.toolKey(), result.arguments())) {
            String action = normalizeTaskAction(result.arguments().get("action"));
            fields.addAll(buildTaskHandleOutcomeFields(result.arguments(), "拟执行动作", action));
            metrics.add(new Metric("动作类型", resolveTaskActionLabel(action), null, "warning"));
            metrics.add(new Metric("动作语义", resolveTaskActionSemantic(action), null, "neutral"));
        }
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
                buildPendingConfirmationPayload(result, domain, routePath),
                null,
                List.of(
                        new TraceStep("route", "路由模式", gatewayResponse.routeMode(), "pending"),
                        new TraceStep("agent", "命中智能体", gatewayResponse.agentId(), "pending"),
                        new TraceStep("context", "业务域", domain, "pending"),
                        new TraceStep("context", "来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, "pending"),
                        new TraceStep("tool", "工具调用", result.toolCallId(), "pending")
                ),
                List.copyOf(fields),
                List.copyOf(metrics)
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
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>(List.of(
                new Field("工具名称", toolCall.toolKey(), null),
                new Field("工具类型", toolCall.toolType().name(), null),
                new Field("确认人", currentUserId(), null),
                new Field("确认意见", request.comment() == null || request.comment().isBlank() ? "无" : request.comment(), null)
        ));
        java.util.ArrayList<Metric> metrics = new java.util.ArrayList<>(List.of(
                new Metric("执行状态", finalResult.status(), null, "neutral")
        ));
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            fields.addAll(buildTaskHandleOutcomeFields(finalResult.arguments(), "待办动作", action));
            metrics.add(new Metric("动作语义", resolveTaskActionSemantic(action), null, "neutral"));
        }
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
                buildResolvedConfirmationPayload(toolCall, confirmation, request, finalResult),
                null,
                List.of(
                        new TraceStep("tool", "工具调用", toolCall.toolCallId(), status),
                        new TraceStep("confirmation", "确认单", confirmation.confirmationId(), status),
                        new TraceStep("result", "执行状态", finalResult.status(), status)
                ),
                List.copyOf(fields),
                List.copyOf(metrics)
        );
    }

    private Map<String, Object> buildPendingConfirmationPayload(
            AiToolCallResultResponse result,
            String domain,
            String routePath
    ) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("toolCallId", result.toolCallId());
        payload.put("confirmationId", result.confirmationId());
        payload.put("status", result.status());
        payload.put("requiresConfirmation", result.requiresConfirmation());
        payload.put("toolType", result.toolType().name());
        payload.put("toolSource", result.toolSource().name());
        payload.put("domain", domain);
        payload.put("routePath", routePath);
        payload.put("arguments", result.arguments());
        if (isTaskHandleTool(result.toolKey(), result.arguments())) {
            String action = normalizeTaskAction(result.arguments().get("action"));
            payload.put("actionLabel", resolveTaskActionLabel(action));
            payload.put("actionSemantic", resolveTaskActionSemantic(action));
        }
        return Map.copyOf(payload);
    }

    private Map<String, Object> buildResolvedConfirmationPayload(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            AiToolCallResultResponse finalResult
    ) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("toolCallId", toolCall.toolCallId());
        payload.put("confirmationId", confirmation.confirmationId());
        payload.put("status", finalResult.status());
        payload.put("approved", request.approved());
        payload.put("comment", request.comment() == null ? "" : request.comment());
        payload.put("toolType", toolCall.toolType().name());
        payload.put("toolSource", toolCall.toolSource().name());
        payload.put("toolKey", toolCall.toolKey());
        payload.put("result", finalResult.result());
        payload.put("summary", finalResult.summary());
        payload.put("arguments", finalResult.arguments());
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            payload.put("actionLabel", resolveTaskActionLabel(action));
            payload.put("actionSemantic", resolveTaskActionSemantic(action));
        }
        return Map.copyOf(payload);
    }

    private Map<String, Object> buildTaskHandleResultPayload(
            String toolCallId,
            String confirmationId,
            String toolKey,
            String toolType,
            Map<String, Object> arguments,
            Map<String, ?> extra
    ) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        if (extra != null) {
            extra.forEach(payload::put);
        }
        String action = normalizeTaskAction(arguments.get("action"));
        payload.put("toolCallId", toolCallId);
        payload.put("confirmationId", confirmationId);
        payload.put("toolKey", toolKey);
        payload.put("toolType", toolType);
        payload.put("arguments", arguments);
        payload.put("action", action);
        payload.put("actionLabel", resolveTaskActionLabel(action));
        payload.put("actionSemantic", resolveTaskActionSemantic(action));
        payload.put("domain", stringValue(arguments.get("domain")));
        payload.put("businessType", stringValue(arguments.get("businessType")));
        payload.put("businessId", stringValue(arguments.get("businessId")));
        payload.put("billNo", stringValue(arguments.get("billNo")));
        payload.put("businessTitle", resolveBusinessTitle(arguments));
        payload.put("routePath", stringValue(arguments.get("routePath")));
        return Map.copyOf(payload);
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

    private Map<String, Object> mergeArguments(
            Map<String, Object> originalArguments,
            Map<String, Object> argumentsOverride
    ) {
        Map<String, Object> merged = new java.util.LinkedHashMap<>(originalArguments == null ? Map.of() : originalArguments);
        if (argumentsOverride == null || argumentsOverride.isEmpty()) {
            return Map.copyOf(merged);
        }
        argumentsOverride.forEach((key, value) -> {
            if ("formData".equals(key) && value instanceof Map<?, ?> overrideFormData) {
                Map<String, Object> currentFormData = new java.util.LinkedHashMap<>(mapValue(merged.get("formData")));
                overrideFormData.forEach((formKey, formValue) -> currentFormData.put(String.valueOf(formKey), formValue));
                merged.put("formData", Map.copyOf(currentFormData));
            } else {
                merged.put(key, value);
            }
        });
        return Map.copyOf(merged);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
            return Map.copyOf(normalized);
        }
        return Map.of();
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::mapValue)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return List.of();
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
        if ("feature.catalog.query".equals(result.toolKey())) {
            return List.of(
                    defaultTextBlock("已根据当前页面和业务域整理可用功能目录。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中功能推荐与使用说明链路。",
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
                            buildFeatureCatalogFields(result.result()),
                            buildFeatureCatalogMetrics(result.result())
                    )
            );
        }
        if ("task.query".equals(result.toolKey()) || "workflow.todo.list".equals(result.toolKey())) {
            String approvalSheetView = resolveApprovalSheetViewFromArguments(result.arguments());
            Map<String, Object> normalizedApprovalResult = normalizeApprovalSheetResult(result.result());
            Object count = normalizedApprovalResult.getOrDefault("count", 0);
            java.util.ArrayList<AiMessageBlockResponse> blocks = new java.util.ArrayList<>(List.of(
                    defaultTextBlock("已通过平台内置工具读取" + ("INITIATED".equals(approvalSheetView) ? "我发起的申请数据。" : "待办数据。")),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中" + ("INITIATED".equals(approvalSheetView) ? "我发起审批单" : "待办") + "查询链路。",
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
                        buildTodoResultFields(routePath, normalizedApprovalResult, approvalSheetView),
                        buildTodoResultMetrics(routePath, count, content, approvalSheetView)
                    ),
                    buildTodoStatsBlock(routePath, count, approvalSheetView)
            ));
            if (content != null && (content.contains("解释") || content.contains("路径") || content.contains("为什么"))) {
                blocks.add(defaultTextBlock(buildTodoExplanation(routePath, normalizedApprovalResult, approvalSheetView)));
            }
            return List.copyOf(blocks);
        }
        if ("stats.query".equals(result.toolKey())) {
            if (isStatsErrorResult(result.result())) {
                return List.of(
                        defaultTextBlock("统计查询未能生成可执行结果。"),
                        traceBlock(
                                gatewayResponse.routeMode(),
                                gatewayResponse.agentId(),
                                gatewayResponse.agentKey(),
                                "统计查询链路已执行，但未生成安全可用的查询结果。",
                                "failed",
                                trace
                        ),
                        failureBlock(
                                result.toolSource().name(),
                                result.toolKey(),
                                result.toolKey(),
                                result.toolType().name(),
                                stringValue(result.result().get("summary")),
                                "AI.STATS_QUERY_FAILED",
                                stringValue(result.result().get("summary")),
                                stringValue(result.result().get("summary")),
                                trace,
                                List.of(),
                                List.of(),
                                result.result()
                        )
                );
            }
            java.util.ArrayList<AiMessageBlockResponse> blocks = new java.util.ArrayList<>(List.of(
                    defaultTextBlock("已通过平台统计工具生成指标摘要。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中统计查询链路。",
                            "executed",
                            trace
                    )
            ));
            AiMessageBlockResponse chartBlock = buildStatsChartBlock(result.result());
            if (chartBlock != null) {
                blocks.add(chartBlock);
            } else {
                blocks.add(buildStatsAnswerBlock(result.result()));
            }
            return List.copyOf(blocks);
        }
        if ("plm.bill.query".equals(result.toolKey()) || "plm.change.summary".equals(result.toolKey())) {
            return List.of(
                    defaultTextBlock("已通过 PLM 助手读取变更单据与业务摘要。"),
                    traceBlock(
                            gatewayResponse.routeMode(),
                            gatewayResponse.agentId(),
                            gatewayResponse.agentKey(),
                            "已命中 PLM 变更摘要链路。",
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
                            buildPlmSummaryFields(result.result(), routePath, result),
                            buildPlmSummaryMetrics(result.result())
                    ),
                    buildPlmSummaryStatsBlock(result.result())
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
        if ("feature.catalog.query".equals(result.toolKey())) {
            return "我已经整理当前页面可用的功能目录，可继续追问某个功能怎么使用。";
        }
        if ("task.query".equals(result.toolKey()) || "workflow.trace.summary".equals(result.toolKey())) {
            return "INITIATED".equals(resolveApprovalSheetViewFromArguments(result.arguments()))
                    ? "我已经汇总你发起的申请进度，可继续追问某条申请当前卡在哪个节点。"
                    : "我已经汇总当前审批轨迹，可继续追问节点处理路径。";
        }
        if ("plm.bill.query".equals(result.toolKey()) || "plm.change.summary".equals(result.toolKey())) {
            return "我已经生成当前 PLM 变更摘要，可继续追问 ECR/ECO 影响范围。";
        }
        if ("stats.query".equals(result.toolKey())) {
            return "我已经整理出当前统计摘要，可继续追问时间范围或业务域。";
        }
        return "我已经完成本次只读分析。";
    }

    private String buildReadFinalAnswer(String content, String routePath, AiToolCallResultResponse result) {
        if ("workflow.definition.list".equals(result.toolKey())) {
            int count = countItems(result.result().get("items"));
            if (count > 0) {
                return "当前共命中 " + count + " 条流程定义，可继续按流程名或流程键查看详情。";
            }
            return "当前没有命中的流程定义。";
        }
        if ("feature.catalog.query".equals(result.toolKey())) {
            int toolCount = countItems(result.result().get("tools"));
            int skillCount = countItems(result.result().get("skills"));
            return "当前页面共整理出 " + toolCount + " 个可用工具、" + skillCount + " 个可用技能，可继续问我某个功能怎么使用。";
        }
        if ("task.query".equals(result.toolKey()) || "workflow.todo.list".equals(result.toolKey())) {
            return buildTodoFinalAnswer(content, routePath, normalizeApprovalSheetResult(result.result()), resolveApprovalSheetViewFromArguments(result.arguments()));
        }
        if ("workflow.trace.summary".equals(result.toolKey())) {
            return buildTodoExplanation(routePath, normalizeApprovalSheetResult(result.result()), resolveApprovalSheetViewFromArguments(result.arguments()));
        }
        if ("stats.query".equals(result.toolKey())) {
            return buildStatsFinalAnswer(result.result());
        }
        if ("plm.bill.query".equals(result.toolKey()) || "plm.change.summary".equals(result.toolKey())) {
            return buildPlmFinalAnswer(result.result());
        }
        return buildReadSummary(result);
    }

    private List<Field> buildFeatureCatalogFields(Map<String, Object> result) {
        return List.of(
                new Field("业务域", stringValue(result.get("domain")), null),
                new Field("来源页面", stringValue(result.get("pageRoute")), null),
                new Field("功能摘要", stringValue(result.get("summary")), null)
        );
    }

    private List<Metric> buildFeatureCatalogMetrics(Map<String, Object> result) {
        return List.of(
                new Metric("工具数", String.valueOf(countItems(result.get("tools"))), null, "neutral"),
                new Metric("技能数", String.valueOf(countItems(result.get("skills"))), null, "positive"),
                new Metric("MCP 数", String.valueOf(countItems(result.get("mcps"))), null, "warning")
        );
    }

    private String normalizeTaskAction(Object value) {
        String action = stringValue(value).toUpperCase();
        return action.isBlank() ? "COMPLETE" : action;
    }

    private String resolveTaskActionLabel(String action) {
        return switch (normalizeTaskAction(action)) {
            case "CLAIM" -> "认领待办";
            case "REJECT" -> "驳回/退回";
            case "READ" -> "标记已读";
            case "APPROVE", "COMPLETE" -> "通过待办";
            default -> action;
        };
    }

    private boolean isTaskHandleTool(String toolKey, Map<String, Object> arguments) {
        if ("task.handle".equals(toolKey)) {
            return true;
        }
        return arguments != null
                && (!stringValue(arguments.get("taskId")).isBlank()
                || !stringValue(arguments.get("action")).isBlank());
    }

    private boolean isProcessStartTool(String toolKey, Map<String, Object> arguments) {
        if ("process.start".equals(toolKey)) {
            return true;
        }
        return arguments != null
                && !stringValue(arguments.get("processKey")).isBlank()
                && arguments.containsKey("formData")
                && stringValue(arguments.get("taskId")).isBlank();
    }

    private String resolveTaskActionSemantic(String action) {
        return switch (normalizeTaskAction(action)) {
            case "CLAIM" -> "认领后待办归当前处理人，不推进流程节点";
            case "REJECT" -> "退回当前审批事项，流程回到上一步节点";
            case "READ" -> "仅标记已读，不改变流程流转";
            case "APPROVE", "COMPLETE" -> "审批通过并推进到后续节点";
            default -> "按当前动作处理待办";
        };
    }

    private String resolveTaskHandleNextSuggestion(String action, Object nextTasks) {
        String nextTasksText = describeNextTasks(nextTasks);
        if (!nextTasksText.isBlank()) {
            return "下一步待办：" + nextTasksText;
        }
        return switch (normalizeTaskAction(action)) {
            case "CLAIM" -> "认领后回到工作台继续处理该待办";
            case "REJECT" -> "刷新流程状态，确认退回节点和补充材料";
            case "READ" -> "已读后可继续追问轨迹或返回工作台处理";
            case "APPROVE", "COMPLETE" -> "刷新流程状态，确认是否已进入终态";
            default -> "刷新当前待办状态并核对参数";
        };
    }

    private String resolveBusinessTitle(Map<String, Object> arguments) {
        String businessTitle = stringValue(arguments.get("businessTitle"));
        if (!businessTitle.isBlank()) {
            return businessTitle;
        }
        return stringValue(arguments.get("title"));
    }

    private List<Field> buildTaskHandleBusinessFields(Map<String, Object> arguments) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        String businessType = stringValue(arguments.get("businessType"));
        String businessId = stringValue(arguments.get("businessId"));
        String billNo = stringValue(arguments.get("billNo"));
        String businessTitle = resolveBusinessTitle(arguments);
        if (!businessType.isBlank()) {
            fields.add(new Field("业务类型", businessType, null));
        }
        if (!businessId.isBlank()) {
            fields.add(new Field("业务标识", businessId, null));
        }
        if (!billNo.isBlank()) {
            fields.add(new Field("业务单据", billNo, null));
        }
        if (!businessTitle.isBlank()) {
            fields.add(new Field("业务标题", businessTitle, null));
        }
        return List.copyOf(fields);
    }

    private List<Field> buildTaskHandleOutcomeFields(
            Map<String, Object> arguments,
            String actionFieldLabel,
            String action
    ) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        String taskId = stringValue(arguments.get("taskId"));
        String comment = stringValue(arguments.get("comment"));
        String routePath = stringValue(arguments.get("routePath"));
        fields.add(new Field("待办编号", taskId.isBlank() ? "未定位" : taskId, null));
        fields.add(new Field(actionFieldLabel, resolveTaskActionLabel(action), null));
        fields.add(new Field("动作语义", resolveTaskActionSemantic(action), null));
        if (!routePath.isBlank()) {
            fields.add(new Field("来源页面", routePath, null));
        }
        fields.addAll(buildTaskHandleBusinessFields(arguments));
        if (!comment.isBlank()) {
            fields.add(new Field("处理意见", comment, null));
        }
        return List.copyOf(fields);
    }

    private List<Field> buildWriteExecutionFields(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            AiToolCallResultResponse finalResult
    ) {
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            Map<String, Object> result = mapValue(finalResult.result());
            String instanceId = stringValue(result.get("instanceId"));
            String nextTasks = describeNextTasks(result.get("nextTasks"));
            List<Field> fields = new java.util.ArrayList<>(buildTaskHandleOutcomeFields(finalResult.arguments(), "待办动作", action));
            fields.add(new Field("工具调用编号", toolCall.toolCallId(), null));
            fields.add(new Field("确认单编号", confirmation.confirmationId(), null));
            fields.add(new Field("执行状态", finalResult.status(), null));
            if (!instanceId.isBlank()) {
                fields.add(new Field("流程实例", instanceId, null));
            }
            if (!nextTasks.isBlank()) {
                fields.add(new Field("后续任务", nextTasks, "处理完成后命中的下一步待办"));
            }
            fields.add(new Field("下一步建议", resolveTaskHandleNextSuggestion(action, result.get("nextTasks")), null));
            return List.copyOf(fields);
        }
        if (isProcessStartTool(toolCall.toolKey(), finalResult.arguments())) {
            Map<String, Object> result = mapValue(finalResult.result());
            return List.of(
                    new Field("业务类型", stringValue(finalResult.arguments().get("businessType")), null),
                    new Field("流程编码", stringValue(finalResult.arguments().get("processKey")), null),
                    new Field("来源页面", stringValue(finalResult.arguments().get("routePath")), null),
                    new Field("业务摘要", buildProcessStartBusinessSummary(finalResult.arguments()), null),
                    new Field("单据编号", stringValue(result.get("billNo")), null),
                    new Field("流程实例", stringValue(result.get("instanceId")), null),
                    new Field("首个待办", describeNextTasks(result.get("activeTasks")), "发起成功后会优先进入首个待办"),
                    new Field("发起后动作", resolveProcessStartNextAction(finalResult.arguments()), null),
                    new Field("执行状态", finalResult.status(), null)
            );
        }
        return buildToolContextFields(
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
        );
    }

    private List<Metric> buildWriteExecutionMetrics(
            AiToolCallRecord toolCall,
            AiToolCallResultResponse finalResult
    ) {
        if ("task.handle".equals(toolCall.toolKey())) {
            Map<String, Object> result = mapValue(finalResult.result());
            Object nextTasks = result.get("nextTasks");
            int nextTaskCount = countItems(nextTasks);
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            String domain = stringValue(finalResult.arguments().get("domain"));
            return List.of(
                    new Metric("动作", resolveTaskActionLabel(action), null, "warning"),
                    new Metric("动作语义", resolveTaskActionSemantic(action), null, "neutral"),
                    new Metric("后续待办数", String.valueOf(nextTaskCount), nextTaskCount > 0 ? "流程仍在继续推进" : "当前动作后可能已到终态", nextTaskCount > 0 ? "positive" : "neutral"),
                    new Metric("执行状态", finalResult.status(), null, "positive"),
                    new Metric("业务域", domain.isBlank() ? "未定位" : domain, null, "neutral")
            );
        }
        if ("process.start".equals(toolCall.toolKey())) {
            return List.of(
                    new Metric("业务域", stringValue(finalResult.arguments().get("domain")), null, "neutral"),
                    new Metric("表单字段数", String.valueOf(mapValue(finalResult.arguments().get("formData")).size()), null, "neutral"),
                    new Metric("首个待办数", String.valueOf(countItems(mapValue(finalResult.result()).get("activeTasks"))), "发起后系统生成的当前活动任务", "positive"),
                    new Metric("执行状态", finalResult.status(), null, "positive")
            );
        }
        return List.of();
    }

    private List<Field> buildWriteFailureFields(
            AiToolCallRecord toolCall,
            AiConfirmationRecord confirmation,
            AiConfirmToolCallRequest request,
            AiToolCallResultResponse finalResult
    ) {
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            List<Field> fields = new java.util.ArrayList<>(
                    buildTaskHandleOutcomeFields(
                            finalResult.arguments(),
                            "待办动作",
                            action
                    )
            );
            fields.add(new Field("工具调用编号", toolCall.toolCallId(), null));
            fields.add(new Field("确认单编号", confirmation.confirmationId(), null));
            fields.add(new Field("下一步建议", resolveTaskHandleNextSuggestion(action, null), null));
            return List.copyOf(fields);
        }
        if (isProcessStartTool(toolCall.toolKey(), finalResult.arguments())) {
            return List.of(
                    new Field("业务类型", stringValue(finalResult.arguments().get("businessType")), null),
                    new Field("流程编码", stringValue(finalResult.arguments().get("processKey")), null),
                    new Field("来源页面", stringValue(finalResult.arguments().get("routePath")), null),
                    new Field("工具调用编号", toolCall.toolCallId(), null),
                    new Field("确认单编号", confirmation.confirmationId(), null)
            );
        }
        return buildToolContextFields(
                toolCall.toolSource().name(),
                toolCall.toolKey(),
                toolCall.toolKey(),
                toolCall.toolType().name(),
                stringValue(finalResult.arguments().get("domain")),
                stringValue(finalResult.arguments().get("routePath")),
                toolCall.toolCallId(),
                confirmation.confirmationId(),
                finalResult.summary(),
                TOOL_STATUS_FAILED
        );
    }

    private List<Metric> buildWriteFailureMetrics(
            AiToolCallRecord toolCall,
            AiToolCallResultResponse finalResult
    ) {
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            String domain = stringValue(finalResult.arguments().get("domain"));
            return List.of(
                    new Metric("动作类型", resolveTaskActionLabel(action), null, "warning"),
                    new Metric("动作语义", resolveTaskActionSemantic(action), null, "warning"),
                    new Metric("执行状态", finalResult.status(), null, "warning"),
                    new Metric("业务域", domain.isBlank() ? "未定位" : domain, null, "neutral")
            );
        }
        if (isProcessStartTool(toolCall.toolKey(), finalResult.arguments())) {
            return List.of(
                    new Metric("业务类型", stringValue(finalResult.arguments().get("businessType")), null, "neutral"),
                    new Metric("执行状态", finalResult.status(), null, "warning"),
                    new Metric("可重试", "是", null, "warning")
            );
        }
        return List.of(
                new Metric("执行状态", finalResult.status(), null, "warning"),
                new Metric("可重试", "是", null, "warning")
        );
    }

    private List<TraceStep> buildWriteExecutionTrace(
            AiToolCallRecord toolCall,
            AiToolCallResultResponse finalResult,
            boolean approved
    ) {
        if ("process.start".equals(toolCall.toolKey())) {
            return List.of(
                    new TraceStep("source", "来源页面", stringValue(finalResult.arguments().get("routePath")), finalResult.status()),
                    new TraceStep("business", "业务摘要", buildProcessStartBusinessSummary(finalResult.arguments()), finalResult.status()),
                    new TraceStep("result", "执行说明", approved ? "写操作确认完成" : "写操作已取消", finalResult.status())
            );
        }
        return buildToolResultTrace(
                toolCall.toolSource().name(),
                toolCall.toolKey(),
                toolCall.toolKey(),
                finalResult.status(),
                approved ? "写操作确认完成" : "写操作已取消"
        );
    }

    private String buildWriteFailureDetail(AiToolCallRecord toolCall, AiToolCallResultResponse finalResult) {
        if (isTaskHandleTool(toolCall.toolKey(), finalResult.arguments())) {
            String action = normalizeTaskAction(finalResult.arguments().get("action"));
            return "待办动作“" + resolveTaskActionLabel(action) + "”执行失败，" + resolveTaskActionSemantic(action) + "，请检查当前任务状态或重试。";
        }
        if (isProcessStartTool(toolCall.toolKey(), finalResult.arguments())) {
            return "流程发起执行失败，请检查业务表单字段和业务绑定配置后重试。";
        }
        return finalResult.summary();
    }

    private int countItems(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return (Map<String, Object>) map;
                    }
                    if (item == null || item instanceof CharSequence || item instanceof Number || item instanceof Boolean) {
                        return null;
                    }
                    return objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() { });
                })
                .filter(map -> map != null && !map.isEmpty())
                .toList();
    }

    private String describeNextTasks(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(this::describeTaskLikeItem)
                .filter(text -> text != null && !text.isBlank())
                .limit(3)
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private String describeTaskLikeItem(Object item) {
        if (item instanceof Map<?, ?> map) {
            String nodeName = stringValue(map.get("nodeName"));
            String taskId = stringValue(map.get("taskId"));
            if (!nodeName.isBlank() && !taskId.isBlank()) {
                return nodeName + "(" + taskId + ")";
            }
            return !nodeName.isBlank() ? nodeName : taskId;
        }
        return item == null ? "" : item.toString();
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

    private String resolveReadRuntimeReply(
            String content,
            String domain,
            String routePath,
            AiGatewayResponse gatewayResponse,
            AiToolCallResultResponse toolResult,
            String fallback
    ) {
        try {
            String runtimeReply = aiCopilotRuntimeService.generateReadReply(
                    new AiGatewayRequest(
                            null,
                            currentUserId(),
                            content,
                            domain,
                            false,
                            gatewayResponse.skillIds(),
                            routePath == null || routePath.isBlank() ? List.of() : List.of("route:" + routePath),
                            routePath
                    ),
                    gatewayResponse,
                    toolResult.toolKey(),
                    toJson(toolResult.result()),
                    fallback
            );
            if (runtimeReply == null || runtimeReply.isBlank() || isPlaceholderAiReply(runtimeReply)) {
                return fallback;
            }
            return runtimeReply;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private boolean isPlaceholderAiReply(String runtimeReply) {
        String normalized = runtimeReply == null ? "" : runtimeReply.trim();
        return "routing-reply".equalsIgnoreCase(normalized)
                || "supervisor-reply".equalsIgnoreCase(normalized);
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

    private Map<String, Object> enrichBusinessContextFromRoute(String routePath) {
        if (routePath == null || routePath.isBlank()) {
            return Map.of();
        }
        java.util.regex.Matcher plmMatcher = java.util.regex.Pattern.compile("^/plm/(ecr|eco|material-master)/([^/?]+)$").matcher(routePath);
        if (plmMatcher.find()) {
            String segment = plmMatcher.group(1);
            String businessId = plmMatcher.group(2);
            String businessType = switch (segment) {
                case "ecr" -> "PLM_ECR";
                case "eco" -> "PLM_ECO";
                case "material-master" -> "PLM_MATERIAL";
                default -> "";
            };
            if (!businessType.isBlank()) {
                return Map.of("businessType", businessType, "businessId", businessId);
            }
        }
        return Map.of();
    }

    private String extractTaskKeyword(String routePath, String fallbackKeyword) {
        String taskId = extractTaskId(routePath);
        return taskId.isBlank() ? fallbackKeyword : taskId;
    }

    private String extractApprovalSheetKeyword(String routePath, String content, String view) {
        if ("TODO".equalsIgnoreCase(view)) {
            return extractTaskKeyword(routePath, content);
        }
        String routeKeyword = extractInitiatedRouteKeyword(routePath);
        if (!routeKeyword.isBlank()) {
            return routeKeyword;
        }
        return extractInitiatedContentKeyword(content);
    }

    private String extractInitiatedRouteKeyword(String routePath) {
        if (routePath == null || routePath.isBlank()) {
            return "";
        }
        if (routePath.startsWith("/oa/leave")) {
            return "请假";
        }
        if (routePath.startsWith("/oa/expense")) {
            return "报销";
        }
        if (routePath.startsWith("/oa/common")) {
            return "通用申请";
        }
        return "";
    }

    private String extractInitiatedContentKeyword(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.contains("请假")) {
            return "请假";
        }
        if (normalized.contains("报销")) {
            return "报销";
        }
        if (normalized.contains("通用")) {
            return "通用申请";
        }
        if (normalized.contains("ECR")) {
            return "ECR";
        }
        if (normalized.contains("ECO")) {
            return "ECO";
        }
        return "";
    }

    private String extractBillKeyword(String routePath, String fallbackKeyword) {
        if (routePath == null || routePath.isBlank()) {
            return fallbackKeyword;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/(?:oa|plm)/[^/]+/([^/?]+)").matcher(routePath);
        return matcher.find() ? matcher.group(1) : fallbackKeyword;
    }

    private String extractTitleSeed(String content, String fallbackTitle) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return fallbackTitle;
        }
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
    }

    private String extractReasonSeed(String content, String fallbackReason) {
        String normalized = content == null ? "" : content.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:原因是|原因为|事由是|内容是)([^，。；]+)").matcher(normalized);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            if (!extracted.isBlank()) {
                return extracted;
            }
        }
        return normalized.isBlank() ? fallbackReason : normalized;
    }

    private int extractLeaveDays(String content) {
        if (content == null || content.isBlank()) {
            return 1;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*天").matcher(content);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private String extractLeaveType(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.contains("病假")) {
            return "SICK";
        }
        if (normalized.contains("事假")) {
            return "PERSONAL";
        }
        return "ANNUAL";
    }

    private String extractManagerUserId(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.contains("李四")) {
            return "usr_002";
        }
        return "usr_002";
    }

    private String extractExpenseAmount(String content) {
        if (content == null || content.isBlank()) {
            return "100.00";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d{1,2})?)").matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "100.00";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private AiMessageBlockResponse buildProcessStartPreviewBlock(
            String content,
            String domain,
            String routePath,
            AiToolCallResultResponse toolResult
    ) {
        Map<String, Object> arguments = toolResult.arguments();
        Map<String, Object> formData = mapValue(arguments.get("formData"));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("toolCallId", toolResult.toolCallId());
        result.put("confirmationId", toolResult.confirmationId());
        result.put("toolKey", toolResult.toolKey());
        result.put("toolType", toolResult.toolType().name());
        result.put("businessType", stringValue(arguments.get("businessType")));
        result.put("processKey", stringValue(arguments.get("processKey")));
        result.put("processDefinitionId", stringValue(arguments.get("processDefinitionId")));
        result.put("processName", stringValue(arguments.get("processName")));
        result.put("processFormKey", stringValue(arguments.get("processFormKey")));
        result.put("processFormVersion", stringValue(arguments.get("processFormVersion")));
        result.put("sceneCode", stringValue(arguments.getOrDefault("sceneCode", "default")));
        result.put("formData", formData);
        result.put("editable", true);
        return new AiMessageBlockResponse(
                "form-preview",
                "拟发起流程预览",
                null,
                null,
                null,
                "确认前只需核对业务表单内容。",
                null,
                null,
                null,
                null,
                null,
                null,
                toolResult.toolSource().name(),
                toolResult.toolKey(),
                toolResult.toolKey(),
                toolResult.toolType().name(),
                Map.copyOf(result),
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private AiMessageBlockResponse buildTaskHandlePreviewBlock(
            String domain,
            String routePath,
            AiToolCallResultResponse toolResult
    ) {
        Map<String, Object> arguments = toolResult.arguments();
        String action = normalizeTaskAction(arguments.get("action"));
        String taskId = stringValue(arguments.get("taskId"));
        return resultBlock(
                toolResult.toolSource().name(),
                toolResult.toolKey(),
                toolResult.toolKey(),
                toolResult.toolType().name(),
                "待办处理预览已生成，确认后才会执行真实审批动作。",
                buildTaskHandleResultPayload(
                        toolResult.toolCallId(),
                        toolResult.confirmationId(),
                        toolResult.toolKey(),
                        toolResult.toolType().name(),
                        arguments,
                        Map.of()
                ),
                buildToolResultTrace(
                        toolResult.toolSource().name(),
                        toolResult.toolKey(),
                        toolResult.toolKey(),
                        toolResult.status(),
                        "待确认前可继续核对待办动作"
                ),
                previewFields(domain, routePath, toolResult, arguments, action, taskId),
                List.of(
                        new Metric("动作类型", resolveTaskActionLabel(action), "确认后才会真正落到流程运行态", "warning"),
                        new Metric("动作语义", resolveTaskActionSemantic(action), null, "neutral"),
                        new Metric("待办定位", taskId.isBlank() ? "待补充" : "已命中", null, taskId.isBlank() ? "warning" : "positive")
                )
        );
    }

    private List<Field> previewFields(
            String domain,
            String routePath,
            AiToolCallResultResponse toolResult,
            Map<String, Object> arguments,
            String action,
            String taskId
    ) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        fields.add(new Field("业务域", domain, null));
        fields.add(new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null));
        fields.add(new Field("待办编号", taskId.isBlank() ? "未定位" : taskId, "优先使用当前页面上下文中的任务编号"));
        fields.add(new Field("拟执行动作", resolveTaskActionLabel(action), null));
        fields.add(new Field("动作语义", resolveTaskActionSemantic(action), null));
        fields.addAll(buildTaskHandleBusinessFields(arguments));
        fields.add(new Field("工具调用编号", toolResult.toolCallId(), null));
        fields.add(new Field("确认单编号", toolResult.confirmationId(), null));
        fields.add(new Field("状态", toolResult.status(), null));
        return List.copyOf(fields);
    }

    private AiMessageBlockResponse buildWritePreviewBlock(
            String content,
            String domain,
            String routePath,
            AiToolCallResultResponse toolResult
    ) {
        if ("process.start".equals(toolResult.toolKey())) {
            return buildProcessStartPreviewBlock(content, domain, routePath, toolResult);
        }
        if ("task.handle".equals(toolResult.toolKey())) {
            return buildTaskHandlePreviewBlock(domain, routePath, toolResult);
        }
        return resultBlock(
                toolResult.toolSource().name(),
                toolResult.toolKey(),
                toolResult.toolKey(),
                toolResult.toolType().name(),
                "写操作预览已生成，确认后才会真正执行。",
                Map.of(
                        "toolCallId", toolResult.toolCallId(),
                        "confirmationId", toolResult.confirmationId(),
                        "toolKey", toolResult.toolKey(),
                        "toolType", toolResult.toolType().name()
                ),
                buildToolResultTrace(
                        toolResult.toolSource().name(),
                        toolResult.toolKey(),
                        toolResult.toolKey(),
                        toolResult.status(),
                        "待确认前可继续核对写操作"
                ),
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
        );
    }

    private String resolveApprovalSheetViewFromArguments(Map<String, Object> arguments) {
        String view = stringValue(arguments.get("view")).toUpperCase();
        return view.isBlank() ? "TODO" : view;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeApprovalSheetResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return Map.of("count", 0, "items", List.of());
        }
        if (result.containsKey("count") || result.containsKey("items")) {
            return result;
        }
        Object pageValue = result.get("page");
        if (pageValue instanceof PageResponse<?> page) {
            return Map.of("count", page.total(), "items", page.records());
        }
        if (pageValue instanceof Map<?, ?> pageMap) {
            Object total = pageMap.containsKey("total") ? pageMap.get("total") : 0;
            Object records = pageMap.containsKey("records") ? pageMap.get("records") : List.of();
            return Map.of("count", total, "items", records);
        }
        return result;
    }

    private AiMessageBlockResponse buildTodoStatsBlock(String routePath, Object count, String view) {
        String taskId = extractTaskId(routePath);
        boolean initiatedView = "INITIATED".equals(view);
        return new AiMessageBlockResponse(
                "stats",
                initiatedView ? "我发起的申请摘要" : "待办摘要",
                null,
                null,
                null,
                initiatedView ? "当前你发起的审批单和路由上下文已经同步到 Copilot。" : "当前待办和路由上下文已经同步到 Copilot。",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field(initiatedView ? "上下文任务" : "待办编号", taskId.isBlank() ? "未定位" : taskId, null),
                        new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                        new Field("视图", view, initiatedView ? "来自平台我发起审批单查询" : "来自平台待办查询")
                ),
                List.of(
                        new Metric(initiatedView ? "当前发起数" : "当前待办数", String.valueOf(count), null, "neutral"),
                        new Metric(initiatedView ? "上下文任务" : "待办编号", taskId.isBlank() ? "未定位" : taskId, null, "warning"),
                        new Metric("视图", view, initiatedView ? "来自平台我发起审批单查询" : "来自平台待办查询", "positive")
                )
        );
    }

    private List<Field> buildTodoResultFields(String routePath, Map<String, Object> result, String view) {
        String taskId = extractTaskId(routePath);
        boolean initiatedView = "INITIATED".equals(view);
        return List.of(
                new Field("上下文命中", taskId.isBlank() ? "列表模式" : (initiatedView ? "当前申请" : "当前待办"), null),
                new Field(initiatedView ? "当前申请" : "当前待办", taskId.isBlank() ? (initiatedView ? "未绑定单条申请" : "未绑定单条待办") : taskId, null),
                new Field(initiatedView ? "申请摘要" : "待办摘要", summarizeTodoItems(result.get("items"), view), null),
                new Field(initiatedView ? "进度建议" : "处理路径建议", taskId.isBlank()
                        ? (initiatedView ? "可以继续追问某条申请卡在哪个节点、谁可以处理。" : "先打开待办详情，再让 Copilot 给出处理建议。")
                        : (initiatedView ? "继续追问该申请当前节点、处理人和下一步去向。" : "继续追问该待办的处理路径、退回节点或审批原因。"), null),
                new Field("下一步建议", taskId.isBlank()
                        ? (initiatedView ? "可以继续追问我今天发起了几条、哪些已完成或已撤销。" : "可以继续追问审批中心统计、流程轨迹或某条待办。")
                        : (initiatedView ? "可以继续让 Copilot 解释这条申请为什么卡在当前节点。" : "可以继续让 Copilot 解释该待办为何到你、下一步去哪。"), null)
        );
    }

    private List<Metric> buildTodoResultMetrics(String routePath, Object count, String content, String view) {
        String taskId = extractTaskId(routePath);
        boolean explainMode = content != null && (content.contains("解释") || content.contains("路径") || content.contains("为什么"));
        boolean initiatedView = "INITIATED".equals(view);
        return List.of(
                new Metric(initiatedView ? "当前发起数" : "当前待办数", String.valueOf(count), null, "neutral"),
                new Metric("上下文命中", taskId.isBlank() ? "列表模式" : (initiatedView ? "单申请" : "单待办"), null, taskId.isBlank() ? "neutral" : "positive"),
                new Metric("解释模式", explainMode ? "是" : "否", null, explainMode ? "positive" : "neutral")
        );
    }

    private String summarizeTodoItems(Object items, String view) {
        boolean initiatedView = "INITIATED".equals(view);
        List<Map<String, Object>> entries = listOfMaps(items);
        if (!entries.isEmpty()) {
            return entries.stream()
                    .map(item -> {
                        String taskId = stringValue(item.get("taskId"));
                        String title = stringValue(item.get("title"));
                        String nodeName = stringValue(item.get("nodeName"));
                        String currentNodeName = stringValue(item.get("currentNodeName"));
                        String businessTitle = stringValue(item.get("businessTitle"));
                        String billNo = stringValue(item.get("billNo"));
                        String instanceStatus = stringValue(item.get("instanceStatus"));
                        if (initiatedView) {
                            String summaryTitle = !businessTitle.isBlank() ? businessTitle : (!title.isBlank() ? title : billNo);
                            String statusText = instanceStatus.isBlank() ? "进行中" : instanceStatus;
                            String progressNode = !currentNodeName.isBlank() ? currentNodeName : nodeName;
                            if (!summaryTitle.isBlank()) {
                                return summaryTitle + "（" + statusText + (progressNode.isBlank() ? "" : " · " + progressNode) + "）";
                            }
                        }
                        if (!title.isBlank()) {
                            return title;
                        }
                        if (!nodeName.isBlank()) {
                            return nodeName + (taskId.isBlank() ? "" : "（" + taskId + "）");
                        }
                        return taskId.isBlank() ? "待办项" : taskId;
                    })
                    .distinct()
                    .limit(2)
                    .reduce((left, right) -> left + "；" + right)
                    .orElse(initiatedView ? "当前没有命中的申请摘要。" : "当前没有命中的待办摘要。");
        }
        if (items instanceof List<?> rawItems && !rawItems.isEmpty()) {
            List<String> plainItems = rawItems.stream()
                    .filter(item -> !(item instanceof Map<?, ?>))
                    .map(item -> item == null ? "" : item.toString())
                    .filter(text -> !text.isBlank())
                    .limit(2)
                    .toList();
            if (!plainItems.isEmpty()) {
                return String.join("；", plainItems);
            }
        }
        return initiatedView ? "当前没有命中的申请摘要。" : "当前没有命中的待办摘要。";
    }

    private String buildTodoFinalAnswer(String content, String routePath, Map<String, Object> result, String view) {
        String taskId = extractTaskId(routePath);
        boolean explainMode = content != null && (content.contains("解释") || content.contains("路径") || content.contains("为什么"));
        boolean initiatedView = "INITIATED".equals(view);
        Object answerItems = initiatedView ? narrowApprovalItemsByTimeScope(content, result.get("items")) : result.get("items");
        int count = initiatedView ? countItems(answerItems) : intValue(result.get("count"));
        String summary = summarizeTodoItems(answerItems, view);
        if (!taskId.isBlank() && explainMode) {
            return buildTodoExplanation(routePath, result, view);
        }
        if (count <= 0) {
            return initiatedView
                    ? (containsTodayScope(content) ? "今天没有命中你发起的申请。" : "当前没有命中你发起的申请。")
                    : "当前没有待办。";
        }
        if (!taskId.isBlank()) {
            return initiatedView ? "当前命中 1 条你发起的申请，" + summary + "。" : "当前命中 1 条待办，" + summary + "。";
        }
        if (initiatedView) {
            String statusAnalysis = buildInitiatedStatusAnalysis(answerItems);
            return containsTodayScope(content)
                    ? "今天你共发起 " + count + " 条申请，" + statusAnalysis + " 重点包括：" + summary + "。"
                    : "当前你共发起 " + count + " 条申请，" + statusAnalysis + " 重点包括：" + summary + "。";
        }
        return "当前共有 " + count + " 条待办，重点包括：" + summary + "。";
    }

    private String buildInitiatedStatusAnalysis(Object items) {
        List<Map<String, Object>> entries = listOfMaps(items);
        if (entries.isEmpty()) {
            return "暂无可分析的进度分布。";
        }
        long running = entries.stream()
                .filter(item -> "RUNNING".equalsIgnoreCase(stringValue(item.get("instanceStatus"))))
                .count();
        long completed = entries.stream()
                .filter(item -> "COMPLETED".equalsIgnoreCase(stringValue(item.get("instanceStatus"))))
                .count();
        long revoked = entries.stream()
                .filter(item -> "REVOKED".equalsIgnoreCase(stringValue(item.get("instanceStatus"))))
                .count();
        long terminated = entries.stream()
                .filter(item -> "TERMINATED".equalsIgnoreCase(stringValue(item.get("instanceStatus"))))
                .count();
        java.util.LinkedHashMap<String, Long> runningNodeCounts = entries.stream()
                .filter(item -> "RUNNING".equalsIgnoreCase(stringValue(item.get("instanceStatus"))))
                .map(item -> stringValue(item.get("currentNodeName")))
                .filter(node -> !node.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        node -> node,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        String mainNode = runningNodeCounts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("");
        StringBuilder builder = new StringBuilder();
        builder.append("其中进行中 ").append(running).append(" 条");
        if (completed > 0) {
            builder.append("，已完成 ").append(completed).append(" 条");
        }
        if (revoked > 0) {
            builder.append("，已撤销 ").append(revoked).append(" 条");
        }
        if (terminated > 0) {
            builder.append("，已终止 ").append(terminated).append(" 条");
        }
        if (!mainNode.isBlank()) {
            builder.append("，当前主要停留在“").append(mainNode).append("”");
        }
        String nodeBreakdown = runningNodeCounts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(2)
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 条")
                .reduce((left, right) -> left + "，" + right)
                .orElse("");
        if (!nodeBreakdown.isBlank()) {
            builder.append("，当前卡点分布为：").append(nodeBreakdown);
        }
        return builder.toString();
    }

    private String buildProcessStartBusinessSummary(Map<String, Object> arguments) {
        Map<String, Object> formData = mapValue(arguments.get("formData"));
        String businessType = stringValue(arguments.get("businessType"));
        if ("OA_LEAVE".equalsIgnoreCase(businessType)) {
            return "请假天数 " + stringValue(formData.get("days")) + " · 原因 " + stringValue(formData.get("reason"));
        }
        if ("OA_EXPENSE".equalsIgnoreCase(businessType)) {
            return "报销金额 " + stringValue(formData.get("amount")) + " · 事由 " + stringValue(formData.get("reason"));
        }
        if ("PLM_ECR".equalsIgnoreCase(businessType)) {
            return "影响产品 " + stringValue(formData.get("affectedProductCode")) + " · 标题 " + stringValue(formData.get("changeTitle"));
        }
        if ("PLM_ECO".equalsIgnoreCase(businessType)) {
            return "生效日期 " + stringValue(formData.get("effectiveDate")) + " · 标题 " + stringValue(formData.get("executionTitle"));
        }
        if ("PLM_MATERIAL".equalsIgnoreCase(businessType)) {
            return "物料 " + stringValue(formData.get("materialCode")) + " / " + stringValue(formData.get("materialName"));
        }
        return formData.isEmpty() ? "未识别业务摘要" : "已填写 " + formData.size() + " 个字段";
    }

    private String resolveProcessStartNextAction(Map<String, Object> arguments) {
        String routePath = stringValue(arguments.get("routePath"));
        if (!routePath.isBlank()) {
            return "发起后自动跳转到业务详情，并从详情联查审批单。";
        }
        return "发起后回到审批中心查看首个待办。";
    }

    private String buildTodoExplanation(String routePath, Map<String, Object> result, String view) {
        String taskId = extractTaskId(routePath);
        Object items = result.get("items");
        boolean initiatedView = "INITIATED".equals(view);
        if (!taskId.isBlank()) {
            return initiatedView
                    ? "申请 " + taskId + " 已命中当前上下文，Copilot 会优先围绕该申请解释审批进度。"
                    : "待办 " + taskId + " 已命中当前上下文，Copilot 会优先围绕该事项解释处理路径。";
        }
        return initiatedView
                ? "当前已读取你发起的申请列表，命中结果：" + (items == null ? "[]" : items)
                : "当前已读取待办列表，命中结果：" + (items == null ? "[]" : items);
    }

    private boolean containsTodayScope(String content) {
        String normalized = content == null ? "" : content;
        return normalized.contains("今天") || normalized.contains("今日");
    }

    private Object narrowApprovalItemsByTimeScope(String content, Object items) {
        if (!containsTodayScope(content)) {
            return items;
        }
        return listOfMaps(items).stream()
                .filter(item -> isCreatedToday(item.get("createdAt")))
                .toList();
    }

    private boolean isCreatedToday(Object createdAt) {
        String text = stringValue(createdAt);
        if (text.isBlank()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().isEqual(LocalDate.now(TIME_ZONE));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String buildStatsFinalAnswer(Map<String, Object> result) {
        String summary = stringValue(result.get("summary"));
        if (!summary.isBlank()) {
            return summary;
        }
        List<Metric> metrics = buildStatsMetrics(result);
        if (!metrics.isEmpty()) {
            String metricSummary = metrics.stream()
                    .map(metric -> metric.label() + " " + metric.value())
                    .reduce((left, right) -> left + "，" + right)
                    .orElse("");
            if (!metricSummary.isBlank()) {
                return "当前统计结果：" + metricSummary + "。";
            }
        }
        return "当前已返回统计结果。";
    }

    private AiMessageBlockResponse buildStatsAnswerBlock(Map<String, Object> result) {
        String title = stringValue(result.get("title"));
        String summary = stringValue(result.get("summary"));
        List<Metric> metrics = buildStatsMetrics(result);
        return new AiMessageBlockResponse(
                "stats",
                title.isBlank() ? "统计问答结果" : title,
                null,
                null,
                null,
                summary.isBlank() ? "当前回答来自平台统计工具，可继续细分到流程、OA 或 PLM 维度。" : summary,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field("统计口径", stringValue(result.getOrDefault("scope", "platform")), "来自平台统计工具"),
                        new Field("统计主题", title.isBlank() ? "当前流程运营指标" : title, null),
                        new Field("摘要", "已返回真实统计数据", null)
                ),
                metrics.isEmpty()
                        ? List.of()
                        : metrics
        );
    }

    private AiMessageBlockResponse buildStatsChartBlock(Map<String, Object> result) {
        Map<String, Object> chart = mapValue(result.get("chart"));
        List<Map<String, Object>> data = mapList(result.get("data"));
        if (chart.isEmpty() || data.isEmpty()) {
            return null;
        }
        return new AiMessageBlockResponse(
                "chart",
                stringValue(chart.get("title")).isBlank() ? "统计图表" : stringValue(chart.get("title")),
                null,
                null,
                stringValue(chart.get("description")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "chart", chart,
                        "data", data
                ),
                null,
                List.of(),
                List.of(),
                buildStatsMetrics(result)
        );
    }

    private boolean isStatsErrorResult(Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("error"));
    }

    private List<Metric> buildStatsMetrics(Map<String, Object> result) {
        List<Map<String, Object>> metricMaps = mapList(result.get("metrics"));
        if (metricMaps.isEmpty()) {
            return List.of();
        }
        return metricMaps.stream()
                .map(metric -> new Metric(
                        stringValue(metric.get("label")),
                        stringValue(metric.get("value")),
                        stringValue(metric.get("hint")).isBlank() ? null : stringValue(metric.get("hint")),
                        stringValue(metric.get("tone")).isBlank() ? null : stringValue(metric.get("tone"))
                ))
                .toList();
    }

    private List<Field> buildPlmSummaryFields(
            Map<String, Object> result,
            String routePath,
            AiToolCallResultResponse toolResult
    ) {
        Object items = result.get("items");
        String firstBill = "";
        String businessType = "";
        if (items instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> firstItem) {
            firstBill = stringValue(firstItem.get("billNo"));
            businessType = stringValue(firstItem.get("businessType"));
        }
        List<Field> fields = new java.util.ArrayList<>(List.of(
                new Field("来源页面", routePath == null || routePath.isBlank() ? "未绑定页面" : routePath, null),
                new Field("工具调用编号", toolResult.toolCallId(), null),
                new Field("命中业务类型", businessType.isBlank() ? "未定位" : businessType, null),
                new Field("首条单据", firstBill.isBlank() ? "无匹配单据" : firstBill, null)
        ));
        String topSummary = summarizeTopPlmItems(items);
        if (!topSummary.isBlank()) {
            fields.add(new Field("命中摘要", topSummary, "最多展示前三条命中单据"));
        }
        return List.copyOf(fields);
    }

    private List<Metric> buildPlmSummaryMetrics(Map<String, Object> result) {
        int count = countItems(result.get("items"));
        return List.of(
                new Metric("命中单据数", String.valueOf(count), count > 0 ? "已读取真实 PLM 业务数据" : "当前关键词未匹配到 PLM 单据", count > 0 ? "positive" : "warning"),
                new Metric("业务域", "PLM", null, "neutral")
        );
    }

    private AiMessageBlockResponse buildPlmSummaryStatsBlock(Map<String, Object> result) {
        return new AiMessageBlockResponse(
                "stats",
                "PLM 业务摘要",
                null,
                null,
                null,
                "当前结果来自 PLM 单据查询，可继续追问 ECR/ECO/物料变更影响范围。",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new Field("摘要", summarizeTopPlmItems(result.get("items")), "最多展示前三条命中单据")
                ),
                buildPlmSummaryMetrics(result)
        );
    }

    private String summarizeTopPlmItems(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "暂无匹配单据";
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .limit(3)
                .map(item -> {
                    String billNo = stringValue(item.get("billNo"));
                    String title = stringValue(item.get("title"));
                    String businessType = stringValue(item.get("businessType"));
                    if (!billNo.isBlank() && !title.isBlank()) {
                        return businessType + " · " + billNo + " · " + title;
                    }
                    if (!billNo.isBlank()) {
                        return businessType + " · " + billNo;
                    }
                    return title;
                })
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无匹配单据");
    }

    private String buildPlmFinalAnswer(Map<String, Object> result) {
        int count = countItems(result.get("items"));
        String summary = summarizeTopPlmItems(result.get("items"));
        if (count <= 0) {
            return "当前没有命中的 PLM 单据。";
        }
        return "当前命中 " + count + " 条 PLM 单据，重点包括：" + summary + "。";
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
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
