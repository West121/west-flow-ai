package com.westflow.processruntime.termination.api;

import com.westflow.common.api.ApiResponse;
import com.westflow.processruntime.termination.model.ProcessTerminationCommand;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import com.westflow.processruntime.termination.service.ProcessTerminationStrategyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 终止高级策略对外入口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/process-runtime/termination")
public class ProcessTerminationController {

    private final ProcessTerminationStrategyService processTerminationStrategyService;

    @PostMapping("/preview")
    public ApiResponse<ProcessTerminationPlanResponse> preview(@Valid @RequestBody ProcessTerminationRequest request) {
        return ApiResponse.success(processTerminationStrategyService.preview(toCommand(request)));
    }

    @PostMapping("/snapshot")
    public ApiResponse<ProcessTerminationSnapshotResponse> snapshot(@Valid @RequestBody ProcessTerminationRequest request) {
        return ApiResponse.success(processTerminationStrategyService.snapshot(toCommand(request)));
    }

    @GetMapping("/audit-trail")
    public ApiResponse<List<ProcessTerminationAuditResponse>> auditTrail(@RequestParam String rootInstanceId) {
        return ApiResponse.success(processTerminationStrategyService.listAuditTrail(rootInstanceId));
    }

    private ProcessTerminationCommand toCommand(ProcessTerminationRequest request) {
        return new ProcessTerminationCommand(
                request.rootInstanceId(),
                request.targetInstanceId(),
                ProcessTerminationScope.from(request.scope()),
                ProcessTerminationPropagationPolicy.from(request.propagationPolicy()),
                request.reason(),
                request.operatorUserId()
        );
    }
}
