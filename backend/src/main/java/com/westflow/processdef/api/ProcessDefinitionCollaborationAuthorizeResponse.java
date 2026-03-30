package com.westflow.processdef.api;

/**
 * 流程设计器协同授权返回值。
 */
public record ProcessDefinitionCollaborationAuthorizeResponse(
        // 协同房间名。
        String roomName,
        // 对应的流程定义标识，草稿场景可为空。
        String processDefinitionId,
        // 当前用户标识。
        String userId,
        // 当前用户显示名。
        String displayName,
        // 当前岗位标识。
        String activePostId,
        // 当前部门名称。
        String activeDepartmentName,
        // 当前岗位名称。
        String activePostName
) {
}
