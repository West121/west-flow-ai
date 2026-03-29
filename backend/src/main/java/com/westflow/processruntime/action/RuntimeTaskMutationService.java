package com.westflow.processruntime.action;

import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.ClaimTaskRequest;
import com.westflow.processruntime.api.request.CompleteTaskRequest;
import com.westflow.processruntime.api.request.DelegateTaskRequest;
import com.westflow.processruntime.api.request.RevokeTaskRequest;
import com.westflow.processruntime.api.request.TakeBackTaskRequest;
import com.westflow.processruntime.api.request.TransferTaskRequest;
import com.westflow.processruntime.api.request.UrgeTaskRequest;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.ClaimTaskResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskMutationService {

    private final RuntimeTaskClaimReadService runtimeTaskClaimReadService;
    private final RuntimeTaskExecutionService runtimeTaskExecutionService;
    private final RuntimeTaskControlService runtimeTaskControlService;

    public CompleteTaskResponse read(String taskId) {
        return runtimeTaskClaimReadService.read(taskId);
    }

    public BatchTaskActionResponse batchRead(BatchTaskActionRequest request) {
        return runtimeTaskClaimReadService.batchRead(request);
    }

    public ClaimTaskResponse claim(String taskId, ClaimTaskRequest request) {
        return runtimeTaskClaimReadService.claim(taskId, request);
    }

    public BatchTaskActionResponse batchClaim(BatchTaskActionRequest request) {
        return runtimeTaskClaimReadService.batchClaim(request);
    }

    public CompleteTaskResponse complete(String taskId, CompleteTaskRequest request) {
        return runtimeTaskExecutionService.complete(taskId, request);
    }

    public CompleteTaskResponse transfer(String taskId, TransferTaskRequest request) {
        return runtimeTaskExecutionService.transfer(taskId, request);
    }

    public CompleteTaskResponse delegate(String taskId, DelegateTaskRequest request) {
        return runtimeTaskExecutionService.delegate(taskId, request);
    }

    public CompleteTaskResponse takeBack(String taskId, TakeBackTaskRequest request) {
        return runtimeTaskControlService.takeBack(taskId, request);
    }

    public BatchTaskActionResponse batchComplete(BatchTaskActionRequest request) {
        return runtimeTaskExecutionService.batchComplete(request);
    }

    public CompleteTaskResponse revoke(String taskId, RevokeTaskRequest request) {
        return runtimeTaskControlService.revoke(taskId, request);
    }

    public CompleteTaskResponse urge(String taskId, UrgeTaskRequest request) {
        return runtimeTaskControlService.urge(taskId, request);
    }
}
