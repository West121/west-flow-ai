package com.westflow.processdef.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ProcessDefinitionCollaborationAuditRequest(
        @NotBlank String roomName,
        @NotBlank String eventType,
        @NotBlank String eventName,
        Map<String, Object> details
) {
}
