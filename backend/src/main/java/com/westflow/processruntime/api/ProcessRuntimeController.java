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
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/approval-sheets/page")
    @SaCheckLogin
    public ApiResponse<PageResponse<ApprovalSheetListItemResponse>> approvalSheetPage(
            @Valid @RequestBody ApprovalSheetPageRequest request
    ) {
        return ApiResponse.success(processDemoService.pageApprovalSheets(request));
    }

    @GetMapping("/tasks/{taskId}")
    @SaCheckLogin
    public ApiResponse<ProcessTaskDetailResponse> detail(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.detail(taskId));
    }

    @GetMapping("/approval-sheets/by-business")
    @SaCheckLogin
    public ApiResponse<ProcessTaskDetailResponse> detailByBusiness(
            @RequestParam String businessType,
            @RequestParam String businessId
    ) {
        return ApiResponse.success(processDemoService.detailByBusiness(businessType, businessId));
    }

    @GetMapping("/tasks/{taskId}/actions")
    @SaCheckLogin
    public ApiResponse<TaskActionAvailabilityResponse> actions(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.actions(taskId));
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> addSign(
            @PathVariable String taskId,
            @Valid @RequestBody AddSignTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.addSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/remove-sign")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> removeSign(
            @PathVariable String taskId,
            @Valid @RequestBody RemoveSignTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.removeSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/revoke")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> revoke(
            @PathVariable String taskId,
            @Valid @RequestBody RevokeTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.revoke(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/urge")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> urge(
            @PathVariable String taskId,
            @Valid @RequestBody UrgeTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.urge(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/read")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> read(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.read(taskId));
    }

    @PostMapping("/tasks/{taskId}/reject")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> reject(
            @PathVariable String taskId,
            @Valid @RequestBody RejectTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.reject(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/jump")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> jump(
            @PathVariable String taskId,
            @Valid @RequestBody JumpTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.jump(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/take-back")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> takeBack(
            @PathVariable String taskId,
            @RequestBody(required = false) TakeBackTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.takeBack(taskId, request == null ? new TakeBackTaskRequest(null) : request));
    }

    @PostMapping("/instances/{instanceId}/wake-up")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> wakeUp(
            @PathVariable String instanceId,
            @Valid @RequestBody WakeUpInstanceRequest request
    ) {
        return ApiResponse.success(processDemoService.wakeUp(instanceId, request));
    }

    @PostMapping("/tasks/{taskId}/delegate")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> delegate(
            @PathVariable String taskId,
            @Valid @RequestBody DelegateTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.delegate(taskId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover")
    @SaCheckLogin
    public ApiResponse<CompleteTaskResponse> handover(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.handover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/preview")
    @SaCheckLogin
    public ApiResponse<HandoverPreviewResponse> handoverPreview(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.previewHandover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/execute")
    @SaCheckLogin
    public ApiResponse<HandoverExecutionResponse> handoverExecute(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.executeHandover(sourceUserId, request));
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
