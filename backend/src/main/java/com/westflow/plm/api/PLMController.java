package com.westflow.plm.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.plm.service.PlmConnectorAckService;
import com.westflow.plm.service.PlmConnectorOrchestrationService;
import com.westflow.plm.service.PlmImplementationEvidenceService;
import com.westflow.plm.api.PlmImplementationEvidenceUpdateRequest;
import com.westflow.plm.service.PlmImplementationTemplateService;
import com.westflow.plm.service.PlmLaunchService;
import com.westflow.plm.service.PlmProjectService;
import com.westflow.plm.service.PlmPublicationService;
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
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * PLM 业务发起、生命周期与台账接口。
 */
@RestController
@RequestMapping("/api/v1/plm")
@SaCheckLogin
@RequiredArgsConstructor
public class PLMController {

    private final PlmLaunchService plmLaunchService;
    private final PlmConnectorOrchestrationService plmConnectorOrchestrationService;
    private final PlmConnectorAckService plmConnectorAckService;
    private final PlmImplementationTemplateService plmImplementationTemplateService;
    private final PlmImplementationEvidenceService plmImplementationEvidenceService;
    private final PlmPublicationService plmPublicationService;
    private final PlmProjectService plmProjectService;

    @PostMapping("/projects/page")
    public ApiResponse<PageResponse<PlmProjectListItemResponse>> projectPage(
            @Valid @RequestBody PageRequest request
    ) {
        return ApiResponse.success(plmProjectService.page(request));
    }

    @PostMapping("/projects")
    public ApiResponse<PlmProjectDetailResponse> createProject(
            @Valid @RequestBody CreatePlmProjectRequest request
    ) {
        return ApiResponse.success(plmProjectService.createProject(request));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<PlmProjectDetailResponse> projectDetail(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.detail(projectId));
    }

    @PutMapping("/projects/{projectId}")
    public ApiResponse<PlmProjectDetailResponse> updateProject(
            @PathVariable String projectId,
            @Valid @RequestBody UpdatePlmProjectRequest request
    ) {
        return ApiResponse.success(plmProjectService.updateProject(projectId, request));
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public ApiResponse<PlmProjectDashboardResponse> projectDashboard(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.dashboard(projectId));
    }

    @GetMapping("/projects/{projectId}/members")
    public ApiResponse<java.util.List<PlmProjectMemberResponse>> projectMembers(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.members(projectId));
    }

    @GetMapping("/projects/{projectId}/milestones")
    public ApiResponse<java.util.List<PlmProjectMilestoneResponse>> projectMilestones(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.milestones(projectId));
    }

    @GetMapping("/projects/{projectId}/links")
    public ApiResponse<java.util.List<PlmProjectLinkResponse>> projectLinks(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.links(projectId));
    }

    @GetMapping("/projects/{projectId}/stage-events")
    public ApiResponse<java.util.List<PlmProjectStageEventResponse>> projectStageEvents(@PathVariable String projectId) {
        return ApiResponse.success(plmProjectService.stageEvents(projectId));
    }

    @PostMapping("/projects/{projectId}/phase-transition")
    public ApiResponse<PlmProjectDetailResponse> transitionProjectPhase(
            @PathVariable String projectId,
            @Valid @RequestBody PlmProjectPhaseTransitionRequest request
    ) {
        return ApiResponse.success(plmProjectService.transitionPhase(projectId, request));
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<PlmDashboardSummaryResponse> dashboardSummary() {
        return ApiResponse.success(plmLaunchService.dashboardSummary());
    }

    @GetMapping("/dashboard/analytics")
    public ApiResponse<PlmDashboardAnalyticsResponse> dashboardAnalytics() {
        return ApiResponse.success(plmLaunchService.dashboardAnalytics());
    }

    @GetMapping("/dashboard/cockpit")
    public ApiResponse<PlmDashboardCockpitResponse> dashboardCockpit() {
        return ApiResponse.success(plmLaunchService.dashboardCockpit());
    }

    @GetMapping("/bills/{businessType}/{billId}/object-links")
    public ApiResponse<java.util.List<PlmObjectLinkResponse>> objectLinks(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.objectLinks(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/bom-nodes")
    public ApiResponse<java.util.List<PlmBomNodeResponse>> bomNodes(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.bomNodes(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/document-assets")
    public ApiResponse<java.util.List<PlmDocumentAssetResponse>> documentAssets(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmPublicationService.listDocumentAssets(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/baselines")
    public ApiResponse<java.util.List<PlmConfigurationBaselineResponse>> baselines(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmPublicationService.listBaselines(businessType, billId));
    }

    @PutMapping("/bills/{businessType}/{billId}/baselines/{baselineId}/release")
    public ApiResponse<PlmPublicationActionResponse> releaseBaseline(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String baselineId,
            @RequestBody(required = false) PlmPublicationActionRequest request
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        return ApiResponse.success(
                plmPublicationService.releaseBaseline(
                        businessType,
                        billId,
                        baselineId,
                        request,
                        plmLaunchService.currentUserIdForWorkspace()
                )
        );
    }

    @PutMapping("/bills/{businessType}/{billId}/document-assets/{assetId}/release")
    public ApiResponse<PlmPublicationActionResponse> releaseDocumentAsset(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String assetId,
            @RequestBody(required = false) PlmPublicationActionRequest request
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        return ApiResponse.success(
                plmPublicationService.releaseDocumentAsset(
                        businessType,
                        billId,
                        assetId,
                        request,
                        plmLaunchService.currentUserIdForWorkspace()
                )
        );
    }

    @GetMapping("/bills/{businessType}/{billId}/acl")
    public ApiResponse<java.util.List<PlmObjectAclResponse>> objectAcl(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.objectAcl(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/domain-acl")
    public ApiResponse<java.util.List<PlmDomainAclResponse>> domainAcl(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.domainAcl(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/role-matrix")
    public ApiResponse<java.util.List<PlmRoleAssignmentResponse>> roleMatrix(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.roleAssignments(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/permissions")
    public ApiResponse<PlmPermissionSummaryResponse> permissions(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.permissionSummary(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/external-integrations")
    public ApiResponse<java.util.List<PlmExternalIntegrationResponse>> externalIntegrations(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.externalIntegrations(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/external-sync-events")
    public ApiResponse<java.util.List<PlmExternalSyncEventEnvelopeResponse>> externalSyncEvents(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        return ApiResponse.success(plmLaunchService.externalSyncEvents(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/connector-jobs")
    public ApiResponse<java.util.List<PlmConnectorJobResponse>> connectorJobs(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmConnectorOrchestrationService.listBillJobs(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/connector-health-summary")
    public ApiResponse<PlmConnectorHealthSummaryResponse> connectorHealthSummary(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmConnectorOrchestrationService.summarizeBillHealth(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/connector-tasks")
    public ApiResponse<java.util.List<PlmConnectorJobResponse>> connectorTasks(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmConnectorOrchestrationService.listBillJobs(businessType, billId));
    }

    @GetMapping("/connector-jobs/{jobId}/dispatch-logs")
    public ApiResponse<java.util.List<PlmConnectorDispatchLogResponse>> dispatchLogs(@PathVariable String jobId) {
        return ApiResponse.success(loadDispatchLogs(jobId));
    }

    @PostMapping("/connector-jobs/{jobId}/retry")
    public ApiResponse<PlmConnectorJobResponse> retryConnectorJob(@PathVariable String jobId) {
        return ApiResponse.success(retryConnectorJobInternal(jobId));
    }

    @PostMapping("/connector-jobs/{jobId}/dispatch")
    public ApiResponse<PlmConnectorJobResponse> dispatchConnectorJob(@PathVariable String jobId) {
        return ApiResponse.success(dispatchConnectorJobInternal(jobId));
    }

    @PostMapping("/connector-jobs/{jobId}/acks")
    public ApiResponse<PlmConnectorExternalAckResponse> writeConnectorAck(
            @PathVariable String jobId,
            @RequestBody(required = false) PlmConnectorExternalAckRequest request
    ) {
        var locator = plmConnectorOrchestrationService.requireJobLocator(jobId);
        plmLaunchService.requireManageableBillForWorkspace(locator.businessType(), locator.billId());
        return ApiResponse.success(plmConnectorAckService.writeAck(jobId, request));
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

    @GetMapping("/bills/{businessType}/{billId}/implementation-templates")
    public ApiResponse<java.util.List<PlmImplementationTemplateResponse>> implementationTemplates(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        String sceneCode = plmLaunchService.requireReadableBillForWorkspace(businessType, billId).sceneCode();
        return ApiResponse.success(plmImplementationTemplateService.listTemplates(businessType, sceneCode));
    }

    @GetMapping("/bills/{businessType}/{billId}/implementation-dependencies")
    public ApiResponse<java.util.List<PlmImplementationDependencyResponse>> implementationDependencies(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmImplementationTemplateService.listDependencies(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/implementation-evidence")
    public ApiResponse<java.util.List<PlmImplementationEvidenceResponse>> implementationEvidence(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmImplementationEvidenceService.listEvidence(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/acceptance-checklist")
    public ApiResponse<java.util.List<PlmAcceptanceChecklistResponse>> acceptanceChecklist(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmImplementationEvidenceService.listAcceptanceChecklist(businessType, billId));
    }

    @GetMapping("/bills/{businessType}/{billId}/implementation-workspace")
    public ApiResponse<PlmImplementationWorkspaceResponse> implementationWorkspace(
            @PathVariable String businessType,
            @PathVariable String billId
    ) {
        plmLaunchService.requireReadableBillForWorkspace(businessType, billId);
        return ApiResponse.success(plmImplementationEvidenceService.workspace(businessType, billId));
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

    @PostMapping("/bills/{businessType}/{billId}/implementation-tasks/{taskId}/evidence")
    public ApiResponse<PlmImplementationEvidenceResponse> addImplementationEvidence(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String taskId,
            @RequestBody PlmImplementationEvidenceUpsertRequest request
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        return ApiResponse.success(
                plmImplementationEvidenceService.addEvidence(
                        businessType,
                        billId,
                        taskId,
                        request,
                        plmLaunchService.currentUserIdForWorkspace()
                )
        );
    }

    @PutMapping("/bills/{businessType}/{billId}/implementation-evidence/{evidenceId}")
    public ApiResponse<PlmImplementationEvidenceResponse> updateImplementationEvidence(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String evidenceId,
            @RequestBody PlmImplementationEvidenceUpdateRequest request
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        return ApiResponse.success(
                plmImplementationEvidenceService.updateEvidence(
                        businessType,
                        billId,
                        evidenceId,
                        request
                )
        );
    }

    @DeleteMapping("/bills/{businessType}/{billId}/implementation-evidence/{evidenceId}")
    public ApiResponse<Boolean> deleteImplementationEvidence(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String evidenceId
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        plmImplementationEvidenceService.deleteEvidence(businessType, billId, evidenceId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @PutMapping("/bills/{businessType}/{billId}/acceptance-checklist/{checklistId}")
    public ApiResponse<PlmAcceptanceChecklistResponse> updateAcceptanceChecklist(
            @PathVariable String businessType,
            @PathVariable String billId,
            @PathVariable String checklistId,
            @RequestBody PlmAcceptanceChecklistUpdateRequest request
    ) {
        plmLaunchService.requireManageableBillForWorkspace(businessType, billId);
        return ApiResponse.success(
                plmImplementationEvidenceService.updateAcceptanceChecklist(
                        businessType,
                        billId,
                        checklistId,
                        request,
                        plmLaunchService.currentUserIdForWorkspace()
                )
        );
    }

    private java.util.List<PlmConnectorDispatchLogResponse> loadDispatchLogs(String jobId) {
        var locator = plmConnectorOrchestrationService.requireJobLocator(jobId);
        plmLaunchService.requireReadableBillForWorkspace(locator.businessType(), locator.billId());
        return plmConnectorOrchestrationService.listDispatchLogs(jobId);
    }

    private PlmConnectorJobResponse retryConnectorJobInternal(String jobId) {
        var locator = plmConnectorOrchestrationService.requireJobLocator(jobId);
        plmLaunchService.requireManageableBillForWorkspace(locator.businessType(), locator.billId());
        return plmConnectorOrchestrationService.retryJob(jobId, plmLaunchService.currentUserIdForWorkspace());
    }

    private PlmConnectorJobResponse dispatchConnectorJobInternal(String jobId) {
        var locator = plmConnectorOrchestrationService.requireJobLocator(jobId);
        plmLaunchService.requireManageableBillForWorkspace(locator.businessType(), locator.billId());
        return plmConnectorOrchestrationService.dispatchJob(jobId, plmLaunchService.currentUserIdForWorkspace());
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
