package com.westflow.system.dict.type.mapper;

import com.westflow.system.dict.type.model.SystemDictTypeRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 字典类型内存态数据访问层。
 */
@Component
public class SystemDictTypeMapper {

    private final Map<String, SystemDictTypeRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void upsert(SystemDictTypeRecord record) {
        storage.put(record.dictTypeId(), record);
    }

    public synchronized SystemDictTypeRecord selectById(String dictTypeId) {
        return storage.get(dictTypeId);
    }

    public synchronized SystemDictTypeRecord selectByTypeCode(String typeCode) {
        return storage.values().stream()
                .filter(record -> record.typeCode().equals(typeCode))
                .findFirst()
                .orElse(null);
    }

    public synchronized List<SystemDictTypeRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized boolean existsByCode(String typeCode, String excludeDictTypeId) {
        return storage.values().stream()
                .anyMatch(record -> record.typeCode().equals(typeCode)
                        && (excludeDictTypeId == null || !excludeDictTypeId.equals(record.dictTypeId())));
    }
}
