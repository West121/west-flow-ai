package com.westflow.plm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.service.ProcessDefinitionService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.flowable.task.api.Task;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private FlowableEngineFacade flowableEngineFacade;

    @BeforeEach
    void resetRuntime() {
        createPlmTables();
        jdbcTemplate.execute("DELETE FROM plm_acceptance_checklist");
        jdbcTemplate.execute("DELETE FROM plm_implementation_task_evidence");
        jdbcTemplate.execute("DELETE FROM plm_implementation_task_dependency");
        jdbcTemplate.execute("DELETE FROM plm_implementation_task");
        jdbcTemplate.execute("DELETE FROM plm_revision_diff");
        jdbcTemplate.execute("DELETE FROM plm_bill_object_link");
        jdbcTemplate.execute("DELETE FROM plm_object_revision");
        jdbcTemplate.execute("DELETE FROM plm_object_master");
        jdbcTemplate.execute("DELETE FROM plm_object_acl");
        jdbcTemplate.execute("DELETE FROM plm_domain_acl");
        jdbcTemplate.execute("DELETE FROM plm_role_assignment");
        jdbcTemplate.execute("DELETE FROM plm_external_ack");
        jdbcTemplate.execute("DELETE FROM plm_connector_dispatch_log");
        jdbcTemplate.execute("DELETE FROM plm_connector_job");
        jdbcTemplate.execute("DELETE FROM plm_external_sync_event");
        jdbcTemplate.execute("DELETE FROM plm_external_integration_record");
        jdbcTemplate.execute("DELETE FROM plm_project_stage_event");
        jdbcTemplate.execute("DELETE FROM plm_project_link");
        jdbcTemplate.execute("DELETE FROM plm_project_milestone");
        jdbcTemplate.execute("DELETE FROM plm_project_member");
        jdbcTemplate.execute("DELETE FROM plm_project");
        jdbcTemplate.execute("DELETE FROM plm_configuration_baseline_item");
        jdbcTemplate.execute("DELETE FROM plm_configuration_baseline");
        jdbcTemplate.execute("DELETE FROM plm_document_asset");
        jdbcTemplate.execute("DELETE FROM plm_bom_node");
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
        JsonNode submittedEvents = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-sync-events").path("data");
        assertThat(StreamSupport.stream(submittedEvents.spliterator(), false)
                .map(node -> node.path("eventType").asText())
                .toList()).contains("BILL_SUBMITTED");
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
        JsonNode bomNodes = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/bom-nodes").path("data");
        JsonNode baselines = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/baselines").path("data");
        JsonNode acl = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/acl").path("data");
        JsonNode domainAcl = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/domain-acl").path("data");
        JsonNode roleMatrix = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/role-matrix").path("data");
        JsonNode permissions = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/permissions").path("data");
        JsonNode integrations = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/external-integrations").path("data");
        JsonNode syncEvents = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + created.path("billId").asText() + "/external-sync-events").path("data");
        assertThat(bomNodes.isArray()).isTrue();
        assertThat(baselines.isArray()).isTrue();
        assertThat(acl.isArray()).isTrue();
        assertThat(domainAcl.isArray()).isTrue();
        assertThat(roleMatrix.isArray()).isTrue();
        assertThat(permissions.path("canReadBill").asBoolean()).isTrue();
        assertThat(permissions.path("canManageBill").asBoolean()).isTrue();
        assertThat(permissions.path("canAdminAcl").asBoolean()).isTrue();
        assertThat(integrations.isArray()).isTrue();
        assertThat(syncEvents.isArray()).isTrue();
        assertThat(syncEvents.size()).isGreaterThanOrEqualTo(1);

        JsonNode analytics = getJson(token, "/api/v1/plm/dashboard/analytics").path("data");
        assertThat(analytics.path("summary").path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(StreamSupport.stream(analytics.path("typeDistribution").spliterator(), false)
                .anyMatch(node -> node.path("code").asText().equals("PLM_ECR"))).isTrue();
        JsonNode cockpit = getJson(token, "/api/v1/plm/dashboard/cockpit").path("data");
        assertThat(cockpit.path("objectTypeDistribution").isArray()).isTrue();
        assertThat(cockpit.path("integrationSystemDistribution").isArray()).isTrue();
        assertThat(cockpit.path("integrationStatusDistribution").isArray()).isTrue();
        assertThat(cockpit.path("connectorStatusDistribution").isArray()).isTrue();
        assertThat(cockpit.path("implementationHealthDistribution").isArray()).isTrue();
        assertThat(cockpit.path("stuckSyncItems").isArray()).isTrue();
        assertThat(cockpit.path("closeBlockerItems").isArray()).isTrue();
        assertThat(cockpit.path("failedSystemHotspots").isArray()).isTrue();
        assertThat(cockpit.path("connectorTaskBacklogCount").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(cockpit.path("pendingReceiptCount").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(cockpit.path("acceptanceDueCount").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(cockpit.path("implementationHealthyRate").asDouble()).isGreaterThanOrEqualTo(0D);
    }

    @Test
    void shouldCreatePageUpdateAndTransitionProject() throws Exception {
        String token = login();
        publishDefinition("plm_project_initiation", "PLM 项目立项审批");
        seedBinding("PLM_PROJECT", "default", "plm_project_initiation", "bind_plm_project_default");

        JsonNode created = postJson(token, "/api/v1/plm/projects", """
                {
                  "projectCode": "PRJ-PLM-001",
                  "projectName": "动力总成变更项目",
                  "projectType": "NPI",
                  "projectLevel": "L1",
                  "ownerUserId": "usr_001",
                  "sponsorUserId": "usr_002",
                  "domainCode": "PRODUCT",
                  "priorityLevel": "HIGH",
                  "targetRelease": "2026-Q3",
                  "startDate": "2026-04-01",
                  "targetEndDate": "2026-06-30",
                  "summary": "统一管理动力总成相关 ECO、BOM 与验证任务",
                  "businessGoal": "确保 Q3 前完成量产切换",
                  "riskSummary": "供应切换与试制验证是主要风险",
                  "members": [
                    {"userId": "usr_001", "roleCode": "PM", "roleLabel": "项目经理", "responsibilitySummary": "推进里程碑"},
                    {"userId": "usr_002", "roleCode": "RND", "roleLabel": "研发负责人", "responsibilitySummary": "控制设计变更"}
                  ],
                  "milestones": [
                    {"milestoneCode": "M1", "milestoneName": "设计冻结", "status": "PENDING", "ownerUserId": "usr_002", "plannedAt": "2026-04-20T10:00:00", "summary": "完成结构和图纸冻结"},
                    {"milestoneCode": "M2", "milestoneName": "验证完成", "status": "PENDING", "ownerUserId": "usr_001", "plannedAt": "2026-05-20T10:00:00", "summary": "完成试制验证"}
                  ],
                  "links": [
                    {"linkType": "PLM_BILL", "targetBusinessType": "PLM_ECR", "targetId": "bill-ecr-001", "targetNo": "ECR-001", "targetTitle": "动力总成结构变更", "targetStatus": "RUNNING", "summary": "核心变更申请"},
                    {"linkType": "DOCUMENT", "targetId": "doc-001", "targetNo": "DOC-001", "targetTitle": "总成图纸", "targetStatus": "RELEASED", "summary": "受控图纸"}
                  ]
                }
                """).path("data");
        String projectId = created.path("projectId").asText();
        assertThat(created.path("projectCode").asText()).isEqualTo("PRJ-PLM-001");
        assertThat(created.path("phaseCode").asText()).isEqualTo("INITIATION");
        assertThat(created.path("initiationStatus").asText()).isEqualTo("DRAFT");
        assertThat(created.path("members")).hasSize(2);
        assertThat(created.path("milestones")).hasSize(2);
        assertThat(created.path("links")).hasSize(2);

        JsonNode page = postJson(token, "/api/v1/plm/projects/page", """
                {
                  "page": 1,
                  "pageSize": 10,
                  "keyword": "动力总成",
                  "filters": [
                    {"field": "ownerUserId", "operator": "eq", "value": "usr_001"}
                  ],
                  "sorts": [
                    {"field": "updatedAt", "direction": "desc"}
                  ]
                }
                """).path("data");
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("projectId").asText()).isEqualTo(projectId);
        assertThat(page.path("records").get(0).path("initiationStatus").asText()).isEqualTo("DRAFT");

        JsonNode updated = putJson(token, "/api/v1/plm/projects/" + projectId, """
                {
                  "projectName": "动力总成变更项目-更新",
                  "projectType": "NPI",
                  "projectLevel": "L2",
                  "status": "PLANNING",
                  "phaseCode": "INITIATION",
                  "ownerUserId": "usr_001",
                  "sponsorUserId": "usr_002",
                  "domainCode": "PRODUCT",
                  "priorityLevel": "HIGH",
                  "targetRelease": "2026-Q4",
                  "startDate": "2026-04-01",
                  "targetEndDate": "2026-07-15",
                  "summary": "更新后的项目摘要",
                  "businessGoal": "完成量产导入",
                  "riskSummary": "验证窗口紧张",
                  "members": [
                    {"userId": "usr_001", "roleCode": "PM", "roleLabel": "项目经理", "responsibilitySummary": "推进整体交付"}
                  ],
                  "milestones": [
                    {"milestoneCode": "M1", "milestoneName": "设计冻结", "status": "RUNNING", "ownerUserId": "usr_002", "plannedAt": "2026-04-20T10:00:00", "summary": "结构冻结推进中"}
                  ],
                  "links": [
                    {"linkType": "PLM_BILL", "targetBusinessType": "PLM_ECO", "targetId": "bill-eco-001", "targetNo": "ECO-001", "targetTitle": "量产执行", "targetStatus": "RUNNING", "summary": "执行主单"}
                  ]
                }
                """).path("data");
        assertThat(updated.path("projectName").asText()).isEqualTo("动力总成变更项目-更新");
        assertThat(updated.path("members")).hasSize(1);
        assertThat(updated.path("milestones")).hasSize(1);
        assertThat(updated.path("links")).hasSize(1);

        JsonNode submitted = postJson(token, "/api/v1/plm/projects/" + projectId + "/submit-initiation", "").path("data");
        assertThat(submitted.path("initiationStatus").asText()).isEqualTo("PENDING_APPROVAL");
        String processInstanceId = submitted.path("initiationProcessInstanceId").asText();
        assertThat(processInstanceId).isNotBlank();

        mockMvc.perform(post("/api/v1/plm/projects/" + projectId + "/phase-transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toPhaseCode": "VALIDATION",
                                  "actionCode": "ADVANCE",
                                  "comment": "审批未通过前不能推进"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        Task activeTask = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        assertThat(activeTask).isNotNull();
        flowableEngineFacade.taskService().complete(activeTask.getId(), java.util.Map.of(
                "westflowLastAction", "APPROVE"
        ));

        JsonNode approved = getJson(token, "/api/v1/plm/projects/" + projectId).path("data");
        assertThat(approved.path("initiationStatus").asText()).isEqualTo("APPROVED");

        JsonNode transitioned = postJson(token, "/api/v1/plm/projects/" + projectId + "/phase-transition", """
                {
                  "toPhaseCode": "VALIDATION",
                  "actionCode": "ADVANCE",
                  "comment": "设计评审通过，进入验证阶段"
                }
                """).path("data");
        assertThat(transitioned.path("phaseCode").asText()).isEqualTo("VALIDATION");
        assertThat(transitioned.path("stageEvents").isArray()).isTrue();
        assertThat(transitioned.path("stageEvents").get(0).path("actionCode").asText()).isEqualTo("ADVANCE");

        JsonNode dashboard = getJson(token, "/api/v1/plm/projects/" + projectId + "/dashboard").path("data");
        assertThat(dashboard.path("memberCount").asInt()).isEqualTo(1);
        assertThat(dashboard.path("milestoneCount").asInt()).isEqualTo(1);
        assertThat(dashboard.path("linkTypeDistribution").isArray()).isTrue();
        assertThat(dashboard.path("milestoneStatusDistribution").isArray()).isTrue();
    }

    @Test
    void shouldReleaseBaselineAndDocumentAssetAndEnqueueSyncJobs() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "基线与文档发布",
                  "changeReason": "验证发布动作",
                  "affectedProductCode": "PRD-2201",
                  "priorityLevel": "HIGH",
                  "changeCategory": "DOCUMENT",
                  "targetVersion": "R5",
                  "affectedItems": [
                    {"itemType": "DOCUMENT", "itemCode": "DOC-2201", "itemName": "受控图纸", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        String billId = created.path("billId").asText();

        JsonNode baselines = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/baselines").path("data");
        JsonNode assets = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/document-assets").path("data");
        assertThat(baselines.isArray()).isTrue();
        assertThat(assets.isArray()).isTrue();
        assertThat(baselines).isNotEmpty();
        assertThat(assets).isNotEmpty();

        String baselineId = baselines.get(0).path("id").asText();
        String assetId = assets.get(0).path("id").asText();

        JsonNode baselineRelease = putJson(
                token,
                "/api/v1/plm/bills/PLM_ECR/" + billId + "/baselines/" + baselineId + "/release",
                """
                        {
                          "summaryMessage": "发布基线到下游系统"
                        }
                        """
        ).path("data");
        JsonNode assetRelease = putJson(
                token,
                "/api/v1/plm/bills/PLM_ECR/" + billId + "/document-assets/" + assetId + "/release",
                """
                        {
                          "summaryMessage": "发布文档资产到受控文库"
                        }
                        """
        ).path("data");

        assertThat(baselineRelease.path("targetType").asText()).isEqualTo("BASELINE");
        assertThat(baselineRelease.path("status").asText()).isEqualTo("RELEASED");
        assertThat(assetRelease.path("targetType").asText()).isEqualTo("DOCUMENT_ASSET");
        assertThat(assetRelease.path("status").asText()).isEqualTo("RELEASED");

        JsonNode refreshedBaselines = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/baselines").path("data");
        JsonNode refreshedAssets = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/document-assets").path("data");
        assertThat(refreshedBaselines.get(0).path("status").asText()).isEqualTo("RELEASED");
        assertThat(refreshedAssets.get(0).path("vaultState").asText()).isEqualTo("RELEASED");

        JsonNode syncEvents = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-sync-events").path("data");
        List<String> eventTypes = StreamSupport.stream(syncEvents.spliterator(), false)
                .map(node -> node.path("eventType").asText())
                .toList();
        assertThat(eventTypes).contains("BASELINE_RELEASED", "DOCUMENT_ASSET_RELEASED");

        JsonNode connectorJobs = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-jobs").path("data");
        List<String> jobTypes = StreamSupport.stream(connectorJobs.spliterator(), false)
                .map(node -> node.path("jobType").asText())
                .toList();
        assertThat(jobTypes).contains("BASELINE_RELEASED", "DOCUMENT_ASSET_RELEASED");

        JsonNode repeatedBaselineRelease = putJson(
                token,
                "/api/v1/plm/bills/PLM_ECR/" + billId + "/baselines/" + baselineId + "/release",
                "{}"
        ).path("data");
        JsonNode repeatedAssetRelease = putJson(
                token,
                "/api/v1/plm/bills/PLM_ECR/" + billId + "/document-assets/" + assetId + "/release",
                "{}"
        ).path("data");
        assertThat(repeatedBaselineRelease.path("message").asText()).contains("已处于发布状态");
        assertThat(repeatedAssetRelease.path("message").asText()).contains("已处于受控发布状态");

        JsonNode syncEventsAfterRepeat = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-sync-events").path("data");
        JsonNode connectorJobsAfterRepeat = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-jobs").path("data");
        assertThat(syncEventsAfterRepeat.size()).isEqualTo(syncEvents.size());
        assertThat(connectorJobsAfterRepeat.size()).isEqualTo(connectorJobs.size());
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
        assertThat(taskList.size()).isEqualTo(3);
        JsonNode templates = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-templates").path("data");
        JsonNode dependencies = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-dependencies").path("data");
        JsonNode checklist = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/acceptance-checklist").path("data");
        JsonNode workspace = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-workspace").path("data");
        assertThat(templates.isArray()).isTrue();
        assertThat(dependencies.isArray()).isTrue();
        assertThat(dependencies.size()).isEqualTo(2);
        assertThat(checklist.isArray()).isTrue();
        assertThat(checklist.size()).isEqualTo(3);
        assertThat(workspace.path("tasks").isArray()).isTrue();
        assertThat(workspace.path("tasks").size()).isEqualTo(3);
        assertThat(workspace.path("templates").isArray()).isTrue();
        assertThat(workspace.path("templates").size()).isEqualTo(3);
        assertThat(workspace.path("dependencies").size()).isEqualTo(2);
        assertThat(workspace.path("acceptanceCheckpoints").size()).isEqualTo(3);

        String taskId = taskList.get(0).path("id").asText();
        String secondTaskId = taskList.get(1).path("id").asText();

        mockMvc.perform(put("/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + secondTaskId + "/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": "usr_002",
                                  "resultSummary": "尝试提前开始"
                                }
                                """))
                .andExpect(status().isConflict());

        JsonNode evidence = postJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + taskId + "/evidence", """
                {
                  "evidenceType": "ATTACHMENT",
                  "evidenceName": "实施记录",
                  "evidenceRef": "minio://ecr/task1-proof.pdf",
                  "evidenceSummary": "实施留痕"
                }
                """).path("data");
        assertThat(evidence.path("taskId").asText()).isEqualTo(taskId);
        JsonNode updatedEvidence = putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-evidence/" + evidence.path("id").asText(), """
                {
                  "evidenceType": "REPORT",
                  "evidenceName": "实施记录-更新",
                  "evidenceRef": "minio://ecr/task1-proof-v2.pdf",
                  "evidenceSummary": "实施留痕更新"
                }
                """).path("data");
        assertThat(updatedEvidence.path("evidenceType").asText()).isEqualTo("REPORT");
        assertThat(updatedEvidence.path("evidenceName").asText()).isEqualTo("实施记录-更新");
        JsonNode transientEvidence = postJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + taskId + "/evidence", """
                {
                  "evidenceType": "ATTACHMENT",
                  "evidenceName": "临时证据",
                  "evidenceRef": "minio://ecr/task1-temp-proof.pdf",
                  "evidenceSummary": "临时留痕"
                }
                """).path("data");
        deleteJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-evidence/" + transientEvidence.path("id").asText());

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

        for (int i = 1; i < taskList.size(); i++) {
            String currentTaskId = taskList.get(i).path("id").asText();
            postJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + currentTaskId + "/evidence", """
                    {
                      "evidenceType": "ATTACHMENT",
                      "evidenceName": "后续任务证据",
                      "evidenceRef": "minio://ecr/task-proof-%d.pdf",
                      "evidenceSummary": "后续任务留痕"
                    }
                    """.formatted(i)).path("data");
            putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + currentTaskId + "/start", """
                    {
                      "ownerUserId": "usr_002",
                      "resultSummary": "后续任务开始"
                    }
                    """);
            putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/implementation-tasks/" + currentTaskId + "/complete", """
                    {
                      "ownerUserId": "usr_002",
                      "resultSummary": "后续任务完成"
                    }
                    """);
        }

        JsonNode validating = postJson(token, "/api/v1/plm/ecrs/" + billId + "/validation", """
                {
                  "validationOwner": "usr_002",
                  "validationSummary": "验证通过"
                }
                """).path("data");
        assertThat(validating.path("status").asText()).isEqualTo("VALIDATING");

        mockMvc.perform(post("/api/v1/plm/ecrs/" + billId + "/close")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closedBy": "usr_002",
                                  "closeComment": "未完成验收尝试关闭"
                                }
                                """))
                .andExpect(status().isConflict());

        for (JsonNode item : checklist) {
            putJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/acceptance-checklist/" + item.path("id").asText(), """
                    {
                      "status": "ACCEPTED",
                      "resultSummary": "验收完成"
                    }
                    """);
        }

        JsonNode closed = postJson(token, "/api/v1/plm/ecrs/" + billId + "/close", """
                {
                  "closedBy": "usr_002",
                  "closeComment": "关闭单据"
                }
                """).path("data");
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");

        JsonNode integrations = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-integrations").path("data");
        JsonNode syncEvents = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-sync-events").path("data");
        assertThat(integrations.isArray()).isTrue();
        assertThat(syncEvents.isArray()).isTrue();
        assertThat(StreamSupport.stream(syncEvents.spliterator(), false)
                .map(node -> node.path("eventType").asText())
                .toList()).contains("IMPLEMENTATION_STARTED", "VALIDATION_SUBMITTED", "BILL_CLOSED");
        assertThat(StreamSupport.stream(integrations.spliterator(), false)
                .anyMatch(node -> "SYNCED".equals(node.path("status").asText()) || "PENDING".equals(node.path("status").asText()))).isTrue();
    }

    @Test
    void shouldCreateRetryAndAckConnectorJobs() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs", """
                {
                  "sceneCode": "default",
                  "changeTitle": "连接器任务测试",
                  "changeReason": "验证 job/ack/retry",
                  "affectedProductCode": "PRD-5001",
                  "priorityLevel": "HIGH",
                  "changeCategory": "BOM",
                  "targetVersion": "R8",
                  "affectedItems": [
                    {"itemType": "PART", "itemCode": "PART-5001", "itemName": "执行器", "changeAction": "UPDATE"}
                  ]
                }
                """).path("data");
        String billId = created.path("billId").asText();

        JsonNode jobs = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-jobs").path("data");
        if (jobs.isArray() && jobs.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO plm_external_integration_record (
                        id, business_type, bill_id, object_id, system_code, system_name, direction_code,
                        integration_type, status, endpoint_key, external_ref, last_sync_at, message, sort_order,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, NULL, 'ERP', 'ERP 主数据', 'DOWNSTREAM', 'MASTER_SYNC', 'PENDING',
                              'plm/erp/sync', ?, CURRENT_TIMESTAMP, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    "intg_test_" + billId,
                    "PLM_ECR",
                    billId,
                    billId,
                    "测试补种连接器集成记录"
            );
            jdbcTemplate.update("""
                    INSERT INTO plm_connector_job (
                        id, business_type, bill_id, integration_id, connector_registry_id, job_type, status,
                        request_payload_json, external_ref, retry_count, next_run_at, last_dispatched_at, last_ack_at,
                        last_error, created_by, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'conn_plm_erp_sync', 'BILL_SUBMITTED', 'PENDING',
                              ?, NULL, 0, CURRENT_TIMESTAMP, NULL, NULL, NULL, 'usr_001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    "job_test_" + billId,
                    "PLM_ECR",
                    billId,
                    "intg_test_" + billId,
                    "{\"businessType\":\"PLM_ECR\",\"billId\":\"" + billId + "\",\"jobType\":\"BILL_SUBMITTED\"}"
            );
            jdbcTemplate.update("""
                    INSERT INTO plm_connector_dispatch_log (
                        id, job_id, action_type, status, request_payload_json, response_payload_json, error_message,
                        happened_at, sort_order, created_at, updated_at
                    ) VALUES (?, ?, 'ENQUEUED', 'PENDING', ?, NULL, NULL, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    "dlog_test_" + billId,
                    "job_test_" + billId,
                    "{\"businessType\":\"PLM_ECR\",\"billId\":\"" + billId + "\",\"jobType\":\"BILL_SUBMITTED\"}"
            );
            jobs = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-jobs").path("data");
        }
        assertThat(jobs.isArray()).isTrue();
        assertThat(jobs.size()).isGreaterThanOrEqualTo(1);
        JsonNode firstJob = jobs.get(0);
        assertThat(firstJob.path("jobType").asText()).isEqualTo("BILL_SUBMITTED");
        assertThat(firstJob.path("dispatchLogs").isArray()).isTrue();
        assertThat(firstJob.path("dispatchLogs").size()).isGreaterThanOrEqualTo(1);
        String jobId = firstJob.path("id").asText();

        JsonNode retried = postJson(token, "/api/v1/plm/connector-jobs/" + jobId + "/retry", "").path("data");
        assertThat(retried.path("status").asText()).isEqualTo("RETRY_PENDING");
        assertThat(retried.path("retryCount").asInt()).isEqualTo(1);

        JsonNode healthAfterRetry = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-health-summary").path("data");
        assertThat(healthAfterRetry.path("retryPendingCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(healthAfterRetry.path("unhealthyCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(healthAfterRetry.path("systems").isArray()).isTrue();

        JsonNode dispatched = postJson(token, "/api/v1/plm/connector-jobs/" + jobId + "/dispatch", "").path("data");
        assertThat(dispatched.path("status").asText()).isEqualTo("DISPATCHED");
        assertThat(StreamSupport.stream(dispatched.path("dispatchLogs").spliterator(), false)
                .anyMatch(node -> node.path("responsePayloadJson").asText().contains("\"handlerKey\":\"plm.connector.erp.stub\""))).isTrue();
        assertThat(StreamSupport.stream(dispatched.path("dispatchLogs").spliterator(), false)
                .anyMatch(node -> node.path("responsePayloadJson").asText().contains("\"mode\":\"stub\"")
                        && node.path("responsePayloadJson").asText().contains("\"endpointUrl\":\"http://localhost:18081\"")
                        && node.path("responsePayloadJson").asText().contains("\"transport\":\"http-simulated\""))).isTrue();

        mockMvc.perform(post("/api/v1/plm/connector-jobs/" + jobId + "/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/plm/connector-jobs/" + jobId + "/acks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ackStatus": "SUCCESS",
                                  "ackCode": "200",
                                  "sourceSystem": "MES",
                                  "ackToken": "MES-ACK-TOKEN",
                                  "externalRef": "ERP-REF-INVALID",
                                  "message": "错误系统回执"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/plm/connector-jobs/" + jobId + "/acks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ackStatus": "SUCCESS",
                                  "ackCode": "200",
                                  "sourceSystem": "ERP",
                                  "ackToken": "ERP-ACK-TOKEN-WRONG",
                                  "externalRef": "ERP-REF-INVALID",
                                  "message": "错误口令回执"
                                }
                                """))
                .andExpect(status().isForbidden());

        JsonNode ack = postJson(token, "/api/v1/plm/connector-jobs/" + jobId + "/acks", """
                {
                  "ackStatus": "SUCCESS",
                  "ackCode": "200",
                  "sourceSystem": "ERP",
                  "ackToken": "ERP-ACK-TOKEN",
                  "idempotencyKey": "ack-key-1001",
                  "externalRef": "ERP-REF-1001",
                  "message": "ERP 已确认收单"
                }
                """).path("data");
        assertThat(ack.path("ackStatus").asText()).isEqualTo("SUCCESS");
        assertThat(ack.path("externalRef").asText()).isEqualTo("ERP-REF-1001");
        assertThat(ack.path("sourceSystem").asText()).isEqualTo("ERP");
        assertThat(ack.path("idempotencyKey").asText()).isEqualTo("ack-key-1001");

        JsonNode duplicateAck = postJson(token, "/api/v1/plm/connector-jobs/" + jobId + "/acks", """
                {
                  "ackStatus": "SUCCESS",
                  "ackCode": "200",
                  "sourceSystem": "ERP",
                  "ackToken": "ERP-ACK-TOKEN",
                  "idempotencyKey": "ack-key-1001",
                  "externalRef": "ERP-REF-1001",
                  "message": "ERP 已确认收单"
                }
                """).path("data");
        assertThat(duplicateAck.path("id").asText()).isEqualTo(ack.path("id").asText());

        mockMvc.perform(post("/api/v1/plm/connector-jobs/" + jobId + "/acks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ackStatus": "SUCCESS",
                                  "ackCode": "200",
                                  "sourceSystem": "ERP",
                                  "idempotencyKey": "ack-key-1002",
                                  "externalRef": "ERP-REF-1002",
                                  "message": "重复回执"
                                }
                                """))
                .andExpect(status().isBadRequest());

        JsonNode refreshedJobs = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-jobs").path("data");
        JsonNode refreshedJob = StreamSupport.stream(refreshedJobs.spliterator(), false)
                .filter(node -> node.path("id").asText().equals(jobId))
                .findFirst()
                .orElseThrow();
        assertThat(refreshedJob.path("status").asText()).isEqualTo("ACKED");
        assertThat(refreshedJob.path("acknowledgements").isArray()).isTrue();
        assertThat(refreshedJob.path("acknowledgements").size()).isEqualTo(1);
        JsonNode healthAfterAck = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/connector-health-summary").path("data");
        assertThat(healthAfterAck.path("ackedCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(healthAfterAck.path("unhealthyCount").asInt()).isEqualTo(0);
        assertThat(healthAfterAck.path("latestDispatchedAt").isNull()).isFalse();
        assertThat(healthAfterAck.path("latestAckAt").isNull()).isFalse();
        JsonNode syncEvents = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/external-sync-events").path("data");
        assertThat(StreamSupport.stream(syncEvents.spliterator(), false)
                .map(node -> node.path("eventType").asText())
                .toList()).contains("BILL_SUBMITTED", "CONNECTOR_RETRY_REQUESTED", "CONNECTOR_DISPATCHED", "EXTERNAL_ACK_RECEIVED");
    }

    @Test
    void shouldAllowRoleBasedReaderToAccessPlmBillPermissionsAndDetail() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "角色矩阵访问测试",
                  "changeReason": "验证 PLM 角色可读权限",
                  "affectedProductCode": "PRD-4001",
                  "priorityLevel": "HIGH",
                  "changeCategory": "BOM",
                  "targetVersion": "R5",
                  "affectedItems": [
                    {
                      "itemType": "BOM",
                      "itemCode": "BOM-ROLE-001",
                      "itemName": "角色矩阵测试 BOM",
                      "changeAction": "UPDATE",
                      "ownerUserId": "usr_002"
                    }
                  ]
                }
                """).path("data");
        String billId = created.path("billId").asText();
        jdbcTemplate.update("UPDATE plm_ecr_change SET creator_user_id = ? WHERE id = ?", "usr_002", billId);
        seedUserRole("zhangsan", "PLM_CHANGE_MANAGER", "PLM 变更经理");

        JsonNode permissions = getJson(token, "/api/v1/plm/bills/PLM_ECR/" + billId + "/permissions").path("data");
        assertThat(permissions.path("canReadBill").asBoolean()).isTrue();
        assertThat(permissions.path("canManageBill").asBoolean()).isTrue();
        assertThat(StreamSupport.stream(permissions.path("matchedRoleCodes").spliterator(), false)
                .map(JsonNode::asText)
                .toList()).contains("PLM_CHANGE_MANAGER");

        JsonNode detail = getJson(token, "/api/v1/plm/ecrs/" + billId).path("data");
        assertThat(detail.path("billId").asText()).isEqualTo(billId);
    }

    @Test
    void shouldRejectReaderWithoutPlmRoleWhenBillCreatorChanges() throws Exception {
        String token = login();
        publishDefinition("plm_ecr", "PLM ECR 变更申请");
        seedBinding("PLM_ECR", "default", "plm_ecr", "bind_plm_ecr_default");

        JsonNode created = postJson(token, "/api/v1/plm/ecrs/draft", """
                {
                  "sceneCode": "default",
                  "changeTitle": "权限拒绝测试",
                  "changeReason": "验证无权限时返回 403",
                  "affectedProductCode": "PRD-4002",
                  "priorityLevel": "HIGH",
                  "changeCategory": "DOCUMENT",
                  "affectedItems": [
                    {
                      "itemType": "DOCUMENT",
                      "itemCode": "DOC-ROLE-002",
                      "itemName": "权限拒绝测试文档",
                      "changeAction": "UPDATE",
                      "ownerUserId": "usr_002"
                    }
                  ]
                }
                """).path("data");
        String billId = created.path("billId").asText();
        jdbcTemplate.update("UPDATE plm_ecr_change SET creator_user_id = ? WHERE id = ?", "usr_002", billId);
        removeUserRole("zhangsan", "PLM_CHANGE_MANAGER");
        removeUserRole("zhangsan", "PLM_DOC_CONTROLLER");
        removeUserRole("zhangsan", "PLM_QUALITY_OWNER");

        mockMvc.perform(get("/api/v1/plm/ecrs/" + billId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/plm/bills/PLM_ECR/" + billId + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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

    private void deleteJson(String token, String path) throws Exception {
        mockMvc.perform(delete(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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

    private void seedUserRole(String username, String roleCode, String roleName) {
        String roleId = jdbcTemplate.query(
                        "SELECT id FROM wf_role WHERE role_code = ?",
                        (rs, rowNum) -> rs.getString("id"),
                        roleCode
                ).stream()
                .findFirst()
                .orElseGet(() -> {
                    String generatedRoleId = "role_test_" + roleCode.toLowerCase();
                    jdbcTemplate.update(
                            """
                            INSERT INTO wf_role (id, role_code, role_name, role_category, description, enabled, created_at, updated_at)
                            VALUES (?, ?, ?, 'SYSTEM', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """,
                            generatedRoleId,
                            roleCode,
                            roleName,
                            roleName + "测试角色"
                    );
                    return generatedRoleId;
                });
        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM wf_user WHERE username = ?",
                String.class,
                username
        );
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_user_role WHERE user_id = ? AND role_id = ?",
                Integer.class,
                userId,
                roleId
        );
        if (existing != null && existing == 0) {
            jdbcTemplate.update(
                    "INSERT INTO wf_user_role (id, user_id, role_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    "ur_test_" + roleCode.toLowerCase() + "_" + userId,
                    userId,
                    roleId
            );
        }
    }

    private void removeUserRole(String username, String roleCode) {
        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM wf_user WHERE username = ?",
                String.class,
                username
        );
        jdbcTemplate.update(
                """
                DELETE FROM wf_user_role
                WHERE user_id = ?
                  AND role_id IN (SELECT id FROM wf_role WHERE role_code = ?)
                """,
                userId,
                roleCode
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
                    template_id VARCHAR(64),
                    template_code VARCHAR(128),
                    task_title VARCHAR(255) NOT NULL,
                    task_type VARCHAR(64),
                    owner_user_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    planned_start_at TIMESTAMP,
                    planned_end_at TIMESTAMP,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    result_summary CLOB,
                    required_evidence_count INT NOT NULL DEFAULT 0,
                    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_implementation_template (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    template_code VARCHAR(128) NOT NULL,
                    template_name VARCHAR(255) NOT NULL,
                    task_type VARCHAR(64) NOT NULL,
                    default_task_title VARCHAR(255),
                    default_owner_role_code VARCHAR(128),
                    required_evidence_count INT NOT NULL DEFAULT 0,
                    verification_required BOOLEAN NOT NULL DEFAULT TRUE,
                    sort_order INT NOT NULL DEFAULT 0,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_implementation_template_dependency (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    predecessor_template_code VARCHAR(128) NOT NULL,
                    successor_template_code VARCHAR(128) NOT NULL,
                    dependency_type VARCHAR(32) NOT NULL,
                    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
                    sort_order INT NOT NULL DEFAULT 0,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_implementation_task_dependency (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    predecessor_task_id VARCHAR(64) NOT NULL,
                    successor_task_id VARCHAR(64) NOT NULL,
                    dependency_type VARCHAR(32) NOT NULL,
                    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_implementation_task_evidence (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    task_id VARCHAR(64) NOT NULL,
                    evidence_type VARCHAR(64) NOT NULL,
                    evidence_name VARCHAR(255) NOT NULL,
                    evidence_ref VARCHAR(500),
                    evidence_summary VARCHAR(1000),
                    uploaded_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_acceptance_checklist_template (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    scene_code VARCHAR(64) NOT NULL,
                    check_code VARCHAR(128) NOT NULL,
                    check_name VARCHAR(255) NOT NULL,
                    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
                    sort_order INT NOT NULL DEFAULT 0,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_acceptance_checklist (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    check_code VARCHAR(128) NOT NULL,
                    check_name VARCHAR(255) NOT NULL,
                    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    result_summary VARCHAR(1000),
                    checked_by VARCHAR(64),
                    checked_at TIMESTAMP,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                MERGE INTO plm_implementation_template (
                    id, business_type, scene_code, template_code, template_name, task_type, default_task_title,
                    default_owner_role_code, required_evidence_count, verification_required, sort_order, enabled, created_at, updated_at
                ) KEY (id) VALUES
                ('plm_tpl_ecr_plan', 'PLM_ECR', 'default', 'ECR_IMPL_PLAN', 'ECR 实施计划', 'IMPLEMENTATION', '工程变更实施', 'PLM_CHANGE_MANAGER', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_ecr_doc', 'PLM_ECR', 'default', 'ECR_DOC_RELEASE', 'ECR 文档发布', 'DOCUMENT', '图纸与文档发布', 'PLM_DOC_CONTROLLER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_ecr_quality', 'PLM_ECR', 'default', 'ECR_QUALITY_VERIFY', 'ECR 质量验证', 'VALIDATION', '质量验证与归档', 'PLM_QUALITY_OWNER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_eco_rollout', 'PLM_ECO', 'default', 'ECO_MANUFACTURING_ROLLOUT', 'ECO 生产切换', 'ROLLOUT', '生产切换与现场执行', 'PLM_MANUFACTURING_OWNER', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_eco_quality', 'PLM_ECO', 'default', 'ECO_QUALITY_VERIFY', 'ECO 质量确认', 'VALIDATION', '质量确认与放行', 'PLM_QUALITY_OWNER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_eco_erp', 'PLM_ECO', 'default', 'ECO_ERP_SYNC', 'ECO ERP 同步', 'SYNC', 'ERP / MES 同步确认', 'PLM_ERP_OWNER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_mat_data', 'PLM_MATERIAL', 'default', 'MAT_MASTER_UPDATE', '物料主数据更新', 'DATA_CHANGE', '主数据更新执行', 'PLM_DATA_STEWARD', 1, TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_mat_erp', 'PLM_MATERIAL', 'default', 'MAT_ERP_SYNC', '物料 ERP 同步', 'SYNC', 'ERP 编码与主数据同步', 'PLM_ERP_OWNER', 1, TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_tpl_mat_confirm', 'PLM_MATERIAL', 'default', 'MAT_CHANGE_CONFIRM', '物料变更确认', 'CONFIRM', '变更确认与关闭准备', 'PLM_CHANGE_MANAGER', 1, TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                MERGE INTO plm_implementation_template_dependency (
                    id, business_type, scene_code, predecessor_template_code, successor_template_code, dependency_type, required_flag, sort_order, enabled, created_at, updated_at
                ) KEY (id) VALUES
                ('plm_dep_ecr_1', 'PLM_ECR', 'default', 'ECR_IMPL_PLAN', 'ECR_DOC_RELEASE', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_dep_ecr_2', 'PLM_ECR', 'default', 'ECR_DOC_RELEASE', 'ECR_QUALITY_VERIFY', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_dep_eco_1', 'PLM_ECO', 'default', 'ECO_MANUFACTURING_ROLLOUT', 'ECO_QUALITY_VERIFY', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_dep_eco_2', 'PLM_ECO', 'default', 'ECO_QUALITY_VERIFY', 'ECO_ERP_SYNC', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_dep_mat_1', 'PLM_MATERIAL', 'default', 'MAT_MASTER_UPDATE', 'MAT_ERP_SYNC', 'FINISH_TO_START', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_dep_mat_2', 'PLM_MATERIAL', 'default', 'MAT_ERP_SYNC', 'MAT_CHANGE_CONFIRM', 'FINISH_TO_START', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                MERGE INTO plm_acceptance_checklist_template (
                    id, business_type, scene_code, check_code, check_name, required_flag, sort_order, enabled, created_at, updated_at
                ) KEY (id) VALUES
                ('plm_chk_ecr_scope', 'PLM_ECR', 'default', 'SCOPE_CONFIRMED', '实施范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_ecr_evidence', 'PLM_ECR', 'default', 'EVIDENCE_ARCHIVED', '实施证据已归档', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_ecr_external', 'PLM_ECR', 'default', 'EXTERNAL_SYNC_VERIFIED', '外部系统同步结果已核对', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_eco_scope', 'PLM_ECO', 'default', 'ROLLOUT_CONFIRMED', '生产切换范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_eco_quality', 'PLM_ECO', 'default', 'QUALITY_ACCEPTED', '质量确认已完成', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_eco_sync', 'PLM_ECO', 'default', 'ERP_SYNC_ACCEPTED', 'ERP / MES 同步已确认', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_mat_scope', 'PLM_MATERIAL', 'default', 'MATERIAL_SCOPE_CONFIRMED', '物料变更范围已确认', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_mat_sync', 'PLM_MATERIAL', 'default', 'MATERIAL_SYNC_ACCEPTED', '主数据同步结果已核对', TRUE, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('plm_chk_mat_close', 'PLM_MATERIAL', 'default', 'MATERIAL_CLOSE_READY', '关闭条件已确认', TRUE, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_bom_node (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    parent_node_id VARCHAR(64),
                    object_id VARCHAR(64),
                    node_code VARCHAR(128) NOT NULL,
                    node_name VARCHAR(255) NOT NULL,
                    node_type VARCHAR(64) NOT NULL,
                    quantity DECIMAL(18,4),
                    unit VARCHAR(32),
                    effectivity VARCHAR(255),
                    change_action VARCHAR(32),
                    hierarchy_level INT NOT NULL DEFAULT 0,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_document_asset (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64),
                    document_code VARCHAR(128) NOT NULL,
                    document_name VARCHAR(255) NOT NULL,
                    document_type VARCHAR(64) NOT NULL,
                    version_label VARCHAR(128),
                    vault_state VARCHAR(32) NOT NULL,
                    file_name VARCHAR(255),
                    file_type VARCHAR(32),
                    source_system VARCHAR(64),
                    external_ref VARCHAR(255),
                    change_action VARCHAR(32),
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_configuration_baseline (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    baseline_code VARCHAR(128) NOT NULL,
                    baseline_name VARCHAR(255) NOT NULL,
                    baseline_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    released_at TIMESTAMP,
                    summary_json CLOB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_configuration_baseline_item (
                    id VARCHAR(64) PRIMARY KEY,
                    baseline_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64),
                    object_code VARCHAR(128) NOT NULL,
                    object_name VARCHAR(255) NOT NULL,
                    object_type VARCHAR(64) NOT NULL,
                    before_revision_code VARCHAR(128),
                    after_revision_code VARCHAR(128),
                    effectivity VARCHAR(255),
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_object_acl (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64),
                    subject_type VARCHAR(32) NOT NULL,
                    subject_code VARCHAR(128) NOT NULL,
                    permission_code VARCHAR(128) NOT NULL,
                    access_scope VARCHAR(64) NOT NULL,
                    inherited BOOLEAN NOT NULL DEFAULT FALSE,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_domain_acl (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    domain_code VARCHAR(64) NOT NULL,
                    role_code VARCHAR(128) NOT NULL,
                    permission_code VARCHAR(128) NOT NULL,
                    access_scope VARCHAR(64) NOT NULL,
                    policy_source VARCHAR(64) NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_role_assignment (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    role_code VARCHAR(128) NOT NULL,
                    role_label VARCHAR(255) NOT NULL,
                    assignee_user_id VARCHAR(64),
                    assignee_display_name VARCHAR(255),
                    assignment_scope VARCHAR(64) NOT NULL,
                    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
                    status VARCHAR(32) NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_external_integration_record (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    object_id VARCHAR(64),
                    system_code VARCHAR(64) NOT NULL,
                    system_name VARCHAR(255) NOT NULL,
                    direction_code VARCHAR(32) NOT NULL,
                    integration_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    endpoint_key VARCHAR(128),
                    external_ref VARCHAR(255),
                    last_sync_at TIMESTAMP,
                    message VARCHAR(500),
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_external_sync_event (
                    id VARCHAR(64) PRIMARY KEY,
                    integration_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    payload_json CLOB,
                    error_message VARCHAR(500),
                    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_connector_registry (
                    id VARCHAR(64) PRIMARY KEY,
                    connector_code VARCHAR(128) NOT NULL UNIQUE,
                    system_code VARCHAR(64) NOT NULL,
                    system_name VARCHAR(255) NOT NULL,
                    direction_code VARCHAR(32) NOT NULL,
                    handler_key VARCHAR(128) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    supports_retry BOOLEAN NOT NULL DEFAULT TRUE,
                    supported_events_json CLOB,
                    config_json CLOB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_connector_job (
                    id VARCHAR(64) PRIMARY KEY,
                    business_type VARCHAR(32) NOT NULL,
                    bill_id VARCHAR(64) NOT NULL,
                    integration_id VARCHAR(64) NOT NULL,
                    connector_registry_id VARCHAR(64) NOT NULL,
                    job_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    request_payload_json CLOB,
                    external_ref VARCHAR(255),
                    retry_count INT NOT NULL DEFAULT 0,
                    next_run_at TIMESTAMP,
                    last_dispatched_at TIMESTAMP,
                    last_ack_at TIMESTAMP,
                    last_error VARCHAR(500),
                    created_by VARCHAR(64),
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_connector_dispatch_log (
                    id VARCHAR(64) PRIMARY KEY,
                    job_id VARCHAR(64) NOT NULL,
                    action_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    request_payload_json CLOB,
                    response_payload_json CLOB,
                    error_message VARCHAR(500),
                    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_external_ack (
                    id VARCHAR(64) PRIMARY KEY,
                    job_id VARCHAR(64) NOT NULL,
                    ack_status VARCHAR(32) NOT NULL,
                    ack_code VARCHAR(64),
                    idempotency_key VARCHAR(128),
                    external_ref VARCHAR(255),
                    message VARCHAR(500),
                    payload_json CLOB,
                    source_system VARCHAR(64),
                    happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_plm_external_ack_job_idempotency
                ON plm_external_ack (job_id, idempotency_key)
                """);
        jdbcTemplate.update("""
                MERGE INTO plm_connector_registry (
                    id, connector_code, system_code, system_name, direction_code, handler_key,
                    enabled, supports_retry, supported_events_json, config_json, created_at, updated_at
                ) KEY (id) VALUES
                ('conn_plm_erp_sync', 'PLM_ERP_SYNC', 'ERP', 'ERP 主数据', 'DOWNSTREAM', 'plm.connector.erp.stub', TRUE, TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('conn_plm_mes_sync', 'PLM_MES_SYNC', 'MES', 'MES 制造执行', 'DOWNSTREAM', 'plm.connector.mes.stub', TRUE, TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('conn_plm_pdm_release', 'PLM_PDM_RELEASE', 'PDM', 'PDM 文档库', 'DOWNSTREAM', 'plm.connector.pdm.stub', TRUE, TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('conn_plm_cad_publish', 'PLM_CAD_PUBLISH', 'CAD', 'CAD 图档中心', 'DOWNSTREAM', 'plm.connector.cad.stub', TRUE, TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "[\"BILL_SUBMITTED\",\"IMPLEMENTATION_STARTED\",\"VALIDATION_SUBMITTED\",\"BILL_CLOSED\",\"BILL_CANCELLED\"]",
                "{\"transport\":\"stub\",\"target\":\"ERP\",\"ackToken\":\"ERP-ACK-TOKEN\"}",
                "[\"BILL_SUBMITTED\",\"IMPLEMENTATION_STARTED\",\"VALIDATION_SUBMITTED\",\"BILL_CLOSED\",\"BILL_CANCELLED\"]",
                "{\"transport\":\"stub\",\"target\":\"MES\",\"ackToken\":\"MES-ACK-TOKEN\"}",
                "[\"BILL_SUBMITTED\",\"IMPLEMENTATION_STARTED\",\"VALIDATION_SUBMITTED\",\"BILL_CLOSED\",\"BILL_CANCELLED\"]",
                "{\"transport\":\"stub\",\"target\":\"PDM\",\"ackToken\":\"PDM-ACK-TOKEN\"}",
                "[\"BILL_SUBMITTED\",\"IMPLEMENTATION_STARTED\",\"VALIDATION_SUBMITTED\",\"BILL_CLOSED\",\"BILL_CANCELLED\"]",
                "{\"transport\":\"stub\",\"target\":\"CAD\",\"ackToken\":\"CAD-ACK-TOKEN\"}"
        );
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_project (
                    id VARCHAR(64) PRIMARY KEY,
                    project_no VARCHAR(64) NOT NULL,
                    project_code VARCHAR(128) NOT NULL,
                    project_name VARCHAR(255) NOT NULL,
                    project_type VARCHAR(64) NOT NULL,
                    project_level VARCHAR(32),
                    status VARCHAR(32) NOT NULL,
                    phase_code VARCHAR(64) NOT NULL,
                    owner_user_id VARCHAR(64),
                    sponsor_user_id VARCHAR(64),
                    domain_code VARCHAR(64),
                    priority_level VARCHAR(32),
                    target_release VARCHAR(128),
                    start_date DATE,
                    target_end_date DATE,
                    actual_end_date DATE,
                    summary CLOB,
                    business_goal CLOB,
                    risk_summary CLOB,
                    creator_user_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_project_member (
                    id VARCHAR(64) PRIMARY KEY,
                    project_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    role_code VARCHAR(64) NOT NULL,
                    role_label VARCHAR(128) NOT NULL,
                    responsibility_summary CLOB,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_project_milestone (
                    id VARCHAR(64) PRIMARY KEY,
                    project_id VARCHAR(64) NOT NULL,
                    milestone_code VARCHAR(128) NOT NULL,
                    milestone_name VARCHAR(255) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    owner_user_id VARCHAR(64),
                    planned_at TIMESTAMP,
                    actual_at TIMESTAMP,
                    summary CLOB,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_project_link (
                    id VARCHAR(64) PRIMARY KEY,
                    project_id VARCHAR(64) NOT NULL,
                    link_type VARCHAR(64) NOT NULL,
                    target_business_type VARCHAR(32),
                    target_id VARCHAR(64) NOT NULL,
                    target_no VARCHAR(128),
                    target_title VARCHAR(255),
                    target_status VARCHAR(64),
                    target_href VARCHAR(500),
                    summary CLOB,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plm_project_stage_event (
                    id VARCHAR(64) PRIMARY KEY,
                    project_id VARCHAR(64) NOT NULL,
                    from_phase_code VARCHAR(64),
                    to_phase_code VARCHAR(64) NOT NULL,
                    action_code VARCHAR(64) NOT NULL,
                    comment CLOB,
                    changed_by VARCHAR(64) NOT NULL,
                    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
