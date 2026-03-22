package com.westflow.processruntime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessRuntimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        assertThat(publishBody.path("data").path("version").asInt()).isEqualTo(1);
        assertThat(publishBody.path("data").path("bpmnXml").asText()).contains("<process");

        String startResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "biz_001",
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

        String completeResponse = mockMvc.perform(post("/api/v1/process-runtime/demo/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "同意"
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
                                  "formKey": "oa-leave-form",
                                  "formVersion": "1.0.0",
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

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "password123"
                                }
                                """))
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
                  "formKey": "oa-leave-form",
                  "formVersion": "1.0.0",
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
}
