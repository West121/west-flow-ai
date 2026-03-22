package com.westflow.processruntime.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.service.ProcessDemoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping("/tasks/page")
    @SaCheckLogin
    public ApiResponse<PageResponse<ProcessTaskListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(processDemoService.page(request));
    }

    @GetMapping("/tasks/{taskId}")
    @SaCheckLogin
    public ApiResponse<ProcessTaskDetailResponse> detail(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.detail(taskId));
    }

    @GetMapping("/tasks/{taskId}/actions")
    @SaCheckLogin
    public ApiResponse<TaskActionAvailabilityResponse> actions(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.actions(taskId));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @SaCheckLogin
    public ApiResponse<ClaimTaskResponse> claim(
            @PathVariable String taskId,
            @RequestBody(required = false) ClaimTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.claim(taskId, request == null ? new ClaimTaskRequest(null) : request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> complete(
            @PathVariable String taskId,
            @Valid @RequestBody CompleteTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.complete(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> transfer(
            @PathVariable String taskId,
            @Valid @RequestBody TransferTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.transfer(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/return")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> returnTask(
            @PathVariable String taskId,
            @RequestBody(required = false) ReturnTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.returnToPrevious(taskId, request == null ? new ReturnTaskRequest(null, null) : request));
    }
}
