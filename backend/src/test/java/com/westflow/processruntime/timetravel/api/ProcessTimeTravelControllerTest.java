package com.westflow.processruntime.timetravel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 穿越时空真实执行集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessTimeTravelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private FlowableEngineFacade flowableEngineFacade;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        flowableEngineFacade.runtimeService().createProcessInstanceQuery().list()
                .forEach(instance -> flowableEngineFacade.runtimeService().deleteProcessInstance(instance.getProcessInstanceId(), "test cleanup"));
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
        jdbcTemplate.update("DELETE FROM wf_process_definition");
        jdbcTemplate.update("DELETE FROM wf_workflow_operation_log");
    }

    @Test
    void shouldExecuteBackToNodeAndPersistExecutionLog() throws Exception {
        String token = login("wangwu");
        processDefinitionService.publish(buildBackToNodeProcess());

        String instanceId = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey("time_travel_back_process", "time_travel_back_001", Map.of())
                .getProcessInstanceId();
        Task firstTask = taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult();
        assertThat(firstTask.getTaskDefinitionKey()).isEqualTo("approve_1");
        taskService.complete(firstTask.getId());

        Task secondTask = taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult();
        assertThat(secondTask.getTaskDefinitionKey()).isEqualTo("approve_2");

        JsonNode executeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/time-travel/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instanceId": "%s",
                                  "strategy": "BACK_TO_NODE",
                                  "taskId": "%s",
                                  "targetNodeId": "approve_1",
                                  "reason": "回退到第一步重新确认",
                                  "variables": {
                                    "reopenReason": "need-review"
                                  }
                                }
                                """.formatted(instanceId, secondTask.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(executeBody.path("executionId").asText()).isNotBlank();
        assertThat(executeBody.path("permissionCode").asText()).isEqualTo("workflow:time-travel:execute");
        assertThat(executeBody.path("actionCategory").asText()).isEqualTo("TIME_TRAVEL");
        assertThat(executeBody.path("targetNodeId").asText()).isEqualTo("approve_1");

        Task rewoundTask = taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult();
        assertThat(rewoundTask.getTaskDefinitionKey()).isEqualTo("approve_1");

        JsonNode pageBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/time-travel/executions/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instanceId": "%s",
                                  "strategy": "BACK_TO_NODE",
                                  "keyword": "回退",
                                  "page": 1,
                                  "pageSize": 20
                                }
                                """.formatted(instanceId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(pageBody.path("total").asInt()).isEqualTo(1);

        JsonNode traceBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/time-travel/instances/{instanceId}/trace", instanceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(traceBody.isArray()).isTrue();
        assertThat(traceBody.get(0).path("strategy").asText()).isEqualTo("BACK_TO_NODE");
    }

    @Test
    void shouldExecuteReopenInstanceAndPersistExecutionLog() throws Exception {
        String token = login("wangwu");
        processDefinitionService.publish(buildReopenProcess());

        String instanceId = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey("time_travel_reopen_process", "time_travel_reopen_001", Map.of())
                .getProcessInstanceId();
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult();
        taskService.complete(task.getId());

        JsonNode executeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/time-travel/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instanceId": "%s",
                                  "strategy": "REOPEN_INSTANCE",
                                  "reason": "完成后重新发起审查",
                                  "variables": {
                                    "reopenReason": "reopen"
                                  }
                                }
                                """.formatted(instanceId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(executeBody.path("newInstanceId").asText()).isNotBlank();
        assertThat(executeBody.path("strategy").asText()).isEqualTo("REOPEN_INSTANCE");

        Task reopenedTask = taskService.createTaskQuery()
                .processInstanceId(executeBody.path("newInstanceId").asText())
                .active()
                .singleResult();
        assertThat(reopenedTask).isNotNull();
        assertThat(reopenedTask.getTaskDefinitionKey()).isEqualTo("approve_1");

        JsonNode traceBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/time-travel/instances/{instanceId}/trace", instanceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(traceBody.isArray()).isTrue();
        assertThat(traceBody.get(0).path("strategy").asText()).isEqualTo("REOPEN_INSTANCE");
    }

    private ProcessDslPayload buildBackToNodeProcess() {
        return new ProcessDslPayload(
                "1.0.0",
                "time_travel_back_process",
                "回退测试流程",
                "OA",
                "time-travel-form",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of("x", 100, "y", 100), Map.of("initiatorEditable", true), Map.of("width", 240, "height", 88)),
                        new ProcessDslPayload.Node("approve_1", "approver", "第一步审批", null, Map.of("x", 320, "y", 100), Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", Map.of("type", "SEQUENTIAL"),
                                "operations", List.of("APPROVE", "REJECT", "RETURN")
                        ), Map.of("width", 240, "height", 88)),
                        new ProcessDslPayload.Node("approve_2", "approver", "第二步审批", null, Map.of("x", 540, "y", 100), Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_003"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", Map.of("type", "SEQUENTIAL"),
                                "operations", List.of("APPROVE", "REJECT", "RETURN")
                        ), Map.of("width", 240, "height", 88)),
                        new ProcessDslPayload.Node("end_1", "end", "结束", null, Map.of("x", 760, "y", 100), Map.of(), Map.of("width", 240, "height", 88))
                ),
                List.of(
                        new ProcessDslPayload.Edge("edge_1", "start_1", "approve_1", 10, "提交", Map.of()),
                        new ProcessDslPayload.Edge("edge_2", "approve_1", "approve_2", 10, "通过", Map.of()),
                        new ProcessDslPayload.Edge("edge_3", "approve_2", "end_1", 10, "通过", Map.of())
                )
        );
    }

    private ProcessDslPayload buildReopenProcess() {
        return new ProcessDslPayload(
                "1.0.0",
                "time_travel_reopen_process",
                "重开测试流程",
                "OA",
                "time-travel-reopen-form",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of("x", 100, "y", 100), Map.of("initiatorEditable", true), Map.of("width", 240, "height", 88)),
                        new ProcessDslPayload.Node("approve_1", "approver", "单步审批", null, Map.of("x", 320, "y", 100), Map.of(
                                "assignment", Map.of(
                                        "mode", "USER",
                                        "userIds", List.of("usr_002"),
                                        "roleCodes", List.of(),
                                        "departmentRef", "",
                                        "formFieldKey", ""
                                ),
                                "approvalPolicy", Map.of("type", "SEQUENTIAL"),
                                "operations", List.of("APPROVE", "REJECT", "RETURN")
                        ), Map.of("width", 240, "height", 88)),
                        new ProcessDslPayload.Node("end_1", "end", "结束", null, Map.of("x", 540, "y", 100), Map.of(), Map.of("width", 240, "height", 88))
                ),
                List.of(
                        new ProcessDslPayload.Edge("edge_1", "start_1", "approve_1", 10, "提交", Map.of()),
                        new ProcessDslPayload.Edge("edge_2", "approve_1", "end_1", 10, "通过", Map.of())
                )
        );
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
