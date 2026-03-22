package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlowableRuntimeStartServiceTest {

    @Autowired
    private FlowableRuntimeStartService flowableRuntimeStartService;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每次测试前清理流程定义与引擎实例，避免历史部署互相污染。
     */
    @BeforeEach
    void setUp() {
        StpUtil.logout();
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
        jdbcTemplate.update("DELETE FROM wf_process_definition");
    }

    @Test
    void shouldStartRealFlowableInstanceAndReturnFirstActiveTask() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {
                        "initiatorEditable": true
                      },
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
                        "operations": ["APPROVE", "REJECT", "RETURN"]
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
                """, ProcessDslPayload.class));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_leave",
                "leave_bill_001",
                "OA_LEAVE",
                java.util.Map.of("days", 3, "reason", "年假")
        ));

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(response.instanceId())
                .singleResult();
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getBusinessKey()).isEqualTo("leave_bill_001");
        assertThat(response.processDefinitionId()).isEqualTo("oa_leave:1");
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.activeTasks()).singleElement().satisfies(task -> {
            assertThat(task.nodeId()).isEqualTo("approve_manager");
            assertThat(task.nodeName()).isEqualTo("部门负责人审批");
            assertThat(task.assigneeUserId()).isEqualTo("usr_002");
            assertThat(task.status()).isEqualTo("PENDING");
        });
    }
}
