package com.westflow.oa.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.oa.api.CreateOACommonRequestBillRequest;
import com.westflow.oa.api.CreateOAExpenseBillRequest;
import com.westflow.oa.api.CreateOALeaveBillRequest;
import com.westflow.oa.api.OABillDraftListItemResponse;
import com.westflow.oa.api.OACommonRequestBillDetailResponse;
import com.westflow.oa.api.OAExpenseBillDetailResponse;
import com.westflow.oa.api.OALaunchResponse;
import com.westflow.oa.api.OALeaveBillDetailResponse;
import com.westflow.oa.mapper.OACommonRequestBillMapper;
import com.westflow.oa.mapper.OAExpenseBillMapper;
import com.westflow.oa.mapper.OALeaveBillMapper;
import com.westflow.oa.model.OACommonRequestBillRecord;
import com.westflow.oa.model.OAExpenseBillRecord;
import com.westflow.oa.model.OALeaveBillRecord;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.service.FlowableRuntimeStartService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OA 单据发起服务。
 */
@Service
@RequiredArgsConstructor
public class OALaunchService {

    private static final DateTimeFormatter BILL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final BusinessProcessBindingService businessProcessBindingService;
    private final BusinessProcessLinkMapper businessProcessLinkMapper;
    private final OALeaveBillMapper oaLeaveBillMapper;
    private final OAExpenseBillMapper oaExpenseBillMapper;
    private final OACommonRequestBillMapper oaCommonRequestBillMapper;
    private final FlowableRuntimeStartService flowableRuntimeStartService;

    /**
     * 发起请假单。
     */
    @Transactional
    public OALaunchResponse createLeaveBill(CreateOALeaveBillRequest request) {
        return submitLeaveDraftInternal(saveLeaveDraftInternal(null, request, false).billId(), request);
    }

    @Transactional
    public OALaunchResponse saveLeaveDraft(CreateOALeaveBillRequest request) {
        return saveLeaveDraftInternal(null, request, true);
    }

    @Transactional
    public OALaunchResponse updateLeaveDraft(String billId, CreateOALeaveBillRequest request) {
        return saveLeaveDraftInternal(billId, request, true);
    }

    @Transactional
    public OALaunchResponse submitLeaveDraft(String billId, CreateOALeaveBillRequest request) {
        return submitLeaveDraftInternal(billId, request);
    }

    /**
     * 发起报销单。
     */
    @Transactional
    public OALaunchResponse createExpenseBill(CreateOAExpenseBillRequest request) {
        return submitExpenseDraftInternal(saveExpenseDraftInternal(null, request, false).billId(), request);
    }

    @Transactional
    public OALaunchResponse saveExpenseDraft(CreateOAExpenseBillRequest request) {
        return saveExpenseDraftInternal(null, request, true);
    }

    @Transactional
    public OALaunchResponse updateExpenseDraft(String billId, CreateOAExpenseBillRequest request) {
        return saveExpenseDraftInternal(billId, request, true);
    }

    @Transactional
    public OALaunchResponse submitExpenseDraft(String billId, CreateOAExpenseBillRequest request) {
        return submitExpenseDraftInternal(billId, request);
    }

    /**
     * 发起通用申请单。
     */
    @Transactional
    public OALaunchResponse createCommonRequestBill(CreateOACommonRequestBillRequest request) {
        return submitCommonDraftInternal(saveCommonDraftInternal(null, request, false).billId(), request);
    }

    @Transactional
    public OALaunchResponse saveCommonRequestDraft(CreateOACommonRequestBillRequest request) {
        return saveCommonDraftInternal(null, request, true);
    }

    @Transactional
    public OALaunchResponse updateCommonRequestDraft(String billId, CreateOACommonRequestBillRequest request) {
        return saveCommonDraftInternal(billId, request, true);
    }

    @Transactional
    public OALaunchResponse submitCommonRequestDraft(String billId, CreateOACommonRequestBillRequest request) {
        return submitCommonDraftInternal(billId, request);
    }

    /**
     * 查询请假单详情。
     */
    public OALeaveBillDetailResponse leaveDetail(String billId) {
        OALeaveBillDetailResponse detail = oaLeaveBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("请假单不存在", billId);
        }
        return detail;
    }

    /**
     * 查询报销单详情。
     */
    public OAExpenseBillDetailResponse expenseDetail(String billId) {
        OAExpenseBillDetailResponse detail = oaExpenseBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("报销单不存在", billId);
        }
        return detail;
    }

    /**
     * 查询通用申请单详情。
     */
    public OACommonRequestBillDetailResponse commonDetail(String billId) {
        OACommonRequestBillDetailResponse detail = oaCommonRequestBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("通用申请单不存在", billId);
        }
        return detail;
    }

    public List<OABillDraftListItemResponse> leaveDrafts() {
        return oaLeaveBillMapper.selectDraftsByCreatorUserId(currentUserId());
    }

    public List<OABillDraftListItemResponse> expenseDrafts() {
        return oaExpenseBillMapper.selectDraftsByCreatorUserId(currentUserId());
    }

    public List<OABillDraftListItemResponse> commonDrafts() {
        return oaCommonRequestBillMapper.selectDraftsByCreatorUserId(currentUserId());
    }

    private OALaunchResponse saveLeaveDraftInternal(String billId, CreateOALeaveBillRequest request, boolean draftOnly) {
        String draftBillId = billId == null || billId.isBlank() ? buildId("leave") : billId;
        String billNo = billId == null || billId.isBlank() ? buildBillNo("LEAVE") : requireLeaveDraft(draftBillId).billNo();
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        String leaveType = normalizeLeaveType(request.leaveType());
        String reason = request.reason().trim();
        boolean urgent = Boolean.TRUE.equals(request.urgent());
        String managerUserId = normalizeManagerUserId(request.managerUserId());
        OALeaveBillRecord record = new OALeaveBillRecord(
                draftBillId,
                billNo,
                sceneCode,
                leaveType,
                request.days(),
                reason,
                urgent,
                managerUserId,
                null,
                "DRAFT",
                userId
        );
        if (billId == null || billId.isBlank()) {
            oaLeaveBillMapper.insert(record);
        } else {
            oaLeaveBillMapper.updateDraft(record);
        }
        return toDraftResponse(draftBillId, billNo);
    }

    private OALaunchResponse submitLeaveDraftInternal(String billId, CreateOALeaveBillRequest request) {
        OALeaveBillDetailResponse draft = requireLeaveDraft(billId);
        saveLeaveDraftInternal(billId, request, false);
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String leaveType = normalizeLeaveType(request.leaveType());
        String reason = request.reason().trim();
        boolean urgent = Boolean.TRUE.equals(request.urgent());
        String managerUserId = normalizeManagerUserId(request.managerUserId());
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_LEAVE",
                sceneCode,
                billId,
                buildLeaveFormData(leaveType, request.days(), reason, urgent, managerUserId)
        );
        oaLeaveBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_LEAVE", billId, startResponse, currentUserId());
        return toLaunchResponse(billId, draft.billNo(), startResponse);
    }

    private OALaunchResponse saveExpenseDraftInternal(String billId, CreateOAExpenseBillRequest request, boolean draftOnly) {
        String draftBillId = billId == null || billId.isBlank() ? buildId("expense") : billId;
        String billNo = billId == null || billId.isBlank() ? buildBillNo("EXPENSE") : requireExpenseDraft(draftBillId).billNo();
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        OAExpenseBillRecord record = new OAExpenseBillRecord(
                draftBillId,
                billNo,
                sceneCode,
                request.amount(),
                request.reason().trim(),
                null,
                "DRAFT",
                userId
        );
        if (billId == null || billId.isBlank()) {
            oaExpenseBillMapper.insert(record);
        } else {
            oaExpenseBillMapper.updateDraft(record);
        }
        return toDraftResponse(draftBillId, billNo);
    }

    private OALaunchResponse submitExpenseDraftInternal(String billId, CreateOAExpenseBillRequest request) {
        OAExpenseBillDetailResponse draft = requireExpenseDraft(billId);
        saveExpenseDraftInternal(billId, request, false);
        String sceneCode = normalizeSceneCode(request.sceneCode());
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_EXPENSE",
                sceneCode,
                billId,
                Map.of("amount", request.amount(), "reason", request.reason().trim())
        );
        oaExpenseBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_EXPENSE", billId, startResponse, currentUserId());
        return toLaunchResponse(billId, draft.billNo(), startResponse);
    }

    private OALaunchResponse saveCommonDraftInternal(String billId, CreateOACommonRequestBillRequest request, boolean draftOnly) {
        String draftBillId = billId == null || billId.isBlank() ? buildId("common") : billId;
        String billNo = billId == null || billId.isBlank() ? buildBillNo("COMMON") : requireCommonDraft(draftBillId).billNo();
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        OACommonRequestBillRecord record = new OACommonRequestBillRecord(
                draftBillId,
                billNo,
                sceneCode,
                request.title().trim(),
                request.content().trim(),
                null,
                "DRAFT",
                userId
        );
        if (billId == null || billId.isBlank()) {
            oaCommonRequestBillMapper.insert(record);
        } else {
            oaCommonRequestBillMapper.updateDraft(record);
        }
        return toDraftResponse(draftBillId, billNo);
    }

    private OALaunchResponse submitCommonDraftInternal(String billId, CreateOACommonRequestBillRequest request) {
        OACommonRequestBillDetailResponse draft = requireCommonDraft(billId);
        saveCommonDraftInternal(billId, request, false);
        String sceneCode = normalizeSceneCode(request.sceneCode());
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_COMMON",
                sceneCode,
                billId,
                Map.of("title", request.title().trim(), "content", request.content().trim())
        );
        oaCommonRequestBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_COMMON", billId, startResponse, currentUserId());
        return toLaunchResponse(billId, draft.billNo(), startResponse);
    }

    private StartProcessResponse startBusinessProcess(
            String businessType,
            String sceneCode,
            String billId,
            Map<String, Object> formData
    ) {
        // 通过业务类型和场景码解析流程定义，保持业务发起和流程绑定解耦。
        String processKey = businessProcessBindingService.resolveProcessKey(businessType, sceneCode);
        return flowableRuntimeStartService.start(new StartProcessRequest(
                processKey,
                billId,
                businessType,
                formData
        ));
    }

    private void insertLink(String businessType, String billId, StartProcessResponse startResponse, String userId) {
        // 业务单和流程实例之间用关联表保存，方便后续查询和追踪。
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

    private OALaunchResponse toLaunchResponse(String billId, String billNo, StartProcessResponse startResponse) {
        return new OALaunchResponse(
                billId,
                billNo,
                startResponse.instanceId(),
                startResponse.activeTasks().isEmpty() ? null : startResponse.activeTasks().get(0),
                startResponse.activeTasks()
        );
    }

    private OALaunchResponse toDraftResponse(String billId, String billNo) {
        return new OALaunchResponse(
                billId,
                billNo,
                null,
                null,
                List.of()
        );
    }

    private OALeaveBillDetailResponse requireLeaveDraft(String billId) {
        OALeaveBillDetailResponse detail = leaveDetail(billId);
        if (!"DRAFT".equals(detail.status()) || detail.processInstanceId() != null) {
            throw resourceNotFound("请假草稿不存在", billId);
        }
        return detail;
    }

    private OAExpenseBillDetailResponse requireExpenseDraft(String billId) {
        OAExpenseBillDetailResponse detail = expenseDetail(billId);
        if (!"DRAFT".equals(detail.status()) || detail.processInstanceId() != null) {
            throw resourceNotFound("报销草稿不存在", billId);
        }
        return detail;
    }

    private OACommonRequestBillDetailResponse requireCommonDraft(String billId) {
        OACommonRequestBillDetailResponse detail = commonDetail(billId);
        if (!"DRAFT".equals(detail.status()) || detail.processInstanceId() != null) {
            throw resourceNotFound("通用申请草稿不存在", billId);
        }
        return detail;
    }

    private Map<String, Object> buildLeaveFormData(
            String leaveType,
            Integer days,
            String reason,
            boolean urgent,
            String managerUserId
    ) {
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("leaveType", leaveType);
        formData.put("days", days);
        formData.put("leaveDays", days);
        formData.put("reason", reason);
        formData.put("urgent", urgent);
        formData.put("managerUserId", managerUserId);
        return formData;
    }

    private String normalizeSceneCode(String sceneCode) {
        return sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
    }

    private String normalizeLeaveType(String leaveType) {
        if (leaveType == null || leaveType.isBlank()) {
            return "ANNUAL";
        }
        return leaveType.trim().toUpperCase();
    }

    private String normalizeManagerUserId(String managerUserId) {
        if (managerUserId == null || managerUserId.isBlank()) {
            return "usr_002";
        }
        return managerUserId.trim();
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildBillNo(String prefix) {
        return prefix + "-" + LocalDate.now().format(BILL_DATE) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
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
