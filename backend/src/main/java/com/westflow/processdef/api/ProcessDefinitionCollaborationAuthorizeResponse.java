package com.westflow.processdef.api;

public record ProcessDefinitionCollaborationAuthorizeResponse(
        String roomName,
        String processDefinitionId,
        String userId,
        String displayName,
        String activePostId,
        String activeDepartmentName,
        String activePostName
) {
}
