package com.westflow.processruntime.collaboration.api;

import com.westflow.common.api.ApiResponse;
import com.westflow.processruntime.collaboration.service.ProcessCollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协同事件入口。
 */
@RestController
@RequestMapping("/api/v1/process-runtime/collaboration")
@RequiredArgsConstructor
public class ProcessCollaborationController {

    private final ProcessCollaborationService processCollaborationService;

    @PostMapping("/events")
    public ApiResponse<ProcessCollaborationEventResponse> create(@RequestBody CreateProcessCollaborationEventRequest request) {
        return ApiResponse.success(processCollaborationService.createEvent(request));
    }

    @PostMapping("/events/page")
    public ApiResponse<?> page(@RequestBody ProcessCollaborationQueryRequest request) {
        return ApiResponse.success(processCollaborationService.page(request));
    }

    @GetMapping("/instances/{instanceId}/trace")
    public ApiResponse<?> trace(@PathVariable String instanceId) {
        return ApiResponse.success(processCollaborationService.trace(instanceId));
    }
}
