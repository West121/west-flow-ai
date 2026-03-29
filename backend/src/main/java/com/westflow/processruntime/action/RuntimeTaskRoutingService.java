package com.westflow.processruntime.action;

import com.westflow.processruntime.api.request.BatchTaskActionRequest;
import com.westflow.processruntime.api.request.JumpTaskRequest;
import com.westflow.processruntime.api.request.RejectTaskRequest;
import com.westflow.processruntime.api.request.ReturnTaskRequest;
import com.westflow.processruntime.api.response.BatchTaskActionResponse;
import com.westflow.processruntime.api.response.CompleteTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskRoutingService {

    private final RuntimeTaskJumpRoutingService runtimeTaskJumpRoutingService;
    private final RuntimeTaskRejectRoutingService runtimeTaskRejectRoutingService;

    public CompleteTaskResponse jump(String taskId, JumpTaskRequest request) {
        return runtimeTaskJumpRoutingService.jump(taskId, request);
    }

    public CompleteTaskResponse returnToPrevious(String taskId, ReturnTaskRequest request) {
        return runtimeTaskRejectRoutingService.returnToPrevious(taskId, request);
    }

    public CompleteTaskResponse reject(String taskId, RejectTaskRequest request) {
        return runtimeTaskRejectRoutingService.reject(taskId, request);
    }

    public BatchTaskActionResponse batchReject(BatchTaskActionRequest request) {
        return runtimeTaskRejectRoutingService.batchReject(request);
    }
}
