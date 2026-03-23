package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmMaterialChangeBillDetailResponse;
import com.westflow.plm.api.PlmMaterialChangeBillListItemResponse;
import com.westflow.plm.model.PlmMaterialChangeBillRecord;
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

    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              material_code AS materialCode,
              material_name AS materialName,
              change_reason AS changeReason,
              change_type AS changeType,
              process_instance_id AS processInstanceId,
              status
            FROM plm_material_change
            WHERE id = #{billId}
            """)
    PlmMaterialChangeBillDetailResponse selectDetail(@Param("billId") String billId);

    /**
     * 查询物料主数据变更分页列表。
     */
    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              material_code AS materialCode,
              material_name AS materialName,
              change_type AS changeType,
              change_reason AS changeReason,
              process_instance_id AS processInstanceId,
              status,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_material_change
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(material_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(material_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    java.util.List<PlmMaterialChangeBillListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 统计物料主数据变更数量。
     */
    @Select("""
            SELECT COUNT(*)
            FROM plm_material_change
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(material_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(material_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            """)
    long countPage(@Param("keyword") String keyword);
}
