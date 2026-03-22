package com.westflow.system.trigger.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.trigger.service.SystemTriggerService;
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
 * 系统触发器管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/triggers")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemTriggerController {

    private final SystemTriggerService systemTriggerService;

    /**
     * 分页查询触发器。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemTriggerListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        // 触发器列表也是标准 CRUD 页面，统一走分页查询契约。
        return ApiResponse.success(systemTriggerService.page(request));
    }

    /**
     * 查询触发器详情。
     */
    @GetMapping("/{triggerId}")
    public ApiResponse<SystemTriggerDetailResponse> detail(@PathVariable String triggerId) {
        return ApiResponse.success(systemTriggerService.detail(triggerId));
    }

    /**
     * 获取触发器表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemTriggerFormOptionsResponse> options() {
        return ApiResponse.success(systemTriggerService.formOptions());
    }

    /**
     * 新建触发器。
     */
    @PostMapping
    public ApiResponse<SystemTriggerMutationResponse> create(@Valid @RequestBody SaveSystemTriggerRequest request) {
        return ApiResponse.success(systemTriggerService.create(request));
    }

    /**
     * 更新触发器。
     */
    @PutMapping("/{triggerId}")
    public ApiResponse<SystemTriggerMutationResponse> update(
            @PathVariable String triggerId,
            @Valid @RequestBody SaveSystemTriggerRequest request
    ) {
        return ApiResponse.success(systemTriggerService.update(triggerId, request));
    }
}
