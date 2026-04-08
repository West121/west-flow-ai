package com.westflow.plm.api;

import java.util.List;

/**
 * PLM 实施协同工作区响应。
 */
public record PlmImplementationWorkspaceResponse(
        List<PlmImplementationTaskResponse> tasks,
        List<PlmImplementationTemplateResponse> templates,
        List<PlmImplementationDependencyResponse> dependencies,
        List<PlmImplementationEvidenceResponse> evidences,
        List<PlmAcceptanceChecklistResponse> acceptanceCheckpoints
) {
}
