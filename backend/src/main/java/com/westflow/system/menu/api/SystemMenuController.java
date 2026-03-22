package com.westflow.system.menu.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.menu.service.SystemMenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/menus")
@SaCheckLogin
public class SystemMenuController {

    private final SystemMenuService systemMenuService;

    public SystemMenuController(SystemMenuService systemMenuService) {
        this.systemMenuService = systemMenuService;
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemMenuListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemMenuService.page(request));
    }

    @GetMapping("/{menuId}")
    public ApiResponse<SystemMenuDetailResponse> detail(@PathVariable String menuId) {
        return ApiResponse.success(systemMenuService.detail(menuId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemMenuFormOptionsResponse> options() {
        return ApiResponse.success(systemMenuService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemMenuMutationResponse> create(@Valid @RequestBody SaveSystemMenuRequest request) {
        return ApiResponse.success(systemMenuService.create(request));
    }

    @PutMapping("/{menuId}")
    public ApiResponse<SystemMenuMutationResponse> update(
            @PathVariable String menuId,
            @Valid @RequestBody SaveSystemMenuRequest request
    ) {
        return ApiResponse.success(systemMenuService.update(menuId, request));
    }
}
