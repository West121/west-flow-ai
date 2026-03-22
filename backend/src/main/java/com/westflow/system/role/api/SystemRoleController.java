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

@RestController
@RequestMapping("/api/v1/system/roles")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemRoleController {

    private final SystemRoleService systemRoleService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemRoleListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemRoleService.page(request));
    }

    @GetMapping("/{roleId}")
    public ApiResponse<SystemRoleDetailResponse> detail(@PathVariable String roleId) {
        return ApiResponse.success(systemRoleService.detail(roleId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemRoleFormOptionsResponse> options() {
        return ApiResponse.success(systemRoleService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemRoleMutationResponse> create(@Valid @RequestBody SaveSystemRoleRequest request) {
        return ApiResponse.success(systemRoleService.create(request));
    }

    @PutMapping("/{roleId}")
    public ApiResponse<SystemRoleMutationResponse> update(
            @PathVariable String roleId,
            @Valid @RequestBody SaveSystemRoleRequest request
    ) {
        return ApiResponse.success(systemRoleService.update(roleId, request));
    }
}
