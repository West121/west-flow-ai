package com.westflow.system.message.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.message.service.SystemMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/messages")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemMessageController {

    private final SystemMessageService systemMessageService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemMessageListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemMessageService.page(request));
    }

    @GetMapping("/{messageId}")
    public ApiResponse<SystemMessageDetailResponse> detail(@PathVariable String messageId) {
        return ApiResponse.success(systemMessageService.detail(messageId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemMessageFormOptionsResponse> options() {
        return ApiResponse.success(systemMessageService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemMessageMutationResponse> create(@Valid @RequestBody SaveSystemMessageRequest request) {
        return ApiResponse.success(systemMessageService.create(request));
    }

    @PutMapping("/{messageId}")
    public ApiResponse<SystemMessageMutationResponse> update(
            @PathVariable String messageId,
            @Valid @RequestBody SaveSystemMessageRequest request
    ) {
        return ApiResponse.success(systemMessageService.update(messageId, request));
    }
}
