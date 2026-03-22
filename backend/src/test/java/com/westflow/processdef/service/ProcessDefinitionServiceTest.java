package com.westflow.processdef.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.processdef.api.ProcessDefinitionDetailResponse;
import com.westflow.processdef.api.ProcessDefinitionListItemResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import java.util.List;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessDefinitionServiceTest {

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_process_definition");
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldSaveDraftAndLoadDetailFromDatabase() throws Exception {
        ProcessDefinitionDetailResponse saved = processDefinitionService.saveDraft(
                payload("oa_leave", "请假审批", "OA")
        );

        assertThat(saved.processDefinitionId()).isEqualTo("oa_leave:draft");
        assertThat(saved.version()).isEqualTo(0);
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.bpmnXml()).isBlank();

        ProcessDefinitionDetailResponse detail = processDefinitionService.detail("oa_leave:draft");
        assertThat(detail.processKey()).isEqualTo("oa_leave");
        assertThat(detail.dsl().processName()).isEqualTo("请假审批");
        assertThat(detail.dsl().processFormKey()).isEqualTo("oa_leave-form");
        assertThat(detail.dsl().processFormVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldPublishDefinitionWithNodeConfigurationInBpmn() throws Exception {
        ProcessDefinitionDetailResponse published = processDefinitionService.publish(
                payload("oa_leave", "请假审批", "OA")
        );

        assertThat(published.processDefinitionId()).isEqualTo("oa_leave:1");
        assertThat(published.bpmnXml()).contains("startEvent");
        assertThat(published.bpmnXml()).contains("initiatorEditable=\"true\"");
        assertThat(published.bpmnXml()).contains("assignmentMode=\"USER\"");
        assertThat(published.bpmnXml()).contains("operations=\"APPROVE,REJECT,RETURN\"");
    }

    @Test
    void shouldDeployPublishedDefinitionToFlowable() throws Exception {
        ProcessDefinitionDetailResponse published = processDefinitionService.publish(
                payload("oa_leave", "请假审批", "OA")
        );

        assertThat(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_leave")
                .count()).isEqualTo(1);
        assertThat(repositoryService.createDeploymentQuery()
                .deploymentName(published.processDefinitionId())
                .count()).isEqualTo(1);
    }

    @Test
    void shouldFilterDefinitionsByKeywordStatusAndCategory() throws Exception {
        publish("oa_leave_1", "请假审批一", "OA");
        publish("oa_leave_2", "请假审批二", "OA");
        publish("hr_onboarding", "入职审批", "HR");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                "请假",
                List.of(
                        new FilterItem("status", "eq", TextNode.valueOf("PUBLISHED")),
                        new FilterItem("category", "eq", TextNode.valueOf("OA"))
                ),
                List.of(),
                List.of()
        ));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::processName)
                .containsExactly("请假审批二", "请假审批一");
        assertThat(response.records()).allMatch(item -> "PUBLISHED".equals(item.status()));
    }

    @Test
    void shouldDefaultSortByCreatedAtDesc() throws Exception {
        publish("oa_leave_1", "请假审批一", "OA");
        publish("oa_leave_2", "请假审批二", "OA");
        publish("oa_leave_3", "请假审批三", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::processDefinitionId)
                .containsExactly("oa_leave_3:1", "oa_leave_2:1", "oa_leave_1:1");
    }

    @Test
    void shouldSortByCreatedAt() throws Exception {
        publish("oa_leave_1", "请假审批一", "OA");
        publish("oa_leave_2", "请假审批二", "OA");
        publish("oa_leave_3", "请假审批三", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(new SortItem("createdAt", "asc")),
                List.of()
        ));

        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::processDefinitionId)
                .containsExactly("oa_leave_1:1", "oa_leave_2:1", "oa_leave_3:1");
    }

    @Test
    void shouldSortByVersion() throws Exception {
        publish("oa_leave", "请假审批", "OA");
        publish("oa_leave", "请假审批", "OA");
        publish("hr_onboarding", "入职审批", "HR");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(new SortItem("version", "desc")),
                List.of()
        ));

        assertThat(response.records().get(0).version()).isEqualTo(2);
    }

    @Test
    void shouldSortByProcessName() throws Exception {
        publish("gamma_key", "流程Gamma", "OA");
        publish("alpha_key", "流程Alpha", "OA");
        publish("beta_key", "流程Beta", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(new SortItem("processName", "asc")),
                List.of()
        ));

        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::processName)
                .containsExactly("流程Alpha", "流程Beta", "流程Gamma");
    }

    @Test
    void shouldSortByProcessKey() throws Exception {
        publish("gamma_key", "流程Gamma", "OA");
        publish("alpha_key", "流程Alpha", "OA");
        publish("beta_key", "流程Beta", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(new SortItem("processKey", "asc")),
                List.of()
        ));

        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::processKey)
                .containsExactly("alpha_key", "beta_key", "gamma_key");
    }

    @Test
    void shouldSortByCategory() throws Exception {
        publish("k1", "流程A", "FIN");
        publish("k2", "流程B", "HR");
        publish("k3", "流程C", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                1,
                20,
                null,
                List.of(),
                List.of(new SortItem("category", "asc")),
                List.of()
        ));

        assertThat(response.records()).extracting(ProcessDefinitionListItemResponse::category)
                .containsExactly("FIN", "HR", "OA");
    }

    @Test
    void shouldReturnEmptyPageWhenRequestedPageOverflows() throws Exception {
        publish("oa_leave_1", "请假审批一", "OA");
        publish("oa_leave_2", "请假审批二", "OA");

        PageResponse<ProcessDefinitionListItemResponse> response = processDefinitionService.page(new PageRequest(
                2,
                20,
                null,
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.pages()).isEqualTo(1);
        assertThat(response.records()).isEmpty();
    }

    private void publish(String processKey, String processName, String category) throws Exception {
        processDefinitionService.publish(payload(processKey, processName, category));
    }

    private ProcessDslPayload payload(String processKey, String processName, String category) throws Exception {
        String json = """
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
                          "type": "SEQUENTIAL"
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

        return objectMapper.readValue(json, ProcessDslPayload.class);
    }
}
