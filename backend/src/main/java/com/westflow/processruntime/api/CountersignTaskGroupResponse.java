package com.westflow.processruntime.api;

import java.util.List;

/**
 * 会签任务组快照，供任务详情和审批单详情直接展示当前进度。
 */
public record CountersignTaskGroupResponse(
        String groupId,
        String instanceId,
        String nodeId,
        String nodeName,
        String approvalMode,
        String groupStatus,
        int totalCount,
        int completedCount,
        int activeCount,
        int waitingCount,
        List<CountersignTaskGroupMemberResponse> members
) {
}
