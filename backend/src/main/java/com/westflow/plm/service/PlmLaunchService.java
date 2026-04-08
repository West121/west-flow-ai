package com.westflow.plm.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.plm.api.CreatePLMEcoBillRequest;
import com.westflow.plm.api.CreatePLMEcrBillRequest;
import com.westflow.plm.api.CreatePLMMaterialChangeBillRequest;
import com.westflow.plm.api.ClosePlmBillRequest;
import com.westflow.plm.api.PlmBillActionResponse;
import com.westflow.plm.api.PlmAffectedItemRequest;
import com.westflow.plm.api.PlmAffectedItemResponse;
import com.westflow.plm.api.PlmAcceptanceChecklistResponse;
import com.westflow.plm.api.PlmAcceptanceChecklistUpdateRequest;
import com.westflow.plm.api.PlmDashboardAnalyticsResponse;
import com.westflow.plm.api.PlmDashboardCockpitResponse;
import com.westflow.plm.api.PlmDashboardRecentBillResponse;
import com.westflow.plm.api.PlmDashboardSummaryResponse;
import com.westflow.plm.api.PlmBomNodeResponse;
import com.westflow.plm.api.PlmConfigurationBaselineResponse;
import com.westflow.plm.api.PlmConnectorExternalAckRequest;
import com.westflow.plm.api.PlmConnectorExternalAckResponse;
import com.westflow.plm.api.PlmConnectorJobResponse;
import com.westflow.plm.api.PlmDomainAclResponse;
import com.westflow.plm.api.PlmDocumentAssetResponse;
import com.westflow.plm.api.PlmEcoBillDetailResponse;
import com.westflow.plm.api.PlmEcoBillListItemResponse;
import com.westflow.plm.api.PlmEcrBillDetailResponse;
import com.westflow.plm.api.PlmEcrBillListItemResponse;
import com.westflow.plm.api.PlmExternalIntegrationResponse;
import com.westflow.plm.api.PlmExternalSyncEventEnvelopeResponse;
import com.westflow.plm.api.PlmImplementationDependencyResponse;
import com.westflow.plm.api.PlmImplementationEvidenceResponse;
import com.westflow.plm.api.PlmImplementationEvidenceUpsertRequest;
import com.westflow.plm.api.PlmImplementationTaskActionRequest;
import com.westflow.plm.api.PlmImplementationTaskResponse;
import com.westflow.plm.api.PlmImplementationTemplateResponse;
import com.westflow.plm.api.PlmImplementationTaskUpsertRequest;
import com.westflow.plm.api.PlmLaunchResponse;
import com.westflow.plm.api.PlmObjectAclResponse;
import com.westflow.plm.api.PlmObjectLinkResponse;
import com.westflow.plm.api.PlmPermissionSummaryResponse;
import com.westflow.plm.api.PlmRevisionDiffResponse;
import com.westflow.plm.api.PlmMaterialChangeBillDetailResponse;
import com.westflow.plm.api.PlmMaterialChangeBillListItemResponse;
import com.westflow.plm.api.PlmRoleAssignmentResponse;
import com.westflow.plm.api.StartPlmImplementationRequest;
import com.westflow.plm.api.SubmitPlmValidationRequest;
import com.westflow.plm.mapper.PlmAffectedItemMapper;
import com.westflow.plm.mapper.PlmEcoBillMapper;
import com.westflow.plm.mapper.PlmEcrBillMapper;
import com.westflow.plm.mapper.PlmMaterialChangeBillMapper;
import com.westflow.plm.model.PlmAffectedItemRecord;
import com.westflow.plm.model.PlmBillObjectLinkRecord;
import com.westflow.plm.model.PlmBillLifecycleRecord;
import com.westflow.plm.model.PlmDashboardStatsRecord;
import com.westflow.plm.model.PlmEcoBillRecord;
import com.westflow.plm.model.PlmEcrBillRecord;
import com.westflow.plm.model.PlmMaterialChangeBillRecord;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import com.westflow.processbinding.service.BusinessProcessBindingService;
import com.westflow.processruntime.action.FlowableRuntimeStartService;
import com.westflow.processruntime.api.request.ApprovalSheetListView;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.api.request.TerminateProcessInstanceRequest;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.response.StartProcessResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLM 单据生命周期、发起与台账服务。
 */
@Service
@RequiredArgsConstructor
public class PlmLaunchService {

    private static final List<String> PLM_APPROVAL_BUSINESS_TYPES = List.of(
            "PLM_ECR",
            "PLM_ECO",
            "PLM_MATERIAL"
    );

    private static final int DASHBOARD_RECENT_LIMIT = 6;

    private final BusinessProcessBindingService businessProcessBindingService;
    private final BusinessProcessLinkMapper businessProcessLinkMapper;
    private final PlmAffectedItemMapper plmAffectedItemMapper;
    private final PlmEcrBillMapper plmEcrBillMapper;
    private final PlmEcoBillMapper plmEcoBillMapper;
    private final PlmMaterialChangeBillMapper plmMaterialChangeBillMapper;
    private final PlmObjectService plmObjectService;
    private final PlmRevisionDiffService plmRevisionDiffService;
    private final PlmImplementationTaskService plmImplementationTaskService;
    private final PlmDashboardService plmDashboardService;
    private final PlmEnterpriseDepthService plmEnterpriseDepthService;
    private final PlmConnectorJobService plmConnectorJobService;
    private final FlowableRuntimeStartService flowableRuntimeStartService;
    private final FlowableProcessRuntimeService flowableProcessRuntimeService;

    @Transactional
    public PlmLaunchResponse createEcrBill(CreatePLMEcrBillRequest request) {
        PlmBillLifecycleRecord bill = createEcrDraftInternal(request);
        return submitEcrBill(bill.billId());
    }

    @Transactional
    public PlmLaunchResponse createEcoBill(CreatePLMEcoBillRequest request) {
        PlmBillLifecycleRecord bill = createEcoDraftInternal(request);
        return submitEcoBill(bill.billId());
    }

    @Transactional
    public PlmLaunchResponse createMaterialChangeBill(CreatePLMMaterialChangeBillRequest request) {
        PlmBillLifecycleRecord bill = createMaterialDraftInternal(request);
        return submitMaterialChangeBill(bill.billId());
    }

    @Transactional
    public PlmEcrBillDetailResponse saveEcrDraft(CreatePLMEcrBillRequest request) {
        PlmBillLifecycleRecord bill = createEcrDraftInternal(request);
        return ecrDetail(bill.billId());
    }

    @Transactional
    public PlmEcoBillDetailResponse saveEcoDraft(CreatePLMEcoBillRequest request) {
        PlmBillLifecycleRecord bill = createEcoDraftInternal(request);
        return ecoDetail(bill.billId());
    }

    @Transactional
    public PlmMaterialChangeBillDetailResponse saveMaterialChangeDraft(CreatePLMMaterialChangeBillRequest request) {
        PlmBillLifecycleRecord bill = createMaterialDraftInternal(request);
        return materialChangeDetail(bill.billId());
    }

    public PlmDashboardAnalyticsResponse dashboardAnalytics() {
        return plmDashboardService.dashboardAnalytics(currentUserId());
    }

    public PlmDashboardCockpitResponse dashboardCockpit() {
        return plmEnterpriseDepthService.dashboardCockpit(currentUserId());
    }

    @Transactional
    public PlmEcrBillDetailResponse updateEcrDraft(String billId, CreatePLMEcrBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        ensureDraftEditable(lifecycle);
        plmEcrBillMapper.updateDraft(toEcrRecord(lifecycle.billId(), lifecycle.billNo(), lifecycle.processInstanceId(), lifecycle.status(), lifecycle.creatorUserId(), request));
        replaceAffectedItems("PLM_ECR", lifecycle.billId(), request.affectedItems());
        return ecrDetail(billId);
    }

    @Transactional
    public PlmEcoBillDetailResponse updateEcoDraft(String billId, CreatePLMEcoBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        ensureDraftEditable(lifecycle);
        plmEcoBillMapper.updateDraft(toEcoRecord(lifecycle.billId(), lifecycle.billNo(), lifecycle.processInstanceId(), lifecycle.status(), lifecycle.creatorUserId(), request));
        replaceAffectedItems("PLM_ECO", lifecycle.billId(), request.affectedItems());
        return ecoDetail(billId);
    }

    @Transactional
    public PlmMaterialChangeBillDetailResponse updateMaterialChangeDraft(String billId, CreatePLMMaterialChangeBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        ensureDraftEditable(lifecycle);
        plmMaterialChangeBillMapper.updateDraft(toMaterialRecord(lifecycle.billId(), lifecycle.billNo(), lifecycle.processInstanceId(), lifecycle.status(), lifecycle.creatorUserId(), request));
        replaceAffectedItems("PLM_MATERIAL", lifecycle.billId(), request.affectedItems());
        return materialChangeDetail(billId);
    }

    @Transactional
    public PlmLaunchResponse submitEcrBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        ensureSubmittable(lifecycle);
        PlmEcrBillDetailResponse detail = ecrDetail(billId);
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_ECR",
                lifecycle.sceneCode(),
                billId,
                buildFormData(
                        "changeTitle", detail.changeTitle(),
                        "changeReason", detail.changeReason(),
                        "affectedProductCode", detail.affectedProductCode(),
                        "priorityLevel", detail.priorityLevel(),
                        "changeCategory", detail.changeCategory(),
                        "targetVersion", detail.targetVersion(),
                        "affectedObjectsText", detail.affectedObjectsText(),
                        "impactScope", detail.impactScope(),
                        "riskLevel", detail.riskLevel()
                )
        );
        plmEcrBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_ECR", billId, startResponse, lifecycle.creatorUserId());
        plmEnterpriseDepthService.appendLifecycleSyncEvents("PLM_ECR", billId, "BILL_SUBMITTED", "PENDING", "ECR 变更申请已提交审批，等待外部系统消费", currentUserId());
        plmConnectorJobService.enqueueLifecycleJobs("PLM_ECR", billId, "BILL_SUBMITTED", currentUserId(), "ECR 变更申请已提交审批");
        return toLaunchResponse(billId, lifecycle.billNo(), startResponse);
    }

    @Transactional
    public PlmLaunchResponse submitEcoBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        ensureSubmittable(lifecycle);
        PlmEcoBillDetailResponse detail = ecoDetail(billId);
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_ECO",
                lifecycle.sceneCode(),
                billId,
                buildFormData(
                        "executionTitle", detail.executionTitle(),
                        "executionPlan", detail.executionPlan(),
                        "effectiveDate", detail.effectiveDate() == null ? null : detail.effectiveDate().toString(),
                        "changeReason", detail.changeReason(),
                        "implementationOwner", detail.implementationOwner(),
                        "targetVersion", detail.targetVersion(),
                        "rolloutScope", detail.rolloutScope(),
                        "validationPlan", detail.validationPlan(),
                        "rollbackPlan", detail.rollbackPlan()
                )
        );
        plmEcoBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_ECO", billId, startResponse, lifecycle.creatorUserId());
        plmEnterpriseDepthService.appendLifecycleSyncEvents("PLM_ECO", billId, "BILL_SUBMITTED", "PENDING", "ECO 变更执行单已提交审批，等待外部系统消费", currentUserId());
        plmConnectorJobService.enqueueLifecycleJobs("PLM_ECO", billId, "BILL_SUBMITTED", currentUserId(), "ECO 变更执行单已提交审批");
        return toLaunchResponse(billId, lifecycle.billNo(), startResponse);
    }

    @Transactional
    public PlmLaunchResponse submitMaterialChangeBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        ensureSubmittable(lifecycle);
        PlmMaterialChangeBillDetailResponse detail = materialChangeDetail(billId);
        StartProcessResponse startResponse = startBusinessProcess(
                "PLM_MATERIAL",
                lifecycle.sceneCode(),
                billId,
                buildFormData(
                        "materialCode", detail.materialCode(),
                        "materialName", detail.materialName(),
                        "changeReason", detail.changeReason(),
                        "changeType", detail.changeType(),
                        "specificationChange", detail.specificationChange(),
                        "oldValue", detail.oldValue(),
                        "newValue", detail.newValue(),
                        "uom", detail.uom(),
                        "affectedSystemsText", detail.affectedSystemsText()
                )
        );
        plmMaterialChangeBillMapper.updateProcessLink(billId, startResponse.instanceId(), startResponse.status());
        insertLink("PLM_MATERIAL", billId, startResponse, lifecycle.creatorUserId());
        plmEnterpriseDepthService.appendLifecycleSyncEvents("PLM_MATERIAL", billId, "BILL_SUBMITTED", "PENDING", "物料主数据变更单已提交审批，等待外部系统消费", currentUserId());
        plmConnectorJobService.enqueueLifecycleJobs("PLM_MATERIAL", billId, "BILL_SUBMITTED", currentUserId(), "物料主数据变更单已提交审批");
        return toLaunchResponse(billId, lifecycle.billNo(), startResponse);
    }

    @Transactional
    public PlmBillActionResponse cancelEcrBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        return cancelBill("ECR 变更申请", lifecycle, plmEcrBillMapper::updateStatus);
    }

    @Transactional
    public PlmBillActionResponse cancelEcoBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        return cancelBill("ECO 变更执行", lifecycle, plmEcoBillMapper::updateStatus);
    }

    @Transactional
    public PlmBillActionResponse cancelMaterialChangeBill(String billId) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        return cancelBill("物料主数据变更申请", lifecycle, plmMaterialChangeBillMapper::updateStatus);
    }

    @Transactional
    public PlmBillActionResponse startEcrImplementation(String billId, StartPlmImplementationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        return startImplementation("ECR 变更申请", lifecycle, request, plmEcrBillMapper::startImplementation);
    }

    @Transactional
    public PlmBillActionResponse startEcoImplementation(String billId, StartPlmImplementationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        return startImplementation("ECO 变更执行", lifecycle, request, plmEcoBillMapper::startImplementation);
    }

    @Transactional
    public PlmBillActionResponse startMaterialChangeImplementation(String billId, StartPlmImplementationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        return startImplementation("物料主数据变更申请", lifecycle, request, plmMaterialChangeBillMapper::startImplementation);
    }

    @Transactional
    public PlmBillActionResponse submitEcrValidation(String billId, SubmitPlmValidationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        return submitValidation("ECR 变更申请", lifecycle, request, plmEcrBillMapper::submitValidation);
    }

    @Transactional
    public PlmBillActionResponse submitEcoValidation(String billId, SubmitPlmValidationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        return submitValidation("ECO 变更执行", lifecycle, request, plmEcoBillMapper::submitValidation);
    }

    @Transactional
    public PlmBillActionResponse submitMaterialChangeValidation(String billId, SubmitPlmValidationRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        return submitValidation("物料主数据变更申请", lifecycle, request, plmMaterialChangeBillMapper::submitValidation);
    }

    @Transactional
    public PlmBillActionResponse closeEcrBill(String billId, ClosePlmBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
        return closeBill("ECR 变更申请", lifecycle, request, plmEcrBillMapper::closeBill);
    }

    @Transactional
    public PlmBillActionResponse closeEcoBill(String billId, ClosePlmBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
        return closeBill("ECO 变更执行", lifecycle, request, plmEcoBillMapper::closeBill);
    }

    @Transactional
    public PlmBillActionResponse closeMaterialChangeBill(String billId, ClosePlmBillRequest request) {
        PlmBillLifecycleRecord lifecycle = requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
        return closeBill("物料主数据变更申请", lifecycle, request, plmMaterialChangeBillMapper::closeBill);
    }

    public List<PlmObjectLinkResponse> objectLinks(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmObjectService.listBillObjectLinks(businessType, billId);
    }

    public List<PlmBomNodeResponse> bomNodes(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillBomNodes(businessType, billId);
    }

    public List<PlmDocumentAssetResponse> documentAssets(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillDocumentAssets(businessType, billId);
    }

    public List<PlmConfigurationBaselineResponse> baselines(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillBaselines(businessType, billId);
    }

    public PlmBillLifecycleRecord requireReadableBillForWorkspace(String businessType, String billId) {
        return requireReadableBill(businessType, billId);
    }

    public List<PlmObjectAclResponse> objectAcl(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillObjectAcl(businessType, billId);
    }

    public List<PlmDomainAclResponse> domainAcl(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillDomainAcl(businessType, billId);
    }

    public List<PlmRoleAssignmentResponse> roleAssignments(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillRoleAssignments(businessType, billId);
    }

    public PlmPermissionSummaryResponse permissionSummary(String businessType, String billId) {
        PlmBillLifecycleRecord lifecycle = requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.permissionSummary(businessType, billId, lifecycle.creatorUserId(), currentUserId());
    }

    public PlmBillLifecycleRecord requireManageableBillForWorkspace(String businessType, String billId) {
        return requireManageableBill(businessType, billId);
    }

    public List<PlmExternalIntegrationResponse> externalIntegrations(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillExternalIntegrations(businessType, billId);
    }

    public List<PlmExternalSyncEventEnvelopeResponse> externalSyncEvents(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmEnterpriseDepthService.listBillExternalSyncEvents(businessType, billId);
    }

    public List<PlmConnectorJobResponse> connectorJobs(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmConnectorJobService.listBillJobs(businessType, billId);
    }

    public PlmConnectorJobResponse retryConnectorJob(String jobId) {
        PlmConnectorJobService.JobLocator locator = plmConnectorJobService.requireJobLocator(jobId);
        requireManageableBill(locator.businessType(), locator.billId());
        return plmConnectorJobService.retryJob(jobId, currentUserId());
    }

    public PlmConnectorExternalAckResponse writeConnectorAck(String jobId, PlmConnectorExternalAckRequest request) {
        PlmConnectorJobService.JobLocator locator = plmConnectorJobService.requireJobLocator(jobId);
        requireManageableBill(locator.businessType(), locator.billId());
        return plmConnectorJobService.writeAck(jobId, request);
    }

    public List<PlmRevisionDiffResponse> revisionDiffs(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmRevisionDiffService.listBillRevisionDiffs(businessType, billId);
    }

    public List<PlmImplementationTaskResponse> implementationTasks(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmImplementationTaskService.listBillTasks(businessType, billId);
    }

    public List<PlmImplementationTemplateResponse> implementationTemplates(String businessType, String billId) {
        PlmBillLifecycleRecord lifecycle = requireReadableBill(businessType, billId);
        return plmImplementationTaskService.listTemplates(businessType, lifecycle.sceneCode());
    }

    public List<PlmImplementationDependencyResponse> implementationDependencies(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmImplementationTaskService.listDependencies(businessType, billId);
    }

    public List<PlmImplementationEvidenceResponse> implementationEvidence(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmImplementationTaskService.listEvidence(businessType, billId);
    }

    public List<PlmAcceptanceChecklistResponse> acceptanceChecklist(String businessType, String billId) {
        requireReadableBill(businessType, billId);
        return plmImplementationTaskService.listAcceptanceChecklist(businessType, billId);
    }

    public PlmImplementationTaskResponse upsertImplementationTask(
            String businessType,
            String billId,
            PlmImplementationTaskUpsertRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.upsertTask(businessType, billId, request, currentUserId());
    }

    public PlmImplementationTaskResponse startImplementationTask(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationTaskActionRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.startTask(businessType, billId, taskId, request);
    }

    public PlmImplementationTaskResponse completeImplementationTask(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationTaskActionRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.completeTask(businessType, billId, taskId, request);
    }

    public PlmImplementationTaskResponse blockImplementationTask(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationTaskActionRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.blockTask(businessType, billId, taskId, request);
    }

    public PlmImplementationTaskResponse cancelImplementationTask(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationTaskActionRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.cancelTask(businessType, billId, taskId, request);
    }

    public PlmImplementationEvidenceResponse addImplementationEvidence(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationEvidenceUpsertRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.addEvidence(businessType, billId, taskId, request, currentUserId());
    }

    public PlmAcceptanceChecklistResponse updateAcceptanceChecklist(
            String businessType,
            String billId,
            String checklistId,
            PlmAcceptanceChecklistUpdateRequest request
    ) {
        requireManageableBill(businessType, billId);
        return plmImplementationTaskService.updateAcceptanceChecklist(businessType, billId, checklistId, request, currentUserId());
    }

    public PlmDashboardSummaryResponse dashboardSummary() {
        return plmDashboardService.dashboardSummary(currentUserId());
    }

    public PlmEcrBillDetailResponse ecrDetail(String billId) {
        requireReadableBill("PLM_ECR", billId);
        PlmEcrBillDetailResponse detail = plmEcrBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("ECR 变更申请不存在", billId);
        }
        return withDeepData(detail, "PLM_ECR", billId);
    }

    public PageResponse<PlmEcrBillListItemResponse> ecrPage(PageRequest request) {
        PlmPageFilters filters = resolvePageFilters(request.filters());
        return toPageResponse(
                request,
                plmEcrBillMapper.countPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo()
                ),
                plmEcrBillMapper.selectPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo(),
                        request.pageSize(),
                        offsetOf(request)
                )
        );
    }

    public PlmEcoBillDetailResponse ecoDetail(String billId) {
        requireReadableBill("PLM_ECO", billId);
        PlmEcoBillDetailResponse detail = plmEcoBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("ECO 变更执行不存在", billId);
        }
        return withDeepData(detail, "PLM_ECO", billId);
    }

    public PageResponse<PlmEcoBillListItemResponse> ecoPage(PageRequest request) {
        PlmPageFilters filters = resolvePageFilters(request.filters());
        return toPageResponse(
                request,
                plmEcoBillMapper.countPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo()
                ),
                plmEcoBillMapper.selectPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo(),
                        request.pageSize(),
                        offsetOf(request)
                )
        );
    }

    public PlmMaterialChangeBillDetailResponse materialChangeDetail(String billId) {
        requireReadableBill("PLM_MATERIAL", billId);
        PlmMaterialChangeBillDetailResponse detail = plmMaterialChangeBillMapper.selectDetail(billId);
        if (detail == null) {
            throw resourceNotFound("物料主数据变更申请不存在", billId);
        }
        return withDeepData(detail, "PLM_MATERIAL", billId);
    }

    public PageResponse<PlmMaterialChangeBillListItemResponse> materialChangePage(PageRequest request) {
        PlmPageFilters filters = resolvePageFilters(request.filters());
        return toPageResponse(
                request,
                plmMaterialChangeBillMapper.countPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo()
                ),
                plmMaterialChangeBillMapper.selectPage(
                        normalizeKeyword(request.keyword()),
                        filters.sceneCode(),
                        filters.creatorUserId(),
                        filters.statuses(),
                        filters.createdAtFrom(),
                        filters.createdAtTo(),
                        filters.updatedAtFrom(),
                        filters.updatedAtTo(),
                        request.pageSize(),
                        offsetOf(request)
                )
        );
    }

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

    private PlmBillLifecycleRecord createEcrDraftInternal(CreatePLMEcrBillRequest request) {
        String billId = buildId("ecr");
        String billNo = buildBillNo("ECR");
        String userId = currentUserId();
        plmEcrBillMapper.insert(toEcrRecord(billId, billNo, null, "DRAFT", userId, request));
        replaceAffectedItems("PLM_ECR", billId, request.affectedItems());
        return requireOwnedBill(plmEcrBillMapper.selectLifecycle(billId), "ECR 变更申请不存在", billId);
    }

    private PlmBillLifecycleRecord createEcoDraftInternal(CreatePLMEcoBillRequest request) {
        String billId = buildId("eco");
        String billNo = buildBillNo("ECO");
        String userId = currentUserId();
        plmEcoBillMapper.insert(toEcoRecord(billId, billNo, null, "DRAFT", userId, request));
        replaceAffectedItems("PLM_ECO", billId, request.affectedItems());
        return requireOwnedBill(plmEcoBillMapper.selectLifecycle(billId), "ECO 变更执行不存在", billId);
    }

    private PlmBillLifecycleRecord createMaterialDraftInternal(CreatePLMMaterialChangeBillRequest request) {
        String billId = buildId("material");
        String billNo = buildBillNo("MAT");
        String userId = currentUserId();
        plmMaterialChangeBillMapper.insert(toMaterialRecord(billId, billNo, null, "DRAFT", userId, request));
        replaceAffectedItems("PLM_MATERIAL", billId, request.affectedItems());
        return requireOwnedBill(plmMaterialChangeBillMapper.selectLifecycle(billId), "物料主数据变更申请不存在", billId);
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

    private PlmBillActionResponse cancelBill(String label, PlmBillLifecycleRecord lifecycle, StatusUpdater statusUpdater) {
        ensureCancellable(lifecycle, label);
        if ("RUNNING".equalsIgnoreCase(lifecycle.status()) && lifecycle.processInstanceId() != null && !lifecycle.processInstanceId().isBlank()) {
            flowableProcessRuntimeService.terminate(
                    lifecycle.processInstanceId(),
                    new TerminateProcessInstanceRequest("ROOT", null, label + "已取消")
            );
        }
        statusUpdater.update(lifecycle.billId(), "CANCELLED");
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                resolveBusinessType(label),
                lifecycle.billId(),
                "BILL_CANCELLED",
                "FAILED",
                label + "已取消，需要外部系统回滚或忽略待处理项",
                currentUserId()
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                resolveBusinessType(label),
                lifecycle.billId(),
                "BILL_CANCELLED",
                currentUserId(),
                label + "已取消，等待外部系统回滚确认"
        );
        return new PlmBillActionResponse(
                lifecycle.billId(),
                lifecycle.billNo(),
                "CANCELLED",
                lifecycle.processInstanceId(),
                label + "已取消"
        );
    }

    private PlmBillActionResponse startImplementation(
            String label,
            PlmBillLifecycleRecord lifecycle,
            StartPlmImplementationRequest request,
            ImplementationStatusUpdater statusUpdater
    ) {
        ensureImplementationStartable(lifecycle, label);
        StartPlmImplementationRequest safeRequest = request == null ? new StartPlmImplementationRequest(null, null) : request;
        String implementationOwner = blankToNull(safeRequest.implementationOwner());
        if (implementationOwner == null) {
            implementationOwner = currentUserId();
        }
        String implementationSummary = blankToNull(safeRequest.implementationSummary());
        plmImplementationTaskService.seedDefaultTaskIfMissing(
                resolveBusinessType(label),
                lifecycle.billId(),
                lifecycle.sceneCode(),
                implementationSummary,
                implementationOwner
        );
        statusUpdater.update(lifecycle.billId(), implementationOwner, implementationSummary, "IMPLEMENTING");
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                resolveBusinessType(label),
                lifecycle.billId(),
                "IMPLEMENTATION_STARTED",
                "PENDING",
                label + "已进入实施阶段，等待下游系统更新",
                currentUserId()
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                resolveBusinessType(label),
                lifecycle.billId(),
                "IMPLEMENTATION_STARTED",
                currentUserId(),
                label + "已进入实施阶段"
        );
        return new PlmBillActionResponse(
                lifecycle.billId(),
                lifecycle.billNo(),
                "IMPLEMENTING",
                lifecycle.processInstanceId(),
                label + "已开始实施"
        );
    }

    private PlmBillActionResponse submitValidation(
            String label,
            PlmBillLifecycleRecord lifecycle,
            SubmitPlmValidationRequest request,
            ValidationStatusUpdater statusUpdater
    ) {
        ensureValidating(lifecycle, label);
        SubmitPlmValidationRequest safeRequest = request == null ? new SubmitPlmValidationRequest(null, null) : request;
        String validationOwner = blankToNull(safeRequest.validationOwner());
        if (validationOwner == null) {
            validationOwner = currentUserId();
        }
        String validationSummary = blankToNull(safeRequest.validationSummary());
        statusUpdater.update(lifecycle.billId(), validationOwner, validationSummary, "VALIDATING");
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                resolveBusinessType(label),
                lifecycle.billId(),
                "VALIDATION_SUBMITTED",
                "PENDING",
                label + "已提交验证，等待外部系统确认验证态",
                currentUserId()
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                resolveBusinessType(label),
                lifecycle.billId(),
                "VALIDATION_SUBMITTED",
                currentUserId(),
                label + "已提交验证"
        );
        return new PlmBillActionResponse(
                lifecycle.billId(),
                lifecycle.billNo(),
                "VALIDATING",
                lifecycle.processInstanceId(),
                label + "已提交验证"
        );
    }

    private PlmBillActionResponse closeBill(
            String label,
            PlmBillLifecycleRecord lifecycle,
            ClosePlmBillRequest request,
            ClosureStatusUpdater statusUpdater
    ) {
        ensureClosable(lifecycle, label);
        ClosePlmBillRequest safeRequest = request == null ? new ClosePlmBillRequest(null, null) : request;
        String closedBy = blankToNull(safeRequest.closedBy());
        if (closedBy == null) {
            closedBy = currentUserId();
        }
        String closeComment = blankToNull(safeRequest.closeComment());
        statusUpdater.update(lifecycle.billId(), closedBy, closeComment, "CLOSED");
        plmEnterpriseDepthService.appendLifecycleSyncEvents(
                resolveBusinessType(label),
                lifecycle.billId(),
                "BILL_CLOSED",
                "SUCCESS",
                label + "已关闭，可对外发布最终版本",
                currentUserId()
        );
        plmConnectorJobService.enqueueLifecycleJobs(
                resolveBusinessType(label),
                lifecycle.billId(),
                "BILL_CLOSED",
                currentUserId(),
                label + "已关闭"
        );
        return new PlmBillActionResponse(
                lifecycle.billId(),
                lifecycle.billNo(),
                "CLOSED",
                lifecycle.processInstanceId(),
                label + "已关闭"
        );
    }

    private void ensureDraftEditable(PlmBillLifecycleRecord lifecycle) {
        if (!"DRAFT".equalsIgnoreCase(lifecycle.status())) {
            throw illegalLifecycle("仅草稿状态支持编辑", lifecycle);
        }
    }

    private void ensureSubmittable(PlmBillLifecycleRecord lifecycle) {
        if (!"DRAFT".equalsIgnoreCase(lifecycle.status())) {
            throw illegalLifecycle("仅草稿状态支持提交", lifecycle);
        }
    }

    private void ensureCancellable(PlmBillLifecycleRecord lifecycle, String label) {
        if (!List.of("DRAFT", "RUNNING", "IMPLEMENTING", "VALIDATING").contains(normalizeStatus(lifecycle.status()))) {
            throw illegalLifecycle(label + "当前状态不支持取消", lifecycle);
        }
    }

    private void ensureImplementationStartable(PlmBillLifecycleRecord lifecycle, String label) {
        if (!List.of("RUNNING", "COMPLETED", "VALIDATING", "IMPLEMENTING").contains(normalizeStatus(lifecycle.status()))) {
            throw illegalLifecycle(label + "当前状态不支持开始实施", lifecycle);
        }
    }

    private void ensureValidating(PlmBillLifecycleRecord lifecycle, String label) {
        plmImplementationTaskService.ensureReadyForValidation(resolveBusinessType(label), lifecycle.billId());
        if (!"IMPLEMENTING".equalsIgnoreCase(lifecycle.status())) {
            throw illegalLifecycle(label + "当前状态不支持提交验证", lifecycle);
        }
    }

    private void ensureClosable(PlmBillLifecycleRecord lifecycle, String label) {
        plmImplementationTaskService.ensureReadyForClose(resolveBusinessType(label), lifecycle.billId());
        if (!"VALIDATING".equalsIgnoreCase(lifecycle.status())) {
            throw illegalLifecycle(label + "当前状态不支持关闭", lifecycle);
        }
    }

    private PlmBillLifecycleRecord selectLifecycle(String businessType, String billId) {
        return switch (businessType) {
            case "PLM_ECR" -> plmEcrBillMapper.selectLifecycle(billId);
            case "PLM_ECO" -> plmEcoBillMapper.selectLifecycle(billId);
            case "PLM_MATERIAL" -> plmMaterialChangeBillMapper.selectLifecycle(billId);
            default -> null;
        };
    }

    private String lifecycleNotFoundMessage(String businessType) {
        return switch (businessType) {
            case "PLM_ECR" -> "ECR 变更申请不存在";
            case "PLM_ECO" -> "ECO 变更执行不存在";
            case "PLM_MATERIAL" -> "物料主数据变更申请不存在";
            default -> "PLM 单据不存在";
        };
    }

    private PlmBillLifecycleRecord requireOwnedBill(PlmBillLifecycleRecord lifecycle, String notFoundMessage, String billId) {
        if (lifecycle == null) {
            throw resourceNotFound(notFoundMessage, billId);
        }
        if (!Objects.equals(lifecycle.creatorUserId(), currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅创建人可操作该 PLM 单据",
                    Map.of("billId", billId)
            );
        }
        return lifecycle;
    }

    private PlmBillLifecycleRecord requireReadableBill(String businessType, String billId) {
        PlmBillLifecycleRecord lifecycle = selectLifecycle(businessType, billId);
        if (lifecycle == null) {
            throw resourceNotFound(lifecycleNotFoundMessage(businessType), billId);
        }
        plmEnterpriseDepthService.assertBillReadable(
                businessType,
                billId,
                lifecycle.creatorUserId(),
                currentUserId()
        );
        return lifecycle;
    }

    private PlmBillLifecycleRecord requireManageableBill(String businessType, String billId) {
        PlmBillLifecycleRecord lifecycle = selectLifecycle(businessType, billId);
        if (lifecycle == null) {
            throw resourceNotFound(lifecycleNotFoundMessage(businessType), billId);
        }
        plmEnterpriseDepthService.assertBillManageable(
                businessType,
                billId,
                lifecycle.creatorUserId(),
                currentUserId()
        );
        return lifecycle;
    }

    private PlmEcrBillRecord toEcrRecord(
            String billId,
            String billNo,
            String processInstanceId,
            String status,
            String creatorUserId,
            CreatePLMEcrBillRequest request
    ) {
        return new PlmEcrBillRecord(
                billId,
                billNo,
                normalizeSceneCode(request.sceneCode()),
                request.changeTitle().trim(),
                request.changeReason().trim(),
                blankToNull(request.affectedProductCode()),
                blankToNull(request.priorityLevel()),
                blankToNull(request.changeCategory()),
                blankToNull(request.targetVersion()),
                resolveAffectedObjectsText(request.affectedObjectsText(), request.affectedItems()),
                blankToNull(request.impactScope()),
                blankToNull(request.riskLevel()),
                processInstanceId,
                status,
                creatorUserId
        );
    }

    private PlmEcoBillRecord toEcoRecord(
            String billId,
            String billNo,
            String processInstanceId,
            String status,
            String creatorUserId,
            CreatePLMEcoBillRequest request
    ) {
        return new PlmEcoBillRecord(
                billId,
                billNo,
                normalizeSceneCode(request.sceneCode()),
                request.executionTitle().trim(),
                request.executionPlan().trim(),
                request.effectiveDate(),
                request.changeReason().trim(),
                blankToNull(request.implementationOwner()),
                blankToNull(request.targetVersion()),
                blankToNull(request.rolloutScope()),
                blankToNull(request.validationPlan()),
                blankToNull(request.rollbackPlan()),
                processInstanceId,
                status,
                creatorUserId
        );
    }

    private PlmMaterialChangeBillRecord toMaterialRecord(
            String billId,
            String billNo,
            String processInstanceId,
            String status,
            String creatorUserId,
            CreatePLMMaterialChangeBillRequest request
    ) {
        return new PlmMaterialChangeBillRecord(
                billId,
                billNo,
                normalizeSceneCode(request.sceneCode()),
                request.materialCode().trim(),
                request.materialName().trim(),
                request.changeReason().trim(),
                blankToNull(request.changeType()),
                blankToNull(request.specificationChange()),
                blankToNull(request.oldValue()),
                blankToNull(request.newValue()),
                blankToNull(request.uom()),
                resolveAffectedSystemsText(request.affectedSystemsText(), request.affectedItems()),
                processInstanceId,
                status,
                creatorUserId
        );
    }

    private PlmDashboardStatsRecord normalizeStats(PlmDashboardStatsRecord stats) {
        if (stats == null) {
            return new PlmDashboardStatsRecord(0, 0, 0, 0, 0, 0);
        }
        return stats;
    }

    private String normalizeSceneCode(String sceneCode) {
        return sceneCode == null || sceneCode.isBlank() ? "default" : sceneCode.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toUpperCase();
    }

    private String resolveBusinessType(String label) {
        String normalized = label == null ? "" : label.toUpperCase();
        if (normalized.contains("ECR")) {
            return "PLM_ECR";
        }
        if (normalized.contains("ECO")) {
            return "PLM_ECO";
        }
        return "PLM_MATERIAL";
    }

    private void replaceAffectedItems(String businessType, String billId, List<PlmAffectedItemRequest> affectedItems) {
        if (affectedItems == null) {
            return;
        }
        plmAffectedItemMapper.deleteByBusinessTypeAndBillId(businessType, billId);
        List<PlmAffectedItemRecord> records = toAffectedItemRecords(businessType, billId, affectedItems);
        if (!records.isEmpty()) {
            plmAffectedItemMapper.batchInsert(records);
        }
        List<PlmBillObjectLinkRecord> objectLinks = plmObjectService.syncBillObjects(businessType, billId, affectedItems, currentUserId());
        plmRevisionDiffService.syncBillRevisionDiffs(businessType, billId, objectLinks, affectedItems);
        plmEnterpriseDepthService.syncBillEnterpriseDepth(businessType, billId, affectedItems, objectLinks, currentUserId());
    }

    private List<PlmAffectedItemRecord> toAffectedItemRecords(
            String businessType,
            String billId,
            List<PlmAffectedItemRequest> affectedItems
    ) {
        List<PlmAffectedItemRecord> records = new ArrayList<>();
        for (int index = 0; index < affectedItems.size(); index++) {
            PlmAffectedItemRequest item = affectedItems.get(index);
            records.add(new PlmAffectedItemRecord(
                    buildId("aff"),
                    businessType,
                    billId,
                    blankToNull(item.itemType()),
                    blankToNull(item.itemCode()),
                    blankToNull(item.itemName()),
                    blankToNull(item.beforeVersion()),
                    blankToNull(item.afterVersion()),
                    blankToNull(item.changeAction()),
                    blankToNull(item.ownerUserId()),
                    blankToNull(item.remark()),
                    item.sortOrder() == null ? index + 1 : item.sortOrder()
            ));
        }
        return records;
    }

    private String resolveAffectedObjectsText(String fallbackText, List<PlmAffectedItemRequest> affectedItems) {
        String text = blankToNull(fallbackText);
        if (text != null) {
            return text;
        }
        if (affectedItems == null || affectedItems.isEmpty()) {
            return null;
        }
        return affectedItems.stream()
                .map(item -> {
                    String itemCode = blankToNull(item.itemCode());
                    String itemName = blankToNull(item.itemName());
                    if (itemCode != null && itemName != null) {
                        return itemCode + "(" + itemName + ")";
                    }
                    return itemCode != null ? itemCode : itemName;
                })
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private String resolveAffectedSystemsText(String fallbackText, List<PlmAffectedItemRequest> affectedItems) {
        String text = blankToNull(fallbackText);
        if (text != null) {
            return text;
        }
        return resolveAffectedObjectsText(null, affectedItems);
    }

    private List<PlmAffectedItemResponse> affectedItemResponses(String businessType, String billId) {
        return plmAffectedItemMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .map(item -> new PlmAffectedItemResponse(
                        item.id(),
                        item.businessType(),
                        item.billId(),
                        item.itemType(),
                        item.itemCode(),
                        item.itemName(),
                        item.beforeVersion(),
                        item.afterVersion(),
                        item.changeAction(),
                        item.ownerUserId(),
                        item.remark(),
                        item.sortOrder()
                ))
                .toList();
    }

    private PlmEcrBillDetailResponse withDeepData(PlmEcrBillDetailResponse detail, String businessType, String billId) {
        return new PlmEcrBillDetailResponse(
                detail.billId(),
                detail.billNo(),
                detail.sceneCode(),
                detail.changeTitle(),
                detail.changeReason(),
                detail.affectedProductCode(),
                detail.priorityLevel(),
                detail.changeCategory(),
                detail.targetVersion(),
                detail.affectedObjectsText(),
                detail.impactScope(),
                detail.riskLevel(),
                detail.processInstanceId(),
                detail.status(),
                detail.implementationOwner(),
                detail.implementationSummary(),
                detail.implementationStartedAt(),
                detail.validationOwner(),
                detail.validationSummary(),
                detail.validatedAt(),
                detail.closedBy(),
                detail.closedAt(),
                detail.closeComment(),
                detail.detailSummary(),
                detail.approvalSummary(),
                detail.creatorUserId(),
                detail.createdAt(),
                detail.updatedAt(),
                affectedItemResponses(businessType, billId),
                plmObjectService.listBillObjectLinks(businessType, billId),
                plmRevisionDiffService.listBillRevisionDiffs(businessType, billId),
                plmImplementationTaskService.listBillTasks(businessType, billId)
        );
    }

    private PlmEcoBillDetailResponse withDeepData(PlmEcoBillDetailResponse detail, String businessType, String billId) {
        return new PlmEcoBillDetailResponse(
                detail.billId(),
                detail.billNo(),
                detail.sceneCode(),
                detail.executionTitle(),
                detail.executionPlan(),
                detail.effectiveDate(),
                detail.changeReason(),
                detail.implementationOwner(),
                detail.targetVersion(),
                detail.rolloutScope(),
                detail.validationPlan(),
                detail.rollbackPlan(),
                detail.processInstanceId(),
                detail.status(),
                detail.implementationSummary(),
                detail.implementationStartedAt(),
                detail.validationOwner(),
                detail.validationSummary(),
                detail.validatedAt(),
                detail.closedBy(),
                detail.closedAt(),
                detail.closeComment(),
                detail.detailSummary(),
                detail.approvalSummary(),
                detail.creatorUserId(),
                detail.createdAt(),
                detail.updatedAt(),
                affectedItemResponses(businessType, billId),
                plmObjectService.listBillObjectLinks(businessType, billId),
                plmRevisionDiffService.listBillRevisionDiffs(businessType, billId),
                plmImplementationTaskService.listBillTasks(businessType, billId)
        );
    }

    private PlmMaterialChangeBillDetailResponse withDeepData(
            PlmMaterialChangeBillDetailResponse detail,
            String businessType,
            String billId
    ) {
        return new PlmMaterialChangeBillDetailResponse(
                detail.billId(),
                detail.billNo(),
                detail.sceneCode(),
                detail.materialCode(),
                detail.materialName(),
                detail.changeReason(),
                detail.changeType(),
                detail.specificationChange(),
                detail.oldValue(),
                detail.newValue(),
                detail.uom(),
                detail.affectedSystemsText(),
                detail.processInstanceId(),
                detail.status(),
                detail.implementationOwner(),
                detail.implementationSummary(),
                detail.implementationStartedAt(),
                detail.validationOwner(),
                detail.validationSummary(),
                detail.validatedAt(),
                detail.closedBy(),
                detail.closedAt(),
                detail.closeComment(),
                detail.detailSummary(),
                detail.approvalSummary(),
                detail.creatorUserId(),
                detail.createdAt(),
                detail.updatedAt(),
                affectedItemResponses(businessType, billId),
                plmObjectService.listBillObjectLinks(businessType, billId),
                plmRevisionDiffService.listBillRevisionDiffs(businessType, billId),
                plmImplementationTaskService.listBillTasks(businessType, billId)
        );
    }

    private Map<String, Object> buildFormData(Object... keyValues) {
        Map<String, Object> formData = new LinkedHashMap<>();
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

    public String currentUserIdForWorkspace() {
        return currentUserId();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildBillNo(String prefix) {
        return prefix + "-" + LocalDate.now() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

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

    private int offsetOf(PageRequest request) {
        return Math.max(0, (request.page() - 1) * request.pageSize());
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private PlmPageFilters resolvePageFilters(List<FilterItem> filters) {
        if (filters == null || filters.isEmpty()) {
            return new PlmPageFilters(null, null, List.of(), null, null, null, null);
        }
        return new PlmPageFilters(
                resolveStringFilter(filters, "sceneCode"),
                resolveStringFilter(filters, "creatorUserId"),
                resolveStatuses(filters),
                resolveDateRange(filters, "createdAt").from(),
                resolveDateRange(filters, "createdAt").to(),
                resolveDateRange(filters, "updatedAt").from(),
                resolveDateRange(filters, "updatedAt").to()
        );
    }

    private String resolveStringFilter(List<FilterItem> filters, String field) {
        return filters.stream()
                .filter(filter -> field.equalsIgnoreCase(filter.field()))
                .filter(filter -> "eq".equalsIgnoreCase(filter.operator()))
                .map(FilterItem::value)
                .filter(Objects::nonNull)
                .map(this::asText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<String> resolveStatuses(List<FilterItem> filters) {
        List<String> statuses = new ArrayList<>();
        for (FilterItem filter : filters) {
            if (!"status".equalsIgnoreCase(filter.field()) || filter.value() == null) {
                continue;
            }
            if ("eq".equalsIgnoreCase(filter.operator())) {
                String value = asText(filter.value());
                if (value != null) {
                    statuses.add(value);
                }
            } else if ("in".equalsIgnoreCase(filter.operator()) && filter.value().isArray()) {
                filter.value().forEach(node -> {
                    String value = asText(node);
                    if (value != null) {
                        statuses.add(value);
                    }
                });
            }
        }
        return statuses.stream().distinct().toList();
    }

    private DateRange resolveDateRange(List<FilterItem> filters, String field) {
        return filters.stream()
                .filter(filter -> field.equalsIgnoreCase(filter.field()))
                .filter(filter -> "between".equalsIgnoreCase(filter.operator()))
                .map(FilterItem::value)
                .filter(Objects::nonNull)
                .filter(JsonNode::isArray)
                .filter(node -> node.size() == 2)
                .findFirst()
                .map(node -> new DateRange(parseDateValue(node.get(0), false), parseDateValue(node.get(1), true)))
                .orElse(new DateRange(null, null));
    }

    private LocalDateTime parseDateValue(JsonNode node, boolean endInclusive) {
        String value = asText(node);
        if (value == null) {
            return null;
        }
        if (value.contains("T")) {
            return LocalDateTime.parse(value);
        }
        LocalDate date = LocalDate.parse(value);
        return endInclusive ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ContractException illegalLifecycle(String message, PlmBillLifecycleRecord lifecycle) {
        return new ContractException(
                "BIZ.STATE_INVALID",
                HttpStatus.CONFLICT,
                message,
                Map.of(
                        "billId", lifecycle.billId(),
                        "status", lifecycle.status()
                )
        );
    }

    private ContractException resourceNotFound(String message, String billId) {
        return new ContractException(
                "BIZ.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                Map.of("billId", billId)
        );
    }

    private record PlmPageFilters(
            String sceneCode,
            String creatorUserId,
            List<String> statuses,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo,
            LocalDateTime updatedAtFrom,
            LocalDateTime updatedAtTo
    ) {
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
    }

    @FunctionalInterface
    private interface StatusUpdater {
        int update(String billId, String status);
    }

    @FunctionalInterface
    private interface ImplementationStatusUpdater {
        int update(String billId, String implementationOwner, String implementationSummary, String status);
    }

    @FunctionalInterface
    private interface ValidationStatusUpdater {
        int update(String billId, String validationOwner, String validationSummary, String status);
    }

    @FunctionalInterface
    private interface ClosureStatusUpdater {
        int update(String billId, String closedBy, String closeComment, String status);
    }
}
