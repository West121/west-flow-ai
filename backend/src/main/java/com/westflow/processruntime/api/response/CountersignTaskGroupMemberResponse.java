package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会签任务组成员快照。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountersignTaskGroupMemberResponse(
        String memberId,
        String taskId,
        String assigneeUserId,
        int sequenceNo,
        Integer voteWeight,
        String memberStatus
) {
}
