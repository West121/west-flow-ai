package com.westflow.system.handover.service;

import com.westflow.processruntime.api.HandoverExecutionResponse;
import com.westflow.processruntime.api.HandoverPreviewResponse;
import com.westflow.processruntime.api.HandoverTaskRequest;
import com.westflow.processruntime.service.ProcessDemoService;
import com.westflow.system.handover.api.SystemHandoverRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemHandoverService {

    private final ProcessDemoService processDemoService;

    public HandoverPreviewResponse preview(SystemHandoverRequest request) {
        // 系统管理页先预览，确认任务范围后再执行真正转办。
        return processDemoService.previewHandover(
                request.sourceUserId(),
                new HandoverTaskRequest(request.targetUserId(), request.comment())
        );
    }

    public HandoverExecutionResponse execute(SystemHandoverRequest request) {
        return processDemoService.executeHandover(
                request.sourceUserId(),
                new HandoverTaskRequest(request.targetUserId(), request.comment())
        );
    }
}
