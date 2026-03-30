package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 会签任务组快照，供任务详情和审批单详情直接展示当前进度。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountersignTaskGroupResponse(
        // 用户组标识
        String groupId,
        String instanceId,
        // 节点标识
        String nodeId,
        String nodeName,
        // 审批模式
        String approvalMode,
        String groupStatus,
        // 总数
        int totalCount,
        int completedCount,
        // active数量。
        int activeCount,
        int waitingCount,
        // voteThresholdPercent。
        Integer voteThresholdPercent,
        Integer approvedWeight,
        // rejectedWeight。
        Integer rejectedWeight,
        String decisionStatus,
        List<CountersignTaskGroupMemberResponse> members
) {
}
