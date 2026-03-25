package com.westflow.oa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM wf_business_process_link",
        "DELETE FROM oa_leave_bill"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OASeededLeaveFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateLeaveBillWithSeededPublishedDefinition() throws Exception {
        String token = login();

        String response = mockMvc.perform(post("/api/v1/oa/leaves")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "days": 3,
                                  "reason": "年假"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        JsonNode data = body.path("data");
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(data.path("billId").asText()).isNotBlank();
        assertThat(data.path("processInstanceId").asText()).isNotBlank();
        assertThat(data.path("activeTasks").isArray()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_leave_bill", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wf_business_process_link WHERE business_type = 'OA_LEAVE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldLoadLeaveTaskDetailWithSeededPublishedDefinition() throws Exception {
        String token = login();
        String taskId = createSeededLeaveBill(token)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        String response = mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(body.path("data").path("taskId").asText()).isEqualTo(taskId);
        assertThat(body.path("data").path("businessType").asText()).isEqualTo("OA_LEAVE");
    }

    @Test
    void shouldExposeDepartmentCandidatesForSeededLeaveApprovalTask() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String taskId = startSeededLeaveRuntimeProcess(applicantToken, 5).path("activeTasks").get(0).path("taskId").asText();

        String detailResponse = mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailBody = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailBody.path("nodeId").asText()).isEqualTo("approve_dept_lead");
        assertThat(detailBody.path("status").asText()).isEqualTo("PENDING_CLAIM");
        assertThat(detailBody.path("assignmentMode").asText()).isEqualTo("DEPARTMENT");
        assertThat(detailBody.path("candidateUserIds").isArray()).isTrue();
        assertThat(detailBody.path("candidateUserIds")).isEmpty();
        assertThat(detailBody.path("candidateGroupIds").isArray()).isTrue();
        assertThat(detailBody.path("candidateGroupIds")).hasSize(1);
        assertThat(detailBody.path("candidateGroupIds").get(0).asText()).isEqualTo("dept_002");
        assertThat(detailBody.path("assigneeUserId").isNull()).isTrue();

        String actionsResponse = mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode actionsBody = objectMapper.readTree(actionsResponse).path("data");
        assertThat(actionsBody.path("canClaim").asBoolean()).isTrue();
        assertThat(actionsBody.path("canApprove").asBoolean()).isTrue();
    }

    private String login() throws Exception {
        return login("zhangsan");
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

        return objectMapper.readTree(response)
                .path("data")
                .path("accessToken")
                .asText();
    }

    private JsonNode createSeededLeaveBill(String token) throws Exception {
        return createSeededLeaveBill(token, 3);
    }

    private JsonNode createSeededLeaveBill(String token, int days) throws Exception {
        String response = mockMvc.perform(post("/api/v1/oa/leaves")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "days": %s,
                                  "reason": "年假"
                                }
                                """.formatted(days)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode startSeededLeaveRuntimeProcess(String token, int leaveDays) throws Exception {
        String response = mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessType": "OA_LEAVE",
                                  "businessKey": "leave-runtime-%s",
                                  "formData": {
                                    "leaveDays": %s,
                                    "reason": "年假",
                                    "urgent": false
                                  }
                                }
                                """.formatted(leaveDays, leaveDays)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
}
