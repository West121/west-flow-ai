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

    // 预研阶段保留固定返回，真实流程下应替换为数据库/快照表查询。
    default List<OrchestratorScanTargetRecord> selectDemoScanTargets() {
        Instant now = Instant.now().minusSeconds(1);
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
                        now.minusSeconds(60),
                        "节点到达后等待到点推进"
                ),
                new OrchestratorScanTargetRecord(
                        "orc_target_trigger_001",
                        OrchestratorAutomationType.TRIGGER_NODE,
                        "触发节点演示任务",
                        "trigger_callback_node",
                        "biz_leave_003",
                        now.minusSeconds(10),
                        "到点或立即执行业务触发器"
                )
        );
    }

    // 预研阶段先做执行记录写入契约，接入日志表前保持空实现。
    default void insertExecutionRecord(OrchestratorScanExecutionRecord record) {
    }
}
