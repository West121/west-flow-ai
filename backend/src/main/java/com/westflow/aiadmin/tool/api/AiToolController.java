package com.westflow.aiadmin.tool.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.tool.service.AiToolRegistryService;
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
 * AI 工具注册表管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/tools")
@SaCheckLogin
@RequiredArgsConstructor
public class AiToolController {

    private final AiToolRegistryService aiToolRegistryService;

    /**
     * 分页查询工具注册表。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiToolListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiToolRegistryService.page(request));
    }

    /**
     * 查询工具注册表详情。
     */
    @GetMapping("/{toolId}")
    public ApiResponse<AiToolDetailResponse> detail(@PathVariable String toolId) {
        return ApiResponse.success(aiToolRegistryService.detail(toolId));
    }

    /**
     * 获取工具表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<AiToolFormOptionsResponse> options() {
        return ApiResponse.success(aiToolRegistryService.formOptions());
    }

    /**
     * 新建工具注册记录。
     */
    @PostMapping
    public ApiResponse<AiToolMutationResponse> create(@Valid @RequestBody SaveAiToolRequest request) {
        return ApiResponse.success(aiToolRegistryService.create(request));
    }

    /**
     * 更新工具注册记录。
     */
    @PutMapping("/{toolId}")
    public ApiResponse<AiToolMutationResponse> update(
            @PathVariable String toolId,
            @Valid @RequestBody SaveAiToolRequest request
    ) {
        return ApiResponse.success(aiToolRegistryService.update(toolId, request));
    }
}
