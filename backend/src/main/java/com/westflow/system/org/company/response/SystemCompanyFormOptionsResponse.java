package com.westflow.system.org.company.response;

import java.util.List;

/**
 * 公司表单下拉选项响应。
 */
public record SystemCompanyFormOptionsResponse(
        List<CompanyOption> companies
) {

    public record CompanyOption(
            String id,
            String name,
            boolean enabled
    ) {
    }
}
