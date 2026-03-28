package com.westflow.processruntime.signature.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.signature.api.SignTaskRequest;
import com.westflow.processruntime.signature.api.TaskSignatureResponse;
import com.westflow.workflowadmin.service.WorkflowOperationLogService;
import com.westflow.processruntime.api.response.ProcessTaskDetailResponse;
import com.westflow.processruntime.api.response.TaskActionAvailabilityResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 电子签章服务。
 */
@Service
@RequiredArgsConstructor
public class TaskSignatureService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableProcessRuntimeService flowableProcessRuntimeService;
    private final WorkflowOperationLogService workflowOperationLogService;

    public TaskSignatureResponse sign(String taskId, SignTaskRequest request) {
        TaskActionAvailabilityResponse actions = flowableProcessRuntimeService.actions(taskId);
        if (!actions.canSign()) {
            throw new ContractException(
                    "BIZ.ACTION_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "当前任务不支持签章",
                    Map.of("taskId", taskId)
            );
        }

        ProcessTaskDetailResponse detail = flowableProcessRuntimeService.detail(taskId);
        String operatorUserId = StpUtil.getLoginIdAsString();
        OffsetDateTime signatureAt = OffsetDateTime.now(TIME_ZONE);
        String signatureComment = normalizeComment(request.signatureComment());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("signatureType", request.signatureType());
        details.put("signatureStatus", "SIGNED");
        details.put("signatureComment", signatureComment);
        details.put("signatureAt", signatureAt.toString());
        details.put("signatureTaskId", taskId);
        details.put("signatureInstanceId", detail.instanceId());
        details.put("signatureNodeId", detail.nodeId());
        details.put("signatureOperatorUserId", operatorUserId);

        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                detail.instanceId(),
                detail.processDefinitionId(),
                detail.processDefinitionId(),
                detail.businessType(),
                detail.businessKey(),
                taskId,
                detail.nodeId(),
                "SIGNATURE",
                "电子签章",
                "SIGNATURE",
                operatorUserId,
                detail.assigneeUserId(),
                taskId,
                taskId,
                signatureComment,
                details,
                signatureAt.toInstant()
        ));

        return new TaskSignatureResponse(
                taskId,
                detail.instanceId(),
                detail.nodeId(),
                request.signatureType(),
                "SIGNED",
                signatureComment,
                signatureAt,
                operatorUserId
        );
    }

    private String normalizeComment(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
