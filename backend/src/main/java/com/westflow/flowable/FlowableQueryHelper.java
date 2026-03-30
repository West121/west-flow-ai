package com.westflow.flowable;

import java.util.List;
import java.util.Optional;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

/**
 * Flowable 查询辅助类。
 */
@Component
public class FlowableQueryHelper {

    private final FlowableEngineFacade engineFacade;

    public FlowableQueryHelper(FlowableEngineFacade engineFacade) {
        this.engineFacade = engineFacade;
    }

    /**
     * 查询当前运行中的流程实例。
     */
    public Optional<ProcessInstance> findProcessInstance(String processInstanceId) {
        return Optional.ofNullable(engineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult());
    }

    /**
     * 查询历史流程实例。
     */
    public Optional<HistoricProcessInstance> findHistoricProcessInstance(String processInstanceId) {
        return Optional.ofNullable(engineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult());
    }

    /**
     * 查询历史活动节点。
     */
    public List<HistoricActivityInstance> listHistoricActivities(String processInstanceId) {
        return engineFacade.historyService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();
    }
}
