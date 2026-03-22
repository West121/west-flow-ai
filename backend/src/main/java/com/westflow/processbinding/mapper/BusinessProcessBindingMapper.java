package com.westflow.processbinding.mapper;

import com.westflow.processbinding.model.BusinessProcessBindingRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
              priority,
              created_at AS createdAt,
              updated_at AS updatedAt
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

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              scene_code AS sceneCode,
              process_key AS processKey,
              process_definition_id AS processDefinitionId,
              enabled,
              priority,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_business_process_binding
            ORDER BY business_type ASC, scene_code ASC, priority DESC, updated_at DESC, created_at DESC
            """)
    List<BusinessProcessBindingRecord> selectAll();

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              scene_code AS sceneCode,
              process_key AS processKey,
              process_definition_id AS processDefinitionId,
              enabled,
              priority,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_business_process_binding
            WHERE id = #{bindingId}
            """)
    BusinessProcessBindingRecord selectById(@Param("bindingId") String bindingId);

    @Select("""
            SELECT COUNT(1)
            FROM wf_business_process_binding
            WHERE business_type = #{businessType}
              AND scene_code = #{sceneCode}
              AND id <> COALESCE(#{excludeBindingId}, '')
            """)
    boolean existsByBusinessScene(
            @Param("businessType") String businessType,
            @Param("sceneCode") String sceneCode,
            @Param("excludeBindingId") String excludeBindingId
    );

    @Insert("""
            INSERT INTO wf_business_process_binding (
              id,
              business_type,
              scene_code,
              process_key,
              process_definition_id,
              enabled,
              priority,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{businessType},
              #{sceneCode},
              #{processKey},
              #{processDefinitionId},
              #{enabled},
              #{priority},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(BusinessProcessBindingRecord record);

    @Update("""
            UPDATE wf_business_process_binding
            SET business_type = #{businessType},
                scene_code = #{sceneCode},
                process_key = #{processKey},
                process_definition_id = #{processDefinitionId},
                enabled = #{enabled},
                priority = #{priority},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(BusinessProcessBindingRecord record);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              scene_code AS sceneCode,
              process_key AS processKey,
              process_definition_id AS processDefinitionId,
              enabled,
              priority,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_business_process_binding
            WHERE process_definition_id = #{processDefinitionId}
            ORDER BY priority DESC, updated_at DESC
            """)
    List<BusinessProcessBindingRecord> selectByProcessDefinitionId(@Param("processDefinitionId") String processDefinitionId);
}
