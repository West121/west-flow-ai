package com.westflow.processruntime.service;

import com.westflow.flowable.FlowableEngineFacade;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Flowable 动态构建节点执行入口。
 */
@Component("flowableDynamicBuilderDelegate")
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableDynamicBuilderDelegate implements JavaDelegate {

    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        String sourceNodeId = execution.getCurrentActivityId();
        if (processInstanceId == null || processInstanceId.isBlank() || sourceNodeId == null || sourceNodeId.isBlank()) {
            return;
        }
        flowableProcessRuntimeService.executeDynamicBuilder(processInstanceId, sourceNodeId);
    }
}
