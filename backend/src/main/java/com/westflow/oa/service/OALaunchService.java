package com.westflow.oa.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.oa.api.CreateOACommonRequestBillRequest;
import com.westflow.oa.api.CreateOAExpenseBillRequest;
import com.westflow.oa.api.CreateOALeaveBillRequest;
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
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.service.ProcessDemoService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final ProcessDemoService processDemoService;

    /**
     * 发起请假单。
     */
    @Transactional
    public OALaunchResponse createLeaveBill(CreateOALeaveBillRequest request) {
        // 先落业务单，再启动流程实例，避免流程实例和业务单脱节。
        String billId = buildId("leave");
        String billNo = buildBillNo("LEAVE");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        oaLeaveBillMapper.insert(new OALeaveBillRecord(
                billId,
                billNo,
                sceneCode,
                request.days(),
                request.reason().trim(),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_LEAVE",
                sceneCode,
                billId,
                Map.of("days", request.days(), "reason", request.reason().trim())
        );
        oaLeaveBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_LEAVE", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
    }

    /**
     * 发起报销单。
     */
    @Transactional
    public OALaunchResponse createExpenseBill(CreateOAExpenseBillRequest request) {
        String billId = buildId("expense");
        String billNo = buildBillNo("EXPENSE");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        oaExpenseBillMapper.insert(new OAExpenseBillRecord(
                billId,
                billNo,
                sceneCode,
                request.amount(),
                request.reason().trim(),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_EXPENSE",
                sceneCode,
                billId,
                Map.of("amount", request.amount(), "reason", request.reason().trim())
        );
        oaExpenseBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_EXPENSE", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
    }

    /**
     * 发起通用申请单。
     */
    @Transactional
    public OALaunchResponse createCommonRequestBill(CreateOACommonRequestBillRequest request) {
        String billId = buildId("common");
        String billNo = buildBillNo("COMMON");
        String sceneCode = normalizeSceneCode(request.sceneCode());
        String userId = currentUserId();
        oaCommonRequestBillMapper.insert(new OACommonRequestBillRecord(
                billId,
                billNo,
                sceneCode,
                request.title().trim(),
                request.content().trim(),
                null,
                "DRAFT",
                userId
        ));
        StartProcessResponse startResponse = startBusinessProcess(
                "OA_COMMON",
                sceneCode,
                billId,
                Map.of("title", request.title().trim(), "content", request.content().trim())
        );
        oaCommonRequestBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("OA_COMMON", billId, startResponse, userId);
        return toLaunchResponse(billId, billNo, startResponse);
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

    private StartProcessResponse startBusinessProcess(
            String businessType,
            String sceneCode,
            String billId,
            Map<String, Object> formData
    ) {
        // 通过业务类型和场景码解析流程定义，保持业务发起和流程绑定解耦。
        String processKey = businessProcessBindingService.resolveProcessKey(businessType, sceneCode);
        return processDemoService.start(new StartProcessRequest(
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

    private String normalizeSceneCode(String sceneCode) {
        return sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
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
