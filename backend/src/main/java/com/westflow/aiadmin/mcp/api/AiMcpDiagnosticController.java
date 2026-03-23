package com.westflow.aiadmin.mcp.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.mcp.service.AiMcpDiagnosticService;
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
 * AI MCP 连通性诊断接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/mcps/diagnostics")
@SaCheckLogin
@RequiredArgsConstructor
public class AiMcpDiagnosticController {

    private final AiMcpDiagnosticService aiMcpDiagnosticService;

    /**
     * 分页查询 MCP 连通性诊断。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiMcpDiagnosticResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiMcpDiagnosticService.page(request));
    }

    /**
     * 查询单个 MCP 的连通性诊断。
     */
    @GetMapping("/{mcpId}")
    public ApiResponse<AiMcpDiagnosticResponse> detail(@PathVariable String mcpId) {
        return ApiResponse.success(aiMcpDiagnosticService.detail(mcpId));
    }

    /**
     * 重新执行单个 MCP 的连通性诊断。
     */
    @PostMapping("/{mcpId}/recheck")
    public ApiResponse<AiMcpDiagnosticResponse> recheck(@PathVariable String mcpId) {
        return ApiResponse.success(aiMcpDiagnosticService.recheck(mcpId));
    }
}
