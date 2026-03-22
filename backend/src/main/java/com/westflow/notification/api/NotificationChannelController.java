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
public class NotificationChannelController {

    private final NotificationChannelService notificationChannelService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotificationChannelListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(notificationChannelService.page(request));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<NotificationChannelDetailResponse> detail(@PathVariable String channelId) {
        return ApiResponse.success(notificationChannelService.detail(channelId));
    }

    @GetMapping("/options")
    public ApiResponse<NotificationChannelFormOptionsResponse> options() {
        return ApiResponse.success(notificationChannelService.formOptions());
    }

    @PostMapping
    public ApiResponse<NotificationChannelMutationResponse> create(@Valid @RequestBody SaveNotificationChannelRequest request) {
        return ApiResponse.success(notificationChannelService.create(request));
    }

    @PutMapping("/{channelId}")
    public ApiResponse<NotificationChannelMutationResponse> update(
            @PathVariable String channelId,
            @Valid @RequestBody SaveNotificationChannelRequest request
    ) {
        return ApiResponse.success(notificationChannelService.update(channelId, request));
    }
}
