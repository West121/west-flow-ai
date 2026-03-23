package com.westflow.aiadmin.mcp.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.mcp.service.AiMcpRegistryService;
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
 * AI MCP 注册表管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/mcps")
@SaCheckLogin
@RequiredArgsConstructor
public class AiMcpController {

    private final AiMcpRegistryService aiMcpRegistryService;

    /**
     * 分页查询 MCP 注册表。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiMcpListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiMcpRegistryService.page(request));
    }

    /**
     * 查询 MCP 注册表详情。
     */
    @GetMapping("/{mcpId}")
    public ApiResponse<AiMcpDetailResponse> detail(@PathVariable String mcpId) {
        return ApiResponse.success(aiMcpRegistryService.detail(mcpId));
    }

    /**
     * 获取 MCP 表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<AiMcpFormOptionsResponse> options() {
        return ApiResponse.success(aiMcpRegistryService.formOptions());
    }

    /**
     * 新建 MCP 注册记录。
     */
    @PostMapping
    public ApiResponse<AiMcpMutationResponse> create(@Valid @RequestBody SaveAiMcpRequest request) {
        return ApiResponse.success(aiMcpRegistryService.create(request));
    }

    /**
     * 更新 MCP 注册记录。
     */
    @PutMapping("/{mcpId}")
    public ApiResponse<AiMcpMutationResponse> update(
            @PathVariable String mcpId,
            @Valid @RequestBody SaveAiMcpRequest request
    ) {
        return ApiResponse.success(aiMcpRegistryService.update(mcpId, request));
    }
}
