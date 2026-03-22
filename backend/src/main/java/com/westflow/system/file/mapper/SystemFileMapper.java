package com.westflow.system.file.mapper;

import com.westflow.system.file.model.SystemFileRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 文件管理的内存态数据访问层。
 */
@Component
public class SystemFileMapper {

    private final Map<String, SystemFileRecord> metadataStorage = new LinkedHashMap<>();
    private final Map<String, byte[]> contentStorage = new LinkedHashMap<>();

    public synchronized void clear() {
        metadataStorage.clear();
        contentStorage.clear();
    }

    public synchronized void upsert(SystemFileRecord record, byte[] content) {
        metadataStorage.put(record.fileId(), record);
        if (content != null) {
            contentStorage.put(record.fileId(), content.clone());
        }
    }

    public synchronized void update(SystemFileRecord record) {
        metadataStorage.put(record.fileId(), record);
    }

    public synchronized SystemFileRecord selectById(String fileId) {
        return metadataStorage.get(fileId);
    }

    public synchronized byte[] selectContent(String fileId) {
        byte[] content = contentStorage.get(fileId);
        return content == null ? null : content.clone();
    }

    public synchronized List<SystemFileRecord> selectAll() {
        return new ArrayList<>(metadataStorage.values());
    }
}
