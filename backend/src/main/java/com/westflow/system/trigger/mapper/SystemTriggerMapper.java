package com.westflow.system.trigger.mapper;

import com.westflow.system.trigger.model.TriggerDefinitionRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
/**
 * 触发器定义的数据访问层。
 */
public class SystemTriggerMapper {

    private final Map<String, TriggerDefinitionRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void upsert(TriggerDefinitionRecord record) {
        storage.put(record.triggerId(), record);
    }

    public synchronized TriggerDefinitionRecord selectById(String triggerId) {
        return storage.get(triggerId);
    }

    public synchronized List<TriggerDefinitionRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized boolean existsByKey(String triggerKey, String excludeTriggerId) {
        return storage.values().stream()
                .anyMatch(record -> record.triggerKey().equalsIgnoreCase(triggerKey)
                        && (excludeTriggerId == null || !excludeTriggerId.equals(record.triggerId())));
    }
}
