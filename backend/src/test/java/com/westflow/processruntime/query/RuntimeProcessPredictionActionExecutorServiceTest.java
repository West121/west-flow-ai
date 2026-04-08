package com.westflow.processruntime.query;

import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationDispatchResult;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.service.NotificationDispatchService;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.processruntime.api.response.ProcessPredictionAutomationActionResponse;
import com.westflow.processruntime.api.response.ProcessPredictionFeatureSnapshotResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import com.westflow.processruntime.trace.RuntimeInstanceEventRecorder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeProcessPredictionActionExecutorServiceTest {

    @Mock
    private OrchestratorExecutionRepository orchestratorExecutionRepository;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private RuntimeInstanceEventRecorder runtimeInstanceEventRecorder;

    @Mock
    private RuntimeProcessPredictionGovernanceService runtimeProcessPredictionGovernanceService;

    @Mock
    private RuntimeProcessPredictionAutomationProperties automationProperties;

    @InjectMocks
    private RuntimeProcessPredictionActionExecutorService actionExecutorService;

    @Test
    void shouldSkipNotificationActionWhenRecipientMissing() {
        when(runtimeProcessPredictionGovernanceService.isActionEnabled(anyString())).thenReturn(true);
        when(runtimeProcessPredictionGovernanceService.isInQuietHoursNow()).thenReturn(false);
        when(automationProperties.getDedupWindowMinutes()).thenReturn(240);
        when(orchestratorExecutionRepository.countSucceededByTargetIdSince(anyString(), any())).thenReturn(0L);
        ProcessPredictionResponse response = actionExecutorService.execute(
                "pi-001",
                "task-001",
                "请假审批",
                "approve",
                "部门经理审批",
                null,
                null,
                prediction(new ProcessPredictionAutomationActionResponse(
                        "AUTO_URGE",
                        "AUTO_URGE",
                        "READY",
                        "高风险自动催办",
                        "建议立即催办。"
                ))
        );

        assertThat(response.automationActions()).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("SKIPPED");
            assertThat(action.detail()).contains("未找到有效接收人");
        });
        ArgumentCaptor<OrchestratorScanExecutionRecord> captor = ArgumentCaptor.forClass(OrchestratorScanExecutionRecord.class);
        verify(orchestratorExecutionRepository).insert(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrchestratorExecutionStatus.SKIPPED);
        verify(notificationDispatchService, never()).dispatchByChannelCode(anyString(), any());
        verify(runtimeInstanceEventRecorder, never()).appendInstanceEvent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldNotReDispatchWhenActionAlreadyExecuted() {
        when(runtimeProcessPredictionGovernanceService.isActionEnabled(anyString())).thenReturn(true);
        when(runtimeProcessPredictionGovernanceService.isInQuietHoursNow()).thenReturn(false);
        when(automationProperties.getDedupWindowMinutes()).thenReturn(240);
        when(orchestratorExecutionRepository.countSucceededByTargetIdSince(anyString(), any())).thenReturn(1L);

        ProcessPredictionResponse response = actionExecutorService.execute(
                "pi-001",
                "task-001",
                "请假审批",
                "approve",
                "部门经理审批",
                "usr_002",
                "usr_001",
                prediction(new ProcessPredictionAutomationActionResponse(
                        "SLA_REMINDER",
                        "NOTIFY",
                        "READY",
                        "SLA 临近提醒",
                        "建议提前提醒。"
                ))
        );

        assertThat(response.automationActions()).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("EXECUTED");
            assertThat(action.detail()).contains("节流窗口内已执行");
        });
        verify(orchestratorExecutionRepository, never()).insert(any());
        verify(notificationDispatchService, never()).dispatchByChannelCode(anyString(), any());
        verify(runtimeInstanceEventRecorder, never()).appendInstanceEvent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldDispatchAndRecordAuditForNextNodePreNotify() {
        when(runtimeProcessPredictionGovernanceService.isActionEnabled(anyString())).thenReturn(true);
        when(runtimeProcessPredictionGovernanceService.isInQuietHoursNow()).thenReturn(false);
        when(automationProperties.getDedupWindowMinutes()).thenReturn(240);
        when(automationProperties.getChannelCode()).thenReturn("in_app_default");
        when(orchestratorExecutionRepository.countSucceededByTargetIdSince(anyString(), any())).thenReturn(0L);
        when(notificationDispatchService.dispatchByChannelCode(anyString(), any())).thenReturn(
                new NotificationDispatchResult(
                        "log-001",
                        "channel-001",
                        "in_app_default",
                        NotificationChannelType.IN_APP.name(),
                        true,
                        "IN_APP",
                        "ok",
                        Instant.now()
                )
        );

        ProcessPredictionResponse response = actionExecutorService.execute(
                "pi-001",
                "task-001",
                "请假审批",
                "approve",
                "部门经理审批",
                "usr_002",
                "usr_001",
                prediction(new ProcessPredictionAutomationActionResponse(
                        "NEXT_NODE_PRE_NOTIFY",
                        "NOTIFY",
                        "READY",
                        "下一审批人预提醒",
                        "建议提前同步候选办理人。"
                ))
        );

        assertThat(response.automationActions()).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("EXECUTED");
            assertThat(action.actionType()).isEqualTo("NEXT_NODE_PRE_NOTIFY");
        });
        ArgumentCaptor<NotificationDispatchRequest> requestCaptor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatchService).dispatchByChannelCode(anyString(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().recipient()).isEqualTo("usr_001");
        verify(runtimeInstanceEventRecorder).appendInstanceEvent(
                eq("pi-001"), eq("task-001"), eq("approve"),
                eq("PREDICTION_NEXT_NODE_PRE_NOTIFY"), eq("下一审批人预提醒"), eq("AUTOMATION"),
                eq("task-001"), eq("task-001"), eq("usr_001"), any(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("system")
        );
        ArgumentCaptor<OrchestratorScanExecutionRecord> recordCaptor = ArgumentCaptor.forClass(OrchestratorScanExecutionRecord.class);
        verify(orchestratorExecutionRepository).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().status()).isEqualTo(OrchestratorExecutionStatus.SUCCEEDED);
    }

    @Test
    void shouldSkipActionWhenGovernanceDisablesIt() {
        when(runtimeProcessPredictionGovernanceService.isActionEnabled(anyString())).thenReturn(false);

        ProcessPredictionResponse response = actionExecutorService.execute(
                "pi-001",
                "task-001",
                "请假审批",
                "approve",
                "部门经理审批",
                "usr_002",
                "usr_001",
                prediction(new ProcessPredictionAutomationActionResponse(
                        "AUTO_URGE",
                        "AUTO_URGE",
                        "READY",
                        "高风险自动催办",
                        "建议立即催办。"
                ))
        );

        assertThat(response.automationActions()).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("SKIPPED");
            assertThat(action.detail()).contains("治理开关禁用");
        });
        verify(orchestratorExecutionRepository, never()).insert(any());
        verify(notificationDispatchService, never()).dispatchByChannelCode(anyString(), any());
    }

    @Test
    void shouldSkipActionDuringQuietHours() {
        when(runtimeProcessPredictionGovernanceService.isActionEnabled(anyString())).thenReturn(true);
        when(runtimeProcessPredictionGovernanceService.isInQuietHoursNow()).thenReturn(true);

        ProcessPredictionResponse response = actionExecutorService.execute(
                "pi-001",
                "task-001",
                "请假审批",
                "approve",
                "部门经理审批",
                "usr_002",
                "usr_001",
                prediction(new ProcessPredictionAutomationActionResponse(
                        "SLA_REMINDER",
                        "NOTIFY",
                        "READY",
                        "SLA 临近提醒",
                        "建议提前提醒。"
                ))
        );

        assertThat(response.automationActions()).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("SKIPPED");
            assertThat(action.detail()).contains("静默时间窗");
        });
        verify(orchestratorExecutionRepository, never()).insert(any());
        verify(notificationDispatchService, never()).dispatchByChannelCode(anyString(), any());
    }

    private ProcessPredictionResponse prediction(ProcessPredictionAutomationActionResponse action) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ProcessPredictionResponse(
                now.plusHours(2),
                now.plusMinutes(90),
                120L,
                50L,
                60L,
                90L,
                120L,
                "HIGH",
                "HIGH",
                24,
                20,
                "FLOW::approve::weekday",
                "FLOW_NODE_DAY",
                "WEEKDAY",
                "ORG::DEFAULT",
                "历史中位时长 + 当前停留时长。",
                null,
                "预计仍需两小时。",
                "当前节点风险较高。",
                "部门经理审批停留偏长",
                List.of("当前节点停留偏长"),
                List.of("建议立即催办"),
                List.of("补充代理人策略"),
                List.of(action),
                new ProcessPredictionFeatureSnapshotResponse(
                        "leave_approval",
                        "approve",
                        "LEAVE",
                        "usr_002",
                        "ORG::DEFAULT",
                        "WEEKDAY",
                        "FLOW_NODE_DAY",
                        24,
                        20
                ),
                List.of()
        );
    }
}
