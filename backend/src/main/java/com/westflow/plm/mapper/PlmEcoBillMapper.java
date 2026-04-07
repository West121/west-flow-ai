package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmDashboardRecentBillResponse;
import com.westflow.plm.api.PlmEcoBillDetailResponse;
import com.westflow.plm.api.PlmEcoBillListItemResponse;
import com.westflow.plm.model.PlmBillLifecycleRecord;
import com.westflow.plm.model.PlmDashboardStatsRecord;
import com.westflow.plm.model.PlmEcoBillRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM ECO 变更执行持久化接口。
 */
@Mapper
public interface PlmEcoBillMapper {

    @Insert("""
            INSERT INTO plm_eco_execution (
              id,
              bill_no,
              scene_code,
              execution_title,
              execution_plan,
              effective_date,
              change_reason,
              implementation_owner,
              target_version,
              rollout_scope,
              validation_plan,
              rollback_plan,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{executionTitle},
              #{executionPlan},
              #{effectiveDate},
              #{changeReason},
              #{implementationOwner},
              #{targetVersion},
              #{rolloutScope},
              #{validationPlan},
              #{rollbackPlan},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmEcoBillRecord record);

    @Update("""
            UPDATE plm_eco_execution
            SET scene_code = #{sceneCode},
                execution_title = #{executionTitle},
                execution_plan = #{executionPlan},
                effective_date = #{effectiveDate},
                change_reason = #{changeReason},
                implementation_owner = #{implementationOwner},
                target_version = #{targetVersion},
                rollout_scope = #{rolloutScope},
                validation_plan = #{validationPlan},
                rollback_plan = #{rollbackPlan},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDraft(PlmEcoBillRecord record);

    @Update("""
            UPDATE plm_eco_execution
            SET process_instance_id = #{processInstanceId},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int updateProcessLink(
            @Param("billId") String billId,
            @Param("processInstanceId") String processInstanceId,
            @Param("status") String status
    );

    @Update("""
            UPDATE plm_eco_execution
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int updateStatus(@Param("billId") String billId, @Param("status") String status);

    @Update("""
            UPDATE plm_eco_execution
            SET implementation_owner = #{implementationOwner},
                implementation_summary = #{implementationSummary},
                implementation_started_at = CURRENT_TIMESTAMP,
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int startImplementation(
            @Param("billId") String billId,
            @Param("implementationOwner") String implementationOwner,
            @Param("implementationSummary") String implementationSummary,
            @Param("status") String status
    );

    @Update("""
            UPDATE plm_eco_execution
            SET validation_owner = #{validationOwner},
                validation_summary = #{validationSummary},
                validated_at = CURRENT_TIMESTAMP,
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int submitValidation(
            @Param("billId") String billId,
            @Param("validationOwner") String validationOwner,
            @Param("validationSummary") String validationSummary,
            @Param("status") String status
    );

    @Update("""
            UPDATE plm_eco_execution
            SET closed_by = #{closedBy},
                close_comment = #{closeComment},
                closed_at = CURRENT_TIMESTAMP,
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int closeBill(
            @Param("billId") String billId,
            @Param("closedBy") String closedBy,
            @Param("closeComment") String closeComment,
            @Param("status") String status
    );

    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              process_instance_id AS processInstanceId,
              status,
              creator_user_id AS creatorUserId
            FROM plm_eco_execution
            WHERE id = #{billId}
            """)
    PlmBillLifecycleRecord selectLifecycle(@Param("billId") String billId);

    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              execution_title AS executionTitle,
              execution_plan AS executionPlan,
              effective_date AS effectiveDate,
              change_reason AS changeReason,
              implementation_owner AS implementationOwner,
              target_version AS targetVersion,
              rollout_scope AS rolloutScope,
              validation_plan AS validationPlan,
              rollback_plan AS rollbackPlan,
              process_instance_id AS processInstanceId,
              status,
              implementation_summary AS implementationSummary,
              implementation_started_at AS implementationStartedAt,
              validation_owner AS validationOwner,
              validation_summary AS validationSummary,
              validated_at AS validatedAt,
              closed_by AS closedBy,
              closed_at AS closedAt,
              close_comment AS closeComment,
              CONCAT(
                COALESCE(target_version, '目标版本待补充'),
                ' · ',
                COALESCE(implementation_owner, '实施负责人待补充'),
                ' · 生效 ',
                COALESCE(CAST(effective_date AS VARCHAR), '--')
              ) AS detailSummary,
              CONCAT(status, ' · 流程 ', COALESCE(process_instance_id, '待提交')) AS approvalSummary,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt,
              NULL AS affectedItems,
              NULL AS objectLinks,
              NULL AS revisionDiffs,
              NULL AS implementationTasks
            FROM plm_eco_execution
            WHERE id = #{billId}
            """)
    PlmEcoBillDetailResponse selectDetail(@Param("billId") String billId);

    @Select({
            "<script>",
            "SELECT",
            "  id AS billId,",
            "  bill_no AS billNo,",
            "  scene_code AS sceneCode,",
            "  execution_title AS executionTitle,",
            "  effective_date AS effectiveDate,",
            "  change_reason AS changeReason,",
            "  implementation_owner AS implementationOwner,",
            "  target_version AS targetVersion,",
            "  process_instance_id AS processInstanceId,",
            "  status,",
            "  CONCAT(COALESCE(target_version, '目标版本待补充'), ' · ', COALESCE(implementation_owner, '实施负责人待补充')) AS detailSummary,",
            "  CONCAT(status, ' · 流程 ', COALESCE(process_instance_id, '待提交')) AS approvalSummary,",
            "  creator_user_id AS creatorUserId,",
            "  created_at AS createdAt,",
            "  updated_at AS updatedAt",
            "FROM plm_eco_execution",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "    LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(execution_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(execution_plan) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(change_reason) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "  )",
            "</if>",
            "<if test='sceneCode != null and sceneCode != \"\"'>",
            "  AND scene_code = #{sceneCode}",
            "</if>",
            "<if test='creatorUserId != null and creatorUserId != \"\"'>",
            "  AND creator_user_id = #{creatorUserId}",
            "</if>",
            "<if test='statuses != null and statuses.size() > 0'>",
            "  AND status IN",
            "  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>",
            "    #{status}",
            "  </foreach>",
            "</if>",
            "<if test='createdAtFrom != null'>",
            "  AND created_at <![CDATA[>=]]> #{createdAtFrom}",
            "</if>",
            "<if test='createdAtTo != null'>",
            "  AND created_at <![CDATA[<=]]> #{createdAtTo}",
            "</if>",
            "<if test='updatedAtFrom != null'>",
            "  AND updated_at <![CDATA[>=]]> #{updatedAtFrom}",
            "</if>",
            "<if test='updatedAtTo != null'>",
            "  AND updated_at <![CDATA[<=]]> #{updatedAtTo}",
            "</if>",
            "ORDER BY updated_at DESC, created_at DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<PlmEcoBillListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("sceneCode") String sceneCode,
            @Param("creatorUserId") String creatorUserId,
            @Param("statuses") List<String> statuses,
            @Param("createdAtFrom") LocalDateTime createdAtFrom,
            @Param("createdAtTo") LocalDateTime createdAtTo,
            @Param("updatedAtFrom") LocalDateTime updatedAtFrom,
            @Param("updatedAtTo") LocalDateTime updatedAtTo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM plm_eco_execution",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "    LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(execution_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(execution_plan) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(change_reason) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "  )",
            "</if>",
            "<if test='sceneCode != null and sceneCode != \"\"'>",
            "  AND scene_code = #{sceneCode}",
            "</if>",
            "<if test='creatorUserId != null and creatorUserId != \"\"'>",
            "  AND creator_user_id = #{creatorUserId}",
            "</if>",
            "<if test='statuses != null and statuses.size() > 0'>",
            "  AND status IN",
            "  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>",
            "    #{status}",
            "  </foreach>",
            "</if>",
            "<if test='createdAtFrom != null'>",
            "  AND created_at <![CDATA[>=]]> #{createdAtFrom}",
            "</if>",
            "<if test='createdAtTo != null'>",
            "  AND created_at <![CDATA[<=]]> #{createdAtTo}",
            "</if>",
            "<if test='updatedAtFrom != null'>",
            "  AND updated_at <![CDATA[>=]]> #{updatedAtFrom}",
            "</if>",
            "<if test='updatedAtTo != null'>",
            "  AND updated_at <![CDATA[<=]]> #{updatedAtTo}",
            "</if>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("sceneCode") String sceneCode,
            @Param("creatorUserId") String creatorUserId,
            @Param("statuses") List<String> statuses,
            @Param("createdAtFrom") LocalDateTime createdAtFrom,
            @Param("createdAtTo") LocalDateTime createdAtTo,
            @Param("updatedAtFrom") LocalDateTime updatedAtFrom,
            @Param("updatedAtTo") LocalDateTime updatedAtTo
    );

    @Select("""
            SELECT
              COUNT(*) AS totalCount,
              COALESCE(SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draftCount,
              COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS runningCount,
              COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedCount,
              COALESCE(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejectedCount,
              COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount
            FROM plm_eco_execution
            WHERE creator_user_id = #{creatorUserId}
            """)
    PlmDashboardStatsRecord selectDashboardStats(@Param("creatorUserId") String creatorUserId);

    @Select("""
            SELECT
              'PLM_ECO' AS businessType,
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              execution_title AS title,
              status,
              process_instance_id AS processInstanceId,
              creator_user_id AS creatorUserId,
              CONCAT(COALESCE(target_version, '目标版本待补充'), ' · ', COALESCE(implementation_owner, '实施负责人待补充')) AS detailSummary,
              updated_at AS updatedAt
            FROM plm_eco_execution
            WHERE creator_user_id = #{creatorUserId}
            ORDER BY updated_at DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<PlmDashboardRecentBillResponse> selectRecentByCreator(
            @Param("creatorUserId") String creatorUserId,
            @Param("limit") int limit
    );
}
