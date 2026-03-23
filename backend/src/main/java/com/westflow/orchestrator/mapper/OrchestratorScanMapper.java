package com.westflow.orchestrator.mapper;

import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
// 编排器扫描执行记录映射接口。
public interface OrchestratorScanMapper {

    // 执行记录写入契约，未接入持久层时保持空实现。
    default void insertExecutionRecord(OrchestratorScanExecutionRecord record) {
    }
}
