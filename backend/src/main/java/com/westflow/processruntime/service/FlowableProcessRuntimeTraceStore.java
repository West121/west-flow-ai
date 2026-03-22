package com.westflow.processruntime.service;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.ProcessNotificationSendRecordResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * TODO(Phase 3C): 迁移到 Flowable 历史事件表后使用此实现。
 * 当前保留最小骨架，先提供可注入的查询与重放接口，不破坏现有调用面。
 */
public class FlowableProcessRuntimeTraceStore implements ProcessRuntimeTraceStore {

    @Override
    public void appendInstanceEvent(ProcessInstanceEventResponse event) {
        // TODO(Phase 3C): 写入 Flowable 历史事件记录表。
    }

    @Override
    public void reset() {
        // TODO(Phase 3C): 对接 Flowable 时可按测试隔离要求清理临时快照。
    }

    @Override
    public List<ProcessInstanceEventResponse> queryInstanceEvents(String instanceId) {
        // TODO(Phase 3C): 从 Flowable 实例历史查询事件。
        return List.of();
    }

    @Override
    public List<ProcessAutomationTraceItemResponse> queryAutomationTraces(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        // TODO(Phase 3C): 从监控与自动化执行记录聚合触发明细。
        return List.of();
    }

    @Override
    public List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        // TODO(Phase 3C): 从通知服务/审计表补齐发送轨迹。
        return List.of();
    }
}
