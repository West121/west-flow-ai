package com.westflow.workflowadmin.mapper;

import com.westflow.workflowadmin.model.ApprovalOpinionConfigRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 审批意见配置表映射。
 */
@Mapper
public interface ApprovalOpinionConfigMapper {

    @Select("""
            SELECT
              id AS configId,
              config_code AS configCode,
              config_name AS configName,
              enabled,
              quick_opinions_json AS quickOpinionsJson,
              toolbar_actions_json AS toolbarActionsJson,
              button_strategies_json AS buttonStrategiesJson,
              remark,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_approval_opinion_config
            ORDER BY updated_at DESC, created_at DESC, id DESC
            """)
    List<ApprovalOpinionConfigRecord> selectAll();

    @Select("""
            SELECT
              id AS configId,
              config_code AS configCode,
              config_name AS configName,
              enabled,
              quick_opinions_json AS quickOpinionsJson,
              toolbar_actions_json AS toolbarActionsJson,
              button_strategies_json AS buttonStrategiesJson,
              remark,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM wf_approval_opinion_config
            WHERE id = #{configId}
            """)
    ApprovalOpinionConfigRecord selectById(@Param("configId") String configId);

    @Select("""
            SELECT COUNT(1)
            FROM wf_approval_opinion_config
            WHERE config_code = #{configCode}
              AND id <> COALESCE(#{excludeConfigId}, '')
            """)
    boolean existsByCode(
            @Param("configCode") String configCode,
            @Param("excludeConfigId") String excludeConfigId
    );

    @Insert("""
            INSERT INTO wf_approval_opinion_config (
              id,
              config_code,
              config_name,
              enabled,
              quick_opinions_json,
              toolbar_actions_json,
              button_strategies_json,
              remark,
              created_at,
              updated_at
            ) VALUES (
              #{configId},
              #{configCode},
              #{configName},
              #{enabled},
              #{quickOpinionsJson},
              #{toolbarActionsJson},
              #{buttonStrategiesJson},
              #{remark},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insert(ApprovalOpinionConfigRecord record);

    @Update("""
            UPDATE wf_approval_opinion_config
            SET config_code = #{configCode},
                config_name = #{configName},
                enabled = #{enabled},
                quick_opinions_json = #{quickOpinionsJson},
                toolbar_actions_json = #{toolbarActionsJson},
                button_strategies_json = #{buttonStrategiesJson},
                remark = #{remark},
                updated_at = #{updatedAt}
            WHERE id = #{configId}
            """)
    int update(ApprovalOpinionConfigRecord record);
}
