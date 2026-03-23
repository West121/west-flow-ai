package com.westflow.processruntime.collaboration.api;

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
 * 协同事件真实执行集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessCollaborationControllerTest {

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
    void shouldCreateQueryAndTraceCollaborationEventsWithoutChangingTaskOwnership() throws Exception {
        String token = login("wangwu");
        processDefinitionService.publish(buildCollaborationProcess());

        String instanceId = flowableEngineFacade.runtimeService()
                .startProcessInstanceByKey("collaboration_leave", "collab_001", Map.of())
                .getProcessInstanceId();
        Task activeTask = taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult();
        assertThat(activeTask).isNotNull();
        assertThat(activeTask.getAssignee()).isEqualTo("usr_002");

        JsonNode createBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/collaboration/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instanceId": "%s",
                                  "taskId": "%s",
                                  "eventType": "COMMENT",
                                  "subject": "请协同补充材料",
                                  "content": "请在今天下班前补充附件并确认。",
                                  "mentionedUserIds": ["usr_002"]
                                }
                                """.formatted(instanceId, activeTask.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(createBody.path("eventId").asText()).isNotBlank();
        assertThat(createBody.path("permissionCode").asText()).isEqualTo("workflow:collaboration:create");
        assertThat(createBody.path("actionCategory").asText()).isEqualTo("COLLABORATION");
        assertThat(createBody.path("mentionedUserIds").get(0).asText()).isEqualTo("usr_002");

        JsonNode pageBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/collaboration/events/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instanceId": "%s",
                                  "taskId": "%s",
                                  "eventType": "COMMENT",
                                  "keyword": "协同",
                                  "page": 1,
                                  "pageSize": 20
                                }
                                """.formatted(instanceId, activeTask.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(pageBody.path("total").asInt()).isEqualTo(1);
        assertThat(pageBody.path("records").get(0).path("subject").asText()).isEqualTo("请协同补充材料");

        JsonNode traceBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/collaboration/instances/{instanceId}/trace", instanceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(traceBody.isArray()).isTrue();
        assertThat(traceBody.get(0).path("eventType").asText()).isEqualTo("COMMENT");

        assertThat(taskService.createTaskQuery().taskId(activeTask.getId()).singleResult()).isNotNull();
        assertThat(taskService.createTaskQuery().processInstanceId(instanceId).active().singleResult().getAssignee()).isEqualTo("usr_002");
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

    private ProcessDslPayload buildCollaborationProcess() {
        return new ProcessDslPayload(
                "1.0.0",
                "collaboration_leave",
                "协同测试流程",
                "OA",
                "collaboration-form",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node(
                                "start_1",
                                "start",
                                "开始",
                                null,
                                Map.of("x", 100, "y", 100),
                                Map.of("initiatorEditable", true),
                                Map.of("width", 240, "height", 88)
                        ),
                        new ProcessDslPayload.Node(
                                "approve_1",
                                "approver",
                                "协同审批",
                                null,
                                Map.of("x", 320, "y", 100),
                                Map.of(
                                        "assignment", Map.of(
                                                "mode", "USER",
                                                "userIds", List.of("usr_002"),
                                                "roleCodes", List.of(),
                                                "departmentRef", "",
                                                "formFieldKey", ""
                                        ),
                                        "approvalPolicy", Map.of("type", "SEQUENTIAL"),
                                        "operations", List.of("APPROVE", "REJECT", "RETURN")
                                ),
                                Map.of("width", 240, "height", 88)
                        ),
                        new ProcessDslPayload.Node(
                                "end_1",
                                "end",
                                "结束",
                                null,
                                Map.of("x", 540, "y", 100),
                                Map.of(),
                                Map.of("width", 240, "height", 88)
                        )
                ),
                List.of(
                        new ProcessDslPayload.Edge("edge_1", "start_1", "approve_1", 10, "提交", Map.of()),
                        new ProcessDslPayload.Edge("edge_2", "approve_1", "end_1", 10, "通过", Map.of())
                )
        );
    }
}
