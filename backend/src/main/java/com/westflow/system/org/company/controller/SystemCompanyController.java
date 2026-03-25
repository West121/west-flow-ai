package com.westflow.system.org.company.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.org.company.request.SaveSystemCompanyRequest;
import com.westflow.system.org.company.response.SystemCompanyDetailResponse;
import com.westflow.system.org.company.response.SystemCompanyFormOptionsResponse;
import com.westflow.system.org.company.response.SystemCompanyListItemResponse;
import com.westflow.system.org.company.response.SystemCompanyMutationResponse;
import com.westflow.system.org.company.service.SystemCompanyService;
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
 * 系统公司管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/companies")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemCompanyController {

    private final SystemCompanyService systemCompanyService;

    /**
     * 分页查询公司。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemCompanyListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemCompanyService.page(request));
    }

    /**
     * 查询公司详情。
     */
    @GetMapping("/{companyId}")
    public ApiResponse<SystemCompanyDetailResponse> detail(@PathVariable String companyId) {
        return ApiResponse.success(systemCompanyService.detail(companyId));
    }

    /**
     * 获取公司表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemCompanyFormOptionsResponse> options() {
        return ApiResponse.success(systemCompanyService.formOptions());
    }

    /**
     * 新建公司。
     */
    @PostMapping
    public ApiResponse<SystemCompanyMutationResponse> create(@Valid @RequestBody SaveSystemCompanyRequest request) {
        return ApiResponse.success(systemCompanyService.create(request));
    }

    /**
     * 更新公司。
     */
    @PutMapping("/{companyId}")
    public ApiResponse<SystemCompanyMutationResponse> update(
            @PathVariable String companyId,
            @Valid @RequestBody SaveSystemCompanyRequest request
    ) {
        return ApiResponse.success(systemCompanyService.update(companyId, request));
    }
}
