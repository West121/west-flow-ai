package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.plm.api.PlmAffectedItemRequest;
import com.westflow.plm.api.PlmRevisionDiffResponse;
import com.westflow.plm.mapper.PlmObjectRevisionMapper;
import com.westflow.plm.mapper.PlmRevisionDiffMapper;
import com.westflow.plm.model.PlmBillObjectLinkRecord;
import com.westflow.plm.model.PlmObjectRevisionRecord;
import com.westflow.plm.model.PlmRevisionDiffRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PLM 版本差异同步服务。
 */
@Service
@RequiredArgsConstructor
public class PlmRevisionDiffService {

    private final PlmRevisionDiffMapper plmRevisionDiffMapper;
    private final PlmObjectRevisionMapper plmObjectRevisionMapper;
    private final ObjectMapper objectMapper;

    public void syncBillRevisionDiffs(
            String businessType,
            String billId,
            List<PlmBillObjectLinkRecord> links,
            List<PlmAffectedItemRequest> affectedItems
    ) {
        plmRevisionDiffMapper.deleteByBusinessTypeAndBillId(businessType, billId);
        if (links == null || links.isEmpty()) {
            return;
        }

        List<PlmRevisionDiffRecord> records = new java.util.ArrayList<>();
        for (int index = 0; index < links.size(); index++) {
            PlmBillObjectLinkRecord link = links.get(index);
            PlmAffectedItemRequest item = affectedItems != null && index < affectedItems.size() ? affectedItems.get(index) : null;
            PlmObjectRevisionRecord beforeRevision = link.beforeRevisionCode() == null
                    ? null
                    : plmObjectRevisionMapper.selectByObjectIdAndRevisionCode(link.objectId(), link.beforeRevisionCode());
            PlmObjectRevisionRecord afterRevision = link.afterRevisionCode() == null
                    ? null
                    : plmObjectRevisionMapper.selectByObjectIdAndRevisionCode(link.objectId(), link.afterRevisionCode());

            records.add(new PlmRevisionDiffRecord(
                    buildId("diff"),
                    businessType,
                    billId,
                    link.objectId(),
                    beforeRevision == null ? null : beforeRevision.id(),
                    afterRevision == null ? null : afterRevision.id(),
                    inferDiffKind(link.roleCode(), item == null ? null : item.changeAction()),
                    buildSummary(link, item),
                    buildPayload(link, item, beforeRevision, afterRevision),
                    null,
                    null
            ));
        }

        plmRevisionDiffMapper.batchInsert(records);
    }

    public List<PlmRevisionDiffResponse> listBillRevisionDiffs(String businessType, String billId) {
        return plmRevisionDiffMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .map(record -> new PlmRevisionDiffResponse(
                        record.id(),
                        record.businessType(),
                        record.billId(),
                        record.objectId(),
                        record.beforeRevisionId(),
                        record.afterRevisionId(),
                        record.diffKind(),
                        record.diffSummary(),
                        record.diffPayloadJson(),
                        record.createdAt()
                ))
                .toList();
    }

    private String inferDiffKind(String roleCode, String changeAction) {
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (normalizedRole.contains("BOM")) {
            return "BOM_STRUCTURE";
        }
        if (normalizedRole.contains("DRAWING") || normalizedRole.contains("DOCUMENT") || normalizedRole.contains("DOC")) {
            return "DOCUMENT";
        }
        if (normalizedRole.contains("ROUTING") || normalizedRole.contains("PROCESS")) {
            return "ROUTING";
        }
        if (changeAction != null && changeAction.trim().equalsIgnoreCase("REPLACE")) {
            return "ATTRIBUTE";
        }
        return "ATTRIBUTE";
    }

    private String buildSummary(PlmBillObjectLinkRecord link, PlmAffectedItemRequest item) {
        StringBuilder builder = new StringBuilder();
        builder.append(link.roleCode() == null ? "UNKNOWN" : link.roleCode());
        builder.append(" ");
        builder.append(link.beforeRevisionCode() == null ? "--" : link.beforeRevisionCode());
        builder.append(" -> ");
        builder.append(link.afterRevisionCode() == null ? "--" : link.afterRevisionCode());
        if (item != null && item.remark() != null && !item.remark().isBlank()) {
            builder.append(" | ").append(item.remark().trim());
        }
        return builder.toString();
    }

    private String buildPayload(
            PlmBillObjectLinkRecord link,
            PlmAffectedItemRequest item,
            PlmObjectRevisionRecord beforeRevision,
            PlmObjectRevisionRecord afterRevision
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("objectId", link.objectId());
        payload.put("roleCode", link.roleCode());
        payload.put("changeAction", link.changeAction());
        payload.put("beforeRevision", beforeRevision == null ? null : beforeRevision.revisionCode());
        payload.put("afterRevision", afterRevision == null ? null : afterRevision.revisionCode());
        payload.put("item", item);
        return toJson(payload);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 PLM 版本差异", ex);
        }
    }

    private String buildId(String prefix) {
        return prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
