package com.westflow.processruntime.api.controller;

import com.westflow.common.api.ApiResponse;
import com.westflow.processruntime.api.request.*;
import com.westflow.processruntime.api.response.*;
import com.westflow.processruntime.timetravel.service.ProcessTimeTravelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 穿越时空入口。
 */
@RestController
@RequestMapping("/api/v1/process-runtime/time-travel")
@RequiredArgsConstructor
public class ProcessTimeTravelController {

    private final ProcessTimeTravelService processTimeTravelService;

    @PostMapping("/execute")
    public ApiResponse<ProcessTimeTravelExecutionResponse> execute(@RequestBody ExecuteProcessTimeTravelRequest request) {
        return ApiResponse.success(processTimeTravelService.execute(request));
    }

    @PostMapping("/executions/page")
    public ApiResponse<?> page(@RequestBody ProcessTimeTravelQueryRequest request) {
        return ApiResponse.success(processTimeTravelService.page(request));
    }

    @GetMapping("/instances/{instanceId}/trace")
    public ApiResponse<?> trace(@PathVariable String instanceId) {
        return ApiResponse.success(processTimeTravelService.trace(instanceId));
    }
}
