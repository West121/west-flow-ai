package com.westflow.orchestrator.service;

import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;

// 编排器运行时桥，负责把“扫描目标”与“执行动作”解耦到不同运行时实现。
public interface OrchestratorRuntimeBridge {

    // 查询当前扫描窗口下需要执行的目标，便于接入 Flowable 任务后按实例进行过滤。
    List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf);

    // 执行单条扫描目标，返回标准化执行记录供监控和复盘使用。
    OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target);
}
