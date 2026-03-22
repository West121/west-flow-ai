package com.westflow.identity.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.dto.LoginResponse;
import com.westflow.identity.dto.SwitchContextRequest;
import com.westflow.identity.service.IdentityAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前用户信息接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IdentityAuthService fixtureAuthService;

    /**
     * 登录并返回令牌信息。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(fixtureAuthService.login(request));
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/current-user")
    @SaCheckLogin
    public ApiResponse<CurrentUserResponse> currentUser() {
        // 当前登录人信息会被前端和权限层反复读取。
        return ApiResponse.success(fixtureAuthService.currentUser());
    }

    /**
     * 切换当前岗位上下文。
     */
    @PostMapping("/switch-context")
    @SaCheckLogin
    public ApiResponse<Void> switchContext(@Valid @RequestBody SwitchContextRequest request) {
        // 切换当前岗位上下文，供数据权限和办理权限复用。
        fixtureAuthService.switchContext(request.activePostId());
        return ApiResponse.success(null);
    }
}
