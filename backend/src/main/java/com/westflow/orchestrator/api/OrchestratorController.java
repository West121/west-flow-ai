package com.westflow.orchestrator.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.orchestrator.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orchestrator")
@SaCheckLogin
@RequiredArgsConstructor
// 编排器的手动扫描入口。
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    @PostMapping("/scans/manual")
    public ApiResponse<OrchestratorManualScanResponse> manualScan() {
        // 手动扫描入口用于验证当前编排运行时，后续再挂定时调度。
        return ApiResponse.success(orchestratorService.manualScan());
    }
}
