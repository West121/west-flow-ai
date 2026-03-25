package com.westflow.processruntime.api.response;

// 任务动作可用性返回值。
public record TaskActionAvailabilityResponse(
        boolean canClaim,
        boolean canApprove,
        boolean canReject,
        boolean canTransfer,
        boolean canReturn,
        boolean canAddSign,
        boolean canRemoveSign,
        boolean canRevoke,
        boolean canUrge,
        boolean canRead,
        boolean canRejectRoute,
        boolean canJump,
        boolean canTakeBack,
        boolean canWakeUp,
        boolean canDelegate,
        boolean canHandover
) {
}
