package com.westflow.processruntime.action;

import com.westflow.processruntime.api.request.AddSignTaskRequest;
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
import com.westflow.processruntime.api.response.AppendTaskResponse;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.ClaimTaskResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import com.westflow.processruntime.api.response.HandoverExecutionResponse;
import com.westflow.processruntime.api.response.HandoverPreviewResponse;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.api.response.TaskActionAvailabilityResponse;
import com.westflow.processruntime.link.RuntimeBusinessLinkService;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessCommandFacadeService {

    private final RuntimeActionSupportService actionSupportService;
    private final RuntimeProcessActionSupportService processActionSupportService;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final RuntimeAppendActionService runtimeAppendActionService;
    private final RuntimeInstanceLifecycleService runtimeInstanceLifecycleService;
    private final RuntimeHandoverService runtimeHandoverService;
    private final RuntimeTaskActionAvailabilityService runtimeTaskActionAvailabilityService;
    private final RuntimeTaskMutationService runtimeTaskMutationService;
    private final RuntimeTaskRoutingService runtimeTaskRoutingService;
    private final RuntimeBusinessLinkService runtimeBusinessLinkService;

    public StartProcessResponse start(StartProcessRequest request) {
        String effectiveBusinessType = processActionSupportService.resolveStartBusinessType(request);
        StartProcessResponse response = flowableRuntimeStartService.start(request);
        if (request.businessKey() != null && !request.businessKey().isBlank()) {
            syncBusinessProcessLinkOnStart(request, response, effectiveBusinessType);
            processActionSupportService.updateBusinessProcessLink(effectiveBusinessType, request.businessKey(), response.instanceId(), response.status());
        }
        return response;
    }

    public CompleteTaskResponse terminate(String instanceId, TerminateProcessInstanceRequest request) {
        return runtimeInstanceLifecycleService.terminate(instanceId, request);
    }

    public TaskActionAvailabilityResponse actions(String taskId) {
        return runtimeTaskActionAvailabilityService.actions(taskId);
    }

    public CompleteTaskResponse addSign(String taskId, AddSignTaskRequest request) {
        return runtimeAppendActionService.addSign(taskId, request);
    }

    public AppendTaskResponse appendTask(String taskId, AppendTaskRequest request) {
        return runtimeAppendActionService.appendTask(taskId, request);
    }

    public AppendTaskResponse appendSubprocess(String taskId, AppendTaskRequest request) {
        return runtimeAppendActionService.appendSubprocess(taskId, request);
    }

    public CompleteTaskResponse removeSign(String taskId, RemoveSignTaskRequest request) {
        return runtimeAppendActionService.removeSign(taskId, request);
    }

    public CompleteTaskResponse read(String taskId) {
        return runtimeTaskMutationService.read(taskId);
    }

    public BatchTaskActionResponse batchRead(BatchTaskActionRequest request) {
        return runtimeTaskMutationService.batchRead(request);
    }

    public ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        return runtimeTaskMutationService.claim(taskId, request);
    }

    public BatchTaskActionResponse batchClaim(BatchTaskActionRequest request) {
        return runtimeTaskMutationService.batchClaim(request);
    }

    public CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        return runtimeTaskMutationService.complete(taskId, request);
    }

    public CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        return runtimeTaskMutationService.transfer(taskId, request);
    }

    public CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        return runtimeTaskMutationService.delegate(taskId, request);
    }

    public CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        return runtimeTaskMutationService.takeBack(taskId, request);
    }

    public BatchTaskActionResponse batchComplete(BatchTaskActionRequest request) {
        return runtimeTaskMutationService.batchComplete(request);
    }

    public CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        return runtimeTaskMutationService.revoke(taskId, request);
    }

    public CompleteTaskResponse wakeUp(String instanceId, WakeUpInstanceRequest request) {
        return runtimeInstanceLifecycleService.wakeUp(instanceId, request);
    }

    public CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        return runtimeTaskMutationService.urge(taskId, request);
    }

    public CompleteTaskResponse handover(String sourceUserId, HandoverTaskRequest request) {
        return runtimeHandoverService.handover(sourceUserId, request);
    }

    public HandoverPreviewResponse previewHandover(String sourceUserId, HandoverTaskRequest request) {
        return runtimeHandoverService.previewHandover(sourceUserId, request);
    }

    public HandoverExecutionResponse executeHandover(String sourceUserId, HandoverTaskRequest request) {
        return runtimeHandoverService.executeHandover(sourceUserId, request);
    }

    public CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        return runtimeTaskRoutingService.jump(taskId, request);
    }

    public CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        return runtimeTaskRoutingService.returnToPrevious(taskId, request);
    }

    public CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        return runtimeTaskRoutingService.reject(taskId, request);
    }

    public BatchTaskActionResponse batchReject(BatchTaskActionRequest request) {
        return runtimeTaskRoutingService.batchReject(request);
    }

    private void syncBusinessProcessLinkOnStart(StartProcessRequest request, StartProcessResponse response, String businessType) {
        Optional<com.westflow.processruntime.link.BusinessLinkSnapshot> existingLink = runtimeBusinessLinkService.findByBusiness(businessType, request.businessKey());
        if (existingLink.isPresent()
                && Objects.equals(existingLink.get().processInstanceId(), response.instanceId())
                && Objects.equals(existingLink.get().processDefinitionId(), response.processDefinitionId())
                && Objects.equals(existingLink.get().startUserId(), actionSupportService.currentUserId())
                && Objects.equals(existingLink.get().status(), response.status())) {
            return;
        }
        runtimeBusinessLinkService.insertLink(
                businessType,
                request.businessKey(),
                response.instanceId(),
                response.processDefinitionId(),
                actionSupportService.currentUserId(),
                response.status()
        );
    }
}
