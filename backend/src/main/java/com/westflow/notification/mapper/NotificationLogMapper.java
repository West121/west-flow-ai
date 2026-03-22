package com.westflow.notification.mapper;

import com.westflow.notification.model.NotificationLogRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
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
