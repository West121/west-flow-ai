package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.westflow.processdef.model.ProcessDslPayload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
// 任务详情返回值，包含流程、任务、轨迹和表单数据。
public record ProcessTaskDetailResponse(
        String taskId,
        String instanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String businessType,
        String applicantUserId,
        Map<String, Object> businessData,
        String automationStatus,
        List<ProcessDslPayload.Node> flowNodes,
        List<ProcessDslPayload.Edge> flowEdges,
        List<ProcessInstanceEventResponse> instanceEvents,
        List<ProcessTaskTraceItemResponse> taskTrace,
        List<ProcessAutomationTraceItemResponse> automationActionTrace,
        List<ProcessNotificationSendRecordResponse> notificationSendRecords,
        String nodeId,
        String nodeName,
        String taskKind,
        String status,
        String assignmentMode,
        List<String> candidateUserIds,
        String assigneeUserId,
        String action,
        String operatorUserId,
        String comment,
        OffsetDateTime receiveTime,
        OffsetDateTime readTime,
        OffsetDateTime handleStartTime,
        OffsetDateTime handleEndTime,
        Long handleDurationSeconds,
        String targetStrategy,
        String targetNodeId,
        String targetNodeName,
        String reapproveStrategy,
        String actingMode,
        String actingForUserId,
        String delegatedByUserId,
        String handoverFromUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        String instanceStatus,
        String processFormKey,
        String processFormVersion,
        String nodeFormKey,
        String nodeFormVersion,
        String effectiveFormKey,
        String effectiveFormVersion,
        List<WorkflowFieldBinding> fieldBindings,
        Map<String, Object> formData,
        Map<String, Object> taskFormData,
        List<CountersignTaskGroupResponse> countersignGroups,
        List<ProcessInstanceLinkResponse> processLinks,
        List<String> activeTaskIds
) {
}
