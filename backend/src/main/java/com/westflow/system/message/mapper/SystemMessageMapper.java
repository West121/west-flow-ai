package com.westflow.system.message.mapper;

import com.westflow.system.message.model.SystemMessageRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 站内消息内存态数据访问层。
 */
@Component
public class SystemMessageMapper {

    private final Map<String, SystemMessageRecord> storage = new LinkedHashMap<>();
    private final Map<String, Set<String>> readUserIdsByMessageId = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
        readUserIdsByMessageId.clear();
    }

    public synchronized void upsert(SystemMessageRecord record) {
        storage.put(record.messageId(), record);
        readUserIdsByMessageId.putIfAbsent(record.messageId(), new LinkedHashSet<>());
    }

    public synchronized SystemMessageRecord selectById(String messageId) {
        return storage.get(messageId);
    }

    public synchronized List<SystemMessageRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized boolean hasRead(String messageId, String userId) {
        return readUserIdsByMessageId.getOrDefault(messageId, Set.of()).contains(userId);
    }

    public synchronized void markRead(String messageId, String userId) {
        readUserIdsByMessageId.computeIfAbsent(messageId, key -> new LinkedHashSet<>())
                .add(userId);
    }
}
