package com.westflow.processdef.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
class PublishedDefinitionBootstrapServiceTest {

    @Autowired
    private PublishedDefinitionBootstrapService publishedDefinitionBootstrapService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_process_definition");
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldDeployPublishedDefinitionAndBackfillFlowableFields() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO wf_process_definition (
                    id, process_key, process_name, category, version, status,
                    dsl_json, bpmn_xml, publisher_user_id, deployment_id, flowable_definition_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "plm_ecr:1",
                "plm_ecr",
                "PLM ECR 变更申请",
                "PLM",
                1,
                "PUBLISHED",
                "{\"dslVersion\":\"1.0.0\",\"processKey\":\"plm_ecr\",\"processName\":\"PLM ECR 变更申请\",\"category\":\"PLM\",\"processFormKey\":\"plm-ecr-start-form\",\"processFormVersion\":\"1.0.0\",\"settings\":{\"allowWithdraw\":true,\"allowUrge\":true,\"allowTransfer\":true},\"nodes\":[],\"edges\":[]}",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://www.westflow.com/bpmn">
                  <process id="plm_ecr" name="PLM ECR 变更申请" isExecutable="true">
                    <startEvent id="start_1" name="开始"/>
                    <userTask id="approve_manager" name="部门负责人审批" flowable:assignee="usr_002"/>
                    <endEvent id="end_1" name="结束"/>
                    <sequenceFlow id="flow_1" sourceRef="start_1" targetRef="approve_manager"/>
                    <sequenceFlow id="flow_2" sourceRef="approve_manager" targetRef="end_1"/>
                  </process>
                </definitions>
                """,
                "usr_admin",
                null,
                null,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );

        publishedDefinitionBootstrapService.syncPublishedDefinitions();

        assertThat(repositoryService.createProcessDefinitionQuery().processDefinitionKey("plm_ecr").count())
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deployment_id FROM wf_process_definition WHERE id = 'plm_ecr:1'",
                String.class
        )).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT flowable_definition_id FROM wf_process_definition WHERE id = 'plm_ecr:1'",
                String.class
        )).isNotBlank();
    }
}
