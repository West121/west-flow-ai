package com.westflow.workflowadmin.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.workflowadmin.api.request.*;
import com.westflow.workflowadmin.api.response.*;
import com.westflow.workflowadmin.service.ApprovalOpinionConfigService;
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
 * 审批意见配置后台接口。
 */
@RestController
@RequestMapping("/api/v1/workflow-management/opinion-configs")
@SaCheckLogin
@RequiredArgsConstructor
public class ApprovalOpinionConfigController {

    private final ApprovalOpinionConfigService approvalOpinionConfigService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<ApprovalOpinionConfigListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(approvalOpinionConfigService.page(request));
    }

    @GetMapping("/{configId}")
    public ApiResponse<ApprovalOpinionConfigDetailResponse> detail(@PathVariable String configId) {
        return ApiResponse.success(approvalOpinionConfigService.detail(configId));
    }

    @GetMapping("/options")
    public ApiResponse<ApprovalOpinionConfigFormOptionsResponse> options() {
        return ApiResponse.success(approvalOpinionConfigService.formOptions());
    }

    @PostMapping
    public ApiResponse<ApprovalOpinionConfigMutationResponse> create(@Valid @RequestBody SaveApprovalOpinionConfigRequest request) {
        return ApiResponse.success(approvalOpinionConfigService.create(request));
    }

    @PutMapping("/{configId}")
    public ApiResponse<ApprovalOpinionConfigMutationResponse> update(
            @PathVariable String configId,
            @Valid @RequestBody SaveApprovalOpinionConfigRequest request
    ) {
        return ApiResponse.success(approvalOpinionConfigService.update(configId, request));
    }
}
