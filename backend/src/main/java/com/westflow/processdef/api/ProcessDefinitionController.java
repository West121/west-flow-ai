package com.westflow.processdef.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-definitions")
@RequiredArgsConstructor
// 流程定义的草稿、发布、详情和分页查询入口。
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    @PostMapping("/draft")
    @SaCheckLogin
    public ApiResponse<ProcessDefinitionDetailResponse> saveDraft(@Valid @RequestBody ProcessDslPayload payload) {
        return ApiResponse.success(processDefinitionService.saveDraft(payload));
    }

    @PostMapping("/publish")
    @SaCheckLogin
    public ApiResponse<ProcessDefinitionDetailResponse> publish(@Valid @RequestBody ProcessDslPayload payload) {
        return ApiResponse.success(processDefinitionService.publish(payload));
    }

    @GetMapping("/{processDefinitionId}")
    @SaCheckLogin
    public ApiResponse<ProcessDefinitionDetailResponse> detail(@PathVariable String processDefinitionId) {
        return ApiResponse.success(processDefinitionService.detail(processDefinitionId));
    }

    @PostMapping("/page")
    @SaCheckLogin
    public ApiResponse<PageResponse<ProcessDefinitionListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(processDefinitionService.page(request));
    }
}
