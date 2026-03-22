package com.westflow.system.user.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
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

@RestController
@RequestMapping("/api/v1/system/users")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService systemUserService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemUserListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemUserService.page(request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<SystemUserDetailResponse> detail(@PathVariable String userId) {
        return ApiResponse.success(systemUserService.detail(userId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemUserFormOptionsResponse> options() {
        return ApiResponse.success(systemUserService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemUserMutationResponse> create(@Valid @RequestBody SaveSystemUserRequest request) {
        return ApiResponse.success(systemUserService.create(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<SystemUserMutationResponse> update(
            @PathVariable String userId,
            @Valid @RequestBody SaveSystemUserRequest request
    ) {
        return ApiResponse.success(systemUserService.update(userId, request));
    }
}
