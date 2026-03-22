package com.westflow.plm.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.plm.service.PlmLaunchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLM 业务发起与详情接口。
 */
@RestController
@RequestMapping("/api/v1/plm")
@SaCheckLogin
@RequiredArgsConstructor
public class PLMController {

    private final PlmLaunchService plmLaunchService;

    /**
     * 发起 ECR 变更申请。
     */
    @PostMapping("/ecrs")
    public ApiResponse<PlmLaunchResponse> createEcr(@Valid @RequestBody CreatePLMEcrBillRequest request) {
        return ApiResponse.success(plmLaunchService.createEcrBill(request));
    }

    /**
     * 查询 ECR 变更申请详情。
     */
    @GetMapping("/ecrs/{billId}")
    public ApiResponse<PlmEcrBillDetailResponse> ecrDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.ecrDetail(billId));
    }

    /**
     * 发起 ECO 变更执行。
     */
    @PostMapping("/ecos")
    public ApiResponse<PlmLaunchResponse> createEco(@Valid @RequestBody CreatePLMEcoBillRequest request) {
        return ApiResponse.success(plmLaunchService.createEcoBill(request));
    }

    /**
     * 查询 ECO 变更执行详情。
     */
    @GetMapping("/ecos/{billId}")
    public ApiResponse<PlmEcoBillDetailResponse> ecoDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.ecoDetail(billId));
    }

    /**
     * 发起物料主数据变更申请。
     */
    @PostMapping("/material-master-changes")
    public ApiResponse<PlmLaunchResponse> createMaterialChange(@Valid @RequestBody CreatePLMMaterialChangeBillRequest request) {
        return ApiResponse.success(plmLaunchService.createMaterialChangeBill(request));
    }

    /**
     * 查询物料主数据变更申请详情。
     */
    @GetMapping("/material-master-changes/{billId}")
    public ApiResponse<PlmMaterialChangeBillDetailResponse> materialChangeDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.materialChangeDetail(billId));
    }
}
