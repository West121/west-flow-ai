package com.westflow.oa.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.oa.service.OALaunchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        // 请假、报销、通用申请都走同一套发起服务。
        return ApiResponse.success(oaLaunchService.createLeaveBill(request));
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

    /**
     * 查询通用申请单详情。
     */
    @GetMapping("/common-requests/{billId}")
    public ApiResponse<OACommonRequestBillDetailResponse> commonDetail(@PathVariable String billId) {
        return ApiResponse.success(oaLaunchService.commonDetail(billId));
    }
}
