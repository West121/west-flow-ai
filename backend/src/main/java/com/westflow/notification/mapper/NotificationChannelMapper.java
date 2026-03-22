package com.westflow.notification.mapper;

import com.westflow.notification.model.NotificationChannelRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
// 通知渠道的内存态数据访问层。
public class NotificationChannelMapper {

    private final Map<String, NotificationChannelRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void upsert(NotificationChannelRecord record) {
        storage.put(record.channelId(), record);
    }

    public synchronized NotificationChannelRecord selectById(String channelId) {
        return storage.get(channelId);
    }

    public synchronized NotificationChannelRecord selectByCode(String channelCode) {
        return storage.values().stream()
                .filter(record -> record.channelCode().equals(channelCode))
                .findFirst()
                .orElse(null);
    }

    public synchronized List<NotificationChannelRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized boolean existsByCode(String channelCode, String excludeChannelId) {
        return storage.values().stream()
                .anyMatch(record -> record.channelCode().equals(channelCode)
                        && (excludeChannelId == null || !excludeChannelId.equals(record.channelId())));
    }

    public synchronized void markLastSentAt(String channelId, Instant lastSentAt) {
        NotificationChannelRecord current = storage.get(channelId);
        if (current == null) {
            return;
        }
        storage.put(channelId, new NotificationChannelRecord(
                current.channelId(),
                current.channelCode(),
                current.channelType(),
                current.channelName(),
                current.enabled(),
                current.mockMode(),
                current.config(),
                current.remark(),
                current.createdAt(),
                lastSentAt,
                lastSentAt
        ));
    }
}
