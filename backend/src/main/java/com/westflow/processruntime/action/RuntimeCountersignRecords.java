package com.westflow.processruntime.action;

import java.util.List;
import java.util.Map;

record CountersignNodeMetadata(
        String nodeId,
        String nodeName,
        String approvalMode,
        String reapprovePolicy,
        List<String> userIds,
        boolean autoFinishRemaining,
        Integer voteThresholdPercent,
        Map<String, Integer> voteWeights
) {
}

record TaskGroupRecord(
        String id,
        String processInstanceId,
        String nodeId,
        String nodeName,
        String approvalMode,
        String groupStatus
) {
}

record TaskGroupMemberRecord(
        String id,
        String taskGroupId,
        String processInstanceId,
        String nodeId,
        String taskId,
        String assigneeUserId,
        int sequenceNo,
        Integer voteWeight,
        String memberStatus
) {
}

record VoteSnapshotRecord(
        String id,
        String processInstanceId,
        String nodeId,
        int thresholdPercent,
        int totalWeight,
        int approvedWeight,
        int rejectedWeight,
        String decisionStatus
) {
}
