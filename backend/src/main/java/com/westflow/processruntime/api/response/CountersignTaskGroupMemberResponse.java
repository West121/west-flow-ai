package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会签任务组成员快照。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountersignTaskGroupMemberResponse(
        // 成员标识。
        String memberId,
        String taskId,
        // 处理人用户标识
        String assigneeUserId,
        int sequenceNo,
        // voteWeight。
        Integer voteWeight,
        String memberStatus
) {
}
