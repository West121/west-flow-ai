package com.westflow.system.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

/**
 * 用户保存请求，供新建和编辑复用。
 */
public record SaveSystemUserRequest(
        @NotBlank(message = "请输入用户姓名")
        String displayName,
        @NotBlank(message = "请输入登录账号")
        String username,
        @NotBlank(message = "请输入手机号")
        String mobile,
        @Email(message = "请输入正确的邮箱地址")
        @NotBlank(message = "请输入邮箱地址")
        String email,
        @NotBlank(message = "请选择所属公司")
        String companyId,
        @NotBlank(message = "请选择主岗位")
        String primaryPostId,
        @NotEmpty(message = "请至少选择一个角色")
        List<String> roleIds,
        @NotNull(message = "请选择启用状态")
        Boolean enabled,
        Assignment primaryAssignment,
        List<Assignment> partTimeAssignments
) {

    @AssertTrue(message = "请配置主职任职")
    public boolean isAssignmentConfigured() {
        return primaryAssignment != null
                || (companyId != null && !companyId.isBlank()
                && primaryPostId != null && !primaryPostId.isBlank()
                && roleIds != null && !roleIds.isEmpty());
    }

    public Assignment resolvedPrimaryAssignment() {
        if (primaryAssignment != null) {
            return primaryAssignment;
        }
        return new Assignment(companyId, primaryPostId, roleIds, enabled);
    }

    public List<Assignment> resolvedPartTimeAssignments() {
        return partTimeAssignments == null ? List.of() : partTimeAssignments.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public record Assignment(
            @NotBlank(message = "请选择所属公司")
            String companyId,
            @NotBlank(message = "请选择岗位")
            String postId,
            @NotEmpty(message = "请至少选择一个角色")
            List<String> roleIds,
            @NotNull(message = "请选择任职启用状态")
            Boolean enabled
    ) {
    }
}
