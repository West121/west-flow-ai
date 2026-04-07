package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmDashboardRecentBillResponse;
import com.westflow.plm.api.PlmMaterialChangeBillDetailResponse;
import com.westflow.plm.api.PlmMaterialChangeBillListItemResponse;
import com.westflow.plm.model.PlmBillLifecycleRecord;
import com.westflow.plm.model.PlmDashboardStatsRecord;
import com.westflow.plm.model.PlmMaterialChangeBillRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM 物料主数据变更申请持久化接口。
 */
@Mapper
public interface PlmMaterialChangeBillMapper {

    @Insert("""
            INSERT INTO plm_material_change (
              id,
              bill_no,
              scene_code,
              material_code,
              material_name,
              change_reason,
              change_type,
              specification_change,
              old_value,
              new_value,
              uom,
              affected_systems_text,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{materialCode},
              #{materialName},
              #{changeReason},
              #{changeType},
              #{specificationChange},
              #{oldValue},
              #{newValue},
              #{uom},
              #{affectedSystemsText},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmMaterialChangeBillRecord record);

    @Update("""
            UPDATE plm_material_change
            SET scene_code = #{sceneCode},
                material_code = #{materialCode},
                material_name = #{materialName},
                change_reason = #{changeReason},
                change_type = #{changeType},
                specification_change = #{specificationChange},
                old_value = #{oldValue},
                new_value = #{newValue},
                uom = #{uom},
                affected_systems_text = #{affectedSystemsText},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDraft(PlmMaterialChangeBillRecord record);

    @Update("""
            UPDATE plm_material_change
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
            UPDATE plm_material_change
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int updateStatus(@Param("billId") String billId, @Param("status") String status);

    @Update("""
            UPDATE plm_material_change
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
            UPDATE plm_material_change
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
            UPDATE plm_material_change
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
            FROM plm_material_change
            WHERE id = #{billId}
            """)
    PlmBillLifecycleRecord selectLifecycle(@Param("billId") String billId);

    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              material_code AS materialCode,
              material_name AS materialName,
              change_reason AS changeReason,
              change_type AS changeType,
              specification_change AS specificationChange,
              old_value AS oldValue,
              new_value AS newValue,
              uom,
              affected_systems_text AS affectedSystemsText,
              process_instance_id AS processInstanceId,
              status,
              implementation_owner AS implementationOwner,
              implementation_summary AS implementationSummary,
              implementation_started_at AS implementationStartedAt,
              validation_owner AS validationOwner,
              validation_summary AS validationSummary,
              validated_at AS validatedAt,
              closed_by AS closedBy,
              closed_at AS closedAt,
              close_comment AS closeComment,
              CONCAT(
                COALESCE(change_type, '变更类型待补充'),
                ' · ',
                COALESCE(specification_change, '规格变更待补充'),
                ' · ',
                COALESCE(uom, '--')
              ) AS detailSummary,
              CONCAT(status, ' · 流程 ', COALESCE(process_instance_id, '待提交')) AS approvalSummary,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt,
              NULL AS affectedItems,
              NULL AS objectLinks,
              NULL AS revisionDiffs,
              NULL AS implementationTasks
            FROM plm_material_change
            WHERE id = #{billId}
            """)
    PlmMaterialChangeBillDetailResponse selectDetail(@Param("billId") String billId);

    @Select({
            "<script>",
            "SELECT",
            "  id AS billId,",
            "  bill_no AS billNo,",
            "  scene_code AS sceneCode,",
            "  material_code AS materialCode,",
            "  material_name AS materialName,",
            "  change_type AS changeType,",
            "  change_reason AS changeReason,",
            "  specification_change AS specificationChange,",
            "  uom,",
            "  process_instance_id AS processInstanceId,",
            "  status,",
            "  CONCAT(COALESCE(change_type, '变更类型待补充'), ' · ', COALESCE(specification_change, '规格变更待补充')) AS detailSummary,",
            "  CONCAT(status, ' · 流程 ', COALESCE(process_instance_id, '待提交')) AS approvalSummary,",
            "  creator_user_id AS creatorUserId,",
            "  created_at AS createdAt,",
            "  updated_at AS updatedAt",
            "FROM plm_material_change",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "    LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(material_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(material_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
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
    List<PlmMaterialChangeBillListItemResponse> selectPage(
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
            "FROM plm_material_change",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "    LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(material_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
            "    OR LOWER(material_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))",
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
            FROM plm_material_change
            WHERE creator_user_id = #{creatorUserId}
            """)
    PlmDashboardStatsRecord selectDashboardStats(@Param("creatorUserId") String creatorUserId);

    @Select("""
            SELECT
              'PLM_MATERIAL' AS businessType,
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              material_name AS title,
              status,
              process_instance_id AS processInstanceId,
              creator_user_id AS creatorUserId,
              CONCAT(COALESCE(change_type, '变更类型待补充'), ' · ', COALESCE(specification_change, '规格变更待补充')) AS detailSummary,
              updated_at AS updatedAt
            FROM plm_material_change
            WHERE creator_user_id = #{creatorUserId}
            ORDER BY updated_at DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<PlmDashboardRecentBillResponse> selectRecentByCreator(
            @Param("creatorUserId") String creatorUserId,
            @Param("limit") int limit
    );
}
