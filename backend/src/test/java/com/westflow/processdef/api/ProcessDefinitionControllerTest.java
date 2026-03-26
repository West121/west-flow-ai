package com.westflow.processdef.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.workflowadmin.mapper.WorkflowOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = "DELETE FROM wf_process_definition", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProcessDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowOperationLogMapper workflowOperationLogMapper;

    @Test
    void shouldSaveDraftLoadDetailAndPublishDefinitionsThroughControllerContract() throws Exception {
        String token = login();

        String saveResponse = mockMvc.perform(post("/api/v1/process-definitions/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批", "OA")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode saveBody = objectMapper.readTree(saveResponse).path("data");
        assertThat(saveBody.path("processDefinitionId").asText()).isEqualTo("oa_leave:draft");
        assertThat(saveBody.path("status").asText()).isEqualTo("DRAFT");
        assertThat(saveBody.path("version").asInt()).isEqualTo(0);
        assertThat(saveBody.path("dsl").path("processKey").asText()).isEqualTo("oa_leave");
        assertThat(saveBody.path("dsl").path("processFormKey").asText()).isEqualTo("oa_leave-form");

        String detailResponse = mockMvc.perform(get("/api/v1/process-definitions/oa_leave:draft")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailBody.path("processDefinitionId").asText()).isEqualTo("oa_leave:draft");
        assertThat(detailBody.path("bpmnXml").asText()).isBlank();
        assertThat(detailBody.path("dsl").path("processName").asText()).isEqualTo("请假审批");

        String publishResponse = mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批", "OA")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode publishBody = objectMapper.readTree(publishResponse).path("data");
        assertThat(publishBody.path("processDefinitionId").asText()).isEqualTo("oa_leave:1");
        assertThat(publishBody.path("version").asInt()).isEqualTo(1);
        assertThat(publishBody.path("bpmnXml").asText()).contains("<process");
    }

    @Test
    void shouldPublishAndPageDefinitionsThroughControllerContract() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批", "OA")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave_extra", "请假补充审批", "OA")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("hr_onboarding", "入职审批", "HR")))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/v1/process-definitions/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "请假",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "PUBLISHED"
                                    },
                                    {
                                      "field": "category",
                                      "operator": "eq",
                                      "value": "OA"
                                    }
                                  ],
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

        JsonNode body = objectMapper.readTree(response);
        JsonNode data = body.path("data");

        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(data.path("page").asInt()).isEqualTo(1);
        assertThat(data.path("pageSize").asInt()).isEqualTo(20);
        assertThat(data.path("total").asInt()).isEqualTo(2);
        assertThat(data.path("pages").asInt()).isEqualTo(1);
        assertThat(data.path("records").isArray()).isTrue();
        assertThat(data.path("records").size()).isEqualTo(2);
        assertThat(data.path("records").get(0).path("processDefinitionId").asText()).isNotBlank();
        assertThat(data.path("records").get(0).path("processKey").asText()).startsWith("oa_leave");
        assertThat(data.path("records").get(0).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(data.path("records").get(0).path("createdAt").asText()).isNotBlank();
    }

    @Test
    void shouldAuthorizeWorkflowDesignerCollaborationRoom() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批", "OA")))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/process-definitions/collaboration/authorize")
                        .header("Authorization", "Bearer " + token)
                        .param("roomName", "workflow-designer:oa_leave:draft"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("roomName").asText()).isEqualTo("workflow-designer:oa_leave:draft");
        assertThat(data.path("processDefinitionId").asText()).isEqualTo("oa_leave:draft");
        assertThat(data.path("userId").asText()).isEqualTo("usr_001");
        assertThat(data.path("displayName").asText()).isEqualTo("张三");
    }

    @Test
    void shouldAuditWorkflowDesignerCollaborationEvent() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/process-definitions/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批", "OA")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/process-definitions/collaboration/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "workflow-designer:oa_leave:draft",
                                  "eventType": "DESIGNER_COLLAB_JOIN",
                                  "eventName": "加入协同房间",
                                  "details": {
                                    "connectionId": "conn-1"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(workflowOperationLogMapper.selectAll()).anySatisfy(record -> {
            assertThat(record.actionType()).isEqualTo("DESIGNER_COLLAB_JOIN");
            assertThat(record.actionName()).isEqualTo("加入协同房间");
            assertThat(record.actionCategory()).isEqualTo("COLLABORATION");
            assertThat(record.processDefinitionId()).isEqualTo("oa_leave:draft");
            assertThat(record.businessType()).isEqualTo("WORKFLOW_DESIGNER");
            assertThat(record.businessId()).isEqualTo("workflow-designer:oa_leave:draft");
            assertThat(record.operatorUserId()).isEqualTo("usr_001");
        });
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

    private String validProcessDsl(String processKey, String processName, String category) {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "%s",
                  "processName": "%s",
                  "category": "%s",
                  "processFormKey": "%s-form",
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
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """.formatted(processKey, processName, category, processKey);
    }
}
