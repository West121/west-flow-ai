package com.westflow.processruntime.trace;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import java.time.OffsetDateTime;
import java.util.List;

// 运行时轨迹与通知记录抽象。正式链路优先读取真实历史、日志和执行记录。
public interface ProcessRuntimeTraceStore {

    // 记录实例事件，供详情和轨迹列表查询使用。
    void appendInstanceEvent(ProcessInstanceEventResponse event);

    // 清理实现内部的临时缓存。
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
