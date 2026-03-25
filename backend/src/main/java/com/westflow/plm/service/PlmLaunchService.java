package com.westflow.plm.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.plm.api.CreatePLMEcoBillRequest;
import com.westflow.plm.api.CreatePLMEcrBillRequest;
import com.westflow.plm.api.CreatePLMMaterialChangeBillRequest;
import com.westflow.plm.api.PlmEcoBillDetailResponse;
import com.westflow.plm.api.PlmEcoBillListItemResponse;
import com.westflow.plm.api.PlmEcrBillDetailResponse;
import com.westflow.plm.api.PlmEcrBillListItemResponse;
import com.westflow.plm.api.PlmLaunchResponse;
import com.westflow.plm.api.PlmMaterialChangeBillDetailResponse;
import com.westflow.plm.api.PlmMaterialChangeBillListItemResponse;
import com.westflow.plm.mapper.PlmEcoBillMapper;
import com.westflow.plm.mapper.PlmEcrBillMapper;
import com.westflow.plm.mapper.PlmMaterialChangeBillMapper;
import com.westflow.plm.model.PlmEcoBillRecord;
import com.westflow.plm.model.PlmEcrBillRecord;
import com.westflow.plm.model.PlmMaterialChangeBillRecord;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.request.ApprovalSheetListView;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.FlowableRuntimeStartService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM 单据发起与详情查询服务。
 */
@Service
@RequiredArgsConstructor
public class PlmLaunchService {

    private static final List<String> PLM_APPROVAL_BUSINESS_TYPES = List.of(
            "PLM_ECR",
            "PLM_ECO",
            "PLM_MATERIAL"
    );

    private final BusinessProcessBindingService businessProcessBindingService;
    private final BusinessProcessLinkMapper businessProcessLinkMapper;
    private final PlmEcrBillMapper plmEcrBillMapper;
    private final PlmEcoBillMapper plmEcoBillMapper;
    private final PlmMaterialChangeBillMapper plmMaterialChangeBillMapper;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    /**
     * 发起 ECR 变更申请。
     */
    @Transactional
    public PlmLaunchResponse createEcrBill(CreatePLMEcrBillRequest request) {
        String billId = buildId("ecr");
        String billNo = buildBillNo("ECR");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        plmEcrBillMapper.insert(new PlmEcrBillRecord(
                billId,
                billNo,
                sceneCode,
                request.changeTitle().trim(),
                request.changeReason().trim(),
                blankToNull(request.affectedProductCode()),
                blankToNull(request.priorityLevel()),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_ECR",
                sceneCode,
                billId,
                buildFormData(
                        "changeTitle", request.changeTitle().trim(),
                        "changeReason", request.changeReason().trim(),
                        "affectedProductCode", blankToNull(request.affectedProductCode()),
                        "priorityLevel", blankToNull(request.priorityLevel())
                )
        );
        plmEcrBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_ECR", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
    }

    /**
     * 发起 ECO 变更执行。
     */
    @Transactional
    public PlmLaunchResponse createEcoBill(CreatePLMEcoBillRequest request) {
        String billId = buildId("eco");
        String billNo = buildBillNo("ECO");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        plmEcoBillMapper.insert(new PlmEcoBillRecord(
                billId,
                billNo,
                sceneCode,
                request.executionTitle().trim(),
                request.executionPlan().trim(),
                request.effectiveDate(),
                request.changeReason().trim(),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_ECO",
                sceneCode,
                billId,
                buildFormData(
                        "executionTitle", request.executionTitle().trim(),
                        "executionPlan", request.executionPlan().trim(),
                        "effectiveDate", request.effectiveDate() == null ? null : request.effectiveDate().toString(),
                        "changeReason", request.changeReason().trim()
                )
        );
        plmEcoBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_ECO", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
    }

    /**
     * 发起物料主数据变更申请。
     */
    @Transactional
    public PlmLaunchResponse createMaterialChangeBill(CreatePLMMaterialChangeBillRequest request) {
        String billId = buildId("material");
        String billNo = buildBillNo("MAT");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        plmMaterialChangeBillMapper.insert(new PlmMaterialChangeBillRecord(
                billId,
                billNo,
                sceneCode,
                request.materialCode().trim(),
                request.materialName().trim(),
                request.changeReason().trim(),
                blankToNull(request.changeType()),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_MATERIAL",
                sceneCode,
                billId,
                buildFormData(
                        "materialCode", request.materialCode().trim(),
                        "materialName", request.materialName().trim(),
                        "changeReason", request.changeReason().trim(),
                        "changeType", blankToNull(request.changeType())
                )
        );
        plmMaterialChangeBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_MATERIAL", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
    }

    /**
     * 查询 ECR 详情。
     */
    public PlmEcrBillDetailResponse ecrDetail(String billId) {
        PlmEcrBillDetailResponse detail = plmEcrBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("ECR 变更申请不存在", billId);
        }
        return detail;
    }

    /**
     * 查询 ECR 业务单分页列表。
     */
    public PageResponse<PlmEcrBillListItemResponse> ecrPage(PageRequest request) {
        String keyword = normalizeKeyword(request.keyword());
        String status = resolveStatusFilter(request.filters());
        return toPageResponse(
                request,
                plmEcrBillMapper.countPage(keyword, status),
                plmEcrBillMapper.selectPage(keyword, status, request.pageSize(), offsetOf(request))
        );
    }

    /**
     * 查询 ECO 详情。
     */
    public PlmEcoBillDetailResponse ecoDetail(String billId) {
        PlmEcoBillDetailResponse detail = plmEcoBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("ECO 变更执行不存在", billId);
        }
        return detail;
    }

    /**
     * 查询 ECO 业务单分页列表。
     */
    public PageResponse<PlmEcoBillListItemResponse> ecoPage(PageRequest request) {
        String keyword = normalizeKeyword(request.keyword());
        String status = resolveStatusFilter(request.filters());
        return toPageResponse(
                request,
                plmEcoBillMapper.countPage(keyword, status),
                plmEcoBillMapper.selectPage(keyword, status, request.pageSize(), offsetOf(request))
        );
    }

    /**
     * 查询物料主数据变更详情。
     */
    public PlmMaterialChangeBillDetailResponse materialChangeDetail(String billId) {
        PlmMaterialChangeBillDetailResponse detail = plmMaterialChangeBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("物料主数据变更申请不存在", billId);
        }
        return detail;
    }

    /**
     * 查询物料主数据变更分页列表。
     */
    public PageResponse<PlmMaterialChangeBillListItemResponse> materialChangePage(PageRequest request) {
        String keyword = normalizeKeyword(request.keyword());
        String status = resolveStatusFilter(request.filters());
        return toPageResponse(
                request,
                plmMaterialChangeBillMapper.countPage(keyword, status),
                plmMaterialChangeBillMapper.selectPage(keyword, status, request.pageSize(), offsetOf(request))
        );
    }

    /**
     * 查询当前登录人发起的 PLM 审批单列表。
     */
    public PageResponse<ApprovalSheetListItemResponse> approvalSheetPage(PageRequest request) {
        return flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                ApprovalSheetListView.INITIATED,
                PLM_APPROVAL_BUSINESS_TYPES,
                request.page(),
                request.pageSize(),
                request.keyword(),
                request.filters(),
                request.sorts(),
                request.groups()
        ));
    }

    private StartProcessResponse startBusinessProcess(
            String businessType,
            String sceneCode,
            String billId,
            Map<String, Object> formData
    ) {
        String processKey = businessProcessBindingService.resolveProcessKey(businessType, sceneCode);
        return flowableRuntimeStartService.start(new StartProcessRequest(
                processKey,
                billId,
                businessType,
                formData
        ));
    }

    private void insertLink(String businessType, String billId, StartProcessResponse startResponse, String userId) {
        businessProcessLinkMapper.insertLink(new BusinessProcessLinkRecord(
                buildId("bpl"),
                businessType,
                billId,
                startResponse.instanceId(),
                startResponse.processDefinitionId(),
                userId,
                startResponse.status()
        ));
    }

    private PlmLaunchResponse toLaunchResponse(String billId, String billNo, StartProcessResponse startResponse) {
        return new PlmLaunchResponse(
                billId,
                billNo,
                startResponse.instanceId(),
                startResponse.activeTasks().isEmpty() ? null : startResponse.activeTasks().get(0),
                startResponse.activeTasks()
        );
    }

    private String normalizeSceneCode(String sceneCode) {
        return sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> buildFormData(Object... keyValues) {
        Map<String, Object> formData = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = keyValues[index + 1];
            if (value != null) {
                formData.put(key, value);
            }
        }
        return formData;
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildBillNo(String prefix) {
        return prefix + "-" + java.time.LocalDate.now() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    /**
     * 将分页查询结果统一转换成分页响应。
     */
    private <T> PageResponse<T> toPageResponse(PageRequest request, long total, List<T> records) {
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / request.pageSize());
        return new PageResponse<>(
                request.page(),
                request.pageSize(),
                total,
                pages,
                records,
                List.of()
        );
    }

    /**
     * 将分页页码转换成数据库 offset。
     */
    private int offsetOf(PageRequest request) {
        return Math.max(0, (request.page() - 1) * request.pageSize());
    }

    /**
     * 关键字为空时统一转成 null，方便 SQL 复用。
     */
    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /**
     * 解析分页请求中的状态筛选。
     */
    private String resolveStatusFilter(List<FilterItem> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.stream()
                .filter(filter -> "status".equalsIgnoreCase(filter.field()))
                .filter(filter -> "eq".equalsIgnoreCase(filter.operator()))
                .map(FilterItem::value)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.isTextual() ? value.asText() : null)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private ContractException resourceNotFound(String message, String billId) {
        return new ContractException(
                "BIZ.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                Map.of("billId", billId)
        );
    }
}
