package com.westflow.system.monitor.mapper;

import com.westflow.system.monitor.model.TriggerExecutionRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 触发执行快照记录存储。
 */
@Component
public class TriggerExecutionRecordMapper {

    private final Map<String, TriggerExecutionRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void insert(TriggerExecutionRecord record) {
        storage.put(record.executionId(), record);
    }

    public synchronized TriggerExecutionRecord selectById(String executionId) {
        return storage.get(executionId);
    }

    public synchronized List<TriggerExecutionRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }
}
