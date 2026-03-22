package com.westflow.system.dict.item.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.dict.item.service.SystemDictItemService;
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
 * 字典项管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/dict-items")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemDictItemController {

    private final SystemDictItemService systemDictItemService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemDictItemListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemDictItemService.page(request));
    }

    @GetMapping("/{dictItemId}")
    public ApiResponse<SystemDictItemDetailResponse> detail(@PathVariable String dictItemId) {
        return ApiResponse.success(systemDictItemService.detail(dictItemId));
    }

    @GetMapping("/options")
    public ApiResponse<SystemDictItemFormOptionsResponse> options() {
        return ApiResponse.success(systemDictItemService.formOptions());
    }

    @PostMapping
    public ApiResponse<SystemDictItemMutationResponse> create(@Valid @RequestBody SaveSystemDictItemRequest request) {
        return ApiResponse.success(systemDictItemService.create(request));
    }

    @PutMapping("/{dictItemId}")
    public ApiResponse<SystemDictItemMutationResponse> update(
            @PathVariable String dictItemId,
            @Valid @RequestBody SaveSystemDictItemRequest request
    ) {
        return ApiResponse.success(systemDictItemService.update(dictItemId, request));
    }
}
