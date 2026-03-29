package com.westflow.processruntime.service;

import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.action.RuntimeProcessCommandFacadeService;
import com.westflow.processruntime.api.request.AddSignTaskRequest;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.request.AppendTaskRequest;
import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.ClaimTaskRequest;
import com.westflow.processruntime.api.request.CompleteTaskRequest;
import com.westflow.processruntime.api.request.DelegateTaskRequest;
import com.westflow.processruntime.api.request.HandoverTaskRequest;
import com.westflow.processruntime.api.request.JumpTaskRequest;
import com.westflow.processruntime.api.request.RejectTaskRequest;
import com.westflow.processruntime.api.request.RemoveSignTaskRequest;
import com.westflow.processruntime.api.request.ReturnTaskRequest;
import com.westflow.processruntime.api.request.RevokeTaskRequest;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.request.TakeBackTaskRequest;
import com.westflow.processruntime.api.request.TerminateProcessInstanceRequest;
import com.westflow.processruntime.api.request.TransferTaskRequest;
import com.westflow.processruntime.api.request.UrgeTaskRequest;
import com.westflow.processruntime.api.request.WakeUpInstanceRequest;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.response.AppendTaskResponse;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.ClaimTaskResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.response.HandoverExecutionResponse;
import com.westflow.processruntime.api.response.HandoverPreviewResponse;
import com.westflow.processruntime.api.response.InclusiveGatewayHitResponse;
import com.westflow.processruntime.api.response.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.response.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.api.response.TaskActionAvailabilityResponse;
import com.westflow.processruntime.api.response.WorkbenchDashboardSummaryResponse;
import com.westflow.processruntime.query.RuntimeProcessQueryFacadeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableProcessRuntimeService {

    private final RuntimeProcessQueryFacadeService queryFacadeService;
    private final RuntimeProcessCommandFacadeService commandFacadeService;

    public StartProcessResponse start(StartProcessRequest request) {
        return commandFacadeService.start(request);
    }

    public PageResponse<ProcessTaskListItemResponse> page(PageRequest request) {
        return queryFacadeService.page(request);
    }

    public PageResponse<ApprovalSheetListItemResponse> pageApprovalSheets(ApprovalSheetPageRequest request) {
        return queryFacadeService.pageApprovalSheets(request);
    }

    public WorkbenchDashboardSummaryResponse dashboardSummary() {
        return queryFacadeService.dashboardSummary();
    }

    public ProcessTaskDetailResponse detail(String taskId) {
        return queryFacadeService.detail(taskId);
    }

    public ProcessTaskDetailResponse detailByBusiness(String businessType, String businessId) {
        return queryFacadeService.detailByBusiness(businessType, businessId);
    }

    public List<CountersignTaskGroupResponse> taskGroups(String instanceId) {
        return queryFacadeService.taskGroups(instanceId);
    }

    public List<InclusiveGatewayHitResponse> inclusiveGatewayHits(String instanceId) {
        return queryFacadeService.inclusiveGatewayHits(instanceId);
    }

    public List<ProcessInstanceLinkResponse> links(String instanceId) {
        return queryFacadeService.links(instanceId);
    }

    public ProcessInstanceLinkResponse confirmParentResume(String instanceId, String linkId) {
        return queryFacadeService.confirmParentResume(instanceId, linkId);
    }

    public List<RuntimeAppendLinkResponse> appendLinks(String instanceId) {
        return queryFacadeService.appendLinks(instanceId);
    }

    public CompleteTaskResponse terminate(String instanceId, TerminateProcessInstanceRequest request) {
        return commandFacadeService.terminate(instanceId, request);
    }

    public TaskActionAvailabilityResponse actions(String taskId) {
        return commandFacadeService.actions(taskId);
    }

    public CompleteTaskResponse addSign(String taskId, AddSignTaskRequest request) {
        return commandFacadeService.addSign(taskId, request);
    }

    public CompleteTaskResponse removeSign(String taskId, RemoveSignTaskRequest request) {
        return commandFacadeService.removeSign(taskId, request);
    }

    public CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        return commandFacadeService.revoke(taskId, request);
    }

    public CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        return commandFacadeService.urge(taskId, request);
    }

    public CompleteTaskResponse read(String taskId) {
        return commandFacadeService.read(taskId);
    }

    public BatchTaskActionResponse batchRead(BatchTaskActionRequest request) {
        return commandFacadeService.batchRead(request);
    }

    public CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        return commandFacadeService.reject(taskId, request);
    }

    public BatchTaskActionResponse batchReject(BatchTaskActionRequest request) {
        return commandFacadeService.batchReject(request);
    }

    public CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        return commandFacadeService.jump(taskId, request);
    }

    public CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        return commandFacadeService.takeBack(taskId, request);
    }

    public CompleteTaskResponse wakeUp(String instanceId, WakeUpInstanceRequest request) {
        return commandFacadeService.wakeUp(instanceId, request);
    }

    public CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        return commandFacadeService.delegate(taskId, request);
    }

    public CompleteTaskResponse handover(String sourceUserId, HandoverTaskRequest request) {
        return commandFacadeService.handover(sourceUserId, request);
    }

    public HandoverPreviewResponse previewHandover(String sourceUserId, HandoverTaskRequest request) {
        return commandFacadeService.previewHandover(sourceUserId, request);
    }

    public HandoverExecutionResponse executeHandover(String sourceUserId, HandoverTaskRequest request) {
        return commandFacadeService.executeHandover(sourceUserId, request);
    }

    public ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        return commandFacadeService.claim(taskId, request);
    }

    public BatchTaskActionResponse batchClaim(BatchTaskActionRequest request) {
        return commandFacadeService.batchClaim(request);
    }

    public CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        return commandFacadeService.complete(taskId, request);
    }

    public BatchTaskActionResponse batchComplete(BatchTaskActionRequest request) {
        return commandFacadeService.batchComplete(request);
    }

    public AppendTaskResponse appendTask(String taskId, AppendTaskRequest request) {
        return commandFacadeService.appendTask(taskId, request);
    }

    public AppendTaskResponse appendSubprocess(String taskId, AppendTaskRequest request) {
        return commandFacadeService.appendSubprocess(taskId, request);
    }

    public CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        return commandFacadeService.transfer(taskId, request);
    }

    public CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        return commandFacadeService.returnToPrevious(taskId, request);
    }
}
