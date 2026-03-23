package com.westflow.aiadmin.conversation.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.conversation.service.AiConversationService;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 会话管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/conversations")
@SaCheckLogin
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService aiConversationService;

    /**
     * 分页查询会话。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiConversationListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiConversationService.page(request));
    }

    /**
     * 查询会话详情。
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<AiConversationDetailResponse> detail(@PathVariable String conversationId) {
        return ApiResponse.success(aiConversationService.detail(conversationId));
    }
}
