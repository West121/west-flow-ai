package com.westflow.aiadmin.toolcall.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.toolcall.service.AiToolCallService;
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
 * AI 工具调用管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/tool-calls")
@SaCheckLogin
@RequiredArgsConstructor
public class AiToolCallController {

    private final AiToolCallService aiToolCallService;

    /**
     * 分页查询工具调用记录。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiToolCallListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiToolCallService.page(request));
    }

    /**
     * 查询工具调用详情。
     */
    @GetMapping("/{toolCallId}")
    public ApiResponse<AiToolCallDetailResponse> detail(@PathVariable String toolCallId) {
        return ApiResponse.success(aiToolCallService.detail(toolCallId));
    }
}
