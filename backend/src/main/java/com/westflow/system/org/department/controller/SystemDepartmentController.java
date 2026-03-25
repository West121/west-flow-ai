package com.westflow.system.org.department.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.org.department.request.SaveSystemDepartmentRequest;
import com.westflow.system.org.department.response.SystemDepartmentDetailResponse;
import com.westflow.system.org.department.response.SystemDepartmentFormOptionsResponse;
import com.westflow.system.org.department.response.SystemDepartmentListItemResponse;
import com.westflow.system.org.department.response.SystemDepartmentMutationResponse;
import com.westflow.system.org.department.service.SystemDepartmentService;
import com.westflow.system.user.response.SystemAssociatedUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统部门管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/departments")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemDepartmentController {

    private final SystemDepartmentService systemDepartmentService;

    /**
     * 分页查询部门。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemDepartmentListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemDepartmentService.page(request));
    }

    /**
     * 查询部门详情。
     */
    @GetMapping("/{departmentId}")
    public ApiResponse<SystemDepartmentDetailResponse> detail(@PathVariable String departmentId) {
        return ApiResponse.success(systemDepartmentService.detail(departmentId));
    }

    /**
     * 查询部门关联用户。
     */
    @GetMapping("/{departmentId}/users")
    public ApiResponse<java.util.List<SystemAssociatedUserResponse>> relatedUsers(@PathVariable String departmentId) {
        return ApiResponse.success(systemDepartmentService.relatedUsers(departmentId));
    }

    /**
     * 获取部门表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemDepartmentFormOptionsResponse> options(
            @RequestParam(required = false) String companyId
    ) {
        return ApiResponse.success(systemDepartmentService.formOptions(companyId));
    }

    /**
     * 新建部门。
     */
    @PostMapping
    public ApiResponse<SystemDepartmentMutationResponse> create(
            @Valid @RequestBody SaveSystemDepartmentRequest request
    ) {
        return ApiResponse.success(systemDepartmentService.create(request));
    }

    /**
     * 更新部门。
     */
    @PutMapping("/{departmentId}")
    public ApiResponse<SystemDepartmentMutationResponse> update(
            @PathVariable String departmentId,
            @Valid @RequestBody SaveSystemDepartmentRequest request
    ) {
        return ApiResponse.success(systemDepartmentService.update(departmentId, request));
    }
}
