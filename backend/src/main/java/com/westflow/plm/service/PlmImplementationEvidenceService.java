package com.westflow.plm.service;

import com.westflow.plm.api.PlmAcceptanceChecklistResponse;
import com.westflow.plm.api.PlmAcceptanceChecklistUpdateRequest;
import com.westflow.plm.api.PlmImplementationEvidenceResponse;
import com.westflow.plm.api.PlmImplementationEvidenceUpdateRequest;
import com.westflow.plm.api.PlmImplementationEvidenceUpsertRequest;
import com.westflow.plm.api.PlmImplementationWorkspaceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 实施证据与验收工作区服务。
 */
@Service
@RequiredArgsConstructor
public class PlmImplementationEvidenceService {

    private final PlmImplementationTaskService plmImplementationTaskService;

    public List<PlmImplementationEvidenceResponse> listEvidence(String businessType, String billId) {
        return plmImplementationTaskService.listEvidence(businessType, billId);
    }

    public List<PlmAcceptanceChecklistResponse> listAcceptanceChecklist(String businessType, String billId) {
        return plmImplementationTaskService.listAcceptanceChecklist(businessType, billId);
    }

    public PlmImplementationEvidenceResponse addEvidence(
            String businessType,
            String billId,
            String taskId,
            PlmImplementationEvidenceUpsertRequest request,
            String operatorUserId
    ) {
        return plmImplementationTaskService.addEvidence(businessType, billId, taskId, request, operatorUserId);
    }

    public PlmAcceptanceChecklistResponse updateAcceptanceChecklist(
            String businessType,
            String billId,
            String checklistId,
            PlmAcceptanceChecklistUpdateRequest request,
            String operatorUserId
    ) {
        return plmImplementationTaskService.updateAcceptanceChecklist(
                businessType,
                billId,
                checklistId,
                request,
                operatorUserId
        );
    }

    public PlmImplementationEvidenceResponse updateEvidence(
            String businessType,
            String billId,
            String evidenceId,
            PlmImplementationEvidenceUpdateRequest request
    ) {
        return plmImplementationTaskService.updateEvidence(businessType, billId, evidenceId, request);
    }

    public void deleteEvidence(String businessType, String billId, String evidenceId) {
        plmImplementationTaskService.deleteEvidence(businessType, billId, evidenceId);
    }

    public PlmImplementationWorkspaceResponse workspace(String businessType, String billId) {
        return plmImplementationTaskService.workspace(businessType, billId);
    }
}
