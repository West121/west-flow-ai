package com.westflow.processruntime.service;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowableProcessRuntimeTraceStoreTest {

    @Test
    void shouldReturnEmptyInTraceSkeletonAndAcceptNoopWrites() {
        FlowableProcessRuntimeTraceStore store = new FlowableProcessRuntimeTraceStore();
        ProcessDslPayload payload = new ProcessDslPayload(
                "1.0.0",
                "demo",
                "演示流程",
                "OA",
                "form_key",
                "1.0.0",
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        store.appendInstanceEvent(event("evt_1", "instance_1", "task_1", "node_1", "TASK", "演示事件", "usr_001", OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))));

        assertThat(store.queryInstanceEvents("instance_1")).isEmpty();
        assertThat(store.queryAutomationTraces("instance_1", "PENDING", "usr_001", payload, OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))))
                .isEmpty();
        assertThat(store.queryNotificationSendRecords("instance_1", "PENDING", "usr_001", payload, OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))))
                .isEmpty();
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
