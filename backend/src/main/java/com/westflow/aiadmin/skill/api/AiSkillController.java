package com.westflow.aiadmin.skill.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.aiadmin.skill.service.AiSkillRegistryService;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
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
 * AI Skill 注册表管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/ai/skills")
@SaCheckLogin
@RequiredArgsConstructor
public class AiSkillController {

    private final AiSkillRegistryService aiSkillRegistryService;

    /**
     * 分页查询 Skill 注册表。
     */
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiSkillListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(aiSkillRegistryService.page(request));
    }

    /**
     * 查询 Skill 注册表详情。
     */
    @GetMapping("/{skillId}")
    public ApiResponse<AiSkillDetailResponse> detail(@PathVariable String skillId) {
        return ApiResponse.success(aiSkillRegistryService.detail(skillId));
    }

    /**
     * 获取 Skill 表单选项。
     */
    @GetMapping("/options")
    public ApiResponse<AiSkillFormOptionsResponse> options() {
        return ApiResponse.success(aiSkillRegistryService.formOptions());
    }

    /**
     * 新建 Skill 注册记录。
     */
    @PostMapping
    public ApiResponse<AiSkillMutationResponse> create(@Valid @RequestBody SaveAiSkillRequest request) {
        return ApiResponse.success(aiSkillRegistryService.create(request));
    }

    /**
     * 更新 Skill 注册记录。
     */
    @PutMapping("/{skillId}")
    public ApiResponse<AiSkillMutationResponse> update(
            @PathVariable String skillId,
            @Valid @RequestBody SaveAiSkillRequest request
    ) {
        return ApiResponse.success(aiSkillRegistryService.update(skillId, request));
    }
}
