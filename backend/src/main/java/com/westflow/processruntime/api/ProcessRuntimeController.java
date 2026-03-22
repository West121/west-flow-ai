package com.westflow.processruntime.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.processruntime.service.ProcessDemoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-runtime/demo")
public class ProcessRuntimeController {

    private final ProcessDemoService processDemoService;

    public ProcessRuntimeController(ProcessDemoService processDemoService) {
        this.processDemoService = processDemoService;
    }

    @PostMapping("/start")
    @SaCheckLogin
    public ApiResponse<StartProcessResponse> start(@Valid @RequestBody StartProcessRequest request) {
        return ApiResponse.success(processDemoService.start(request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> complete(
            @PathVariable String taskId,
            @Valid @RequestBody CompleteTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.complete(taskId, request));
    }
}
