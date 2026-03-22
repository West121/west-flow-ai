package com.westflow.flowable;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

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

    public ProcessEngine processEngine() {
        return processEngineProvider.getIfAvailable();
    }

    public RepositoryService repositoryService() {
        return repositoryServiceProvider.getIfAvailable();
    }

    public RuntimeService runtimeService() {
        return runtimeServiceProvider.getIfAvailable();
    }

    public TaskService taskService() {
        return taskServiceProvider.getIfAvailable();
    }

    public HistoryService historyService() {
        return historyServiceProvider.getIfAvailable();
    }
}
