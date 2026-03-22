package com.westflow.system.monitor.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.monitor.api.response.NotificationChannelHealthDetailResponse;
import com.westflow.system.monitor.api.response.NotificationChannelHealthListItemResponse;
import com.westflow.system.monitor.api.response.OrchestratorScanDetailResponse;
import com.westflow.system.monitor.api.response.OrchestratorScanListItemResponse;
import com.westflow.system.monitor.api.response.TriggerExecutionDetailResponse;
import com.westflow.system.monitor.api.response.TriggerExecutionListItemResponse;
import com.westflow.system.monitor.service.SystemMonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/monitor")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    @PostMapping("/orchestrator-scans/page")
    public ApiResponse<PageResponse<OrchestratorScanListItemResponse>> pageOrchestratorScans(
            @Valid @RequestBody PageRequest request
    ) {
        return ApiResponse.success(systemMonitorService.pageOrchestratorScans(request));
    }

    @GetMapping("/orchestrator-scans/{executionId}")
    public ApiResponse<OrchestratorScanDetailResponse> detailOrchestratorScan(@PathVariable String executionId) {
        return ApiResponse.success(systemMonitorService.detailOrchestratorScan(executionId));
    }

    @PostMapping("/trigger-executions/page")
    public ApiResponse<PageResponse<TriggerExecutionListItemResponse>> pageTriggerExecutions(
            @Valid @RequestBody PageRequest request
    ) {
        return ApiResponse.success(systemMonitorService.pageTriggerExecutions(request));
    }

    @GetMapping("/trigger-executions/{executionId}")
    public ApiResponse<TriggerExecutionDetailResponse> detailTriggerExecution(@PathVariable String executionId) {
        return ApiResponse.success(systemMonitorService.detailTriggerExecution(executionId));
    }

    @PostMapping("/notification-channels/health/page")
    public ApiResponse<PageResponse<NotificationChannelHealthListItemResponse>> pageNotificationChannelHealths(
            @Valid @RequestBody PageRequest request
    ) {
        return ApiResponse.success(systemMonitorService.pageNotificationChannelHealths(request));
    }

    @GetMapping("/notification-channels/health/{channelId}")
    public ApiResponse<NotificationChannelHealthDetailResponse> detailNotificationChannelHealth(@PathVariable String channelId) {
        return ApiResponse.success(systemMonitorService.detailNotificationChannelHealth(channelId));
    }
}
