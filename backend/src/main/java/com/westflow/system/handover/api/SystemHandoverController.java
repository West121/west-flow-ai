package com.westflow.system.handover.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.processruntime.api.HandoverExecutionResponse;
import com.westflow.processruntime.api.HandoverPreviewResponse;
import com.westflow.system.handover.service.SystemHandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统交接预览和执行接口。
 */
@RestController
@RequestMapping("/api/v1/system/handover")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemHandoverController {

    private final SystemHandoverService systemHandoverService;

    /**
     * 预览交接结果。
     */
    @PostMapping("/preview")
    public ApiResponse<HandoverPreviewResponse> preview(@Valid @RequestBody SystemHandoverRequest request) {
        // 先返回受影响任务范围，管理员确认后再执行真正的离职转办。
        return ApiResponse.success(systemHandoverService.preview(request));
    }

    /**
     * 执行交接。
     */
    @PostMapping("/execute")
    public ApiResponse<HandoverExecutionResponse> execute(@Valid @RequestBody SystemHandoverRequest request) {
        return ApiResponse.success(systemHandoverService.execute(request));
    }
}
