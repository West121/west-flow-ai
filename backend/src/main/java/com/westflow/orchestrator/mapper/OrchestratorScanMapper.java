package com.westflow.orchestrator.mapper;

import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
// 编排器扫描目标和执行记录的演示映射接口。
public interface OrchestratorScanMapper {

    default List<OrchestratorScanTargetRecord> selectDemoScanTargets() {
        // demo 阶段先返回固定扫描目标，后续再切换成数据库查询。
        Instant now = Instant.now();
        return List.of(
                new OrchestratorScanTargetRecord(
                        "orc_target_timeout_001",
                        OrchestratorAutomationType.TIMEOUT_APPROVAL,
                        "超时审批演示任务",
                        "approve_manager",
                        "biz_leave_001",
                        now.minusSeconds(300),
                        "人工审批超时后自动通过或拒绝"
                ),
                new OrchestratorScanTargetRecord(
                        "orc_target_reminder_001",
                        OrchestratorAutomationType.AUTO_REMINDER,
                        "自动提醒演示任务",
                        "approve_manager",
                        "biz_leave_001",
                        now.minusSeconds(120),
                        "人工审批超时前按间隔提醒"
                ),
                new OrchestratorScanTargetRecord(
                        "orc_target_timer_001",
                        OrchestratorAutomationType.TIMER_NODE,
                        "定时节点演示任务",
                        "timer_wait_node",
                        "biz_leave_002",
                        now.plusSeconds(600),
                        "节点到达后等待到点推进"
                ),
                new OrchestratorScanTargetRecord(
                        "orc_target_trigger_001",
                        OrchestratorAutomationType.TRIGGER_NODE,
                        "触发节点演示任务",
                        "trigger_callback_node",
                        "biz_leave_003",
                        now,
                        "到点或立即执行业务触发器"
                )
        );
    }

    default void insertExecutionRecord(OrchestratorScanExecutionRecord record) {
        // 目前不落库，先保留执行记录写入契约。
    }
}
