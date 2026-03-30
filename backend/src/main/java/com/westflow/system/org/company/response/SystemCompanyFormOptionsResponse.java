package com.westflow.system.org.company.response;

import java.util.List;

/**
 * 公司表单下拉选项响应。
 */
public record SystemCompanyFormOptionsResponse(
        // 公司选项列表。
        List<CompanyOption> companies
) {

    public record CompanyOption(
            // 公司主键。
            String id,
            // 公司名称。
            String name,
            // 是否启用。
            boolean enabled
    ) {
    }
}
