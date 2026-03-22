package com.westflow.processruntime.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.ProcessDemoService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/process-runtime", "/api/v1/process-runtime/demo"})
// 流程运行入口；当前逐步从 demo 语义迁移到真实 Flowable 路径。
public class ProcessRuntimeController {

    private final ProcessDemoService processDemoService;
    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    public ProcessRuntimeController(
            ProcessDemoService processDemoService,
            FlowableProcessRuntimeService flowableProcessRuntimeService
    ) {
        this.processDemoService = processDemoService;
        this.flowableProcessRuntimeService = flowableProcessRuntimeService;
    }

    @PostMapping("/start")
    @SaCheckLogin
    // 发起一个新的流程实例。
    public ApiResponse<StartProcessResponse> start(
            @Valid @RequestBody StartProcessRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.start(request)
                : flowableProcessRuntimeService.start(request));
    }

    @PostMapping("/tasks/page")
    @SaCheckLogin
    // 分页查询流程任务列表。
    public ApiResponse<PageResponse<ProcessTaskListItemResponse>> page(
            @Valid @RequestBody PageRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.page(request)
                : flowableProcessRuntimeService.page(request));
    }

    @PostMapping("/approval-sheets/page")
    @SaCheckLogin
    // 分页查询审批单列表。
    public ApiResponse<PageResponse<ApprovalSheetListItemResponse>> approvalSheetPage(
            @Valid @RequestBody ApprovalSheetPageRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.pageApprovalSheets(request)
                : flowableProcessRuntimeService.pageApprovalSheets(request));
    }

    @GetMapping("/tasks/{taskId}")
    @SaCheckLogin
    // 查询任务详情。
    public ApiResponse<ProcessTaskDetailResponse> detail(
            @PathVariable String taskId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.detail(taskId)
                : flowableProcessRuntimeService.detail(taskId));
    }

    @GetMapping("/approval-sheets/by-business")
    @SaCheckLogin
    // 按业务类型和业务主键查询审批单详情。
    public ApiResponse<ProcessTaskDetailResponse> detailByBusiness(
            @RequestParam String businessType,
            @RequestParam String businessId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.detailByBusiness(businessType, businessId)
                : flowableProcessRuntimeService.detailByBusiness(businessType, businessId));
    }

    @GetMapping("/tasks/{taskId}/actions")
    @SaCheckLogin
    // 查询任务可执行动作。
    public ApiResponse<TaskActionAvailabilityResponse> actions(
            @PathVariable String taskId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.actions(taskId)
                : flowableProcessRuntimeService.actions(taskId));
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    @SaCheckLogin
    // 为任务增加会签处理人。
    public ApiResponse<CompleteTaskResponse> addSign(
            @PathVariable String taskId,
            @Valid @RequestBody AddSignTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.addSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/remove-sign")
    @SaCheckLogin
    // 移除加签任务。
    public ApiResponse<CompleteTaskResponse> removeSign(
            @PathVariable String taskId,
            @Valid @RequestBody RemoveSignTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.removeSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/revoke")
    @SaCheckLogin
    // 撤回当前任务。
    public ApiResponse<CompleteTaskResponse> revoke(
            @PathVariable String taskId,
            @Valid @RequestBody RevokeTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.revoke(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/urge")
    @SaCheckLogin
    // 催办当前任务。
    public ApiResponse<CompleteTaskResponse> urge(
            @PathVariable String taskId,
            @Valid @RequestBody UrgeTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.urge(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/read")
    @SaCheckLogin
    // 将任务标记为已读。
    public ApiResponse<CompleteTaskResponse> read(@PathVariable String taskId) {
        return ApiResponse.success(processDemoService.read(taskId));
    }

    @PostMapping("/tasks/{taskId}/reject")
    @SaCheckLogin
    // 按规则驳回任务。
    public ApiResponse<CompleteTaskResponse> reject(
            @PathVariable String taskId,
            @Valid @RequestBody RejectTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.reject(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/jump")
    @SaCheckLogin
    // 跳转任务到指定节点。
    public ApiResponse<CompleteTaskResponse> jump(
            @PathVariable String taskId,
            @Valid @RequestBody JumpTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.jump(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/take-back")
    @SaCheckLogin
    // 取回未处理任务。
    public ApiResponse<CompleteTaskResponse> takeBack(
            @PathVariable String taskId,
            @RequestBody(required = false) TakeBackTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.takeBack(taskId, request == null ? new TakeBackTaskRequest(null) : request));
    }

    @PostMapping("/instances/{instanceId}/wake-up")
    @SaCheckLogin
    // 唤醒挂起实例。
    public ApiResponse<CompleteTaskResponse> wakeUp(
            @PathVariable String instanceId,
            @Valid @RequestBody WakeUpInstanceRequest request
    ) {
        return ApiResponse.success(processDemoService.wakeUp(instanceId, request));
    }

    @PostMapping("/tasks/{taskId}/delegate")
    @SaCheckLogin
    // 委派任务给其他用户。
    public ApiResponse<CompleteTaskResponse> delegate(
            @PathVariable String taskId,
            @Valid @RequestBody DelegateTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.delegate(taskId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover")
    @SaCheckLogin
    // 发起离职转办。
    public ApiResponse<CompleteTaskResponse> handover(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.handover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/preview")
    @SaCheckLogin
    // 预览离职转办影响范围。
    public ApiResponse<HandoverPreviewResponse> handoverPreview(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.previewHandover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/execute")
    @SaCheckLogin
    // 执行离职转办。
    public ApiResponse<HandoverExecutionResponse> handoverExecute(
            @PathVariable String sourceUserId,
            @Valid @RequestBody HandoverTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.executeHandover(sourceUserId, request));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @SaCheckLogin
    // 认领待办任务。
    public ApiResponse<ClaimTaskResponse> claim(
            @PathVariable String taskId,
            @RequestBody(required = false) ClaimTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.claim(taskId, request == null ? new ClaimTaskRequest(null) : request)
                : flowableProcessRuntimeService.claim(taskId, request == null ? new ClaimTaskRequest(null) : request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @SaCheckLogin
    // 完成任务。
    public ApiResponse<CompleteTaskResponse> complete(
            @PathVariable String taskId,
            @Valid @RequestBody CompleteTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(isDemoPath(servletRequest)
                ? processDemoService.complete(taskId, request)
                : flowableProcessRuntimeService.complete(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @SaCheckLogin
    // 转交任务。
    public ApiResponse<CompleteTaskResponse> transfer(
            @PathVariable String taskId,
            @Valid @RequestBody TransferTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.transfer(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/return")
    @SaCheckLogin
    // 退回到上一节点。
    public ApiResponse<CompleteTaskResponse> returnTask(
            @PathVariable String taskId,
            @RequestBody(required = false) ReturnTaskRequest request
    ) {
        return ApiResponse.success(processDemoService.returnToPrevious(taskId, request == null ? new ReturnTaskRequest(null, null) : request));
    }

    // 通过路径区分旧 demo 运行态与新真实 Flowable 运行态。
    private boolean isDemoPath(HttpServletRequest servletRequest) {
        return servletRequest.getRequestURI().contains("/process-runtime/demo");
    }
}
