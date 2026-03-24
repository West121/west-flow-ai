package com.westflow.processruntime.service.append;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.model.PublishedProcessDefinition;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.CountersignAssigneeResolver;
import com.westflow.processruntime.service.FlowableTaskActionService;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.processruntime.service.RuntimeAppendLinkService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicBuildAppendRuntimeServiceTest {

    @Mock
    private FlowableEngineFacade flowableEngineFacade;

    @Mock
    private ProcessDefinitionService processDefinitionService;

    @Mock
    private FlowableTaskActionService flowableTaskActionService;

    @Mock
    private RuntimeAppendLinkService runtimeAppendLinkService;

    @Mock
    private ProcessLinkService processLinkService;

    @Mock
    private BusinessProcessBindingService businessProcessBindingService;

    @Mock
    private CountersignAssigneeResolver countersignAssigneeResolver;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private Task createdTask;

    @InjectMocks
    private DynamicBuildAppendRuntimeService dynamicBuildAppendRuntimeService;

    @Test
    void shouldCreateAppendTaskAndPersistLinkFromDynamicBuilder() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001"
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildDynamicBuilderParentDefinition());
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> localVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowableTaskActionService).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                localVariablesCaptor.capture()
        );
        assertThat(localVariablesCaptor.getValue())
                .containsEntry("westflowDynamicResolvedSourceMode", "MANUAL_TEMPLATE")
                .containsEntry("westflowDynamicResolutionPath", "TEMPLATE_PRIMARY")
                .containsEntry("westflowDynamicTemplateSource", "MANUAL_TEMPLATE")
                .containsEntry("westflowDynamicExecutionStrategy", "TEMPLATE_FIRST")
                .containsEntry("westflowDynamicFallbackStrategy", "KEEP_CURRENT");

        ArgumentCaptor<RuntimeAppendLinkRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeAppendLinkRecord.class);
        verify(runtimeAppendLinkService).createLink(recordCaptor.capture());
        RuntimeAppendLinkRecord record = recordCaptor.getValue();
        assertThat(record.appendType()).isEqualTo("TASK");
        assertThat(record.runtimeLinkType()).isEqualTo("ADHOC_TASK");
        assertThat(record.triggerMode()).isEqualTo("DYNAMIC_BUILD");
        assertThat(record.targetTaskId()).isEqualTo("task_001");
        assertThat(record.rootInstanceId()).isEqualTo("instance_1");
        assertThat(record.parentInstanceId()).isEqualTo("instance_1");
        assertThat(record.sourceNodeId()).isEqualTo("dynamic_builder_1");
        assertThat(record.sourceTaskId()).isEqualTo("instance_1::dynamic-build::dynamic_builder_1");
        assertThat(record.targetUserId()).isEqualTo("usr_002");
    }

    @Test
    void shouldCreateAppendSubprocessAndPersistLinkFromDynamicBuilder() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_subprocess",
                "westflowBusinessKey", "biz_001",
                "westflowBusinessType", "OA_COMMON",
                "westflowInitiatorUserId", "usr_001"
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildDynamicBuilderParentDefinitionForSubprocess());
        when(processDefinitionService.getLatestByProcessKey("oa_sub_review")).thenReturn(buildChildDefinition());
        when(processInstance.getProcessInstanceId()).thenReturn("child_001");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap())).thenReturn(processInstance);

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> childVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), childVariablesCaptor.capture());
        assertThat(childVariablesCaptor.getValue())
                .containsEntry("westflowDynamicResolvedSourceMode", "MANUAL_TEMPLATE")
                .containsEntry("westflowDynamicResolutionPath", "TEMPLATE_PRIMARY")
                .containsEntry("westflowDynamicTemplateSource", "MANUAL_TEMPLATE");

        ArgumentCaptor<RuntimeAppendLinkRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeAppendLinkRecord.class);
        verify(runtimeAppendLinkService).createLink(recordCaptor.capture());
        RuntimeAppendLinkRecord record = recordCaptor.getValue();
        assertThat(record.appendType()).isEqualTo("SUBPROCESS");
        assertThat(record.runtimeLinkType()).isEqualTo("ADHOC_SUBPROCESS");
        assertThat(record.triggerMode()).isEqualTo("DYNAMIC_BUILD");
        assertThat(record.targetInstanceId()).isEqualTo("child_001");
        assertThat(record.calledProcessKey()).isEqualTo("oa_sub_review");
        assertThat(record.calledDefinitionId()).isEqualTo("child_pd_001");
        assertThat(record.calledVersionPolicy()).isEqualTo("LATEST_PUBLISHED");
        assertThat(record.resolvedTargetMode()).isEqualTo("PROCESS_KEY");
        assertThat(record.targetBusinessType()).isEqualTo("OA_COMMON");
        assertThat(record.rootInstanceId()).isEqualTo("instance_1");
        assertThat(record.parentInstanceId()).isEqualTo("instance_1");
        assertThat(record.sourceNodeId()).isEqualTo("dynamic_builder_1");
    }

    @Test
    void shouldCreateAppendSubprocessFromModelDrivenSceneCode() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_subprocess",
                "westflowBusinessKey", "biz_001",
                "westflowBusinessType", "OA_COMMON",
                "westflowInitiatorUserId", "usr_001"
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildModelDrivenParentDefinitionForSubprocess());
        when(businessProcessBindingService.resolveProcessKey("OA_COMMON", "oa_sub_review")).thenReturn("oa_sub_review");
        when(processDefinitionService.getLatestByProcessKey("oa_sub_review")).thenReturn(buildChildDefinition());
        when(processInstance.getProcessInstanceId()).thenReturn("child_001");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap())).thenReturn(processInstance);

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> childVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), childVariablesCaptor.capture());
        assertThat(childVariablesCaptor.getValue())
                .containsEntry("westflowDynamicResolvedSourceMode", "MODEL_DRIVEN")
                .containsEntry("westflowDynamicResolutionPath", "TEMPLATE_PRIMARY")
                .containsEntry("westflowDynamicTemplateSource", "SCENE_CODE");

        ArgumentCaptor<RuntimeAppendLinkRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeAppendLinkRecord.class);
        verify(runtimeAppendLinkService).createLink(recordCaptor.capture());
        RuntimeAppendLinkRecord record = recordCaptor.getValue();
        assertThat(record.calledVersionPolicy()).isEqualTo("LATEST_PUBLISHED");
        assertThat(record.resolvedTargetMode()).isEqualTo("PROCESS_KEY");
        assertThat(record.targetBusinessType()).isEqualTo("OA_COMMON");
        assertThat(record.targetSceneCode()).isEqualTo("oa_sub_review");
        assertThat(record.appendType()).isEqualTo("SUBPROCESS");
        assertThat(record.calledProcessKey()).isEqualTo("oa_sub_review");
        assertThat(record.targetInstanceId()).isEqualTo("child_001");
        verify(businessProcessBindingService).resolveProcessKey("OA_COMMON", "oa_sub_review");
    }

    @Test
    void shouldCreateAppendTasksFromModelDrivenSceneCodeTargets() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "leaveDays", 5
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildModelDrivenTaskParentDefinition());
        when(countersignAssigneeResolver.resolve(anyMap(), anyMap())).thenReturn(List.of("usr_010", "usr_011"));
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> localVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowableTaskActionService, org.mockito.Mockito.times(2)).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                localVariablesCaptor.capture()
        );
        assertThat(localVariablesCaptor.getAllValues())
                .allSatisfy(variables -> assertThat(variables)
                        .containsEntry("westflowDynamicResolvedSourceMode", "MODEL_DRIVEN")
                        .containsEntry("westflowDynamicResolutionPath", "TEMPLATE_PRIMARY")
                        .containsEntry("westflowDynamicTemplateSource", "SCENE_CODE"));
        verify(countersignAssigneeResolver).resolve(anyMap(), anyMap());
    }

    @Test
    void shouldFallbackToModelTemplateWhenRuleDrivenStrategyRequestsTemplateFallback() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "leaveDays", 1
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildRuleWithTemplateFallbackParentDefinition());
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> localVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowableTaskActionService).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                localVariablesCaptor.capture()
        );
        assertThat(localVariablesCaptor.getValue())
                .containsEntry("westflowDynamicResolvedSourceMode", "MANUAL_TEMPLATE")
                .containsEntry("westflowDynamicResolutionPath", "FALLBACK_TEMPLATE")
                .containsEntry("westflowDynamicTemplateSource", "MANUAL_TEMPLATE")
                .containsEntry("westflowDynamicExecutionStrategy", "RULE_ONLY")
                .containsEntry("westflowDynamicFallbackStrategy", "USE_TEMPLATE");

        ArgumentCaptor<RuntimeAppendLinkRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeAppendLinkRecord.class);
        verify(runtimeAppendLinkService).createLink(recordCaptor.capture());
        RuntimeAppendLinkRecord record = recordCaptor.getValue();
        assertThat(record.targetUserId()).isEqualTo("usr_002");
    }

    @Test
    void shouldEvaluateControlledFormulaFunctionsForDynamicBuilderRule() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "reviewLevels", List.of("FINANCE", "HR"),
                "dynamicApproverUserIds", List.of("usr_010", "usr_011"),
                "backupApproverId", "",
                "startDate", "2026-03-20",
                "endDate", "2026-03-24"
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildFormulaDrivenParentDefinition());
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        verify(flowableTaskActionService, org.mockito.Mockito.times(2)).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        );
    }

    @Test
    void shouldSkipGenerationWhenRuleOnlyStrategyHasNoRuleResult() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "leaveDays", 1
        ));
        when(processDefinitionService.getById("pd_001")).thenReturn(buildRuleOnlyParentDefinition());

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        verify(flowableTaskActionService, never()).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        );
        verify(runtimeAppendLinkService, never()).createLink(any());
    }

    @Test
    void shouldPreferTemplateWhenExecutionStrategyIsTemplateOnly() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "dynamicApproverUserIds", List.of("usr_003")
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildTemplateOnlyParentDefinition());
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        ArgumentCaptor<RuntimeAppendLinkRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeAppendLinkRecord.class);
        verify(runtimeAppendLinkService).createLink(recordCaptor.capture());
        assertThat(recordCaptor.getValue().targetUserId()).isEqualTo("usr_002");
    }

    @Test
    void shouldResolveFallbackRoleTargetsWhenRuleDrivenResultIsEmpty() {
        when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd_001");
        when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                "westflowProcessDefinitionId", "pd_001",
                "westflowProcessKey", "oa_dynamic_append_tasks",
                "westflowBusinessKey", "biz_001",
                "westflowInitiatorUserId", "usr_001",
                "leaveDays", 1
        ));
        when(processLinkService.getByChildInstanceId("instance_1")).thenReturn(null);
        when(runtimeAppendLinkService.getByTargetInstanceId("instance_1")).thenReturn(null);
        when(processDefinitionService.getById("pd_001")).thenReturn(buildRoleFallbackParentDefinition());
        when(countersignAssigneeResolver.resolve(anyMap(), anyMap())).thenReturn(List.of("usr_010", "usr_011"));
        when(flowableTaskActionService.createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        )).thenReturn(createdTask);
        when(createdTask.getId()).thenReturn("task_001");

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::isLogin).thenReturn(false);
            dynamicBuildAppendRuntimeService.executeDynamicBuilder("instance_1", "dynamic_builder_1");
        }

        verify(countersignAssigneeResolver).resolve(anyMap(), anyMap());
        verify(flowableTaskActionService, org.mockito.Mockito.times(2)).createAdhocTask(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyMap()
        );
    }

    private PublishedProcessDefinition buildDynamicBuilderParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "MANUAL_TEMPLATE",
                                                "manualTemplateCode", "append_manager_review",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildDynamicBuilderParentDefinitionForSubprocess() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_subprocess",
                "动态附属子流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_subprocess",
                        "动态附属子流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "SUBPROCESS_CALLS",
                                                "sourceMode", "MANUAL_TEMPLATE",
                                                "manualTemplateCode", "append_sub_review",
                                                "appendPolicy", "PARALLEL",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildModelDrivenParentDefinitionForSubprocess() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_subprocess",
                "动态附属子流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_subprocess",
                        "动态附属子流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "SUBPROCESS_CALLS",
                                                "sourceMode", "MODEL_DRIVEN",
                                                "sceneCode", "oa_sub_review",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildModelDrivenTaskParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "MODEL_DRIVEN",
                                                "sceneCode", "leave_escalation",
                                                "targets", Map.of(
                                                        "mode", "ROLE",
                                                        "roleCodes", List.of("role_hr", "role_manager")
                                                ),
                                                "appendPolicy", "PARALLEL_WITH_CURRENT",
                                                "maxGeneratedCount", 2
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildRuleWithTemplateFallbackParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "RULE",
                                                "executionStrategy", "RULE_ONLY",
                                                "fallbackStrategy", "USE_TEMPLATE",
                                                "ruleExpression", "leaveDays > 3",
                                                "manualTemplateCode", "append_manager_review",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildRuleOnlyParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "RULE",
                                                "executionStrategy", "RULE_ONLY",
                                                "ruleExpression", "leaveDays > 3",
                                                "manualTemplateCode", "append_manager_review",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildFormulaDrivenParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "RULE",
                                                "executionStrategy", "RULE_ONLY",
                                                "ruleExpression", "ifElse(contains(reviewLevels, \"FINANCE\") && daysBetween(startDate, endDate) >= 4 && isBlank(backupApproverId), dynamicApproverUserIds, \"usr_999\")",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 3
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildTemplateOnlyParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "RULE",
                                                "executionStrategy", "TEMPLATE_ONLY",
                                                "ruleExpression", "dynamicApproverUserIds",
                                                "manualTemplateCode", "append_manager_review",
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 1
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildRoleFallbackParentDefinition() {
        return new PublishedProcessDefinition(
                "pd_001",
                "oa_dynamic_append_tasks",
                "动态附属任务流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_dynamic_append_tasks",
                        "动态附属任务流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(
                                new ProcessDslPayload.Node(
                                        "dynamic_builder_1",
                                        "dynamic-builder",
                                        "动态构建",
                                        null,
                                        Map.of(),
                                        Map.of(
                                                "buildMode", "APPROVER_TASKS",
                                                "sourceMode", "RULE",
                                                "executionStrategy", "RULE_ONLY",
                                                "fallbackStrategy", "USE_RULE",
                                                "ruleExpression", "leaveDays > 3",
                                                "targets", Map.of(
                                                        "mode", "ROLE",
                                                        "roleCodes", List.of("role_manager")
                                                ),
                                                "appendPolicy", "SERIAL_AFTER_CURRENT",
                                                "maxGeneratedCount", 2
                                        ),
                                        Map.of()
                                )
                        ),
                        List.of(new ProcessDslPayload.Edge("e1", "dynamic_builder_1", "next_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }

    private PublishedProcessDefinition buildChildDefinition() {
        return new PublishedProcessDefinition(
                "child_pd_001",
                "oa_sub_review",
                "附属子流程",
                "OA",
                1,
                "PUBLISHED",
                OffsetDateTime.parse("2026-03-23T00:00:00+08:00"),
                new ProcessDslPayload(
                        "1.0.0",
                        "oa_sub_review",
                        "附属子流程",
                        "OA",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        List.of(new ProcessDslPayload.Node("start_1", "start", "开始", null, Map.of(), Map.of(), Map.of())),
                        List.of(new ProcessDslPayload.Edge("e1", "start_1", "end_1", 1, "next", Map.of()))
                ),
                "<xml/>"
        );
    }
}
