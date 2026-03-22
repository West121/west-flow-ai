package com.westflow.processruntime.service;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessNotificationSendRecordResponse;
import java.time.OffsetDateTime;
import java.util.List;

// 运行时轨迹与通知记录抽象，当前默认走演示态内存实现，未来可接 Flowable 历史表。
public interface ProcessRuntimeTraceStore {

    // 记录实例事件（用于 detail 的 events 与 trace 列表）。
    void appendInstanceEvent(ProcessInstanceEventResponse event);

    // 清理测试和人工调试使用的内存快照。
    void reset();

    // 查询实例事件轨迹。
    List<ProcessInstanceEventResponse> queryInstanceEvents(String instanceId);

    // 查询当前实例的自动化动作轨迹。
    List<ProcessAutomationTraceItemResponse> queryAutomationTraces(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    );

    // 查询当前实例的通知发送记录。
    List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    );
}
