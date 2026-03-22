package com.westflow.system.log.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.log.response.AuditLogDetailResponse;
import com.westflow.system.log.response.AuditLogListItemResponse;
import com.westflow.system.log.response.LoginLogDetailResponse;
import com.westflow.system.log.response.LoginLogListItemResponse;
import com.westflow.system.log.response.SystemNotificationLogDetailResponse;
import com.westflow.system.log.response.SystemNotificationLogListItemResponse;
import com.westflow.system.log.service.SystemLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/logs")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @PostMapping("/audit/page")
    public ApiResponse<PageResponse<AuditLogListItemResponse>> pageAudit(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemLogService.pageAudit(request));
    }

    @GetMapping("/audit/{logId}")
    public ApiResponse<AuditLogDetailResponse> detailAudit(@PathVariable String logId) {
        return ApiResponse.success(systemLogService.detailAudit(logId));
    }

    @PostMapping("/login/page")
    public ApiResponse<PageResponse<LoginLogListItemResponse>> pageLogin(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemLogService.pageLogin(request));
    }

    @GetMapping("/login/{logId}")
    public ApiResponse<LoginLogDetailResponse> detailLogin(@PathVariable String logId) {
        return ApiResponse.success(systemLogService.detailLogin(logId));
    }

    @PostMapping("/notifications/page")
    public ApiResponse<PageResponse<SystemNotificationLogListItemResponse>> pageNotification(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemLogService.pageNotification(request));
    }

    @GetMapping("/notifications/{recordId}")
    public ApiResponse<SystemNotificationLogDetailResponse> detailNotification(@PathVariable String recordId) {
        return ApiResponse.success(systemLogService.detailNotification(recordId));
    }
}

