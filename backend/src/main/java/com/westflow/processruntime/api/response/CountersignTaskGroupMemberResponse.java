package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会签任务组成员快照。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountersignTaskGroupMemberResponse(
        // 成员标识。
        String memberId,
        // 任务标识
        String taskId,
        // 处理人用户标识
        String assigneeUserId,
        // sequenceNo。
        int sequenceNo,
        // voteWeight。
        Integer voteWeight,
        // 成员状态。
        String memberStatus
) {
}
