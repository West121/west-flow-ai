package com.westflow.processdef.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class ProcessDefinitionLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSaveDraftFetchDetailPublishAndPageFromDatabase() throws Exception {
        String token = login();
        String draftPayload = validProcessDsl("oa_leave", "请假审批", "OA");

        JsonNode draftBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-definitions/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String draftId = draftBody.path("data").path("processDefinitionId").asText();
        assertThat(draftId).isNotBlank();
        assertThat(draftBody.path("data").path("status").asText()).isEqualTo("DRAFT");
        assertThat(draftBody.path("data").path("version").asInt()).isEqualTo(0);

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-definitions/{processDefinitionId}", draftId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(detailBody.path("data").path("processDefinitionId").asText()).isEqualTo(draftId);
        assertThat(detailBody.path("data").path("status").asText()).isEqualTo("DRAFT");
        assertThat(detailBody.path("data").path("dsl").path("processKey").asText()).isEqualTo("oa_leave");
        assertThat(detailBody.path("data").path("dsl").path("nodes").size()).isEqualTo(3);

        JsonNode publishBody1 = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String publishId1 = publishBody1.path("data").path("processDefinitionId").asText();
        assertThat(publishBody1.path("data").path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishBody1.path("data").path("version").asInt()).isEqualTo(1);
        assertThat(publishBody1.path("data").path("bpmnXml").asText()).contains("<process");

        JsonNode publishBody2 = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProcessDsl("oa_leave", "请假审批-新版", "OA")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String publishId2 = publishBody2.path("data").path("processDefinitionId").asText();
        assertThat(publishId2).isNotEqualTo(publishId1);
        assertThat(publishBody2.path("data").path("version").asInt()).isEqualTo(2);

        JsonNode pageBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-definitions/page")
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
                .getContentAsString());

        assertThat(pageBody.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(pageBody.path("data").path("records").get(0).path("version").asInt()).isEqualTo(2);
        assertThat(pageBody.path("data").path("records").get(1).path("version").asInt()).isEqualTo(1);
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
                  "formKey": "%s-form",
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
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """.formatted(processKey, processName, category, processKey);
    }
}
