package com.westflow.processruntime.api;

/**
 * 会签任务组成员快照。
 */
public record CountersignTaskGroupMemberResponse(
        String memberId,
        String taskId,
        String assigneeUserId,
        int sequenceNo,
        String memberStatus
) {
}
