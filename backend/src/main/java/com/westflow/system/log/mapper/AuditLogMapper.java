package com.westflow.system.log.mapper;

import com.westflow.system.log.model.AuditLogRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 审计日志快照存储，按内存方式保存近期日志。
 */
@Component
public class AuditLogMapper {

    private final List<AuditLogRecord> storage = new ArrayList<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void insert(AuditLogRecord record) {
        storage.add(record);
    }

    public synchronized List<AuditLogRecord> selectAll() {
        return new ArrayList<>(storage);
    }
}
