package com.westflow.system.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.user.request.SaveSystemUserRequest;
import com.westflow.system.user.response.SystemAssociatedUserResponse;
import com.westflow.system.user.response.SystemUserDetailResponse;
import com.westflow.system.user.response.SystemUserFormOptionsResponse;
import com.westflow.system.user.response.SystemUserListItemResponse;
import com.westflow.system.user.response.SystemUserMutationResponse;
import com.westflow.system.user.service.SystemUserService;
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
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/users")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService systemUserService;

    /**
     * 分页查询用户。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemUserListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemUserService.page(request));
    }

    /**
     * 查询用户详情。
     */
    @GetMapping("/{userId}")
    public ApiResponse<SystemUserDetailResponse> detail(@PathVariable String userId) {
        return ApiResponse.success(systemUserService.detail(userId));
    }

    /**
     * 获取用户表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemUserFormOptionsResponse> options() {
        return ApiResponse.success(systemUserService.formOptions());
    }

    /**
     * 新建用户。
     */
    @PostMapping
    public ApiResponse<SystemUserMutationResponse> create(@Valid @RequestBody SaveSystemUserRequest request) {
        return ApiResponse.success(systemUserService.create(request));
    }

    /**
     * 更新用户。
     */
    @PutMapping("/{userId}")
    public ApiResponse<SystemUserMutationResponse> update(
            @PathVariable String userId,
            @Valid @RequestBody SaveSystemUserRequest request
    ) {
        return ApiResponse.success(systemUserService.update(userId, request));
    }
}
