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
    void shouldPageApprovalSheetsForInitiatedDoneAndCcViews() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_page_001");
        seedExpenseBill("expense_page_001");

        String leaveStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_page_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "外出处理事务"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String expenseStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "expense_page_001",
                                  "businessType": "OA_EXPENSE",
                                  "formData": {
                                    "amount": 128.50,
                                    "reason": "客户接待"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String leaveTaskId = objectMapper.readTree(leaveStartResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", leaveTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isOk());

        String initiatedResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/approval-sheets/page")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "INITIATED",
                                  "businessTypes": ["OA_LEAVE"],
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
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

        JsonNode initiatedBody = objectMapper.readTree(initiatedResponse);
        assertThat(initiatedBody.path("code").asText()).isEqualTo("OK");
        assertThat(initiatedBody.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(initiatedBody.path("data").path("records").get(0).path("businessType").asText()).isEqualTo("OA_LEAVE");
        assertThat(initiatedBody.path("data").path("records").get(0).path("billNo").asText()).isEqualTo("LEAVE-20260322-001");
        assertThat(initiatedBody.path("data").path("records").get(0).path("businessTitle").asText()).contains("请假申请");

        String doneResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/approval-sheets/page")
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "DONE",
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode doneBody = objectMapper.readTree(doneResponse);
        assertThat(doneBody.path("code").asText()).isEqualTo("OK");
        assertThat(doneBody.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(doneBody.path("data").path("records").get(0).path("instanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(doneBody.path("data").path("records").get(0).path("currentTaskStatus").asText()).isEqualTo("COMPLETED");
        assertThat(doneBody.path("data").path("records").get(0).path("latestAction").asText()).isEqualTo("APPROVE");
        assertThat(doneBody.path("data").path("records").get(0).path("latestOperatorUserId").asText()).isEqualTo("usr_002");

        String copiedResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/approval-sheets/page")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "CC",
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode copiedBody = objectMapper.readTree(copiedResponse);
        assertThat(copiedBody.path("code").asText()).isEqualTo("OK");
        assertThat(copiedBody.path("data").path("total").asInt()).isEqualTo(0);
        assertThat(copiedBody.path("data").path("records").size()).isEqualTo(0);

        assertThat(objectMapper.readTree(expenseStartResponse).path("data").path("activeTasks").size()).isEqualTo(1);
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
    void shouldSupportRejectRoutingBackToPreviousUserTask() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRejectRouteProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_reject_prev_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_reject_route",
                                  "businessKey": "leave_reject_prev_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "驳回测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String firstTaskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();
        JsonNode firstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String secondTaskId = firstCompleteBody.path("data").path("nextTasks").get(0).path("taskId").asText();

        String rejectResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/reject", secondTaskId)
                        .header("Authorization", "Bearer " + login("wangwu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStrategy": "PREVIOUS_USER_TASK",
                                  "reapproveStrategy": "CONTINUE",
                                  "comment": "退回补充材料"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rejectBody = objectMapper.readTree(rejectResponse);
        assertThat(rejectBody.path("code").asText()).isEqualTo("OK");
        assertThat(rejectBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(rejectBody.path("data").path("nextTasks").size()).isEqualTo(1);

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_reject_prev_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(detailBody.path("data").path("instanceStatus").asText()).isEqualTo("RUNNING");
        assertThat(detailBody.path("data").path("taskTrace").size()).isEqualTo(3);
        assertThat(detailBody.path("data").path("taskTrace").get(1).path("status").asText()).isEqualTo("REJECTED");
        assertThat(detailBody.path("data").path("taskTrace").get(1).path("action").asText()).isEqualTo("REJECT_ROUTE");
        assertThat(detailBody.path("data").path("taskTrace").get(2).path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(detailBody.path("data").path("taskTrace").get(2).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void shouldSupportRejectRoutingBackToInitiator() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRejectToInitiatorProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_reject_init_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_reject_initiator",
                                  "businessKey": "leave_reject_init_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 3,
                                    "reason": "驳回到发起人"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String firstTaskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();
        JsonNode firstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "发起人已确认"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String secondTaskId = firstCompleteBody.path("data").path("nextTasks").get(0).path("taskId").asText();

        String rejectResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/reject", secondTaskId)
                        .header("Authorization", "Bearer " + login("wangwu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStrategy": "INITIATOR",
                                  "reapproveStrategy": "RETURN_TO_REJECTED_NODE",
                                  "comment": "退回到发起人"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rejectBody = objectMapper.readTree(rejectResponse);
        assertThat(rejectBody.path("code").asText()).isEqualTo("OK");
        assertThat(rejectBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(rejectBody.path("data").path("nextTasks").size()).isEqualTo(1);

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_reject_init_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(detailBody.path("data").path("taskTrace").size()).isEqualTo(3);
        assertThat(detailBody.path("data").path("taskTrace").get(1).path("action").asText()).isEqualTo("REJECT_ROUTE");
        assertThat(detailBody.path("data").path("taskTrace").get(2).path("nodeId").asText()).isEqualTo("approve_initiator");
    }

    @Test
    void shouldSupportJumpTakeBackAndWakeUp() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRejectRouteProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_runtime_flow_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_reject_route",
                                  "businessKey": "leave_runtime_flow_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 4,
                                    "reason": "流程回退测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstTaskId = objectMapper.readTree(startResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        JsonNode firstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String secondTaskId = firstCompleteBody.path("data").path("nextTasks").get(0).path("taskId").asText();

        JsonNode takeBackActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", secondTaskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(takeBackActions.path("canApprove").asBoolean()).isFalse();
        assertThat(takeBackActions.path("canTakeBack").asBoolean()).isTrue();

        String takeBackResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/take-back", secondTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "拿回修改"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode takeBackBody = objectMapper.readTree(takeBackResponse);
        assertThat(takeBackBody.path("code").asText()).isEqualTo("OK");
        assertThat(takeBackBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(takeBackBody.path("data").path("nextTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");

        JsonNode takeBackDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_runtime_flow_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(takeBackDetail.path("data").path("taskTrace").get(1).path("status").asText()).isEqualTo("TAKEN_BACK");

        String jumpStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_reject_route",
                                  "businessKey": "leave_runtime_jump_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "跳转测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String jumpFirstTaskId = objectMapper.readTree(jumpStartResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();
        JsonNode jumpFirstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", jumpFirstTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String jumpTaskId = jumpFirstCompleteBody.path("data").path("nextTasks").get(0).path("taskId").asText();

        String jumpResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/jump", jumpTaskId)
                        .header("Authorization", "Bearer " + login("wangwu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetNodeId": "end_1",
                                  "comment": "直接结束"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode jumpBody = objectMapper.readTree(jumpResponse);
        assertThat(jumpBody.path("data").path("status").asText()).isEqualTo("COMPLETED");

        String wakeUpResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/instances/{instanceId}/wake-up",
                        jumpBody.path("data").path("instanceId").asText())
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceTaskId": "%s",
                                  "comment": "唤醒重办"
                                }
                                """.formatted(jumpTaskId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode wakeUpBody = objectMapper.readTree(wakeUpResponse);
        assertThat(wakeUpBody.path("data").path("status").asText()).isEqualTo("RUNNING");
        assertThat(wakeUpBody.path("data").path("nextTasks").size()).isEqualTo(1);

        JsonNode wakeUpDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_runtime_jump_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(wakeUpDetail.path("data").path("instanceStatus").asText()).isEqualTo("RUNNING");
        assertThat(wakeUpDetail.path("data").path("taskTrace"))
                .anySatisfy(item -> assertThat(item.path("action").asText()).isEqualTo("JUMP"));
    }

    @Test
    void shouldAllowDelegatedProxyToApprovePendingTaskAndExposeProxyAuditFields() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_proxy_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_proxy_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "代理代办测试"
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

        JsonNode actionsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + login("zhangsan")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(actionsBody.path("canApprove").asBoolean()).isTrue();
        assertThat(actionsBody.path("canTransfer").asBoolean()).isTrue();
        assertThat(actionsBody.path("canDelegate").asBoolean()).isFalse();

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "代理人代办完成"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completeBody = objectMapper.readTree(completeResponse);
        assertThat(completeBody.path("data").path("status").asText()).isEqualTo("COMPLETED");

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_proxy_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("actingMode").asText()).isEqualTo("PROXY");
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("actingForUserId").asText()).isEqualTo("usr_002");
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("delegatedByUserId").asText()).isEqualTo("usr_002");
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("operatorUserId").asText()).isEqualTo("usr_001");
    }

    @Test
    void shouldSupportDelegateActionAndPreserveDelegateAuditTrail() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_delegate_001");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_delegate_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "委派测试"
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

        JsonNode delegateActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(delegateActions.path("canDelegate").asBoolean()).isTrue();
        assertThat(delegateActions.path("canApprove").asBoolean()).isTrue();

        String delegateResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/delegate", taskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_001",
                                  "comment": "委派给张三代办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode delegateBody = objectMapper.readTree(delegateResponse);
        assertThat(delegateBody.path("code").asText()).isEqualTo("OK");
        assertThat(delegateBody.path("data").path("nextTasks").size()).isEqualTo(1);
        String delegatedTaskId = delegateBody.path("data").path("nextTasks").get(0).path("taskId").asText();
        assertThat(delegateBody.path("data").path("nextTasks").get(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(delegateBody.path("data").path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_001");

        String delegatedCompleteResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", delegatedTaskId)
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "代办完成"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode delegatedCompleteBody = objectMapper.readTree(delegatedCompleteResponse);
        assertThat(delegatedCompleteBody.path("data").path("status").asText()).isEqualTo("COMPLETED");

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_delegate_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("status").asText()).isEqualTo("DELEGATED");
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("actingMode").asText()).isEqualTo("DELEGATE");
        assertThat(detailBody.path("data").path("taskTrace").get(0).path("actingForUserId").asText()).isEqualTo("usr_002");
        assertThat(detailBody.path("data").path("taskTrace").get(1).path("operatorUserId").asText()).isEqualTo("usr_001");
        assertThat(detailBody.path("data").path("taskTrace").get(1).path("actingMode").asText()).isEqualTo("DELEGATE");
    }

    @Test
    void shouldHandOverOnlyPendingHumanTasksAndKeepClaimAndCcTasksUnchanged() throws Exception {
        String adminToken = login("wangwu");
        String sourceUserId = "usr_001";

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHandoverProcessDsl()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimableProcessDsl()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCcProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_handover_001");
        seedLeaveBill("leave_claim_001");
        seedLeaveBill("leave_cc_001");

        String handoverStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_handover_route",
                                  "businessKey": "leave_handover_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "离职转办测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String handoverTaskId = objectMapper.readTree(handoverStartResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        String claimableStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_claim_leave",
                                  "businessKey": "leave_claim_001",
                                  "formData": {
                                    "days": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String claimableTaskId = objectMapper.readTree(claimableStartResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();

        String ccStartResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave_cc",
                                  "businessKey": "leave_cc_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 3,
                                    "reason": "抄送转办测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String ccFirstTaskId = objectMapper.readTree(ccStartResponse)
                .path("data")
                .path("activeTasks")
                .get(0)
                .path("taskId")
                .asText();
        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", ccFirstTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "生成抄送"
                                }
                                """))
                .andExpect(status().isOk());

        String handoverResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/users/{sourceUserId}/handover", sourceUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "离职转办给王五"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode handoverBody = objectMapper.readTree(handoverResponse);
        assertThat(handoverBody.path("code").asText()).isEqualTo("OK");
        assertThat(handoverBody.path("data").path("nextTasks").size()).isEqualTo(1);
        assertThat(handoverBody.path("data").path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
        assertThat(handoverBody.path("data").path("nextTasks").get(0).path("status").asText()).isEqualTo("PENDING");

        JsonNode handoverDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_handover_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(handoverDetail.path("data").path("taskTrace").get(0).path("status").asText()).isEqualTo("HANDOVERED");
        assertThat(handoverDetail.path("data").path("taskTrace").get(0).path("actingMode").asText()).isEqualTo("HANDOVER");
        assertThat(handoverDetail.path("data").path("taskTrace").get(0).path("handoverFromUserId").asText()).isEqualTo(sourceUserId);

        JsonNode claimableDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + login("wangwu"))
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_claim_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(claimableDetail.path("data").path("taskTrace").get(0).path("status").asText()).isEqualTo("PENDING_CLAIM");

        JsonNode ccDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_cc_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(ccDetail.path("data").path("taskTrace"))
                .anySatisfy(item -> assertThat(item.path("taskKind").asText()).isEqualTo("CC"));
    }

    @Test
    void shouldPreviewAndExecuteHandoverWithExecutionDetail() throws Exception {
        String adminToken = login("wangwu");
        String sourceUserId = "usr_001";

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHandoverProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_handover_preview_001");

        mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_handover_route",
                                  "businessKey": "leave_handover_preview_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "离职转办预览测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        // 预览接口只负责告诉管理员会影响哪些任务，不提前改状态。
        JsonNode previewBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/users/{sourceUserId}/handover/preview", sourceUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "预览离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(previewBody.path("previewTaskCount").asInt()).isEqualTo(1);
        assertThat(previewBody.path("previewTasks").get(0).path("processKey").asText()).isEqualTo("oa_handover_route");
        assertThat(previewBody.path("previewTasks").get(0).path("billNo").asText()).isEqualTo("LEAVE-20260322-001");
        assertThat(previewBody.path("targetDisplayName").asText()).isEqualTo("王五");

        // 执行接口会返回每个被转办任务的执行明细，便于前端展示审计结果。
        JsonNode executeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/demo/users/{sourceUserId}/handover/execute", sourceUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "执行离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(executeBody.path("executedTaskCount").asInt()).isEqualTo(1);
        assertThat(executeBody.path("executionTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
        assertThat(executeBody.path("executionTasks").get(0).path("status").asText()).isEqualTo("HANDOVERED");
        assertThat(executeBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
    }

    @Test
    void shouldSupportAddSignRemoveSignAndBlockCompletionUntilAddSignTaskResolves() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "biz_add_sign_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 3,
                                    "reason": "加签测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String taskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();

        JsonNode actionsBodyBefore = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(actionsBodyBefore.path("canAddSign").asBoolean()).isTrue();
        assertThat(actionsBodyBefore.path("canRemoveSign").asBoolean()).isFalse();
        assertThat(actionsBodyBefore.path("canRevoke").asBoolean()).isFalse();
        assertThat(actionsBodyBefore.path("canUrge").asBoolean()).isFalse();
        assertThat(actionsBodyBefore.path("canRead").asBoolean()).isFalse();

        String addSignResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/add-sign", taskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "请王五一起复核"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode addSignBody = objectMapper.readTree(addSignResponse);
        String addSignTaskId = addSignBody.path("data").path("nextTasks").get(0).path("taskId").asText();
        assertThat(addSignBody.path("data").path("nextTasks").get(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(addSignBody.path("data").path("nextTasks").get(0).path("taskKind").asText()).isEqualTo("ADD_SIGN");

        JsonNode actionsBodyAfter = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(actionsBodyAfter.path("canAddSign").asBoolean()).isFalse();
        assertThat(actionsBodyAfter.path("canRemoveSign").asBoolean()).isTrue();
        assertThat(actionsBodyAfter.path("canApprove").asBoolean()).isFalse();
        assertThat(actionsBodyAfter.path("canReject").asBoolean()).isFalse();

        String detailBeforeRemoveResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailBeforeRemoveBody = objectMapper.readTree(detailBeforeRemoveResponse);
        assertThat(detailBeforeRemoveBody.path("data").path("taskTrace").size()).isEqualTo(2);
        assertThat(detailBeforeRemoveBody.path("data").path("taskTrace").get(1).path("taskKind").asText()).isEqualTo("ADD_SIGN");
        assertThat(detailBeforeRemoveBody.path("data").path("taskTrace").get(1).path("sourceTaskId").asText()).isEqualTo(taskId);
        assertThat(detailBeforeRemoveBody.path("data").path("taskTrace").get(1).path("targetUserId").asText()).isEqualTo("usr_003");
        assertThat(detailBeforeRemoveBody.path("data").path("taskTrace").get(1).path("isAddSignTask").asBoolean()).isTrue();

        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/remove-sign", taskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetTaskId": "%s",
                                  "comment": "先取消加签"
                                }
                                """.formatted(addSignTaskId)))
                .andExpect(status().isOk());

        String removeTraceResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + login("lisi")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode removeTraceBody = objectMapper.readTree(removeTraceResponse);
        assertThat(removeTraceBody.path("data").path("taskTrace").get(1).path("status").asText()).isEqualTo("REVOKED");
        assertThat(removeTraceBody.path("data").path("taskTrace").get(1).path("action").asText()).isEqualTo("REMOVE_SIGN");

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "同意",
                                  "taskFormData": {
                                    "approvedDays": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(completeResponse).path("data").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldSupportUrgeAndRevokeActions() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "biz_revoke_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "催办撤销测试"
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

        JsonNode actionsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(actionsBody.path("canUrge").asBoolean()).isTrue();
        assertThat(actionsBody.path("canRevoke").asBoolean()).isTrue();

        String urgeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/urge", taskId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "请尽快审批"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(urgeResponse).path("data").path("status").asText()).isEqualTo("RUNNING");

        JsonNode afterUrgeDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        boolean hasUrgedEvent = false;
        for (JsonNode event : afterUrgeDetail.path("instanceEvents")) {
            if ("TASK_URGED".equals(event.path("eventType").asText())) {
                hasUrgedEvent = true;
                break;
            }
        }
        assertThat(hasUrgedEvent).isTrue();
        assertThat(afterUrgeDetail.path("status").asText()).isEqualTo("PENDING");

        String revokeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/revoke", taskId)
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "撤销流程"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(revokeResponse).path("data").path("status").asText()).isEqualTo("REVOKED");

        JsonNode revokeDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(revokeDetail.path("instanceStatus").asText()).isEqualTo("REVOKED");
        assertThat(revokeDetail.path("taskTrace").get(0).path("isRevoked").asBoolean()).isTrue();
    }

    @Test
    void shouldCreateRealCcTasksSupportReadAndApprovalSheetFilters() throws Exception {
        String initiatorToken = login("zhangsan");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCcProcessDsl()))
                .andExpect(status().isOk());

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave_cc",
                                  "businessKey": "biz_cc_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "抄送测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        String approvalTaskId = startBody.path("data").path("activeTasks").get(0).path("taskId").asText();

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", approvalTaskId)
                        .header("Authorization", "Bearer " + login("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(completeResponse).path("data").path("status").asText()).isEqualTo("COMPLETED");

        String detailBeforeReadResponse = mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "biz_cc_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailBeforeReadBody = objectMapper.readTree(detailBeforeReadResponse);
        assertThat(detailBeforeReadBody.path("data").path("taskTrace").size()).isEqualTo(2);
        JsonNode ccTrace = detailBeforeReadBody.path("data").path("taskTrace").get(1);
        String ccTaskId = ccTrace.path("taskId").asText();
        assertThat(ccTrace.path("taskKind").asText()).isEqualTo("CC");
        assertThat(ccTrace.path("status").asText()).isEqualTo("CC_PENDING");
        assertThat(ccTrace.path("isCcTask").asBoolean()).isTrue();
        assertThat(ccTrace.path("readTime").isNull()).isTrue();

        JsonNode ccActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/tasks/{taskId}/actions", ccTaskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");
        assertThat(ccActions.path("canRead").asBoolean()).isTrue();
        assertThat(ccActions.path("canApprove").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/read", ccTaskId)
                        .header("Authorization", "Bearer " + initiatorToken))
                .andExpect(status().isOk());

        JsonNode detailAfterReadBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/demo/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "biz_cc_001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode ccTraceAfterRead = detailAfterReadBody.path("data").path("taskTrace").get(1);
        assertThat(ccTraceAfterRead.path("status").asText()).isEqualTo("CC_READ");
        assertThat(ccTraceAfterRead.path("readTime").isNull()).isFalse();
        assertThat(detailAfterReadBody.path("data").path("instanceStatus").asText()).isEqualTo("COMPLETED");

        String approvalSheetPageResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/approval-sheets/page")
                        .header("Authorization", "Bearer " + initiatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "CC",
                                  "businessTypes": ["OA_LEAVE"],
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [
                                    {
                                      "field": "latestAction",
                                      "operator": "eq",
                                      "value": "READ"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "updatedAt",
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

        JsonNode approvalSheetPageBody = objectMapper.readTree(approvalSheetPageResponse);
        assertThat(approvalSheetPageBody.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(approvalSheetPageBody.path("data").path("records").get(0).path("currentTaskStatus").asText()).isEqualTo("CC_READ");
        assertThat(approvalSheetPageBody.path("data").path("records").get(0).path("latestAction").asText()).isEqualTo("READ");
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

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
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

    private String validHandoverProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_handover_route",
                  "processName": "离职转办审批",
                  "category": "OA",
                  "processFormKey": "oa-handover-route-form",
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
                          "userIds": ["usr_001"],
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
                      "priority": 10,
                      "label": "通过"
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

    private String validRejectRouteProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_reject_route",
                  "processName": "驳回回退审批",
                  "category": "OA",
                  "processFormKey": "oa-reject-route-form",
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
                      "id": "approve_director",
                      "type": "approver",
                      "name": "流程管理员审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_003"],
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
                      "position": {"x": 760, "y": 100},
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
                      "target": "approve_director",
                      "priority": 10,
                      "label": "通过"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_director",
                      "target": "end_1",
                      "priority": 10,
                      "label": "结束"
                    }
                  ]
                }
                """;
    }

    private String validRejectToInitiatorProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_reject_initiator",
                  "processName": "驳回发起人审批",
                  "category": "OA",
                  "processFormKey": "oa-reject-initiator-form",
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
                      "id": "approve_initiator",
                      "type": "approver",
                      "name": "发起人确认",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_001"],
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
                      "id": "approve_director",
                      "type": "approver",
                      "name": "流程管理员审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_003"],
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
                      "position": {"x": 760, "y": 100},
                      "config": {},
                      "ui": {"width": 240, "height": 88}
                    }
                  ],
                  "edges": [
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "approve_initiator",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_initiator",
                      "target": "approve_director",
                      "priority": 10,
                      "label": "通过"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_director",
                      "target": "end_1",
                      "priority": 10,
                      "label": "结束"
                    }
                  ]
                }
                """;
    }

    private String validCcProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave_cc",
                  "processName": "请假抄送审批",
                  "category": "OA",
                  "processFormKey": "oa-leave-cc-form",
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
                      "id": "cc_1",
                      "type": "cc",
                      "name": "抄送",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "targets": {
                          "mode": "USER",
                          "userIds": ["usr_001"],
                          "roleCodes": [],
                          "departmentRef": ""
                        },
                        "readRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 760, "y": 100},
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
                      "target": "cc_1",
                      "priority": 10,
                      "label": "通过"
                    },
                    {
                      "id": "edge_3",
                      "source": "cc_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "抄送完成"
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

    private void seedExpenseBill(String billId) {
        jdbcTemplate.update(
                """
                INSERT INTO oa_expense_bill (
                    id,
                    bill_no,
                    scene_code,
                    amount,
                    reason,
                    process_instance_id,
                    status,
                    creator_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                billId,
                "EXPENSE-20260322-001",
                "default",
                128.50,
                "客户接待",
                null,
                "DRAFT",
                "usr_001"
        );
    }
}
