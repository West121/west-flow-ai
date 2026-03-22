package com.westflow.system.role.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.role.service.SystemRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统角色管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/roles")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemRoleController {

    private final SystemRoleService systemRoleService;

    /**
     * 分页查询角色。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemRoleListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemRoleService.page(request));
    }

    /**
     * 查询角色详情。
     */
    @GetMapping("/{roleId}")
    public ApiResponse<SystemRoleDetailResponse> detail(@PathVariable String roleId) {
        return ApiResponse.success(systemRoleService.detail(roleId));
    }

    /**
     * 获取角色表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemRoleFormOptionsResponse> options() {
        return ApiResponse.success(systemRoleService.formOptions());
    }

    /**
     * 新建角色。
     */
    @PostMapping
    public ApiResponse<SystemRoleMutationResponse> create(@Valid @RequestBody SaveSystemRoleRequest request) {
        return ApiResponse.success(systemRoleService.create(request));
    }

    /**
     * 更新角色。
     */
    @PutMapping("/{roleId}")
    public ApiResponse<SystemRoleMutationResponse> update(
            @PathVariable String roleId,
            @Valid @RequestBody SaveSystemRoleRequest request
    ) {
        return ApiResponse.success(systemRoleService.update(roleId, request));
    }
}
