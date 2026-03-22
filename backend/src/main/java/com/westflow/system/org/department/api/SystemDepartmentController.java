package com.westflow.system.org.department.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.org.department.service.SystemDepartmentService;
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

@RestController
@RequestMapping("/api/v1/system/departments")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemDepartmentController {

    private final SystemDepartmentService systemDepartmentService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemDepartmentListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemDepartmentService.page(request));
    }

    @GetMapping("/{departmentId}")
    public ApiResponse<SystemDepartmentDetailResponse> detail(@PathVariable String departmentId) {
        return ApiResponse.success(systemDepartmentService.detail(departmentId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemDepartmentFormOptionsResponse> options(
            @RequestParam(required = false) String companyId
    ) {
        return ApiResponse.success(systemDepartmentService.formOptions(companyId));
    }

    @PostMapping
    public ApiResponse<SystemDepartmentMutationResponse> create(
            @Valid @RequestBody SaveSystemDepartmentRequest request
    ) {
        return ApiResponse.success(systemDepartmentService.create(request));
    }

    @PutMapping("/{departmentId}")
    public ApiResponse<SystemDepartmentMutationResponse> update(
            @PathVariable String departmentId,
            @Valid @RequestBody SaveSystemDepartmentRequest request
    ) {
        return ApiResponse.success(systemDepartmentService.update(departmentId, request));
    }
}
