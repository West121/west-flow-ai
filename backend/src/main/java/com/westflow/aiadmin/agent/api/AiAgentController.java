package com.westflow.aiadmin.agent.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.agent.service.AiAgentRegistryService;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
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
 * AI 智能体注册表管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/agents")
@SaCheckLogin
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentRegistryService aiAgentRegistryService;

    /**
     * 分页查询智能体注册表。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiAgentListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiAgentRegistryService.page(request));
    }

    /**
     * 查询智能体注册表详情。
     */
    @GetMapping("/{agentId}")
    public ApiResponse<AiAgentDetailResponse> detail(@PathVariable String agentId) {
        return ApiResponse.success(aiAgentRegistryService.detail(agentId));
    }

    /**
     * 查询智能体表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<AiAgentFormOptionsResponse> options() {
        return ApiResponse.success(aiAgentRegistryService.formOptions());
    }

    /**
     * 新建智能体注册记录。
     */
    @PostMapping
    public ApiResponse<AiAgentMutationResponse> create(@Valid @RequestBody SaveAiAgentRequest request) {
        return ApiResponse.success(aiAgentRegistryService.create(request));
    }

    /**
     * 更新智能体注册记录。
     */
    @PutMapping("/{agentId}")
    public ApiResponse<AiAgentMutationResponse> update(
            @PathVariable String agentId,
            @Valid @RequestBody SaveAiAgentRequest request
    ) {
        return ApiResponse.success(aiAgentRegistryService.update(agentId, request));
    }
}
