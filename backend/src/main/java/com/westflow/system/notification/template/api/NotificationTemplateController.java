package com.westflow.system.notification.template.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.notification.template.request.SaveNotificationTemplateRequest;
import com.westflow.system.notification.template.response.NotificationTemplateDetailResponse;
import com.westflow.system.notification.template.response.NotificationTemplateFormOptionsResponse;
import com.westflow.system.notification.template.response.NotificationTemplateListItemResponse;
import com.westflow.system.notification.template.response.NotificationTemplateMutationResponse;
import com.westflow.system.notification.template.service.NotificationTemplateService;
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
 * 通知模板管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/notification-templates")
@SaCheckLogin
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService notificationTemplateService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotificationTemplateListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(notificationTemplateService.page(request));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<NotificationTemplateDetailResponse> detail(@PathVariable String templateId) {
        return ApiResponse.success(notificationTemplateService.detail(templateId));
    }

    @GetMapping("/options")
    public ApiResponse<NotificationTemplateFormOptionsResponse> options() {
        return ApiResponse.success(notificationTemplateService.formOptions());
    }

    @PostMapping
    public ApiResponse<NotificationTemplateMutationResponse> create(@Valid @RequestBody SaveNotificationTemplateRequest request) {
        return ApiResponse.success(notificationTemplateService.create(request));
    }

    @PutMapping("/{templateId}")
    public ApiResponse<NotificationTemplateMutationResponse> update(
            @PathVariable String templateId,
            @Valid @RequestBody SaveNotificationTemplateRequest request
    ) {
        return ApiResponse.success(notificationTemplateService.update(templateId, request));
    }
}
