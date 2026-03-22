package com.westflow.flowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlowableSupportTest {

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    private final List<String> deploymentIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String deploymentId : deploymentIds) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
        deploymentIds.clear();
    }

    @Test
    void shouldExposeCoreFlowableBeans() throws Exception {
        assertThat(beanFactory.getBean(ProcessEngine.class)).isNotNull();
        assertThat(beanFactory.getBean(Class.forName("com.westflow.flowable.FlowableEngineFacade"))).isNotNull();
        assertThat(beanFactory.getBean(Class.forName("com.westflow.flowable.FlowableQueryHelper"))).isNotNull();
        assertThat(beanFactory.getBean(Class.forName("com.westflow.flowable.FlowableVariableHelper"))).isNotNull();
        assertThat(beanFactory.getBean(Class.forName("com.westflow.flowable.FlowableHistoryHelper"))).isNotNull();
    }

    @Test
    void shouldConvertCompletedHistoricProcessIntoMinimalSummary() throws Exception {
        String processKey = "flowable_history_" + UUID.randomUUID().toString().replace("-", "");
        String deploymentId = repositoryService.createDeployment()
                .name("flowable-history-test")
                .addString(processKey + ".bpmn20.xml", simpleProcessXml(processKey))
                .deploy()
                .getId();
        deploymentIds.add(deploymentId);

        runtimeService.startProcessInstanceByKey(processKey);
        String taskId = taskService.createTaskQuery()
                .processDefinitionKey(processKey)
                .singleResult()
                .getId();
        taskService.complete(taskId);

        HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .singleResult();
        assertThat(historicProcessInstance).isNotNull();

        Object helper = beanFactory.getBean(Class.forName("com.westflow.flowable.FlowableHistoryHelper"));
        Method method = helper.getClass().getMethod("toHistoricProcessInstanceSummary", HistoricProcessInstance.class);
        Object summary = method.invoke(helper, historicProcessInstance);

        JsonNode summaryJson = objectMapper.valueToTree(summary);
        assertThat(summaryJson.path("processInstanceId").asText()).isEqualTo(historicProcessInstance.getId());
        assertThat(summaryJson.path("processDefinitionKey").asText()).isEqualTo(processKey);
        assertThat(summaryJson.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(summaryJson.path("startTime").isNull()).isFalse();
        assertThat(summaryJson.path("endTime").isNull()).isFalse();
    }

    private String simpleProcessXml(String processKey) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://westflow.com/test">
                  <process id="%s" name="Test Process" isExecutable="true">
                    <startEvent id="startEvent" name="Start" />
                    <sequenceFlow id="flow_start_to_task" sourceRef="startEvent" targetRef="approveTask" />
                    <userTask id="approveTask" name="Approve Task" />
                    <sequenceFlow id="flow_task_to_end" sourceRef="approveTask" targetRef="endEvent" />
                    <endEvent id="endEvent" name="End" />
                  </process>
                </definitions>
                """.formatted(processKey).strip();
    }
}
