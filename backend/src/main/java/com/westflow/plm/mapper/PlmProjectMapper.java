package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmProjectDetailResponse;
import com.westflow.plm.api.PlmProjectListItemResponse;
import com.westflow.plm.model.PlmProjectRecord;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM 项目主表持久化接口。
 */
@Mapper
public interface PlmProjectMapper {

    @Insert("""
            INSERT INTO plm_project (
              id,
              project_no,
              project_code,
              project_name,
              project_type,
              project_level,
              status,
              phase_code,
              owner_user_id,
              sponsor_user_id,
              domain_code,
              priority_level,
              target_release,
              start_date,
              target_end_date,
              actual_end_date,
              summary,
              business_goal,
              risk_summary,
              creator_user_id,
              initiation_status,
              initiation_scene_code,
              initiation_process_instance_id,
              initiation_submitted_at,
              initiation_decided_at,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{projectNo},
              #{projectCode},
              #{projectName},
              #{projectType},
              #{projectLevel},
              #{status},
              #{phaseCode},
              #{ownerUserId},
              #{sponsorUserId},
              #{domainCode},
              #{priorityLevel},
              #{targetRelease},
              #{startDate},
              #{targetEndDate},
              #{actualEndDate},
              #{summary},
              #{businessGoal},
              #{riskSummary},
              #{creatorUserId},
              #{initiationStatus},
              #{initiationSceneCode},
              #{initiationProcessInstanceId},
              #{initiationSubmittedAt},
              #{initiationDecidedAt},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmProjectRecord record);

    @Update("""
            UPDATE plm_project
            SET project_name = #{projectName},
                project_type = #{projectType},
                project_level = #{projectLevel},
                status = #{status},
                phase_code = #{phaseCode},
                owner_user_id = #{ownerUserId},
                sponsor_user_id = #{sponsorUserId},
                domain_code = #{domainCode},
                priority_level = #{priorityLevel},
                target_release = #{targetRelease},
                start_date = #{startDate},
                target_end_date = #{targetEndDate},
                actual_end_date = #{actualEndDate},
                summary = #{summary},
                business_goal = #{businessGoal},
                risk_summary = #{riskSummary},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(PlmProjectRecord record);

    @Update("""
            UPDATE plm_project
            SET initiation_status = #{initiationStatus},
                initiation_scene_code = #{initiationSceneCode},
                initiation_process_instance_id = #{initiationProcessInstanceId},
                initiation_submitted_at = #{initiationSubmittedAt},
                initiation_decided_at = #{initiationDecidedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{projectId}
            """)
    int updateInitiation(
            @Param("projectId") String projectId,
            @Param("initiationStatus") String initiationStatus,
            @Param("initiationSceneCode") String initiationSceneCode,
            @Param("initiationProcessInstanceId") String initiationProcessInstanceId,
            @Param("initiationSubmittedAt") java.time.LocalDateTime initiationSubmittedAt,
            @Param("initiationDecidedAt") java.time.LocalDateTime initiationDecidedAt
    );

    @Update("""
            UPDATE plm_project
            SET phase_code = #{phaseCode},
                status = #{status},
                actual_end_date = #{actualEndDate},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{projectId}
            """)
    int updatePhase(
            @Param("projectId") String projectId,
            @Param("phaseCode") String phaseCode,
            @Param("status") String status,
            @Param("actualEndDate") LocalDate actualEndDate
    );

    @Select("""
            SELECT
              id,
              project_no AS projectNo,
              project_code AS projectCode,
              project_name AS projectName,
              project_type AS projectType,
              project_level AS projectLevel,
              status,
              phase_code AS phaseCode,
              owner_user_id AS ownerUserId,
              sponsor_user_id AS sponsorUserId,
              domain_code AS domainCode,
              priority_level AS priorityLevel,
              target_release AS targetRelease,
              start_date AS startDate,
              target_end_date AS targetEndDate,
              actual_end_date AS actualEndDate,
              summary,
              business_goal AS businessGoal,
              risk_summary AS riskSummary,
              creator_user_id AS creatorUserId,
              initiation_status AS initiationStatus,
              initiation_scene_code AS initiationSceneCode,
              initiation_process_instance_id AS initiationProcessInstanceId,
              initiation_submitted_at AS initiationSubmittedAt,
              initiation_decided_at AS initiationDecidedAt
            FROM plm_project
            WHERE id = #{projectId}
            """)
    PlmProjectRecord selectRecord(@Param("projectId") String projectId);

    @Select("""
            SELECT
              p.id AS projectId,
              p.project_no AS projectNo,
              p.project_code AS projectCode,
              p.project_name AS projectName,
              p.project_type AS projectType,
              p.project_level AS projectLevel,
              p.status,
              p.phase_code AS phaseCode,
              p.owner_user_id AS ownerUserId,
              owner.display_name AS ownerDisplayName,
              p.sponsor_user_id AS sponsorUserId,
              sponsor.display_name AS sponsorDisplayName,
              p.domain_code AS domainCode,
              p.priority_level AS priorityLevel,
              p.target_release AS targetRelease,
              p.start_date AS startDate,
              p.target_end_date AS targetEndDate,
              p.actual_end_date AS actualEndDate,
              p.summary,
              p.business_goal AS businessGoal,
              p.risk_summary AS riskSummary,
              p.creator_user_id AS creatorUserId,
              creator.display_name AS creatorDisplayName,
              p.initiation_status AS initiationStatus,
              p.initiation_scene_code AS initiationSceneCode,
              p.initiation_process_instance_id AS initiationProcessInstanceId,
              p.initiation_submitted_at AS initiationSubmittedAt,
              p.initiation_decided_at AS initiationDecidedAt,
              p.created_at AS createdAt,
              p.updated_at AS updatedAt,
              NULL AS members,
              NULL AS milestones,
              NULL AS links,
              NULL AS stageEvents,
              NULL AS dashboard
            FROM plm_project p
            LEFT JOIN wf_user owner ON owner.id = p.owner_user_id
            LEFT JOIN wf_user sponsor ON sponsor.id = p.sponsor_user_id
            LEFT JOIN wf_user creator ON creator.id = p.creator_user_id
            WHERE p.id = #{projectId}
            """)
    PlmProjectDetailResponse selectDetail(@Param("projectId") String projectId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM plm_project p",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(p.project_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(p.project_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(p.project_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(COALESCE(p.summary, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND p.status = #{status}",
            "  </if>",
            "  <if test='phaseCode != null and phaseCode != \"\"'>",
            "    AND p.phase_code = #{phaseCode}",
            "  </if>",
            "  <if test='ownerUserId != null and ownerUserId != \"\"'>",
            "    AND p.owner_user_id = #{ownerUserId}",
            "  </if>",
            "  <if test='domainCode != null and domainCode != \"\"'>",
            "    AND p.domain_code = #{domainCode}",
            "  </if>",
            "  <if test='targetEndDateFrom != null'>",
            "    AND p.target_end_date <![CDATA[>=]]> #{targetEndDateFrom}",
            "  </if>",
            "  <if test='targetEndDateTo != null'>",
            "    AND p.target_end_date <![CDATA[<=]]> #{targetEndDateTo}",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("phaseCode") String phaseCode,
            @Param("ownerUserId") String ownerUserId,
            @Param("domainCode") String domainCode,
            @Param("targetEndDateFrom") LocalDate targetEndDateFrom,
            @Param("targetEndDateTo") LocalDate targetEndDateTo
    );

    @Select({
            "<script>",
            "SELECT",
            "  p.id AS projectId,",
            "  p.project_no AS projectNo,",
            "  p.project_code AS projectCode,",
            "  p.project_name AS projectName,",
            "  p.project_type AS projectType,",
            "  p.project_level AS projectLevel,",
            "  p.status,",
            "  p.phase_code AS phaseCode,",
            "  p.owner_user_id AS ownerUserId,",
            "  owner.display_name AS ownerDisplayName,",
            "  p.sponsor_user_id AS sponsorUserId,",
            "  sponsor.display_name AS sponsorDisplayName,",
            "  p.domain_code AS domainCode,",
            "  p.priority_level AS priorityLevel,",
            "  p.target_release AS targetRelease,",
            "  p.start_date AS startDate,",
            "  p.target_end_date AS targetEndDate,",
            "  p.actual_end_date AS actualEndDate,",
            "  p.summary AS summary,",
            "  p.creator_user_id AS creatorUserId,",
            "  creator.display_name AS creatorDisplayName,",
            "  p.initiation_status AS initiationStatus,",
            "  p.initiation_process_instance_id AS initiationProcessInstanceId,",
            "  p.initiation_submitted_at AS initiationSubmittedAt,",
            "  p.initiation_decided_at AS initiationDecidedAt,",
            "  p.created_at AS createdAt,",
            "  p.updated_at AS updatedAt,",
            "  (SELECT COUNT(1) FROM plm_project_member pm WHERE pm.project_id = p.id) AS memberCount,",
            "  (SELECT COUNT(1) FROM plm_project_milestone mm WHERE mm.project_id = p.id) AS milestoneCount,",
            "  (SELECT COUNT(1) FROM plm_project_link pl WHERE pl.project_id = p.id) AS linkCount",
            "FROM plm_project p",
            "LEFT JOIN wf_user owner ON owner.id = p.owner_user_id",
            "LEFT JOIN wf_user sponsor ON sponsor.id = p.sponsor_user_id",
            "LEFT JOIN wf_user creator ON creator.id = p.creator_user_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(p.project_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(p.project_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(p.project_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "      OR LOWER(COALESCE(p.summary, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND p.status = #{status}",
            "  </if>",
            "  <if test='phaseCode != null and phaseCode != \"\"'>",
            "    AND p.phase_code = #{phaseCode}",
            "  </if>",
            "  <if test='ownerUserId != null and ownerUserId != \"\"'>",
            "    AND p.owner_user_id = #{ownerUserId}",
            "  </if>",
            "  <if test='domainCode != null and domainCode != \"\"'>",
            "    AND p.domain_code = #{domainCode}",
            "  </if>",
            "  <if test='targetEndDateFrom != null'>",
            "    AND p.target_end_date <![CDATA[>=]]> #{targetEndDateFrom}",
            "  </if>",
            "  <if test='targetEndDateTo != null'>",
            "    AND p.target_end_date <![CDATA[<=]]> #{targetEndDateTo}",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}, p.updated_at DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<PlmProjectListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("phaseCode") String phaseCode,
            @Param("ownerUserId") String ownerUserId,
            @Param("domainCode") String domainCode,
            @Param("targetEndDateFrom") LocalDate targetEndDateFrom,
            @Param("targetEndDateTo") LocalDate targetEndDateTo,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );
}
