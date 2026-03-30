package com.westflow.oa.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.oa.service.OALaunchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 单据发起和详情查询接口。
 */
@RestController
@RequestMapping("/api/v1/oa")
@SaCheckLogin
@RequiredArgsConstructor
public class OAController {

    private final OALaunchService oaLaunchService;

    /**
     * 发起请假单。
     */
    @PostMapping("/leaves")
    public ApiResponse<OALaunchResponse> createLeave(@Valid @RequestBody CreateOALeaveBillRequest request) {
        return ApiResponse.success(oaLaunchService.createLeaveBill(request));
    }

    @PostMapping("/leaves/draft")
    public ApiResponse<OALaunchResponse> saveLeaveDraft(@Valid @RequestBody CreateOALeaveBillRequest request) {
        return ApiResponse.success(oaLaunchService.saveLeaveDraft(request));
    }

    @PutMapping("/leaves/{billId}/draft")
    public ApiResponse<OALaunchResponse> updateLeaveDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOALeaveBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.updateLeaveDraft(billId, request));
    }

    @PostMapping("/leaves/{billId}/submit")
    public ApiResponse<OALaunchResponse> submitLeaveDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOALeaveBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.submitLeaveDraft(billId, request));
    }

    @GetMapping("/leaves/drafts")
    public ApiResponse<java.util.List<OABillDraftListItemResponse>> leaveDrafts() {
        return ApiResponse.success(oaLaunchService.leaveDrafts());
    }

    /**
     * 查询请假单详情。
     */
    @GetMapping("/leaves/{billId}")
    public ApiResponse<OALeaveBillDetailResponse> leaveDetail(@PathVariable String billId) {
        return ApiResponse.success(oaLaunchService.leaveDetail(billId));
    }

    /**
     * 发起报销单。
     */
    @PostMapping("/expenses")
    public ApiResponse<OALaunchResponse> createExpense(@Valid @RequestBody CreateOAExpenseBillRequest request) {
        return ApiResponse.success(oaLaunchService.createExpenseBill(request));
    }

    @PostMapping("/expenses/draft")
    public ApiResponse<OALaunchResponse> saveExpenseDraft(@Valid @RequestBody CreateOAExpenseBillRequest request) {
        return ApiResponse.success(oaLaunchService.saveExpenseDraft(request));
    }

    @PutMapping("/expenses/{billId}/draft")
    public ApiResponse<OALaunchResponse> updateExpenseDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOAExpenseBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.updateExpenseDraft(billId, request));
    }

    @PostMapping("/expenses/{billId}/submit")
    public ApiResponse<OALaunchResponse> submitExpenseDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOAExpenseBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.submitExpenseDraft(billId, request));
    }

    @GetMapping("/expenses/drafts")
    public ApiResponse<java.util.List<OABillDraftListItemResponse>> expenseDrafts() {
        return ApiResponse.success(oaLaunchService.expenseDrafts());
    }

    /**
     * 查询报销单详情。
     */
    @GetMapping("/expenses/{billId}")
    public ApiResponse<OAExpenseBillDetailResponse> expenseDetail(@PathVariable String billId) {
        return ApiResponse.success(oaLaunchService.expenseDetail(billId));
    }

    /**
     * 发起通用申请单。
     */
    @PostMapping("/common-requests")
    public ApiResponse<OALaunchResponse> createCommon(@Valid @RequestBody CreateOACommonRequestBillRequest request) {
        return ApiResponse.success(oaLaunchService.createCommonRequestBill(request));
    }

    @PostMapping("/common-requests/draft")
    public ApiResponse<OALaunchResponse> saveCommonDraft(@Valid @RequestBody CreateOACommonRequestBillRequest request) {
        return ApiResponse.success(oaLaunchService.saveCommonRequestDraft(request));
    }

    @PutMapping("/common-requests/{billId}/draft")
    public ApiResponse<OALaunchResponse> updateCommonDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOACommonRequestBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.updateCommonRequestDraft(billId, request));
    }

    @PostMapping("/common-requests/{billId}/submit")
    public ApiResponse<OALaunchResponse> submitCommonDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreateOACommonRequestBillRequest request
    ) {
        return ApiResponse.success(oaLaunchService.submitCommonRequestDraft(billId, request));
    }

    @GetMapping("/common-requests/drafts")
    public ApiResponse<java.util.List<OABillDraftListItemResponse>> commonDrafts() {
        return ApiResponse.success(oaLaunchService.commonDrafts());
    }

    /**
     * 查询通用申请单详情。
     */
    @GetMapping("/common-requests/{billId}")
    public ApiResponse<OACommonRequestBillDetailResponse> commonDetail(@PathVariable String billId) {
        return ApiResponse.success(oaLaunchService.commonDetail(billId));
    }
}
