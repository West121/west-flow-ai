package com.westflow.system.handover.service;

import com.westflow.processruntime.api.response.HandoverExecutionResponse;
import com.westflow.processruntime.api.response.HandoverPreviewResponse;
import com.westflow.processruntime.api.request.HandoverTaskRequest;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.system.handover.api.SystemHandoverRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统交接预览和执行服务。
 */
@Service
@RequiredArgsConstructor
public class SystemHandoverService {

    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    /**
     * 预览交接范围。
     */
    public HandoverPreviewResponse preview(SystemHandoverRequest request) {
        // 系统管理页先预览，确认任务范围后再执行真正转办。
        return flowableProcessRuntimeService.previewHandover(
                request.sourceUserId(),
                new HandoverTaskRequest(request.targetUserId(), request.comment())
        );
    }

    /**
     * 执行交接。
     */
    public HandoverExecutionResponse execute(SystemHandoverRequest request) {
        return flowableProcessRuntimeService.executeHandover(
                request.sourceUserId(),
                new HandoverTaskRequest(request.targetUserId(), request.comment())
        );
    }
}
