package com.westflow.processruntime.service;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProcessRuntimeTraceStoreTest {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldKeepInstanceEventsOrderedByTime() {
        InMemoryProcessRuntimeTraceStore store = new InMemoryProcessRuntimeTraceStore();
        OffsetDateTime earlier = OffsetDateTime.now(TIME_ZONE).minusSeconds(30);
        OffsetDateTime later = OffsetDateTime.now(TIME_ZONE);

        store.appendInstanceEvent(event("evt_2", "instance_1", "task_2", "node_2", "TASK", "Later", "usr_002", later));
        store.appendInstanceEvent(event("evt_1", "instance_1", "task_1", "node_1", "TASK", "Earlier", "usr_001", earlier));

        List<ProcessInstanceEventResponse> events = store.queryInstanceEvents("instance_1");
        assertThat(events).extracting(ProcessInstanceEventResponse::eventId).containsExactly("evt_1", "evt_2");
    }

    @Test
    void shouldProjectAutomationAndNotificationRecordsFromDslPayload() {
        InMemoryProcessRuntimeTraceStore store = new InMemoryProcessRuntimeTraceStore();
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

        List<ProcessNotificationSendRecordResponse> notifyRecords = store.queryNotificationSendRecords(
                "instance_1",
                "PENDING",
                "usr_001",
                payload,
                OffsetDateTime.now(TIME_ZONE).truncatedTo(ChronoUnit.MILLIS)
        );

        assertThat(notifyRecords).hasSize(2);
        assertThat(notifyRecords).extracting(ProcessNotificationSendRecordResponse::channelName)
                .containsExactlyInAnyOrder("站内通知", "邮件通知");
    }

    @Test
    void shouldClearEventsWhenReset() {
        InMemoryProcessRuntimeTraceStore store = new InMemoryProcessRuntimeTraceStore();
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
}
