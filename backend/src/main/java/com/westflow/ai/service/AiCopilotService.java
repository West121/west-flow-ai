package com.westflow.ai.service;

import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiConversationCreateRequest;
import com.westflow.ai.model.AiConversationDetailResponse;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.ai.model.AiConversationSummaryResponse;

/**
 * AI Copilot 服务契约。
 */
public interface AiCopilotService {

    /**
     * 分页查询会话。
     */
    PageResponse<AiConversationSummaryResponse> pageConversations(PageRequest request);

    /**
     * 新建会话。
     */
    AiConversationDetailResponse createConversation(AiConversationCreateRequest request);

    /**
     * 查询会话详情。
     */
    AiConversationDetailResponse getConversation(String conversationId);

    /**
     * 删除会话及其关联记录。
     */
    void deleteConversation(String conversationId);

    /**
     * 清空当前用户的全部会话及关联记录。
     */
    void clearConversations();

    /**
     * 追加会话消息。
     */
    AiConversationDetailResponse appendMessage(String conversationId, AiMessageAppendRequest request);

    /**
     * 执行工具调用。
     */
    AiToolCallResultResponse executeToolCall(String conversationId, AiToolCallRequest request);

    /**
     * 确认写操作工具调用。
     */
    AiToolCallResultResponse confirmToolCall(String toolCallId, AiConfirmToolCallRequest request);
}
