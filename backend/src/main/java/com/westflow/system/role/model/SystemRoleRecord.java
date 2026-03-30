package com.westflow.system.role.model;

/**
 * 角色表实体。
 */
public record SystemRoleRecord(
        // 角色主键。
        String id,
        // 角色编码。
        String roleCode,
        // 角色名称。
        String roleName,
        // 角色分类。
        String roleCategory,
        // 角色说明。
        String description,
        // 是否启用。
        Boolean enabled
) {
}
