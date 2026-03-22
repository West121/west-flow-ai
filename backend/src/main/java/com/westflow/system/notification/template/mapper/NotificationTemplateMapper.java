package com.westflow.system.notification.template.mapper;

import com.westflow.system.notification.template.model.NotificationTemplateRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 通知模板的内存态数据访问层。
 */
@Component
public class NotificationTemplateMapper {

    private final Map<String, NotificationTemplateRecord> storage = new LinkedHashMap<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void upsert(NotificationTemplateRecord record) {
        storage.put(record.templateId(), record);
    }

    public synchronized NotificationTemplateRecord selectById(String templateId) {
        return storage.get(templateId);
    }

    public synchronized NotificationTemplateRecord selectByCode(String templateCode) {
        return storage.values().stream()
                .filter(record -> record.templateCode().equals(templateCode))
                .findFirst()
                .orElse(null);
    }

    public synchronized List<NotificationTemplateRecord> selectAll() {
        return new ArrayList<>(storage.values());
    }

    public synchronized boolean existsByCode(String templateCode, String excludeTemplateId) {
        return storage.values().stream()
                .anyMatch(record -> record.templateCode().equals(templateCode)
                        && (excludeTemplateId == null || !excludeTemplateId.equals(record.templateId())));
    }
}
