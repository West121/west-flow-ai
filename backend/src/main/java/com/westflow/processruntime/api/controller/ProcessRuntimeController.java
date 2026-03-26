package com.westflow.processruntime.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.api.request.*;
import com.westflow.processruntime.api.response.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-runtime")
// 流程运行入口，统一收口到真实 Flowable 运行态。
public class ProcessRuntimeController {

    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    public ProcessRuntimeController(FlowableProcessRuntimeService flowableProcessRuntimeService) {
        this.flowableProcessRuntimeService = flowableProcessRuntimeService;
    }

    @PostMapping("/start")
    @SaCheckLogin
    // 发起一个新的流程实例。
    public ApiResponse<StartProcessResponse> start(@Valid @RequestBody StartProcessRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.start(request));
    }

    @PostMapping("/tasks/page")
    @SaCheckLogin
    // 分页查询流程任务列表。
    public ApiResponse<PageResponse<ProcessTaskListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.page(request));
    }

    @GetMapping("/dashboard/summary")
    @SaCheckLogin
    // 查询工作台首页概览统计。
    public ApiResponse<WorkbenchDashboardSummaryResponse> dashboardSummary() {
        return ApiResponse.success(flowableProcessRuntimeService.dashboardSummary());
    }

    @PostMapping("/approval-sheets/page")
    @SaCheckLogin
    // 分页查询审批单列表。
    public ApiResponse<PageResponse<ApprovalSheetListItemResponse>> approvalSheetPage(@Valid @RequestBody ApprovalSheetPageRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.pageApprovalSheets(request));
    }

    @GetMapping("/tasks/{taskId}")
    @SaCheckLogin
    // 查询任务详情。
    public ApiResponse<ProcessTaskDetailResponse> detail(@PathVariable String taskId) {
        return ApiResponse.success(flowableProcessRuntimeService.detail(taskId));
    }

    @GetMapping("/approval-sheets/by-business")
    @SaCheckLogin
    // 按业务类型和业务主键查询审批单详情。
    public ApiResponse<ProcessTaskDetailResponse> detailByBusiness(
            @RequestParam String businessType,
            @RequestParam String businessId
    ) {
        return ApiResponse.success(flowableProcessRuntimeService.detailByBusiness(businessType, businessId));
    }

    @GetMapping("/instances/{instanceId}/task-groups")
    @SaCheckLogin
    // 查询流程实例的会签任务组进度。
    public ApiResponse<java.util.List<CountersignTaskGroupResponse>> taskGroups(@PathVariable String instanceId) {
        return ApiResponse.success(flowableProcessRuntimeService.taskGroups(instanceId));
    }

    @GetMapping("/instances/{instanceId}/links")
    @SaCheckLogin
    // 查询流程实例下挂载的主子流程关联。
    public ApiResponse<java.util.List<ProcessInstanceLinkResponse>> links(@PathVariable String instanceId) {
        return ApiResponse.success(flowableProcessRuntimeService.links(instanceId));
    }

    @PostMapping("/instances/{instanceId}/links/{linkId}/confirm-parent-resume")
    @SaCheckLogin
    // 父流程确认子流程完成，收口等待确认状态。
    public ApiResponse<ProcessInstanceLinkResponse> confirmParentResume(
            @PathVariable String instanceId,
            @PathVariable String linkId
    ) {
        return ApiResponse.success(flowableProcessRuntimeService.confirmParentResume(instanceId, linkId));
    }

    @GetMapping("/instances/{instanceId}/append-links")
    @SaCheckLogin
    // 查询流程实例下挂载的运行时追加与动态构建关联。
    public ApiResponse<java.util.List<RuntimeAppendLinkResponse>> appendLinks(@PathVariable String instanceId) {
        return ApiResponse.success(flowableProcessRuntimeService.appendLinks(instanceId));
    }

    @PostMapping("/instances/{instanceId}/terminate")
    @SaCheckLogin
    // 按指定作用域终止流程实例。
    public ApiResponse<CompleteTaskResponse> terminate(
            @PathVariable String instanceId,
            @Valid @RequestBody TerminateProcessInstanceRequest request
    ) {
        return ApiResponse.success(flowableProcessRuntimeService.terminate(instanceId, request));
    }

    @GetMapping("/tasks/{taskId}/actions")
    @SaCheckLogin
    // 查询任务可执行动作。
    public ApiResponse<TaskActionAvailabilityResponse> actions(@PathVariable String taskId) {
        return ApiResponse.success(flowableProcessRuntimeService.actions(taskId));
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    @SaCheckLogin
    // 为任务增加会签处理人。
    public ApiResponse<CompleteTaskResponse> addSign(@PathVariable String taskId, @Valid @RequestBody AddSignTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.addSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/remove-sign")
    @SaCheckLogin
    // 移除加签任务。
    public ApiResponse<CompleteTaskResponse> removeSign(@PathVariable String taskId, @Valid @RequestBody RemoveSignTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.removeSign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/revoke")
    @SaCheckLogin
    // 撤回当前任务。
    public ApiResponse<CompleteTaskResponse> revoke(@PathVariable String taskId, @Valid @RequestBody RevokeTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.revoke(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/urge")
    @SaCheckLogin
    // 催办当前任务。
    public ApiResponse<CompleteTaskResponse> urge(@PathVariable String taskId, @Valid @RequestBody UrgeTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.urge(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/read")
    @SaCheckLogin
    // 将任务标记为已读。
    public ApiResponse<CompleteTaskResponse> read(@PathVariable String taskId) {
        return ApiResponse.success(flowableProcessRuntimeService.read(taskId));
    }

    @PostMapping("/tasks/{taskId}/reject")
    @SaCheckLogin
    // 按规则驳回任务。
    public ApiResponse<CompleteTaskResponse> reject(@PathVariable String taskId, @Valid @RequestBody RejectTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.reject(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/jump")
    @SaCheckLogin
    // 跳转任务到指定节点。
    public ApiResponse<CompleteTaskResponse> jump(@PathVariable String taskId, @Valid @RequestBody JumpTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.jump(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/take-back")
    @SaCheckLogin
    // 取回未处理任务。
    public ApiResponse<CompleteTaskResponse> takeBack(@PathVariable String taskId, @RequestBody(required = false) TakeBackTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.takeBack(taskId, request == null ? new TakeBackTaskRequest(null) : request));
    }

    @PostMapping("/instances/{instanceId}/wake-up")
    @SaCheckLogin
    // 唤醒挂起实例。
    public ApiResponse<CompleteTaskResponse> wakeUp(@PathVariable String instanceId, @Valid @RequestBody WakeUpInstanceRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.wakeUp(instanceId, request));
    }

    @PostMapping("/tasks/{taskId}/delegate")
    @SaCheckLogin
    // 委派任务给其他用户。
    public ApiResponse<CompleteTaskResponse> delegate(@PathVariable String taskId, @Valid @RequestBody DelegateTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.delegate(taskId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover")
    @SaCheckLogin
    // 发起离职转办。
    public ApiResponse<CompleteTaskResponse> handover(@PathVariable String sourceUserId, @Valid @RequestBody HandoverTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.handover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/preview")
    @SaCheckLogin
    // 预览离职转办影响范围。
    public ApiResponse<HandoverPreviewResponse> handoverPreview(@PathVariable String sourceUserId, @Valid @RequestBody HandoverTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.previewHandover(sourceUserId, request));
    }

    @PostMapping("/users/{sourceUserId}/handover/execute")
    @SaCheckLogin
    // 执行离职转办。
    public ApiResponse<HandoverExecutionResponse> handoverExecute(@PathVariable String sourceUserId, @Valid @RequestBody HandoverTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.executeHandover(sourceUserId, request));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @SaCheckLogin
    // 认领待办任务。
    public ApiResponse<ClaimTaskResponse> claim(@PathVariable String taskId, @RequestBody(required = false) ClaimTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.claim(taskId, request == null ? new ClaimTaskRequest(null) : request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @SaCheckLogin
    // 完成任务。
    public ApiResponse<CompleteTaskResponse> complete(@PathVariable String taskId, @Valid @RequestBody CompleteTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.complete(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/append")
    @SaCheckLogin
    // 追加人工任务。
    public ApiResponse<AppendTaskResponse> appendTask(@PathVariable String taskId, @Valid @RequestBody AppendTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.appendTask(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/append-subprocess")
    @SaCheckLogin
    // 追加子流程。
    public ApiResponse<AppendTaskResponse> appendSubprocess(@PathVariable String taskId, @Valid @RequestBody AppendTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.appendSubprocess(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @SaCheckLogin
    // 转交任务。
    public ApiResponse<CompleteTaskResponse> transfer(@PathVariable String taskId, @Valid @RequestBody TransferTaskRequest request) {
        return ApiResponse.success(flowableProcessRuntimeService.transfer(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/return")
    @SaCheckLogin
    // 退回到上一节点。
    public ApiResponse<CompleteTaskResponse> returnTask(@PathVariable String taskId, @RequestBody(required = false) ReturnTaskRequest request) {
        ReturnTaskRequest payload = request == null ? new ReturnTaskRequest(null, null) : request;
        return ApiResponse.success(flowableProcessRuntimeService.returnToPrevious(taskId, payload));
    }
}
