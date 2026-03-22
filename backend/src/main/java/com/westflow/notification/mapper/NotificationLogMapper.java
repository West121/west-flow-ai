package com.westflow.notification.mapper;

import com.westflow.notification.model.NotificationLogRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
// 通知发送日志的内存态数据访问层。
public class NotificationLogMapper {

    private final List<NotificationLogRecord> storage = new ArrayList<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void insert(NotificationLogRecord record) {
        storage.add(record);
    }

    public synchronized List<NotificationLogRecord> selectAll() {
        return new ArrayList<>(storage);
    }
}
