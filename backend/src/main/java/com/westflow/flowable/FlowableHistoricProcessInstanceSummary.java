package com.westflow.flowable;

import java.time.OffsetDateTime;
import java.util.Map;

public record FlowableHistoricProcessInstanceSummary(
        String processInstanceId,
        String processDefinitionId,
        String processDefinitionKey,
        String processDefinitionName,
        String businessKey,
        String startUserId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Long durationInMillis,
        String status,
        Map<String, Object> processVariables
) {
}
