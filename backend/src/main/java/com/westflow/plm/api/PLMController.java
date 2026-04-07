package com.westflow.plm.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.plm.service.PlmLaunchService;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLM 业务发起、生命周期与台账接口。
 */
@RestController
@RequestMapping("/api/v1/plm")
@SaCheckLogin
@RequiredArgsConstructor
public class PLMController {

    private final PlmLaunchService plmLaunchService;

    @GetMapping("/dashboard/summary")
    public ApiResponse<PlmDashboardSummaryResponse> dashboardSummary() {
        return ApiResponse.success(plmLaunchService.dashboardSummary());
    }

    @GetMapping("/dashboard/analytics")
    public ApiResponse<PlmDashboardAnalyticsResponse> dashboardAnalytics() {
        return ApiResponse.success(plmLaunchService.dashboardAnalytics());
    }

    @GetMapping("/bills/{businessType}/{billId}/object-links")
    public ApiResponse<java.util.List<PlmObjectLinkResponse>> objectLinks(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.objectLinks(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/revision-diffs")
    public ApiResponse<java.util.List<PlmRevisionDiffResponse>> revisionDiffs(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.revisionDiffs(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/implementation-tasks")
    public ApiResponse<java.util.List<PlmImplementationTaskResponse>> implementationTasks(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.implementationTasks(businessType, billId));
    }

    @PostMapping("/bills/{businessType}/{billId}/implementation-tasks")
    public ApiResponse<PlmImplementationTaskResponse> upsertImplementationTask(
            @PathVariable String businessType,
            @PathVariable String billId,
            @RequestBody PlmImplementationTaskUpsertRequest request
    ) {
        return ApiResponse.success(plmLaunchService.upsertImplementationTask(businessType, billId, request));
    }

    @PutMapping("/bills/{businessType}/{billId}/implementation-tasks/{taskId}/start")
    public ApiResponse<PlmImplementationTaskResponse> startImplementationTask(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String taskId,
            @RequestBody(required = false) PlmImplementationTaskActionRequest request
    ) {
        return ApiResponse.success(plmLaunchService.startImplementationTask(businessType, billId, taskId, request));
    }

    @PutMapping("/bills/{businessType}/{billId}/implementation-tasks/{taskId}/complete")
    public ApiResponse<PlmImplementationTaskResponse> completeImplementationTask(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String taskId,
            @RequestBody(required = false) PlmImplementationTaskActionRequest request
    ) {
        return ApiResponse.success(plmLaunchService.completeImplementationTask(businessType, billId, taskId, request));
    }

    @PutMapping("/bills/{businessType}/{billId}/implementation-tasks/{taskId}/block")
    public ApiResponse<PlmImplementationTaskResponse> blockImplementationTask(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String taskId,
            @RequestBody(required = false) PlmImplementationTaskActionRequest request
    ) {
        return ApiResponse.success(plmLaunchService.blockImplementationTask(businessType, billId, taskId, request));
    }

    @PutMapping("/bills/{businessType}/{billId}/implementation-tasks/{taskId}/cancel")
    public ApiResponse<PlmImplementationTaskResponse> cancelImplementationTask(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String taskId,
            @RequestBody(required = false) PlmImplementationTaskActionRequest request
    ) {
        return ApiResponse.success(plmLaunchService.cancelImplementationTask(businessType, billId, taskId, request));
    }

    @PostMapping("/ecrs")
    public ApiResponse<PlmLaunchResponse> createEcr(@Valid @RequestBody CreatePLMEcrBillRequest request) {
        return ApiResponse.success(plmLaunchService.createEcrBill(request));
    }

    @PostMapping("/ecrs/draft")
    public ApiResponse<PlmEcrBillDetailResponse> saveEcrDraft(@Valid @RequestBody CreatePLMEcrBillRequest request) {
        return ApiResponse.success(plmLaunchService.saveEcrDraft(request));
    }

    @PutMapping("/ecrs/{billId}/draft")
    public ApiResponse<PlmEcrBillDetailResponse> updateEcrDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreatePLMEcrBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.updateEcrDraft(billId, request));
    }

    @PostMapping("/ecrs/{billId}/submit")
    public ApiResponse<PlmLaunchResponse> submitEcr(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.submitEcrBill(billId));
    }

    @PostMapping("/ecrs/{billId}/cancel")
    public ApiResponse<PlmBillActionResponse> cancelEcr(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.cancelEcrBill(billId));
    }

    @PostMapping("/ecrs/{billId}/implementation")
    public ApiResponse<PlmBillActionResponse> startEcrImplementation(
            @PathVariable String billId,
            @RequestBody(required = false) StartPlmImplementationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.startEcrImplementation(billId, request));
    }

    @PostMapping("/ecrs/{billId}/validation")
    public ApiResponse<PlmBillActionResponse> submitEcrValidation(
            @PathVariable String billId,
            @RequestBody(required = false) SubmitPlmValidationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.submitEcrValidation(billId, request));
    }

    @PostMapping("/ecrs/{billId}/close")
    public ApiResponse<PlmBillActionResponse> closeEcr(
            @PathVariable String billId,
            @RequestBody(required = false) ClosePlmBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.closeEcrBill(billId, request));
    }

    @GetMapping("/ecrs/{billId}")
    public ApiResponse<PlmEcrBillDetailResponse> ecrDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.ecrDetail(billId));
    }

    @PostMapping("/ecrs/page")
    public ApiResponse<PageResponse<PlmEcrBillListItemResponse>> ecrPage(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(plmLaunchService.ecrPage(request));
    }

    @PostMapping("/ecos")
    public ApiResponse<PlmLaunchResponse> createEco(@Valid @RequestBody CreatePLMEcoBillRequest request) {
        return ApiResponse.success(plmLaunchService.createEcoBill(request));
    }

    @PostMapping("/ecos/draft")
    public ApiResponse<PlmEcoBillDetailResponse> saveEcoDraft(@Valid @RequestBody CreatePLMEcoBillRequest request) {
        return ApiResponse.success(plmLaunchService.saveEcoDraft(request));
    }

    @PutMapping("/ecos/{billId}/draft")
    public ApiResponse<PlmEcoBillDetailResponse> updateEcoDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreatePLMEcoBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.updateEcoDraft(billId, request));
    }

    @PostMapping("/ecos/{billId}/submit")
    public ApiResponse<PlmLaunchResponse> submitEco(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.submitEcoBill(billId));
    }

    @PostMapping("/ecos/{billId}/cancel")
    public ApiResponse<PlmBillActionResponse> cancelEco(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.cancelEcoBill(billId));
    }

    @PostMapping("/ecos/{billId}/implementation")
    public ApiResponse<PlmBillActionResponse> startEcoImplementation(
            @PathVariable String billId,
            @RequestBody(required = false) StartPlmImplementationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.startEcoImplementation(billId, request));
    }

    @PostMapping("/ecos/{billId}/validation")
    public ApiResponse<PlmBillActionResponse> submitEcoValidation(
            @PathVariable String billId,
            @RequestBody(required = false) SubmitPlmValidationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.submitEcoValidation(billId, request));
    }

    @PostMapping("/ecos/{billId}/close")
    public ApiResponse<PlmBillActionResponse> closeEco(
            @PathVariable String billId,
            @RequestBody(required = false) ClosePlmBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.closeEcoBill(billId, request));
    }

    @GetMapping("/ecos/{billId}")
    public ApiResponse<PlmEcoBillDetailResponse> ecoDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.ecoDetail(billId));
    }

    @PostMapping("/ecos/page")
    public ApiResponse<PageResponse<PlmEcoBillListItemResponse>> ecoPage(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(plmLaunchService.ecoPage(request));
    }

    @PostMapping("/material-master-changes")
    public ApiResponse<PlmLaunchResponse> createMaterialChange(@Valid @RequestBody CreatePLMMaterialChangeBillRequest request) {
        return ApiResponse.success(plmLaunchService.createMaterialChangeBill(request));
    }

    @PostMapping("/material-master-changes/draft")
    public ApiResponse<PlmMaterialChangeBillDetailResponse> saveMaterialChangeDraft(
            @Valid @RequestBody CreatePLMMaterialChangeBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.saveMaterialChangeDraft(request));
    }

    @PutMapping("/material-master-changes/{billId}/draft")
    public ApiResponse<PlmMaterialChangeBillDetailResponse> updateMaterialChangeDraft(
            @PathVariable String billId,
            @Valid @RequestBody CreatePLMMaterialChangeBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.updateMaterialChangeDraft(billId, request));
    }

    @PostMapping("/material-master-changes/{billId}/submit")
    public ApiResponse<PlmLaunchResponse> submitMaterialChange(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.submitMaterialChangeBill(billId));
    }

    @PostMapping("/material-master-changes/{billId}/cancel")
    public ApiResponse<PlmBillActionResponse> cancelMaterialChange(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.cancelMaterialChangeBill(billId));
    }

    @PostMapping("/material-master-changes/{billId}/implementation")
    public ApiResponse<PlmBillActionResponse> startMaterialChangeImplementation(
            @PathVariable String billId,
            @RequestBody(required = false) StartPlmImplementationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.startMaterialChangeImplementation(billId, request));
    }

    @PostMapping("/material-master-changes/{billId}/validation")
    public ApiResponse<PlmBillActionResponse> submitMaterialChangeValidation(
            @PathVariable String billId,
            @RequestBody(required = false) SubmitPlmValidationRequest request
    ) {
        return ApiResponse.success(plmLaunchService.submitMaterialChangeValidation(billId, request));
    }

    @PostMapping("/material-master-changes/{billId}/close")
    public ApiResponse<PlmBillActionResponse> closeMaterialChange(
            @PathVariable String billId,
            @RequestBody(required = false) ClosePlmBillRequest request
    ) {
        return ApiResponse.success(plmLaunchService.closeMaterialChangeBill(billId, request));
    }

    @GetMapping("/material-master-changes/{billId}")
    public ApiResponse<PlmMaterialChangeBillDetailResponse> materialChangeDetail(@PathVariable String billId) {
        return ApiResponse.success(plmLaunchService.materialChangeDetail(billId));
    }

    @PostMapping("/material-master-changes/page")
    public ApiResponse<PageResponse<PlmMaterialChangeBillListItemResponse>> materialChangePage(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(plmLaunchService.materialChangePage(request));
    }

    @GetMapping("/approval-sheets")
    public ApiResponse<PageResponse<ApprovalSheetListItemResponse>> approvalSheetPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(plmLaunchService.approvalSheetPage(new PageRequest(
                page,
                pageSize,
                keyword,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        )));
    }
}
