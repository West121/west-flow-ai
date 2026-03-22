package com.westflow.processdef.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-definitions")
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    public ProcessDefinitionController(ProcessDefinitionService processDefinitionService) {
        this.processDefinitionService = processDefinitionService;
    }

    @PostMapping("/publish")
    @SaCheckLogin
    public ApiResponse<PublishProcessDefinitionResponse> publish(@Valid @RequestBody ProcessDslPayload payload) {
        return ApiResponse.success(processDefinitionService.publish(payload));
    }

    @PostMapping("/page")
    @SaCheckLogin
    public ApiResponse<PageResponse<ProcessDefinitionListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(processDefinitionService.page(request));
    }
}
