package com.westflow.processruntime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.service.OrchestratorRuntimeBridge;
import com.westflow.workflowadmin.mapper.WorkflowOperationLogMapper;
import com.westflow.workflowadmin.model.WorkflowOperationLogRecord;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowableProcessRuntimeTraceStoreTest {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldKeepAppendedInstanceEventsOrderedByTime() {
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore();
        OffsetDateTime earlier = OffsetDateTime.now(TIME_ZONE).minusSeconds(30);
        OffsetDateTime later = OffsetDateTime.now(TIME_ZONE);

        store.appendInstanceEvent(event("evt_2", "instance_1", "task_2", "node_2", "TASK", "Later", "usr_002", later));
        store.appendInstanceEvent(event("evt_1", "instance_1", "task_1", "node_1", "TASK", "Earlier", "usr_001", earlier));

        List<ProcessInstanceEventResponse> events = store.queryInstanceEvents("instance_1");
        assertThat(events).extracting(ProcessInstanceEventResponse::eventId).containsExactly("evt_1", "evt_2");
    }

    @Test
    void shouldPreferPersistedWorkflowOperationLogsOverRuntimeCache() {
        WorkflowOperationLogMapper workflowOperationLogMapper = mock(WorkflowOperationLogMapper.class);
        when(workflowOperationLogMapper.selectByProcessInstanceId("instance_1"))
                .thenReturn(List.of(new WorkflowOperationLogRecord(
                        "log_001",
                        "instance_1",
                        "pd_001",
                        "flowable_pd_001",
                        "OA_LEAVE",
                        "biz_001",
                        "task_001",
                        "approve_manager",
                        "TASK_APPROVED",
                        "任务已审批",
                        "APPROVAL",
                        "usr_002",
                        null,
                        null,
                        null,
                        null,
                        "{\"actingMode\":\"DIRECT\"}",
                        Instant.parse("2026-03-23T01:00:00Z")
                )));
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore(
                null,
                workflowOperationLogMapper,
                null,
                null,
                new ObjectMapper()
        );
        store.appendInstanceEvent(event(
                "evt_cache_001",
                "instance_1",
                "task_cache_001",
                "node_cache_001",
                "TASK_CREATED",
                "缓存事件",
                "usr_cache",
                OffsetDateTime.parse("2026-03-23T09:10:00+08:00")
        ));

        List<ProcessInstanceEventResponse> events = store.queryInstanceEvents("instance_1");

        assertThat(events)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.eventId()).isEqualTo("log_001");
                    assertThat(item.eventType()).isEqualTo("TASK_APPROVED");
                    assertThat(item.operatorUserId()).isEqualTo("usr_002");
                });
    }

    @Test
    void shouldProjectAutomationAndNotificationRecordsFromDslPayload() {
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore();
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "runtime_trace_process",
                "运行态轨迹流程",
                "OA",
                "form_key",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node(
                                "n_approver",
                                "approver",
                                "审批人",
                                null,
                                Map.of(),
                                Map.of(
                                "timeoutPolicy", Map.of("enabled", true, "action", "AUTO_APPROVE"),
                                        "reminderPolicy", Map.of("enabled", true, "channels", List.of("IN_APP", "EMAIL")),
                                        "escalationPolicy", Map.of(
                                                "enabled", true,
                                                "afterMinutes", 60,
                                                "targetMode", "ROLE",
                                                "targetRoleCodes", List.of("role_manager"),
                                                "channels", List.of("IN_APP")
                                        )
                                ),
                                Map.of()
                        ),
                        new ProcessDslPayload.Node(
                                "n_timer",
                                "timer",
                                "定时节点",
                                null,
                                Map.of(),
                                Map.of(),
                                Map.of()
                        ),
                        new ProcessDslPayload.Node(
                                "n_trigger",
                                "trigger",
                                "触发节点",
                                null,
                                Map.of(),
                                Map.of("triggerKey", "runtime.trigger"),
                                Map.of()
                        )
                ),
                List.of(
                        new ProcessDslPayload.Edge("e1", "n_approver", "n_timer", 1, "to timer", Map.of()),
                        new ProcessDslPayload.Edge("e2", "n_timer", "n_trigger", 1, "to trigger", Map.of())
                )
        );

        List<ProcessAutomationTraceItemResponse> automationTraces = store.queryAutomationTraces(
                "instance_1",
                "PENDING",
                "usr_001",
                payload,
                OffsetDateTime.now(TIME_ZONE)
        );

        assertThat(automationTraces)
                .extracting(ProcessAutomationTraceItemResponse::traceType)
                .containsExactlyInAnyOrder("TIMEOUT_APPROVAL", "AUTO_REMINDER", "ESCALATION", "TIMER_NODE", "TRIGGER_NODE");
        assertThat(automationTraces)
                .extracting(ProcessAutomationTraceItemResponse::nodeId)
                .anyMatch(nodeId -> nodeId.endsWith("n_approver"));
        assertThat(automationTraces)
                .extracting(ProcessAutomationTraceItemResponse::nodeId)
                .anyMatch(nodeId -> nodeId.endsWith("n_timer"));
        assertThat(automationTraces)
                .extracting(ProcessAutomationTraceItemResponse::nodeId)
                .anyMatch(nodeId -> nodeId.endsWith("n_trigger"));

        List<ProcessNotificationSendRecordResponse> notificationRecords = store.queryNotificationSendRecords(
                "instance_1",
                "PENDING",
                "usr_001",
                payload,
                OffsetDateTime.now(TIME_ZONE).truncatedTo(ChronoUnit.MILLIS)
        );

        assertThat(notificationRecords).hasSize(2);
        assertThat(notificationRecords)
                .extracting(ProcessNotificationSendRecordResponse::channelName)
                .containsExactlyInAnyOrder("站内通知", "邮件通知");
    }

    @Test
    void shouldPreferDueTargetsFromOrchestratorBridgeForAutomationTrace() {
        Instant reminderDueAt = Instant.parse("2026-03-23T01:10:00Z");
        OrchestratorRuntimeBridge orchestratorRuntimeBridge = new OrchestratorRuntimeBridge() {
            @Override
            public List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf) {
                return List.of(
                        new OrchestratorScanTargetRecord(
                                "orc_target_instance_1_task_approve_001_auto_reminder",
                                OrchestratorAutomationType.AUTO_REMINDER,
                                "审批人自动提醒",
                                "approve_manager",
                                "biz_leave_001",
                                reminderDueAt,
                                "审批节点自动提醒策略"
                        )
                );
            }

            @Override
            public OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target) {
                return new OrchestratorScanExecutionRecord(
                        "exec_001",
                        runId,
                        target.targetId(),
                        target.automationType(),
                        OrchestratorExecutionStatus.SKIPPED,
                        "ignored",
                        Instant.now()
                );
            }
        };
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore(orchestratorRuntimeBridge);

        List<ProcessAutomationTraceItemResponse> traces = store.queryAutomationTraces(
                "instance_1",
                "PENDING",
                "usr_001",
                automationPayload(),
                OffsetDateTime.parse("2026-03-23T09:00:00+08:00")
        );

        ProcessAutomationTraceItemResponse reminderTrace = traces.stream()
                .filter(item -> "AUTO_REMINDER".equals(item.traceType()))
                .findFirst()
                .orElseThrow();
        assertThat(reminderTrace.status()).isEqualTo("READY");
        assertThat(reminderTrace.occurredAt()).isEqualTo(reminderDueAt.atZone(TIME_ZONE).toOffsetDateTime());
        assertThat(reminderTrace.detail()).contains("审批节点自动提醒策略");
    }

    @Test
    void shouldProjectNotificationSendHistoryFromUrgeEvents() {
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore();
        OffsetDateTime urgedAt = OffsetDateTime.parse("2026-03-23T09:15:00+08:00");
        store.appendInstanceEvent(event(
                "evt_urge_1",
                "instance_1",
                "task_approve_001",
                "approve_manager",
                "TASK_URGED",
                "任务已催办",
                "usr_001",
                urgedAt
        ));

        List<ProcessNotificationSendRecordResponse> notificationRecords = store.queryNotificationSendRecords(
                "instance_1",
                "PENDING",
                "usr_001",
                automationPayload(),
                OffsetDateTime.parse("2026-03-23T09:00:00+08:00")
        );

        assertThat(notificationRecords).hasSize(2);
        assertThat(notificationRecords)
                .extracting(ProcessNotificationSendRecordResponse::status)
                .containsOnly("SUCCESS");
        assertThat(notificationRecords)
                .extracting(ProcessNotificationSendRecordResponse::attemptCount)
                .containsOnly(1);
        assertThat(notificationRecords)
                .extracting(ProcessNotificationSendRecordResponse::sentAt)
                .containsOnly(urgedAt);
    }

    @Test
    void shouldPreferPersistedNotificationAndAutomationHistoryOverDslFallback() {
        NotificationLogMapper notificationLogMapper = mock(NotificationLogMapper.class);
        OrchestratorExecutionRepository orchestratorExecutionRepository = mock(OrchestratorExecutionRepository.class);
        when(notificationLogMapper.selectByInstanceId("instance_1"))
                .thenReturn(List.of(new NotificationLogRecord(
                        "notify_001",
                        "channel_001",
                        "wechat_ops",
                        "WECHAT",
                        "wechat:usr_001",
                        "审批提醒",
                        "请及时处理",
                        "WechatProvider",
                        true,
                        "SUCCESS",
                        "ok",
                        Map.of("instanceId", "instance_1"),
                        Instant.parse("2026-03-23T01:15:00Z")
                )));
        when(orchestratorExecutionRepository.selectByInstanceId("instance_1"))
                .thenReturn(List.of(new OrchestratorScanExecutionRecord(
                        "exec_001",
                        "run_001",
                        "orc_target_instance_1_approve_manager_auto_reminder",
                        OrchestratorAutomationType.AUTO_REMINDER,
                        OrchestratorExecutionStatus.SUCCEEDED,
                        "已发送提醒",
                        Instant.parse("2026-03-23T01:05:00Z")
                )));
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore(
                null,
                null,
                notificationLogMapper,
                orchestratorExecutionRepository,
                new ObjectMapper()
        );

        List<ProcessAutomationTraceItemResponse> traces = store.queryAutomationTraces(
                "instance_1",
                "PENDING",
                "usr_001",
                automationPayload(),
                OffsetDateTime.parse("2026-03-23T09:00:00+08:00")
        );
        List<ProcessNotificationSendRecordResponse> records = store.queryNotificationSendRecords(
                "instance_1",
                "PENDING",
                "usr_001",
                automationPayload(),
                OffsetDateTime.parse("2026-03-23T09:00:00+08:00")
        );

        assertThat(traces)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.traceId()).isEqualTo("exec_001");
                    assertThat(item.traceType()).isEqualTo("AUTO_REMINDER");
                    assertThat(item.status()).isEqualTo("SUCCEEDED");
                });
        assertThat(records)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.recordId()).isEqualTo("notify_001");
                    assertThat(item.channelType()).isEqualTo("WECHAT");
                    assertThat(item.status()).isEqualTo("SUCCESS");
                });
    }

    @Test
    void shouldClearEventsWhenReset() {
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore();
        store.appendInstanceEvent(event("evt_1", "instance_1", null, null, "INSTANCE", "实例启动", "usr_001", OffsetDateTime.now(TIME_ZONE)));

        store.reset();

        assertThat(store.queryInstanceEvents("instance_1")).isEmpty();
    }

    private ProcessInstanceEventResponse event(
            String eventId,
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String operatorUserId,
            OffsetDateTime occurredAt
    ) {
        try {
            Constructor<ProcessInstanceEventResponse> constructor = ProcessInstanceEventResponse.class.getConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    OffsetDateTime.class,
                    Map.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class
            );
            return constructor.newInstance(
                    eventId,
                    instanceId,
                    taskId,
                    nodeId,
                    eventType,
                    eventName,
                    null,
                    null,
                    null,
                    null,
                    operatorUserId,
                    occurredAt,
                    Map.<String, Object>of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create ProcessInstanceEventResponse for test", e);
        }
    }

    private ProcessDslPayload automationPayload() {
        return new ProcessDslPayload(
                "1.0.0",
                "runtime_trace_process",
                "运行态轨迹流程",
                "OA",
                "form_key",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(
                        new ProcessDslPayload.Node(
                                "approve_manager",
                                "approver",
                                "部门负责人审批",
                                null,
                                Map.of(),
                                Map.of(
                                        "timeoutPolicy", Map.of("enabled", true, "durationMinutes", 30, "action", "AUTO_APPROVE"),
                                        "reminderPolicy", Map.of(
                                                "enabled", true,
                                                "firstReminderAfterMinutes", 10,
                                                "channels", List.of("IN_APP", "EMAIL")
                                        )
                                ),
                                Map.of()
                        ),
                        new ProcessDslPayload.Node(
                                "n_timer",
                                "timer",
                                "定时节点",
                                null,
                                Map.of(),
                                Map.of("delayMinutes", 15),
                                Map.of()
                        ),
                        new ProcessDslPayload.Node(
                                "n_trigger",
                                "trigger",
                                "触发节点",
                                null,
                                Map.of(),
                                Map.of("triggerKey", "runtime.trigger"),
                                Map.of()
                        )
                ),
                List.of(
                        new ProcessDslPayload.Edge("e1", "approve_manager", "n_timer", 1, "to timer", Map.of()),
                        new ProcessDslPayload.Edge("e2", "n_timer", "n_trigger", 1, "to trigger", Map.of())
                )
        );
    }
}
