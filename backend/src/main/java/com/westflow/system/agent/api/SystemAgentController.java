package com.westflow.system.agent.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.agent.service.SystemAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/agents")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemAgentController {

    private final SystemAgentService systemAgentService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemAgentListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        // 代理关系列表沿用统一分页契约，方便前端表格组件直接复用。
        return ApiResponse.success(systemAgentService.page(request));
    }

    @GetMapping("/{agentId}")
    public ApiResponse<SystemAgentDetailResponse> detail(@PathVariable String agentId) {
        return ApiResponse.success(systemAgentService.detail(agentId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemAgentFormOptionsResponse> options() {
        // 新建和编辑页共用一套选项接口，避免前端分别取人。
        return ApiResponse.success(systemAgentService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemAgentMutationResponse> create(@Valid @RequestBody SaveSystemAgentRequest request) {
        return ApiResponse.success(systemAgentService.create(request));
    }

    @PutMapping("/{agentId}")
    public ApiResponse<SystemAgentMutationResponse> update(
            @PathVariable String agentId,
            @Valid @RequestBody SaveSystemAgentRequest request
    ) {
        return ApiResponse.success(systemAgentService.update(agentId, request));
    }
}
