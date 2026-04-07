package com.westflow.plm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.service.ProcessDefinitionService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.flowable.engine.RepositoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLM 业务发起、生命周期与台账接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM wf_process_definition",
        "DELETE FROM wf_business_process_binding",
        "DELETE FROM wf_business_process_link"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PLMControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private RepositoryService repositoryService;

    @BeforeEach
    void resetRuntime() {
        createPlmTables();
        jdbcTemplate.execute("DELETE FROM plm_implementation_task");
        jdbcTemplate.execute("DELETE FROM plm_revision_diff");
        jdbcTemplate.execute("DELETE FROM plm_bill_object_link");
        jdbcTemplate.execute("DELETE FROM plm_object_revision");
        jdbcTemplate.execute("DELETE FROM plm_object_master");
        jdbcTemplate.execute("DELETE FROM plm_ecr_change");
        jdbcTemplate.execute("DELETE FROM plm_eco_execution");
        jdbcTemplate.execute("DELETE FROM plm_material_change");
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldCreateEcrBillAndStartBoundProcessWithExpandedFields() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode data = postJson(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "升级物料版本",
                  "changeReason": "客户要求",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH",
                  "changeCategory": "VERSION",
                  "targetVersion": "R2",
                  "affectedObjectsText": "BOM-001, DOC-002",
                  "impactScope": "量产机型",
                  "riskLevel": "MEDIUM",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "BOM-001", "itemName": "结构件", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");

        String billId = data.path("billId").asText();
        assertThat(data.path("billNo").asText()).startsWith("ECR-");
        assertThat(data.path("processInstanceId").asText()).isNotBlank();
        assertThat(data.path("firstActiveTask").path("taskId").asText()).isNotBlank();

        JsonNode detail = getJson(token, "/api/v1/plm/ecrs/" + billId).path("data");
        assertThat(detail.path("changeCategory").asText()).isEqualTo("VERSION");
        assertThat(detail.path("targetVersion").asText()).isEqualTo("R2");
        assertThat(detail.path("affectedObjectsText").asText()).contains("BOM-001");
        assertThat(detail.path("impactScope").asText()).isEqualTo("量产机型");
        assertThat(detail.path("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(detail.path("status").asText()).isEqualTo("RUNNING");
    }

    @Test
    void shouldSaveUpdateAndSubmitEcrDraft() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");
        seedBinding("PLM_ECR", "pilot", "plm_ecr", "bind_plm_ecr_pilot");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "草稿标题",
                  "changeReason": "草稿原因",
                  "affectedProductCode": "PRD-2001",
                  "priorityLevel": "LOW",
                  "changeCategory": "DOCUMENT",
                  "targetVersion": "D1",
                  "affectedItems": [
                    {"itemType": "DOCUMENT", "itemCode": "DOC-001", "itemName": "工艺文档", "changeAction": "CREATE"}
                  ]
                }
                """).path("data");
        String billId = created.path("billId").asText();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("processInstanceId").isNull() || created.path("processInstanceId").asText().isBlank()).isTrue();

        JsonNode updated = putJson(token, "/api/v1/plm/ecrs/" + billId + "/draft", """
                {
                  "sceneCode": "pilot",
                  "changeTitle": "草稿标题-更新",
                  "changeReason": "更新后的草稿原因",
                  "affectedProductCode": "PRD-2002",
                  "priorityLevel": "HIGH",
                  "changeCategory": "STRUCTURE",
                  "targetVersion": "D2",
                  "affectedObjectsText": "DOC-001",
                  "impactScope": "试制线",
                  "riskLevel": "HIGH",
                  "affectedItems": [
                    {"itemType": "DOCUMENT", "itemCode": "DOC-002", "itemName": "试制文档", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        assertThat(updated.path("sceneCode").asText()).isEqualTo("pilot");
        assertThat(updated.path("changeTitle").asText()).isEqualTo("草稿标题-更新");
        assertThat(updated.path("riskLevel").asText()).isEqualTo("HIGH");

        JsonNode launch = postJson(token, "/api/v1/plm/ecrs/" + billId + "/submit", "").path("data");
        assertThat(launch.path("processInstanceId").asText()).isNotBlank();
        JsonNode persisted = objectMapper.valueToTree(
                jdbcTemplate.queryForMap("SELECT status, scene_code, target_version FROM plm_ecr_change WHERE id = ?", billId)
        );
        assertThat(persisted.path("status").asText()).isEqualTo("RUNNING");
        assertThat(persisted.path("scene_code").asText()).isEqualTo("pilot");
        assertThat(persisted.path("target_version").asText()).isEqualTo("D2");
    }

    @Test
    void shouldCancelRunningEcoBillThroughLifecycleEndpoint() throws Exception {
        String token = login();
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "执行新品投产",
                  "executionPlan": "按阶段分批执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "流程变更",
                  "implementationOwner": "usr_002",
                  "targetVersion": "ECO-R1",
                  "rolloutScope": "华东工厂",
                  "validationPlan": "验证清单A",
                  "rollbackPlan": "回滚方案A",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-001", "itemName": "控制板", "changeAction": "REPLACE"}
                  ]
                }
                """).path("data");

        JsonNode cancel = postJson(token, "/api/v1/plm/ecos/" + created.path("billId").asText() + "/cancel", "").path("data");
        assertThat(cancel.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM plm_eco_execution WHERE id = ?",
                String.class,
                created.path("billId").asText()
        )).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturnDashboardSummaryAndRecentBills() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        publishDefinition("plm_material_change", "PLM 物料主数据变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");
        seedBinding("PLM_MATERIAL", "default", "plm_material_change", "bind_plm_material_default");

        JsonNode runningEcr = postJson(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "结构件替换",
                  "changeReason": "供应替代",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-002", "itemName": "机壳", "changeAction": "REPLACE"}
                  ]
                }
                """).path("data");
        postJson(token, "/api/v1/plm/ecos/draft", """
                {
                  "sceneCode": "default",
                  "executionTitle": "ECO 草稿",
                  "executionPlan": "通知工厂执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "量产切换",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-003", "itemName": "连接器", "changeAction": "UPDATE"}
                  ]
                }
                """);
        JsonNode material = postJson(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-001",
                  "materialName": "主板总成",
                  "changeReason": "替换物料编码",
                  "changeType": "ATTRIBUTE_UPDATE",
                  "specificationChange": "尺寸变更",
                  "uom": "EA",
                  "affectedItems": [
                    {"itemType": "MATERIAL", "itemCode": "MAT-001", "itemName": "主板总成", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        jdbcTemplate.update("UPDATE plm_material_change SET status = 'COMPLETED' WHERE id = ?", material.path("billId").asText());

        JsonNode summary = getJson(token, "/api/v1/plm/dashboard/summary").path("data");
        assertThat(summary.path("totalCount").asInt()).isEqualTo(3);
        assertThat(summary.path("draftCount").asInt()).isEqualTo(1);
        assertThat(summary.path("runningCount").asInt()).isEqualTo(1);
        assertThat(summary.path("completedCount").asInt()).isEqualTo(1);
        List<String> businessTypes = StreamSupport.stream(summary.path("recentBills").spliterator(), false)
                .map(node -> node.path("businessType").asText())
                .toList();
        assertThat(businessTypes).contains("PLM_ECR", "PLM_ECO", "PLM_MATERIAL");
        assertThat(StreamSupport.stream(summary.path("recentBills").spliterator(), false)
                .anyMatch(node -> node.path("billId").asText().equals(runningEcr.path("billId").asText()))).isTrue();
    }

    @Test
    void shouldExposeV4DeepDetailAndAnalytics() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "版本基线升级",
                  "changeReason": "增加深度对象记录",
                  "affectedProductCode": "PRD-2001",
                  "priorityLevel": "HIGH",
                  "changeCategory": "BOM",
                  "targetVersion": "R3",
                  "affectedObjectsText": "BOM-001, DOC-009",
                  "impactScope": "整机",
                  "riskLevel": "MEDIUM",
                  "affectedItems": [
                    {
                      "itemType": "BOM",
                      "itemCode": "BOM-001",
                      "itemName": "整机BOM",
                      "beforeVersion": "R2",
                      "afterVersion": "R3",
                      "changeAction": "UPDATE",
                      "ownerUserId": "usr_002",
                      "remark": "同步基线"
                    }
                  ]
                }
                """).path("data");

        JsonNode detail = getJson(token, "/api/v1/plm/ecrs/" + created.path("billId").asText()).path("data");
        assertThat(detail.path("objectLinks").isArray()).isTrue();
        assertThat(detail.path("revisionDiffs").isArray()).isTrue();
        assertThat(detail.path("implementationTasks").isArray()).isTrue();

        JsonNode analytics = getJson(token, "/api/v1/plm/dashboard/analytics").path("data");
        assertThat(analytics.path("summary").path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(StreamSupport.stream(analytics.path("typeDistribution").spliterator(), false)
                .anyMatch(node -> node.path("code").asText().equals("PLM_ECR"))).isTrue();
    }

    @Test
    void shouldManageImplementationTasksAndEnforceValidationRules() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode draft = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "任务流转测试",
                  "changeReason": "验证实施任务生命周期",
                  "affectedProductCode": "PRD-3001",
                  "priorityLevel": "HIGH",
                  "changeCategory": "DOCUMENT",
                  "targetVersion": "R4",
                  "affectedItems": [
                    {"itemType": "DOCUMENT", "itemCode": "DOC-3001", "itemName": "图纸A", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        String billId = draft.path("billId").asText();
        jdbcTemplate.update("UPDATE plm_ecr_change SET status = 'RUNNING' WHERE id = ?", billId);

        JsonNode implementing = postJson(token, "/api/v1/plm/ecrs/" + billId + "/implementation", """
                {
                  "implementationSummary": "开始实施任务编排"
                }
                """).path("data");
        assertThat(implementing.path("status").asText()).isEqualTo("IMPLEMENTING");

        JsonNode taskList = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks").path("data");
        assertThat(taskList.isArray()).isTrue();
        assertThat(taskList.size()).isGreaterThanOrEqualTo(1);
        String taskId = taskList.get(0).path("id").asText();

        JsonNode startedTask = putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + taskId + "/start", """
                {
                  "ownerUserId": "usr_002",
                  "resultSummary": "任务开始"
                }
                """).path("data");
        assertThat(startedTask.path("status").asText()).isEqualTo("RUNNING");

        JsonNode completedTask = putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + taskId + "/complete", """
                {
                  "ownerUserId": "usr_002",
                  "resultSummary": "任务完成"
                }
                """).path("data");
        assertThat(completedTask.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode validating = postJson(token, "/api/v1/plm/ecrs/" + billId + "/validation", """
                {
                  "validationOwner": "usr_002",
                  "validationSummary": "验证通过"
                }
                """).path("data");
        assertThat(validating.path("status").asText()).isEqualTo("VALIDATING");

        JsonNode closed = postJson(token, "/api/v1/plm/ecrs/" + billId + "/close", """
                {
                  "closedBy": "usr_002",
                  "closeComment": "关闭单据"
                }
                """).path("data");
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void shouldFilterMaterialPageBySceneStatusAndCreatedAtRange() throws Exception {
        String token = login();
        publishDefinition("plm_material_change", "PLM 物料主数据变更申请");
        seedBinding("PLM_MATERIAL", "default", "plm_material_change", "bind_plm_material_default");
        seedBinding("PLM_MATERIAL", "pilot", "plm_material_change", "bind_plm_material_pilot");

        JsonNode runningBill = postJson(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "pilot",
                  "materialCode": "MAT-001",
                  "materialName": "主板总成",
                  "changeReason": "替换物料编码",
                  "changeType": "ATTRIBUTE_UPDATE",
                  "specificationChange": "尺寸变更",
                  "uom": "EA",
                  "affectedItems": [
                    {"itemType": "MATERIAL", "itemCode": "MAT-001", "itemName": "主板总成", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        JsonNode draftBill = postJson(token, "/api/v1/plm/material-master-changes/draft", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-002",
                  "materialName": "辅料B",
                  "changeReason": "补充属性",
                  "changeType": "ATTRIBUTE_UPDATE",
                  "affectedItems": [
                    {"itemType": "MATERIAL", "itemCode": "MAT-002", "itemName": "辅料B", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        assertThat(draftBill.path("status").asText()).isEqualTo("DRAFT");

        String response = mockMvc.perform(post("/api/v1/plm/material-master-changes/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [
                                    {"field": "sceneCode", "operator": "eq", "value": "pilot"},
                                    {"field": "status", "operator": "in", "value": ["RUNNING"]},
                                    {"field": "createdAt", "operator": "between", "value": ["%s", "%s"]}
                                  ],
                                  "sorts": [],
                                  "groups": []
                                }
                                """.formatted(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("total").asInt()).isEqualTo(1);
        assertThat(data.path("records").get(0).path("billId").asText()).isEqualTo(runningBill.path("billId").asText());
        assertThat(data.path("records").get(0).path("specificationChange").asText()).isEqualTo("尺寸变更");
    }

    @Test
    void shouldPagePlmApprovalSheetsAcrossEcrEcoAndMaterialBills() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        publishDefinition("plm_material_change", "PLM 物料主数据变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");
        seedBinding("PLM_MATERIAL", "default", "plm_material_change", "bind_plm_material_default");

        postJson(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "升级物料版本",
                  "changeReason": "客户要求",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-101", "itemName": "控制器", "changeAction": "UPDATE"}
                  ]
                }
                """);
        postJson(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "执行新品投产",
                  "executionPlan": "按阶段分批执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "流程变更",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-102", "itemName": "线束", "changeAction": "UPDATE"}
                  ]
                }
                """);
        postJson(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-001",
                  "materialName": "主料A",
                  "changeReason": "补充属性",
                  "changeType": "ATTRIBUTE_UPDATE",
                  "affectedItems": [
                    {"itemType": "MATERIAL", "itemCode": "MAT-001", "itemName": "主料A", "changeAction": "UPDATE"}
                  ]
                }
                """);

        JsonNode data = getJson(token, "/api/v1/plm/approval-sheets?page=1&pageSize=20&keyword=").path("data");
        assertThat(data.path("total").asInt()).isEqualTo(3);
        List<String> businessTypes = StreamSupport.stream(data.path("records").spliterator(), false)
                .map(node -> node.path("businessType").asText())
                .toList();
        assertThat(businessTypes).containsExactlyInAnyOrder("PLM_ECR", "PLM_ECO", "PLM_MATERIAL");
    }

    private JsonNode postJson(String token, String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode putJson(String token, String path, String body) throws Exception {
        String response = mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode getJson(String token, String path) throws Exception {
        String response = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
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

    private void publishDefinition(String processKey, String processName) throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "%s",
                  "processName": "%s",
                  "category": "PLM",
                  "processFormKey": "%s_form",
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
                      "name": "业务负责人审批",
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
                """.formatted(processKey, processName, processKey), com.westflow.processdef.model.ProcessDslPayload.class));
    }

    private void seedBinding(String businessType, String sceneCode, String processKey, String bindingId) {
        jdbcTemplate.update(
                """
                INSERT INTO wf_business_process_binding (
                    id,
                    business_type,
                    scene_code,
                    process_key,
                    process_definition_id,
                    enabled,
                    priority,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                bindingId,
                businessType,
                sceneCode,
                processKey,
                null,
                true,
                10
        );
    }

    private void createPlmTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_ecr_change (
                    id VARCHAR(64) PRIMARY KEY,
                    bill_no VARCHAR(64) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    change_title VARCHAR(255) NOT NULL,
                    change_reason CLOB NOT NULL,
                    affected_product_code VARCHAR(64),
                    priority_level VARCHAR(32),
                    change_category VARCHAR(64),
                    target_version VARCHAR(128),
                    affected_objects_text CLOB,
                    impact_scope VARCHAR(1000),
                    risk_level VARCHAR(32),
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    implementation_owner VARCHAR(128),
                    implementation_summary CLOB,
                    implementation_started_at TIMESTAMP,
                    validation_owner VARCHAR(128),
                    validation_summary CLOB,
                    validated_at TIMESTAMP,
                    closed_by VARCHAR(128),
                    closed_at TIMESTAMP,
                    close_comment CLOB,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_eco_execution (
                    id VARCHAR(64) PRIMARY KEY,
                    bill_no VARCHAR(64) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    execution_title VARCHAR(255) NOT NULL,
                    execution_plan CLOB NOT NULL,
                    effective_date DATE,
                    change_reason CLOB NOT NULL,
                    implementation_owner VARCHAR(128),
                    target_version VARCHAR(128),
                    rollout_scope VARCHAR(1000),
                    validation_plan CLOB,
                    rollback_plan CLOB,
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    implementation_summary CLOB,
                    implementation_started_at TIMESTAMP,
                    validation_owner VARCHAR(128),
                    validation_summary CLOB,
                    validated_at TIMESTAMP,
                    closed_by VARCHAR(128),
                    closed_at TIMESTAMP,
                    close_comment CLOB,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_material_change (
                    id VARCHAR(64) PRIMARY KEY,
                    bill_no VARCHAR(64) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    material_code VARCHAR(64) NOT NULL,
                    material_name VARCHAR(255) NOT NULL,
                    change_reason CLOB NOT NULL,
                    change_type VARCHAR(64),
                    specification_change CLOB,
                    old_value VARCHAR(1000),
                    new_value VARCHAR(1000),
                    uom VARCHAR(64),
                    affected_systems_text VARCHAR(1000),
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    implementation_owner VARCHAR(128),
                    implementation_summary CLOB,
                    implementation_started_at TIMESTAMP,
                    validation_owner VARCHAR(128),
                    validation_summary CLOB,
                    validated_at TIMESTAMP,
                    closed_by VARCHAR(128),
                    closed_at TIMESTAMP,
                    close_comment CLOB,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_bill_affected_item (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    item_type VARCHAR(64) NOT NULL,
                    item_code VARCHAR(128) NOT NULL,
                    item_name VARCHAR(255) NOT NULL,
                    before_version VARCHAR(128),
                    after_version VARCHAR(128),
                    change_action VARCHAR(64) NOT NULL,
                    owner_user_id VARCHAR(64),
                    remark CLOB,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_object_master (
                    id VARCHAR(64) PRIMARY KEY,
                    object_type VARCHAR(64) NOT NULL,
                    object_code VARCHAR(128) NOT NULL,
                    object_name VARCHAR(255) NOT NULL,
                    owner_user_id VARCHAR(64),
                    domain_code VARCHAR(64) NOT NULL,
                    lifecycle_state VARCHAR(32) NOT NULL,
                    source_system VARCHAR(64),
                    external_ref VARCHAR(255),
                    latest_revision VARCHAR(128),
                    latest_version_label VARCHAR(128),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_object_revision (
                    id VARCHAR(64) PRIMARY KEY,
                    object_id VARCHAR(64) NOT NULL,
                    revision_code VARCHAR(128) NOT NULL,
                    version_label VARCHAR(128),
                    version_status VARCHAR(32) NOT NULL,
                    checksum VARCHAR(128),
                    summary_json CLOB,
                    snapshot_json CLOB,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_bill_object_link (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64) NOT NULL,
                    object_revision_id VARCHAR(64),
                    role_code VARCHAR(64),
                    change_action VARCHAR(32),
                    before_revision_code VARCHAR(128),
                    after_revision_code VARCHAR(128),
                    remark CLOB,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_revision_diff (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64) NOT NULL,
                    before_revision_id VARCHAR(64),
                    after_revision_id VARCHAR(64),
                    diff_kind VARCHAR(32) NOT NULL,
                    diff_summary VARCHAR(1000),
                    diff_payload_json CLOB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_implementation_task (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    task_no VARCHAR(64) NOT NULL,
                    task_title VARCHAR(255) NOT NULL,
                    task_type VARCHAR(64),
                    owner_user_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    planned_start_at TIMESTAMP,
                    planned_end_at TIMESTAMP,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    result_summary CLOB,
                    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
