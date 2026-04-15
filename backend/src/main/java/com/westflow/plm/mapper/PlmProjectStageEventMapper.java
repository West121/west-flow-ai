package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmProjectStageEventResponse;
import com.westflow.plm.model.PlmProjectStageEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目阶段事件持久化接口。
 */
@Mapper
public interface PlmProjectStageEventMapper {

    @Insert("""
            INSERT INTO plm_project_stage_event (
              id,
              project_id,
              from_phase_code,
              to_phase_code,
              action_code,
              comment,
              changed_by,
              changed_at,
              created_at
            ) VALUES (
              #{id},
              #{projectId},
              #{fromPhaseCode},
              #{toPhaseCode},
              #{actionCode},
              #{comment},
              #{changedBy},
              #{changedAt},
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmProjectStageEventRecord record);

    @Select("""
            SELECT
              pse.id,
              pse.from_phase_code AS fromPhaseCode,
              pse.to_phase_code AS toPhaseCode,
              pse.action_code AS actionCode,
              pse.comment,
              pse.changed_by AS changedBy,
              u.display_name AS changedByDisplayName,
              pse.changed_at AS changedAt
            FROM plm_project_stage_event pse
            LEFT JOIN wf_user u ON u.id = pse.changed_by
            WHERE pse.project_id = #{projectId}
            ORDER BY pse.changed_at DESC, pse.created_at DESC
            """)
    List<PlmProjectStageEventResponse> selectByProjectId(@Param("projectId") String projectId);
}
