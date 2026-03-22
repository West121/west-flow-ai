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
