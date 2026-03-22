package com.westflow.processbinding.mapper;

import com.westflow.processbinding.model.BusinessProcessBindingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
// 业务流程绑定表的查询接口。
public interface BusinessProcessBindingMapper {

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              scene_code AS sceneCode,
              process_key AS processKey,
              process_definition_id AS processDefinitionId,
              enabled,
              priority
            FROM wf_business_process_binding
            WHERE business_type = #{businessType}
              AND scene_code = #{sceneCode}
              AND enabled = TRUE
            ORDER BY priority DESC, updated_at DESC, created_at DESC
            LIMIT 1
            """)
    BusinessProcessBindingRecord selectEnabledBinding(
            @Param("businessType") String businessType,
            @Param("sceneCode") String sceneCode
    );
}
