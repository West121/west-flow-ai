package com.westflow.processruntime.api;

public record TaskActionAvailabilityResponse(
        boolean canClaim,
        boolean canApprove,
        boolean canReject,
        boolean canTransfer,
        boolean canReturn
) {
}
