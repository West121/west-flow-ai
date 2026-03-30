package com.westflow.flowable;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;

/**
 * Flowable 历史数据转换辅助类。
 */
@Component
public class FlowableHistoryHelper {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 转换为历史流程实例摘要。
     */
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

    /**
     * 计算历史实例状态。
     */
    private String resolveStatus(HistoricProcessInstance historicProcessInstance) {
        if (historicProcessInstance.getDeleteReason() != null) {
            return "DELETED";
        }
        return historicProcessInstance.getEndTime() == null ? "RUNNING" : "COMPLETED";
    }

    /**
     * 将日期转换为上海时区时间。
     */
    private OffsetDateTime toOffsetDateTime(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(value.toInstant(), TIME_ZONE);
    }
}
