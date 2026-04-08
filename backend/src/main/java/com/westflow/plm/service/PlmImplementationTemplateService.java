package com.westflow.plm.service;

import com.westflow.plm.api.PlmImplementationDependencyResponse;
import com.westflow.plm.api.PlmImplementationTemplateResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 实施模板与依赖读取服务。
 */
@Service
@RequiredArgsConstructor
public class PlmImplementationTemplateService {

    private final PlmImplementationTaskService plmImplementationTaskService;

    public List<PlmImplementationTemplateResponse> listTemplates(String businessType, String sceneCode) {
        return plmImplementationTaskService.listTemplates(businessType, sceneCode);
    }

    public List<PlmImplementationDependencyResponse> listDependencies(String businessType, String billId) {
        return plmImplementationTaskService.listDependencies(businessType, billId);
    }
}
