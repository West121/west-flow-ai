package com.westflow.flowable;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;

@Component
public class FlowableHistoryHelper {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    public FlowableHistoricProcessInstanceSummary toHistoricProcessInstanceSummary(
            HistoricProcessInstance historicProcessInstance
    ) {
        Map<String, Object> processVariables = historicProcessInstance.getProcessVariables();
        Map<String, Object> safeVariables = processVariables == null || processVariables.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(processVariables));

        return new FlowableHistoricProcessInstanceSummary(
                historicProcessInstance.getId(),
                historicProcessInstance.getProcessDefinitionId(),
                historicProcessInstance.getProcessDefinitionKey(),
                historicProcessInstance.getProcessDefinitionName(),
                historicProcessInstance.getBusinessKey(),
                historicProcessInstance.getStartUserId(),
                toOffsetDateTime(historicProcessInstance.getStartTime()),
                toOffsetDateTime(historicProcessInstance.getEndTime()),
                historicProcessInstance.getDurationInMillis(),
                resolveStatus(historicProcessInstance),
                safeVariables
        );
    }

    private String resolveStatus(HistoricProcessInstance historicProcessInstance) {
        if (historicProcessInstance.getDeleteReason() != null) {
            return "DELETED";
        }
        return historicProcessInstance.getEndTime() == null ? "RUNNING" : "COMPLETED";
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(value.toInstant(), TIME_ZONE);
    }
}
