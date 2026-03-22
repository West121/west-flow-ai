package com.westflow.processruntime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.service.ProcessDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = "DELETE FROM wf_process_definition", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProcessRuntimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessDemoService processDemoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRuntimeState() {
        processDemoService.reset();
        jdbcTemplate.update("DELETE FROM wf_business_process_link");
        jdbcTemplate.update("DELETE FROM oa_leave_bill");
        jdbcTemplate.update("DELETE FROM oa_expense_bill");
        jdbcTemplate.update("DELETE FROM oa_common_request_bill");
    }

    @Test
    void shouldPublishStartAndCompleteDemoProcess() throws Exception {
        String token = login();

        String publishResponse = mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode publishBody = objectMapper.readTree(publishResponse);
        assertThat(publishBody.path("code").asText()).isEqualTo("OK");
        assertThat(publishBody.path("data").path("processDefinitionId").asText()).isNotBlank();
        assertThat(publishBody.path("data").path("processKey").asText()).isEqualTo("oa_leave");
        assertThat(publishBody.path("data").path("dsl").path("processFormKey").asText()).isEqualTo("oa-leave-form");
        assertThat(publishBody.path("data").path("version").asInt()).isEqualTo(1);
        assertThat(publishBody.path("data").path("bpmnXml").asText()).contains("<process");

        seedLeaveBill("leave_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "amount": 500
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        JsonNode activeTask = startBody.path("data").path("activeTasks").get(0);
        assertThat(startBody.path("code").asText()).isEqualTo("OK");
        assertThat(startBody.path("data").path("instanceId").asText()).isNotBlank();
        assertThat(startBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(startBody.path("data").path("activeTasks").isArray()).isTrue();
        assertThat(startBody.path("data").path("activeTasks").size()).isEqualTo(1);
        assertThat(activeTask.path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(activeTask.path("status").asText()).isEqualTo("PENDING");
        String taskId = activeTask.path("taskId").asText();

        String pageResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "请假",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode pageBody = objectMapper.readTree(pageResponse);
        assertThat(pageBody.path("code").asText()).isEqualTo("OK");
        assertThat(pageBody.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(pageBody.path("data").path("records").size()).isEqualTo(1);
        assertThat(pageBody.path("data").path("records").get(0).path("taskId").asText()).isEqualTo(taskId);
        assertThat(pageBody.path("data").path("records").get(0).path("processName").asText()).isEqualTo("请假审批");
        assertThat(pageBody.path("data").path("records").get(0).path("businessType").asText()).isEqualTo("OA_LEAVE");

        String detailResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse);
        assertThat(detailBody.path("code").asText()).isEqualTo("OK");
        assertThat(detailBody.path("data").path("taskId").asText()).isEqualTo(taskId);
        assertThat(detailBody.path("data").path("processName").asText()).isEqualTo("请假审批");
        assertThat(detailBody.path("data").path("businessType").asText()).isEqualTo("OA_LEAVE");
        assertThat(detailBody.path("data").path("businessData").path("billNo").asText()).isEqualTo("LEAVE-20260322-001");
        assertThat(detailBody.path("data").path("businessData").path("days").asInt()).isEqualTo(3);
        assertThat(detailBody.path("data").path("flowNodes").size()).isEqualTo(3);
        assertThat(detailBody.path("data").path("flowEdges").size()).isEqualTo(2);
        assertThat(detailBody.path("data").path("instanceEvents").size()).isEqualTo(3);
        assertThat(detailBody.path("data").path("taskTrace").size()).isEqualTo(1);
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("receiveTime").isNull()).isFalse();
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("readTime").isNull()).isFalse();
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("handleStartTime").isNull()).isFalse();
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("handleEndTime").isNull()).isTrue();
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("handleDurationSeconds").isNull()).isTrue();
        assertThat(detailBody.path("data").path("instanceStatus").asText()).isEqualTo("RUNNING");
        assertThat(detailBody.path("data").path("activeTaskIds").size()).isEqualTo(1);

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "同意",
                                  "taskFormData": {
                                    "approvedDays": 2,
                                    "opinionTag": "同意"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completeBody = objectMapper.readTree(completeResponse);
        assertThat(completeBody.path("code").asText()).isEqualTo("OK");
        assertThat(completeBody.path("data").path("completedTaskId").asText()).isEqualTo(taskId);
        assertThat(completeBody.path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completeBody.path("data").path("nextTasks").size()).isEqualTo(0);

        String completedDetailResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completedDetailBody = objectMapper.readTree(completedDetailResponse);
        assertThat(completedDetailBody.path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completedDetailBody.path("data").path("businessType").asText()).isEqualTo("OA_LEAVE");
        assertThat(completedDetailBody.path("data").path("taskTrace").get(0).path("handleEndTime").isNull()).isFalse();
        assertThat(completedDetailBody.path("data").path("taskTrace").get(0).path("handleDurationSeconds").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(completedDetailBody.path("data").path("completedAt").isNull()).isFalse();
        assertThat(completedDetailBody.path("data").path("taskFormData").path("approvedDays").asInt()).isEqualTo(2);
        assertThat(completedDetailBody.path("data").path("taskFormData").path("opinionTag").asText()).isEqualTo("同意");
    }

    @Test
    void shouldPersistTaskFormDataAndExposeEffectiveFormRefs() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDslWithRuntimeForms()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "biz_002",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 3,
                                    "reason": "事假"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String taskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "同意",
                                  "taskFormData": {
                                    "approvedDays": 2,
                                    "opinionTag": "同意"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completeBody = objectMapper.readTree(completeResponse);
        assertThat(completeBody.path("code").asText()).isEqualTo("OK");

        String detailResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse);
        assertThat(detailBody.path("data").path("nodeFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailBody.path("data").path("nodeFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailBody.path("data").path("processFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailBody.path("data").path("processFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailBody.path("data").path("effectiveFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailBody.path("data").path("effectiveFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailBody.path("data").path("fieldBindings").size()).isEqualTo(1);
        assertThat(detailBody.path("data").path("taskFormData").path("approvedDays").asInt()).isEqualTo(2);
        assertThat(detailBody.path("data").path("taskFormData").path("opinionTag").asText()).isEqualTo("同意");
    }

    @Test
    void shouldResolveApprovalSheetDetailByBusinessWhenTaskIsActive() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 3,
                                    "reason": "事假"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String taskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();

        String response = mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(body.path("data").path("taskId").asText()).isEqualTo(taskId);
        assertThat(body.path("data").path("businessKey").asText()).isEqualTo("leave_001");
        assertThat(body.path("data").path("businessType").asText()).isEqualTo("OA_LEAVE");
        assertThat(body.path("data").path("instanceId").asText()).isNotBlank();
        assertThat(body.path("data").path("taskTrace").size()).isGreaterThanOrEqualTo(1);
        assertThat(body.path("data").path("activeTaskIds").size()).isEqualTo(1);
    }

    @Test
    void shouldResolveApprovalSheetDetailByBusinessUsingLatestTaskWhenInstanceCompleted() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_002");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_002",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "外出"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String taskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();

        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "同意",
                                  "taskFormData": {
                                    "approvedDays": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_002"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(body.path("data").path("taskId").asText()).isEqualTo(taskId);
        assertThat(body.path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("data").path("completedAt").isNull()).isFalse();
        assertThat(body.path("data").path("activeTaskIds").size()).isEqualTo(0);
    }

    @Test
    void shouldReturnNotFoundWhenApprovalSheetBusinessHasNoProcessInstance() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidDslPublishRequests() throws Exception {
        String token = login();

        String response = mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa-leave-form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true,
                    "allowUrge": true,
                    "allowTransfer": true
                                  },
                                  "nodes": [
                                    {
                                      "id": "start_1",
                                      "type": "start",
                                      "name": "开始",
                                      "position": {"x": 100, "y": 100},
                                      "config": {"initiatorEditable": true},
                                      "ui": {"width": 240, "height": 88}
                                    },
                                    {
                                      "id": "start_2",
                                      "type": "start",
                                      "name": "开始2",
                                      "position": {"x": 320, "y": 100},
                                      "config": {"initiatorEditable": true},
                                      "ui": {"width": 240, "height": 88}
                                    },
                                    {
                                      "id": "end_1",
                                      "type": "end",
                                      "name": "结束",
                                      "position": {"x": 540, "y": 100},
                                      "config": {},
                                      "ui": {"width": 240, "height": 88}
                                    }
                                  ],
                                  "edges": [
                                    {
                                      "id": "edge_1",
                                      "source": "start_1",
                                      "target": "end_1",
                                      "priority": 10,
                                      "label": "通过"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("code").asText()).isEqualTo("VALIDATION.REQUEST_INVALID");
        assertThat(body.path("message").asText()).contains("start");
        assertThat(body.path("path").asText()).isEqualTo("/api/v1/process-definitions/publish");
    }

    @Test
    void shouldSupportClaimTransferAndReturnActions() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimableProcessDsl()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_claim_leave",
                                  "businessKey": "biz_claim_001",
                                  "formData": {
                                    "days": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String taskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();
        assertThat(startBody.path("data").path("activeTasks").get(0).path("status").asText()).isEqualTo("PENDING_CLAIM");

        String actionsResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode actionsBody = objectMapper.readTree(actionsResponse);
        assertThat(actionsBody.path("data").path("canClaim").asBoolean()).isTrue();

        String claimResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/claim", taskId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "我来处理"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode claimBody = objectMapper.readTree(claimResponse);
        assertThat(claimBody.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(claimBody.path("data").path("assigneeUserId").asText()).isEqualTo("usr_001");

        String transferResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/transfer", taskId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "转给王五"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode transferBody = objectMapper.readTree(transferResponse);
        String transferredTaskId = transferBody.path("data").path("nextTasks").get(0).path("taskId").asText();
        assertThat(transferBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(transferBody.path("data").path("nextTasks").get(0).path("candidateUserIds").get(0).asText()).isEqualTo("usr_003");

        String receiverToken = login("wangwu");
        String returnResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/return", transferredTaskId)
                        .header("Authorization", "Bearer " + receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStrategy": "PREVIOUS_USER_TASK",
                                  "comment": "退回补充材料"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode returnBody = objectMapper.readTree(returnResponse);
        assertThat(returnBody.path("data").path("nextTasks").size()).isEqualTo(1);
        assertThat(returnBody.path("data").path("nextTasks").get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void shouldRejectClaimWhenCurrentUserIsNotCandidate() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimableProcessDsl()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_claim_leave",
                                  "businessKey": "biz_claim_002",
                                  "formData": {
                                    "days": 1
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = objectMapper.readTree(startResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        String nonCandidateToken = login("wangwu");
        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/claim", taskId)
                        .header("Authorization", "Bearer " + nonCandidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "越权认领"
                                }
                                """))
                .andExpect(status().isForbidden());
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

    private String validProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa-leave-form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true,
                    "allowUrge": true,
                    "allowTransfer": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "部门负责人审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SEQUENTIAL",
                          "voteThreshold": null
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN"],
                        "commentRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 540, "y": 100},
                      "config": {},
                      "ui": {"width": 240, "height": 88}
                    }
                  ],
                  "edges": [
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 20,
                      "label": "审批通过"
                    }
                  ]
                }
                """;
    }

    private String validProcessDslWithRuntimeForms() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_start_form",
                  "processFormVersion": "1.0.0",
                  "formFields": [
                    {
                      "fieldKey": "days",
                      "label": "请假天数",
                      "valueType": "number",
                      "required": true
                    },
                    {
                      "fieldKey": "reason",
                      "label": "请假原因",
                      "valueType": "string",
                      "required": true
                    }
                  ],
                  "settings": {
                    "allowWithdraw": true,
                    "allowUrge": true,
                    "allowTransfer": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "部门负责人审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "nodeFormKey": "oa_leave_approve_form",
                        "nodeFormVersion": "1.0.0",
                        "fieldBindings": [
                          {
                            "source": "PROCESS_FORM",
                            "sourceFieldKey": "days",
                            "targetFieldKey": "approvedDays"
                          }
                        ],
                        "approvalPolicy": {
                          "type": "SEQUENTIAL",
                          "voteThreshold": null
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN"],
                        "commentRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 540, "y": 100},
                      "config": {},
                      "ui": {"width": 240, "height": 88}
                    }
                  ],
                  "edges": [
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """;
    }

    private String validClaimableProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_claim_leave",
                  "processName": "公共认领请假审批",
                  "category": "OA",
                  "processFormKey": "oa-claim-leave-form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true,
                    "allowUrge": true,
                    "allowTransfer": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "共享审批池",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_001", "usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SEQUENTIAL"
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN", "TRANSFER"],
                        "commentRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 540, "y": 100},
                      "config": {},
                      "ui": {"width": 240, "height": 88}
                    }
                  ],
                  "edges": [
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """;
    }

    private void seedLeaveBill(String billId) {
        jdbcTemplate.update(
                """
                INSERT INTO oa_leave_bill (
                    id,
                    bill_no,
                    scene_code,
                    days,
                    reason,
                    process_instance_id,
                    status,
                    creator_user_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                billId,
                "LEAVE-20260322-001",
                "default",
                3,
                "年假",
                null,
                "DRAFT",
                "usr_001"
        );
    }
}
