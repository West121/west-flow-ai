package com.westflow.ai.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.ai.model.AiConfirmToolCallRequest;
import com.westflow.ai.model.AiCopilotAudioTranscriptionResponse;
import com.westflow.ai.model.AiConversationCreateRequest;
import com.westflow.ai.model.AiMessageAppendRequest;
import com.westflow.ai.model.AiToolCallRequest;
import com.westflow.ai.model.AiToolCallResultResponse;
import com.westflow.ai.service.AiCopilotService;
import com.westflow.ai.service.AiCopilotMultimodalService;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * AI Copilot 的对外 HTTP 接口。
 */
@RestController
@RequestMapping("/api/v1/ai/copilot")
@SaCheckLogin
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;
    private final AiCopilotMultimodalService aiCopilotMultimodalService;

    public AiCopilotController(
            AiCopilotService aiCopilotService,
            AiCopilotMultimodalService aiCopilotMultimodalService
    ) {
        this.aiCopilotService = aiCopilotService;
        this.aiCopilotMultimodalService = aiCopilotMultimodalService;
    }

    /**
     * 分页查询会话列表。
     */
    @PostMapping("/conversations/page")
    @SaCheckLogin
    public ApiResponse<PageResponse<com.westflow.ai.model.AiConversationSummaryResponse>> pageConversations(
            @Valid @RequestBody PageRequest request
    ) {
        return ApiResponse.success(aiCopilotService.pageConversations(request));
    }

    /**
     * 新建会话。
     */
    @PostMapping("/conversations")
    @SaCheckLogin
    public ApiResponse<com.westflow.ai.model.AiConversationDetailResponse> createConversation(
            @Valid @RequestBody AiConversationCreateRequest request
    ) {
        return ApiResponse.success(aiCopilotService.createConversation(request));
    }

    /**
     * 查询会话详情。
     */
    @GetMapping("/conversations/{conversationId}")
    @SaCheckLogin
    public ApiResponse<com.westflow.ai.model.AiConversationDetailResponse> getConversation(
            @PathVariable String conversationId
    ) {
        return ApiResponse.success(aiCopilotService.getConversation(conversationId));
    }

    /**
     * 删除会话。
     */
    @DeleteMapping("/conversations/{conversationId}")
    @SaCheckLogin
    public ApiResponse<Map<String, String>> deleteConversation(
            @PathVariable String conversationId
    ) {
        aiCopilotService.deleteConversation(conversationId);
        return ApiResponse.success(Map.of("conversationId", conversationId));
    }

    /**
     * 清空当前登录人的全部会话。
     */
    @DeleteMapping("/conversations")
    @SaCheckLogin
    public ApiResponse<Map<String, Integer>> clearConversations() {
        aiCopilotService.clearConversations();
        return ApiResponse.success(Map.of("cleared", 1));
    }

    /**
     * 追加会话消息。
     */
    @PostMapping("/conversations/{conversationId}/messages")
    @SaCheckLogin
    public ApiResponse<com.westflow.ai.model.AiConversationDetailResponse> appendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody AiMessageAppendRequest request
    ) {
        return ApiResponse.success(aiCopilotService.appendMessage(conversationId, request));
    }

    /**
     * 转写 AI Copilot 语音输入。
     */
    @PostMapping(path = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckLogin
    public ApiResponse<AiCopilotAudioTranscriptionResponse> transcribeAudio(
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(new AiCopilotAudioTranscriptionResponse(
                aiCopilotMultimodalService.transcribeAudio(file)
        ));
    }

    /**
     * 执行工具调用。
     */
    @PostMapping("/conversations/{conversationId}/tool-calls")
    @SaCheckLogin
    public ApiResponse<AiToolCallResultResponse> executeToolCall(
            @PathVariable String conversationId,
            @Valid @RequestBody AiToolCallRequest request
    ) {
        return ApiResponse.success(aiCopilotService.executeToolCall(conversationId, request));
    }

    /**
     * 确认写操作工具调用。
     */
    @PostMapping("/tool-calls/{toolCallId}/confirm")
    @SaCheckLogin
    public ApiResponse<AiToolCallResultResponse> confirmToolCall(
            @PathVariable String toolCallId,
            @Valid @RequestBody AiConfirmToolCallRequest request
    ) {
        return ApiResponse.success(aiCopilotService.confirmToolCall(toolCallId, request));
    }
}
