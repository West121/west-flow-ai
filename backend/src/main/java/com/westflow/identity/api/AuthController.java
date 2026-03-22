package com.westflow.identity.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.dto.LoginResponse;
import com.westflow.identity.dto.SwitchContextRequest;
import com.westflow.identity.service.FixtureAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final FixtureAuthService fixtureAuthService;

    public AuthController(FixtureAuthService fixtureAuthService) {
        this.fixtureAuthService = fixtureAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(fixtureAuthService.login(request));
    }

    @GetMapping("/current-user")
    @SaCheckLogin
    public ApiResponse<CurrentUserResponse> currentUser() {
        return ApiResponse.success(fixtureAuthService.currentUser());
    }

    @PostMapping("/switch-context")
    @SaCheckLogin
    public ApiResponse<Void> switchContext(@Valid @RequestBody SwitchContextRequest request) {
        fixtureAuthService.switchContext(request.activePostId());
        return ApiResponse.success(null);
    }
}
