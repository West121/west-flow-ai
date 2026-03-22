package com.westflow.system.org.post.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.org.post.service.SystemPostService;
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
 * 系统岗位管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/posts")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemPostController {

    private final SystemPostService systemPostService;

    /**
     * 分页查询岗位。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemPostListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemPostService.page(request));
    }

    /**
     * 查询岗位详情。
     */
    @GetMapping("/{postId}")
    public ApiResponse<SystemPostDetailResponse> detail(@PathVariable String postId) {
        return ApiResponse.success(systemPostService.detail(postId));
    }

    /**
     * 获取岗位表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<SystemPostFormOptionsResponse> options(
            @RequestParam(required = false) String companyId
    ) {
        return ApiResponse.success(systemPostService.formOptions(companyId));
    }

    /**
     * 新建岗位。
     */
    @PostMapping
    public ApiResponse<SystemPostMutationResponse> create(@Valid @RequestBody SaveSystemPostRequest request) {
        return ApiResponse.success(systemPostService.create(request));
    }

    /**
     * 更新岗位。
     */
    @PutMapping("/{postId}")
    public ApiResponse<SystemPostMutationResponse> update(
            @PathVariable String postId,
            @Valid @RequestBody SaveSystemPostRequest request
    ) {
        return ApiResponse.success(systemPostService.update(postId, request));
    }
}
