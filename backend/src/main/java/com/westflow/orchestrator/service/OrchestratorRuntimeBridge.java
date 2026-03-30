package com.westflow.orchestrator.service;

import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;

/**
 * 编排器运行时桥，负责把“扫描目标”与“执行动作”解耦到不同运行时实现。
 */
public interface OrchestratorRuntimeBridge {

    /**
     * 查询当前扫描窗口下需要执行的目标。
     *
     * @param asOf 参考时间
     * @return 需要扫描的目标列表
     */
    List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf);

    /**
     * 执行单条扫描目标。
     *
     * @param runId 扫描批次标识
     * @param target 扫描目标
     * @return 标准化执行记录
     */
    OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target);
}
