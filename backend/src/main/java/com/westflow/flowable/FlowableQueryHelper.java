package com.westflow.flowable;

import java.util.List;
import java.util.Optional;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

@Component
public class FlowableQueryHelper {

    private final FlowableEngineFacade engineFacade;

    public FlowableQueryHelper(FlowableEngineFacade engineFacade) {
        this.engineFacade = engineFacade;
    }

    public Optional<ProcessInstance> findProcessInstance(String processInstanceId) {
        return Optional.ofNullable(engineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult());
    }

    public Optional<HistoricProcessInstance> findHistoricProcessInstance(String processInstanceId) {
        return Optional.ofNullable(engineFacade.historyService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult());
    }

    public List<HistoricActivityInstance> listHistoricActivities(String processInstanceId) {
        return engineFacade.historyService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();
    }
}
