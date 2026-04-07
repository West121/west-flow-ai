package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.plm.api.PlmAffectedItemRequest;
import com.westflow.plm.api.PlmObjectLinkResponse;
import com.westflow.plm.mapper.PlmBillObjectLinkMapper;
import com.westflow.plm.mapper.PlmObjectMasterMapper;
import com.westflow.plm.mapper.PlmObjectRevisionMapper;
import com.westflow.plm.model.PlmBillObjectLinkRecord;
import com.westflow.plm.model.PlmObjectMasterRecord;
import com.westflow.plm.model.PlmObjectRevisionRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * PLM 深度对象同步服务。
 */
@Service
@RequiredArgsConstructor
public class PlmObjectService {

    private static final String OBJECT_SOURCE_SYSTEM = "WEST_FLOW";
    private static final String DEFAULT_OBJECT_STATE = "ACTIVE";

    private final PlmObjectMasterMapper plmObjectMasterMapper;
    private final PlmObjectRevisionMapper plmObjectRevisionMapper;
    private final PlmBillObjectLinkMapper plmBillObjectLinkMapper;
    private final ObjectMapper objectMapper;

    public List<PlmBillObjectLinkRecord> syncBillObjects(
            String businessType,
            String billId,
            List<PlmAffectedItemRequest> affectedItems,
            String currentUserId
    ) {
        plmBillObjectLinkMapper.deleteByBusinessTypeAndBillId(businessType, billId);
        if (affectedItems == null || affectedItems.isEmpty()) {
            return List.of();
        }

        List<PlmBillObjectLinkRecord> links = new ArrayList<>();
        for (int index = 0; index < affectedItems.size(); index++) {
            PlmAffectedItemRequest item = affectedItems.get(index);
            String objectType = normalizeValue(item.itemType(), "UNKNOWN");
            String objectCode = normalizeValue(item.itemCode(), billId + "-OBJ-" + (index + 1));
            String objectName = normalizeValue(item.itemName(), objectCode);
            String ownerUserId = normalizeValue(item.ownerUserId(), currentUserId);

            PlmObjectMasterRecord objectMaster = upsertObjectMaster(
                    businessType,
                    objectType,
                    objectCode,
                    objectName,
                    ownerUserId,
                    item.beforeVersion(),
                    item.afterVersion()
            );
            PlmObjectRevisionRecord revision = upsertRevision(objectMaster.id(), item, currentUserId);
            links.add(new PlmBillObjectLinkRecord(
                    buildId("bol"),
                    businessType,
                    billId,
                    objectMaster.id(),
                    revision == null ? null : revision.id(),
                    objectType,
                    normalizeValue(item.changeAction(), "UPDATE"),
                    normalizeValue(item.beforeVersion(), null),
                    normalizeValue(item.afterVersion(), null),
                    normalizeValue(item.remark(), null),
                    item.sortOrder() == null ? index + 1 : item.sortOrder(),
                    null,
                    null
            ));
        }

        if (!links.isEmpty()) {
            plmBillObjectLinkMapper.batchInsert(links);
        }
        return links;
    }

    public List<PlmObjectLinkResponse> listBillObjectLinks(String businessType, String billId) {
        Map<String, PlmObjectMasterRecord> masterCache = new LinkedHashMap<>();
        return plmBillObjectLinkMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .map(link -> {
                    PlmObjectMasterRecord master = masterCache.computeIfAbsent(
                            link.objectId(),
                            plmObjectMasterMapper::selectById
                    );
                    return new PlmObjectLinkResponse(
                            link.id(),
                            link.businessType(),
                            link.billId(),
                            link.objectId(),
                            link.objectRevisionId(),
                            master == null ? null : master.objectType(),
                            master == null ? null : master.objectCode(),
                            master == null ? null : master.objectName(),
                            link.roleCode(),
                            link.changeAction(),
                            link.beforeRevisionCode(),
                            link.afterRevisionCode(),
                            link.remark(),
                            link.sortOrder()
                    );
                })
                .toList();
    }

    public List<PlmBillObjectLinkRecord> listBillObjectLinkRecords(String businessType, String billId) {
        return plmBillObjectLinkMapper.selectByBusinessTypeAndBillId(businessType, billId);
    }

    private PlmObjectMasterRecord upsertObjectMaster(
            String domainCode,
            String objectType,
            String objectCode,
            String objectName,
            String ownerUserId,
            String beforeRevisionCode,
            String afterRevisionCode
    ) {
        String latestRevision = normalizeValue(afterRevisionCode, normalizeValue(beforeRevisionCode, null));
        String latestVersionLabel = latestRevision;
        PlmObjectMasterRecord existing = plmObjectMasterMapper.selectByDomainTypeAndCode(domainCode, objectType, objectCode);
        if (existing == null) {
            PlmObjectMasterRecord record = new PlmObjectMasterRecord(
                    buildId("obj"),
                    objectType,
                    objectCode,
                    objectName,
                    ownerUserId,
                    domainCode,
                    DEFAULT_OBJECT_STATE,
                    OBJECT_SOURCE_SYSTEM,
                    objectCode,
                    latestRevision,
                    latestVersionLabel,
                    null,
                    null
            );
            plmObjectMasterMapper.insert(record);
            return plmObjectMasterMapper.selectById(record.id());
        }

        PlmObjectMasterRecord updated = new PlmObjectMasterRecord(
                existing.id(),
                objectType,
                objectCode,
                objectName,
                ownerUserId,
                domainCode,
                DEFAULT_OBJECT_STATE,
                OBJECT_SOURCE_SYSTEM,
                objectCode,
                latestRevision,
                latestVersionLabel,
                existing.createdAt(),
                existing.updatedAt()
        );
        plmObjectMasterMapper.updateLatestState(updated);
        return plmObjectMasterMapper.selectById(existing.id());
    }

    private PlmObjectRevisionRecord upsertRevision(
            String objectId,
            PlmAffectedItemRequest item,
            String currentUserId
    ) {
        PlmObjectRevisionRecord targetRevision = buildRevisionRecord(objectId, item.afterVersion(), "TARGET", item, currentUserId);
        PlmObjectRevisionRecord baselineRevision = buildRevisionRecord(objectId, item.beforeVersion(), "BASELINE", item, currentUserId);
        PlmObjectRevisionRecord lastRevision = null;
        if (baselineRevision != null) {
            lastRevision = upsertRevisionRecord(baselineRevision);
        }
        if (targetRevision != null) {
            lastRevision = upsertRevisionRecord(targetRevision);
        }
        return lastRevision;
    }

    private PlmObjectRevisionRecord upsertRevisionRecord(PlmObjectRevisionRecord record) {
        PlmObjectRevisionRecord existing = plmObjectRevisionMapper.selectByObjectIdAndRevisionCode(record.objectId(), record.revisionCode());
        if (existing == null) {
            plmObjectRevisionMapper.insert(record);
            return plmObjectRevisionMapper.selectByObjectIdAndRevisionCode(record.objectId(), record.revisionCode());
        }
        PlmObjectRevisionRecord updated = new PlmObjectRevisionRecord(
                existing.id(),
                record.objectId(),
                record.revisionCode(),
                record.versionLabel(),
                record.versionStatus(),
                record.checksum(),
                record.summaryJson(),
                record.snapshotJson(),
                record.createdBy(),
                existing.createdAt(),
                existing.updatedAt()
        );
        plmObjectRevisionMapper.update(updated);
        return plmObjectRevisionMapper.selectByObjectIdAndRevisionCode(record.objectId(), record.revisionCode());
    }

    private PlmObjectRevisionRecord buildRevisionRecord(
            String objectId,
            String versionCode,
            String versionStatus,
            PlmAffectedItemRequest item,
            String currentUserId
    ) {
        String normalizedVersion = normalizeValue(versionCode, null);
        if (normalizedVersion == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("itemType", normalizeValue(item.itemType(), null));
        summary.put("itemCode", normalizeValue(item.itemCode(), null));
        summary.put("itemName", normalizeValue(item.itemName(), null));
        summary.put("beforeVersion", normalizeValue(item.beforeVersion(), null));
        summary.put("afterVersion", normalizeValue(item.afterVersion(), null));
        summary.put("changeAction", normalizeValue(item.changeAction(), null));
        String summaryJson = toJson(summary);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("itemType", normalizeValue(item.itemType(), null));
        snapshot.put("itemCode", normalizeValue(item.itemCode(), null));
        snapshot.put("itemName", normalizeValue(item.itemName(), null));
        snapshot.put("beforeVersion", normalizeValue(item.beforeVersion(), null));
        snapshot.put("afterVersion", normalizeValue(item.afterVersion(), null));
        snapshot.put("changeAction", normalizeValue(item.changeAction(), null));
        snapshot.put("ownerUserId", normalizeValue(item.ownerUserId(), null));
        snapshot.put("remark", normalizeValue(item.remark(), null));
        String snapshotJson = toJson(snapshot);
        return new PlmObjectRevisionRecord(
                buildId("rev"),
                objectId,
                normalizedVersion,
                normalizedVersion,
                versionStatus,
                checksum(objectId + "|" + normalizedVersion + "|" + versionStatus + "|" + snapshotJson),
                summaryJson,
                snapshotJson,
                currentUserId,
                null,
                null
        );
    }

    private String checksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法生成版本校验值", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 PLM 对象版本数据", ex);
        }
    }

    private String normalizeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String buildId(String prefix) {
        return prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
