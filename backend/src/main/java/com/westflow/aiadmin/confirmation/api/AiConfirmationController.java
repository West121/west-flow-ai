package com.westflow.aiadmin.confirmation.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.confirmation.service.AiConfirmationService;
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
 * AI 确认记录管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/confirmations")
@SaCheckLogin
@RequiredArgsConstructor
public class AiConfirmationController {

    private final AiConfirmationService aiConfirmationService;

    /**
     * 分页查询确认记录。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiConfirmationListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiConfirmationService.page(request));
    }

    /**
     * 查询确认记录详情。
     */
    @GetMapping("/{confirmationId}")
    public ApiResponse<AiConfirmationDetailResponse> detail(@PathVariable String confirmationId) {
        return ApiResponse.success(aiConfirmationService.detail(confirmationId));
    }
}
