package com.westflow.identity.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PermissionProbeController {

    @GetMapping("/permission-probe")
    @SaCheckPermission("system:permission-probe")
    public ApiResponse<Map<String, String>> permissionProbe() {
        // 这个接口专门用来验证权限拦截器是否生效。
        StpUtil.checkPermission("system:permission-probe");
        return ApiResponse.success(Map.of("status", "granted"));
    }
}
