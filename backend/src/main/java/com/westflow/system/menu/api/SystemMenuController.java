package com.westflow.system.menu.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.menu.service.SystemMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统菜单管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/menus")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemMenuController {

    private final SystemMenuService systemMenuService;

    /**
     * 分页查询菜单。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemMenuListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemMenuService.page(request));
    }

    /**
     * 查询菜单详情。
     */
    @GetMapping("/{menuId}")
    public ApiResponse<SystemMenuDetailResponse> detail(@PathVariable String menuId) {
        return ApiResponse.success(systemMenuService.detail(menuId));
    }

    /**
     * 获取菜单表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemMenuFormOptionsResponse> options() {
        return ApiResponse.success(systemMenuService.formOptions());
    }

    /**
     * 查询完整菜单树。
     */
    @GetMapping("/tree")
    public ApiResponse<java.util.List<SystemMenuTreeNodeResponse>> tree() {
        return ApiResponse.success(systemMenuService.tree());
    }

    /**
     * 查询当前用户左侧导航菜单树。
     */
    @GetMapping("/sidebar-tree")
    public ApiResponse<java.util.List<SidebarMenuNodeResponse>> sidebarTree() {
        return ApiResponse.success(systemMenuService.sidebarTree(StpUtil.getLoginIdAsString()));
    }

    /**
     * 新建菜单。
     */
    @PostMapping
    public ApiResponse<SystemMenuMutationResponse> create(@Valid @RequestBody SaveSystemMenuRequest request) {
        return ApiResponse.success(systemMenuService.create(request));
    }

    /**
     * 更新菜单。
     */
    @PutMapping("/{menuId}")
    public ApiResponse<SystemMenuMutationResponse> update(
            @PathVariable String menuId,
            @Valid @RequestBody SaveSystemMenuRequest request
    ) {
        return ApiResponse.success(systemMenuService.update(menuId, request));
    }
}
