package com.westflow.workflowadmin.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.workflowadmin.service.WorkflowManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程管理后台查询接口。
 */
@RestController
@RequestMapping("/api/v1/workflow-management")
@SaCheckLogin
@RequiredArgsConstructor
public class WorkflowManagementController {

    private final WorkflowManagementService workflowManagementService;

    @PostMapping("/versions/page")
    public ApiResponse<PageResponse<WorkflowVersionListItemResponse>> pageVersions(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(workflowManagementService.pageVersions(request));
    }

    @GetMapping("/versions/{processDefinitionId}")
    public ApiResponse<WorkflowVersionDetailResponse> versionDetail(@PathVariable String processDefinitionId) {
        return ApiResponse.success(workflowManagementService.versionDetail(processDefinitionId));
    }

    @PostMapping("/publish-records/page")
    public ApiResponse<PageResponse<WorkflowPublishRecordListItemResponse>> pagePublishRecords(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(workflowManagementService.pagePublishRecords(request));
    }

    @GetMapping("/publish-records/{processDefinitionId}")
    public ApiResponse<WorkflowPublishRecordDetailResponse> publishRecordDetail(@PathVariable String processDefinitionId) {
        return ApiResponse.success(workflowManagementService.publishRecordDetail(processDefinitionId));
    }

    @PostMapping("/instances/page")
    public ApiResponse<PageResponse<WorkflowInstanceListItemResponse>> pageInstances(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(workflowManagementService.pageInstances(request));
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<WorkflowInstanceDetailResponse> instanceDetail(@PathVariable String instanceId) {
        return ApiResponse.success(workflowManagementService.instanceDetail(instanceId));
    }

    @PostMapping("/operation-logs/page")
    public ApiResponse<PageResponse<WorkflowOperationLogListItemResponse>> pageOperationLogs(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(workflowManagementService.pageOperationLogs(request));
    }

    @GetMapping("/operation-logs/{logId}")
    public ApiResponse<WorkflowOperationLogDetailResponse> operationLogDetail(@PathVariable String logId) {
        return ApiResponse.success(workflowManagementService.operationLogDetail(logId));
    }
}
