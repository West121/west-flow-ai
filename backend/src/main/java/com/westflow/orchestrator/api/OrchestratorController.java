package com.westflow.orchestrator.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.orchestrator.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编排器手动扫描接口。
 */
@RestController
@RequestMapping("/api/v1/orchestrator")
@SaCheckLogin
@RequiredArgsConstructor
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    /**
     * 手动扫描当前编排运行时。
     *
     * @return 扫描结果汇总
     */
    @PostMapping("/scans/manual")
    public ApiResponse<OrchestratorManualScanResponse> manualScan() {
        return ApiResponse.success(orchestratorService.manualScan());
    }
}
