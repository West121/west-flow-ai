package com.westflow.plm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.service.ProcessDefinitionService;
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

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLM 业务发起与详情接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM wf_process_definition",
        "DELETE FROM wf_business_process_binding",
        "DELETE FROM wf_business_process_link",
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
        jdbcTemplate.execute("DELETE FROM plm_ecr_change");
        jdbcTemplate.execute("DELETE FROM plm_eco_execution");
        jdbcTemplate.execute("DELETE FROM plm_material_change");
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldCreateEcrBillAndStartBoundProcess() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode data = createBill(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "升级物料版本",
                  "changeReason": "客户要求",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH"
                }
                """);

        String billId = data.path("billId").asText();
        assertThat(data.path("billNo").asText()).startsWith("ECR-");
        assertThat(data.path("processInstanceId").asText()).isNotBlank();
        assertThat(data.path("firstActiveTask").path("taskId").asText()).isNotBlank();

        JsonNode row = objectMapper.valueToTree(
                jdbcTemplate.queryForMap("SELECT bill_no, change_title, change_reason, process_instance_id, status FROM plm_ecr_change WHERE id = ?", billId)
        );
        assertThat(row.path("bill_no").asText()).isEqualTo(data.path("billNo").asText());
        assertThat(row.path("change_title").asText()).isEqualTo("升级物料版本");
        assertThat(row.path("status").asText()).isEqualTo("RUNNING");
    }

    @Test
    void shouldCreateEcoBillAndReturnDetail() throws Exception {
        String token = login();
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");

        JsonNode data = createBill(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "执行新品投产",
                  "executionPlan": "按阶段分批执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "流程变更"
                }
                """);

        String billId = data.path("billId").asText();
        String response = mockMvc.perform(get("/api/v1/plm/ecos/{billId}", billId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detail = objectMapper.readTree(response).path("data");
        assertThat(detail.path("billNo").asText()).startsWith("ECO-");
        assertThat(detail.path("executionTitle").asText()).isEqualTo("执行新品投产");
        assertThat(detail.path("status").asText()).isEqualTo("RUNNING");
    }

    @Test
    void shouldCreateMaterialBillAndStartBoundProcess() throws Exception {
        String token = login();
        publishDefinition("plm_material_change", "PLM 物料主数据变更申请");
        seedBinding("PLM_MATERIAL", "default", "plm_material_change", "bind_plm_material_default");

        JsonNode data = createBill(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-001",
                  "materialName": "主料A",
                  "changeReason": "补充属性",
                  "changeType": "ATTRIBUTE_UPDATE"
                }
                """);

        String billId = data.path("billId").asText();
        String response = mockMvc.perform(get("/api/v1/plm/material-master-changes/{billId}", billId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detail = objectMapper.readTree(response).path("data");
        assertThat(detail.path("billNo").asText()).startsWith("MAT-");
        assertThat(detail.path("materialCode").asText()).isEqualTo("MAT-001");
        assertThat(detail.path("status").asText()).isEqualTo("RUNNING");
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

        createBill(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "升级物料版本",
                  "changeReason": "客户要求",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH"
                }
                """);
        createBill(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "执行新品投产",
                  "executionPlan": "按阶段分批执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "流程变更"
                }
                """);
        createBill(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-001",
                  "materialName": "主料A",
                  "changeReason": "补充属性",
                  "changeType": "ATTRIBUTE_UPDATE"
                }
                """);

        String response = mockMvc.perform(get("/api/v1/plm/approval-sheets")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("keyword", ""))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("total").asInt()).isEqualTo(3);
        List<String> businessTypes = StreamSupport.stream(data.path("records").spliterator(), false)
                .map(node -> node.path("businessType").asText())
                .toList();
        List<String> billNos = StreamSupport.stream(data.path("records").spliterator(), false)
                .map(node -> node.path("billNo").asText())
                .toList();
        assertThat(businessTypes)
                .containsExactlyInAnyOrder("PLM_ECR", "PLM_ECO", "PLM_MATERIAL");
        assertThat(billNos)
                .allMatch(value -> value.startsWith("ECR-")
                        || value.startsWith("ECO-")
                        || value.startsWith("MAT-"));
    }

    @Test
    void shouldPageEachPlmBusinessList() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        publishDefinition("plm_material_change", "PLM 物料主数据变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");
        seedBinding("PLM_MATERIAL", "default", "plm_material_change", "bind_plm_material_default");

        JsonNode ecr = createBill(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "结构件替换",
                  "changeReason": "供应替代",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH"
                }
                """);
        JsonNode eco = createBill(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "ECO 执行",
                  "executionPlan": "通知工厂执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "量产切换"
                }
                """);
        JsonNode material = createBill(token, "/api/v1/plm/material-master-changes", """
                {
                  "sceneCode": "default",
                  "materialCode": "MAT-001",
                  "materialName": "主板总成",
                  "changeReason": "替换物料编码",
                  "changeType": "ATTRIBUTE_UPDATE"
                }
                """);

        String ecrResponse = mockMvc.perform(post("/api/v1/plm/ecrs/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "结构件",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ecrData = objectMapper.readTree(ecrResponse).path("data");
        assertThat(ecrData.path("total").asInt()).isEqualTo(1);
        assertThat(ecrData.path("records").get(0).path("billNo").asText()).isEqualTo(ecr.path("billNo").asText());

        String ecoResponse = mockMvc.perform(post("/api/v1/plm/ecos/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "执行",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ecoData = objectMapper.readTree(ecoResponse).path("data");
        assertThat(ecoData.path("total").asInt()).isEqualTo(1);
        assertThat(ecoData.path("records").get(0).path("billNo").asText()).isEqualTo(eco.path("billNo").asText());

        String materialResponse = mockMvc.perform(post("/api/v1/plm/material-master-changes/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "MAT-001",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode materialData = objectMapper.readTree(materialResponse).path("data");
        assertThat(materialData.path("total").asInt()).isEqualTo(1);
        assertThat(materialData.path("records").get(0).path("billNo").asText()).isEqualTo(material.path("billNo").asText());
    }

    @Test
    void shouldFilterEcrListByStatusAndReturnEnhancedSummaryFields() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode runningBill = createBill(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "结构件替换",
                  "changeReason": "供应替代",
                  "affectedProductCode": "PRD-1001",
                  "priorityLevel": "HIGH"
                }
                """);
        JsonNode completedBill = createBill(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "停产切换",
                  "changeReason": "生命周期调整",
                  "affectedProductCode": "PRD-1002",
                  "priorityLevel": "LOW"
                }
                """);
        jdbcTemplate.update("UPDATE plm_ecr_change SET status = 'COMPLETED' WHERE id = ?", completedBill.path("billId").asText());

        String response = mockMvc.perform(post("/api/v1/plm/ecrs/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "RUNNING"
                                    }
                                  ],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("total").asInt()).isEqualTo(1);
        assertThat(data.path("records").get(0).path("billNo").asText()).isEqualTo(runningBill.path("billNo").asText());
        assertThat(data.path("records").get(0).path("detailSummary").asText()).contains("PRD-1001");
        assertThat(data.path("records").get(0).path("approvalSummary").asText()).contains("RUNNING");
    }

    @Test
    void shouldReturnEnhancedDetailFieldsForEcoBill() throws Exception {
        String token = login();
        publishDefinition("plm_eco", "PLM ECO 变更执行");
        seedBinding("PLM_ECO", "default", "plm_eco", "bind_plm_eco_default");

        JsonNode created = createBill(token, "/api/v1/plm/ecos", """
                {
                  "sceneCode": "default",
                  "executionTitle": "执行新品投产",
                  "executionPlan": "按阶段分批执行",
                  "effectiveDate": "2026-04-01",
                  "changeReason": "流程变更"
                }
                """);

        String response = mockMvc.perform(get("/api/v1/plm/ecos/{billId}", created.path("billId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detail = objectMapper.readTree(response).path("data");
        assertThat(detail.path("detailSummary").asText()).contains("2026-04-01");
        assertThat(detail.path("approvalSummary").asText()).contains("RUNNING");
        assertThat(detail.path("creatorUserId").asText()).isEqualTo("usr_001");
        assertThat(detail.path("createdAt").asText()).isNotBlank();
        assertThat(detail.path("updatedAt").asText()).isNotBlank();
    }

    private JsonNode createBill(String token, String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data");
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
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
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
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
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
                    process_instance_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
