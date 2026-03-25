package com.westflow.system.user.model;

/**
 * 用户表实体。
 */
public record SystemUserRecord(
        String id,
        String companyId,
        String activeDepartmentId,
        String activePostId,
        String displayName,
        String username,
        String mobile,
        String email,
        String avatar,
        Boolean enabled
) {
}
