package com.westflow.flowable;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Flowable 引擎对象门面，统一处理可选依赖获取。
 */
@Component
public class FlowableEngineFacade {

    private final ObjectProvider<ProcessEngine> processEngineProvider;
    private final ObjectProvider<RepositoryService> repositoryServiceProvider;
    private final ObjectProvider<RuntimeService> runtimeServiceProvider;
    private final ObjectProvider<TaskService> taskServiceProvider;
    private final ObjectProvider<HistoryService> historyServiceProvider;

    public FlowableEngineFacade(
            ObjectProvider<ProcessEngine> processEngineProvider,
            ObjectProvider<RepositoryService> repositoryServiceProvider,
            ObjectProvider<RuntimeService> runtimeServiceProvider,
            ObjectProvider<TaskService> taskServiceProvider,
            ObjectProvider<HistoryService> historyServiceProvider
    ) {
        this.processEngineProvider = processEngineProvider;
        this.repositoryServiceProvider = repositoryServiceProvider;
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.taskServiceProvider = taskServiceProvider;
        this.historyServiceProvider = historyServiceProvider;
    }

    /**
     * 获取流程引擎。
     */
    public ProcessEngine processEngine() {
        return processEngineProvider.getIfAvailable();
    }

    /**
     * 获取仓储服务。
     */
    public RepositoryService repositoryService() {
        return repositoryServiceProvider.getIfAvailable();
    }

    /**
     * 获取运行时服务。
     */
    public RuntimeService runtimeService() {
        return runtimeServiceProvider.getIfAvailable();
    }

    /**
     * 获取任务服务。
     */
    public TaskService taskService() {
        return taskServiceProvider.getIfAvailable();
    }

    /**
     * 获取历史服务。
     */
    public HistoryService historyService() {
        return historyServiceProvider.getIfAvailable();
    }
}
