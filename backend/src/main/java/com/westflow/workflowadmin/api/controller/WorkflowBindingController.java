package com.westflow.workflowadmin.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.workflowadmin.api.request.*;
import com.westflow.workflowadmin.api.response.*;
import com.westflow.workflowadmin.service.WorkflowBindingService;
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
 * 业务流程绑定后台接口。
 */
@RestController
@RequestMapping("/api/v1/workflow-management/bindings")
@SaCheckLogin
@RequiredArgsConstructor
public class WorkflowBindingController {

    private final WorkflowBindingService workflowBindingService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<WorkflowBindingListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(workflowBindingService.page(request));
    }

    @GetMapping("/{bindingId}")
    public ApiResponse<WorkflowBindingDetailResponse> detail(@PathVariable String bindingId) {
        return ApiResponse.success(workflowBindingService.detail(bindingId));
    }

    @GetMapping("/options")
    public ApiResponse<WorkflowBindingFormOptionsResponse> options() {
        return ApiResponse.success(workflowBindingService.formOptions());
    }

    @PostMapping
    public ApiResponse<WorkflowBindingMutationResponse> create(@Valid @RequestBody SaveWorkflowBindingRequest request) {
        return ApiResponse.success(workflowBindingService.create(request));
    }

    @PutMapping("/{bindingId}")
    public ApiResponse<WorkflowBindingMutationResponse> update(
            @PathVariable String bindingId,
            @Valid @RequestBody SaveWorkflowBindingRequest request
    ) {
        return ApiResponse.success(workflowBindingService.update(bindingId, request));
    }
}
