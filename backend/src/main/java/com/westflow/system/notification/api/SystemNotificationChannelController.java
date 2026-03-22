package com.westflow.system.notification.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.notification.service.SystemNotificationChannelService;
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
 * 系统通知渠道管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/notification-channels")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemNotificationChannelController {

    private final SystemNotificationChannelService systemNotificationChannelService;

    /**
     * 分页查询通知渠道。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemNotificationChannelListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        // 系统管理页沿用统一分页契约，前端表格可以直接复用。
        return ApiResponse.success(systemNotificationChannelService.page(request));
    }

    /**
     * 查询通知渠道详情。
     */
    @GetMapping("/{channelId}")
    public ApiResponse<SystemNotificationChannelDetailResponse> detail(@PathVariable String channelId) {
        return ApiResponse.success(systemNotificationChannelService.detail(channelId));
    }

    /**
     * 获取通知渠道表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemNotificationChannelFormOptionsResponse> options() {
        // 新建和编辑页共享一套选项，避免重复维护渠道枚举。
        return ApiResponse.success(systemNotificationChannelService.formOptions());
    }

    /**
     * 新建通知渠道。
     */
    @PostMapping
    public ApiResponse<SystemNotificationChannelMutationResponse> create(
            @Valid @RequestBody SaveSystemNotificationChannelRequest request
    ) {
        return ApiResponse.success(systemNotificationChannelService.create(request));
    }

    /**
     * 更新通知渠道。
     */
    @PutMapping("/{channelId}")
    public ApiResponse<SystemNotificationChannelMutationResponse> update(
            @PathVariable String channelId,
            @Valid @RequestBody SaveSystemNotificationChannelRequest request
    ) {
        return ApiResponse.success(systemNotificationChannelService.update(channelId, request));
    }
}
