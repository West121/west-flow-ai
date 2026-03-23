package com.westflow.notification.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.notification.service.NotificationChannelService;
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
@RequestMapping("/api/v1/notification/channels")
@SaCheckLogin
@RequiredArgsConstructor
// 通知渠道的分页、详情、配置选项和增改入口。
public class NotificationChannelController {

    private final NotificationChannelService notificationChannelService;

    @PostMapping("/page")
    // 分页查询通知渠道列表。
    public ApiResponse<PageResponse<NotificationChannelListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(notificationChannelService.page(request));
    }

    @GetMapping("/{channelId}")
    // 查询通知渠道详情。
    public ApiResponse<NotificationChannelDetailResponse> detail(@PathVariable String channelId) {
        return ApiResponse.success(notificationChannelService.detail(channelId));
    }

    @GetMapping("/{channelId}/diagnostic")
    // 查询通知渠道诊断信息。
    public ApiResponse<NotificationChannelDiagnosticResponse> diagnostic(@PathVariable String channelId) {
        return ApiResponse.success(notificationChannelService.diagnostic(channelId));
    }

    @GetMapping("/options")
    // 查询通知渠道表单可选项。
    public ApiResponse<NotificationChannelFormOptionsResponse> options() {
        return ApiResponse.success(notificationChannelService.formOptions());
    }

    @PostMapping
    // 新增通知渠道。
    public ApiResponse<NotificationChannelMutationResponse> create(@Valid @RequestBody SaveNotificationChannelRequest request) {
        return ApiResponse.success(notificationChannelService.create(request));
    }

    @PutMapping("/{channelId}")
    // 更新通知渠道。
    public ApiResponse<NotificationChannelMutationResponse> update(
            @PathVariable String channelId,
            @Valid @RequestBody SaveNotificationChannelRequest request
    ) {
        return ApiResponse.success(notificationChannelService.update(channelId, request));
    }
}
