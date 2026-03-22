package com.westflow.system.monitor.mapper;

import com.westflow.system.monitor.model.OrchestratorScanRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 编排扫描记录快照存储。
 */
@Component
public class OrchestratorScanRecordMapper {

    private final Map<String, OrchestratorScanRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void insert(OrchestratorScanRecord record) {
        storage.put(record.executionId(), record);
    }

    public synchronized OrchestratorScanRecord selectById(String executionId) {
        return storage.get(executionId);
    }

    public synchronized List<OrchestratorScanRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }
}
