package com.westflow.system.notification.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.notification.record.response.NotificationRecordDetailResponse;
import com.westflow.system.notification.record.response.NotificationRecordListItemResponse;
import com.westflow.system.notification.record.service.NotificationRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知发送记录管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/notification-records")
@SaCheckLogin
@RequiredArgsConstructor
public class NotificationRecordController {

    private final NotificationRecordService notificationRecordService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotificationRecordListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(notificationRecordService.page(request));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<NotificationRecordDetailResponse> detail(@PathVariable String recordId) {
        return ApiResponse.success(notificationRecordService.detail(recordId));
    }
}
