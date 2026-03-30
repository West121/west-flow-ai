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
        // 流程实例标识
        String instanceId,
        // 节点标识
        String nodeId,
        // 节点名称
        String nodeName,
        // 审批模式
        String approvalMode,
        // 分组状态
        String groupStatus,
        // 总数
        int totalCount,
        // 完成数量。
        int completedCount,
        // active数量。
        int activeCount,
        // waiting数量。
        int waitingCount,
        // voteThresholdPercent。
        Integer voteThresholdPercent,
        // approvedWeight。
        Integer approvedWeight,
        // rejectedWeight。
        Integer rejectedWeight,
        // 决策状态
        String decisionStatus,
        // 成员列表
        List<CountersignTaskGroupMemberResponse> members
) {
}
