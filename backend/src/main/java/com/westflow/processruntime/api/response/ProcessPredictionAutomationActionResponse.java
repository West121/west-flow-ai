package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessPredictionAutomationActionResponse(
        String actionType,
        String mode,
        String status,
        String title,
        String detail
) {
}
