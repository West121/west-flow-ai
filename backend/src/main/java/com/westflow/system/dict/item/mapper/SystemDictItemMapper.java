package com.westflow.system.dict.item.mapper;

import com.westflow.system.dict.item.model.SystemDictItemRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 字典项内存态数据访问层。
 */
@Component
public class SystemDictItemMapper {

    private final Map<String, SystemDictItemRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void upsert(SystemDictItemRecord record) {
        storage.put(record.dictItemId(), record);
    }

    public synchronized SystemDictItemRecord selectById(String dictItemId) {
        return storage.get(dictItemId);
    }

    public synchronized List<SystemDictItemRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized long countByTypeId(String dictTypeId) {
        return storage.values().stream()
                .filter(record -> record.dictTypeId().equals(dictTypeId))
                .count();
    }

    public synchronized boolean existsByCodeInType(String dictTypeId, String itemCode, String excludeDictItemId) {
        return storage.values().stream()
                .anyMatch(record -> record.dictTypeId().equals(dictTypeId)
                        && record.itemCode().equals(itemCode)
                        && (excludeDictItemId == null || !excludeDictItemId.equals(record.dictItemId())));
    }
}
