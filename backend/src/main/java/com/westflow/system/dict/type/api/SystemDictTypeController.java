package com.westflow.system.dict.type.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.dict.type.service.SystemDictTypeService;
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
 * 字典类型管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/dict-types")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemDictTypeController {

    private final SystemDictTypeService systemDictTypeService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemDictTypeListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemDictTypeService.page(request));
    }

    @GetMapping("/{dictTypeId}")
    public ApiResponse<SystemDictTypeDetailResponse> detail(@PathVariable String dictTypeId) {
        return ApiResponse.success(systemDictTypeService.detail(dictTypeId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemDictTypeFormOptionsResponse> options() {
        return ApiResponse.success(systemDictTypeService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemDictTypeMutationResponse> create(@Valid @RequestBody SaveSystemDictTypeRequest request) {
        return ApiResponse.success(systemDictTypeService.create(request));
    }

    @PutMapping("/{dictTypeId}")
    public ApiResponse<SystemDictTypeMutationResponse> update(
            @PathVariable String dictTypeId,
            @Valid @RequestBody SaveSystemDictTypeRequest request
    ) {
        return ApiResponse.success(systemDictTypeService.update(dictTypeId, request));
    }
}
