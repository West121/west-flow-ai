package com.westflow.processdef.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDefinitionRecord;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.RepositoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用启动后把平台表里已发布的流程定义补同步到真实 Flowable 引擎。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = true)
public class PublishedDefinitionBootstrapService {

    private final ProcessDefinitionMapper processDefinitionMapper;
    private final FlowableEngineFacade flowableEngineFacade;

    /**
     * 启动完成后自动同步一次已发布定义，保证 SQL 种子可直接测试。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapPublishedDefinitions() {
        syncPublishedDefinitions();
    }

    /**
     * 扫描已发布定义，缺失部署信息或引擎中定义不存在时自动补部署。
     */
    @Transactional
    public void syncPublishedDefinitions() {
        RepositoryService repositoryService = flowableEngineFacade.repositoryService();
        if (repositoryService == null || repositoryService.createDeployment() == null) {
            return;
        }
        List<ProcessDefinitionRecord> publishedDefinitions = processDefinitionMapper.selectAllPublished();
        for (ProcessDefinitionRecord record : publishedDefinitions) {
            if (record.bpmnXml() == null || record.bpmnXml().isBlank()) {
                continue;
            }
            if (isDefinitionAvailable(record)) {
                continue;
            }
            ProcessDefinitionRecord redeployed = redeploy(record);
            processDefinitionMapper.updateDefinition(redeployed);
        }
    }

    private boolean isDefinitionAvailable(ProcessDefinitionRecord record) {
        if (record.flowableDefinitionId() != null && !record.flowableDefinitionId().isBlank()) {
            long count = flowableEngineFacade.repositoryService()
                    .createProcessDefinitionQuery()
                    .processDefinitionId(record.flowableDefinitionId())
                    .count();
            if (count > 0) {
                return true;
            }
        }
        if (record.deploymentId() != null && !record.deploymentId().isBlank()) {
            long count = flowableEngineFacade.repositoryService()
                    .createDeploymentQuery()
                    .deploymentId(record.deploymentId())
                    .count();
            return count > 0;
        }
        return false;
    }

    private ProcessDefinitionRecord redeploy(ProcessDefinitionRecord record) {
        Deployment deployment = flowableEngineFacade.repositoryService()
                .createDeployment()
                .name(record.processDefinitionId())
                .key(record.processKey())
                .category(record.category())
                .addString(record.processKey() + ".bpmn20.xml", record.bpmnXml())
                .deploy();
        ProcessDefinition flowableDefinition = flowableEngineFacade.repositoryService()
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionKey(record.processKey())
                .latestVersion()
                .singleResult();
        return new ProcessDefinitionRecord(
                record.processDefinitionId(),
                record.processKey(),
                record.processName(),
                record.category(),
                record.version(),
                record.status(),
                record.dslJson(),
                record.bpmnXml(),
                record.publisherUserId(),
                deployment.getId(),
                flowableDefinition == null ? null : flowableDefinition.getId(),
                record.createdAt(),
                LocalDateTime.now()
        );
    }
}
