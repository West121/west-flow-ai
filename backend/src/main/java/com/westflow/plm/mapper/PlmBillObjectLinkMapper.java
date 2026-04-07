package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmBillObjectLinkRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PLM 单据对象关联持久化接口。
 */
@Mapper
public interface PlmBillObjectLinkMapper {

    @Delete("""
            DELETE FROM plm_bill_object_link
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            """)
    int deleteByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );

    @Insert({
            "<script>",
            "INSERT INTO plm_bill_object_link (",
            "  id,",
            "  business_type,",
            "  bill_id,",
            "  object_id,",
            "  object_revision_id,",
            "  role_code,",
            "  change_action,",
            "  before_revision_code,",
            "  after_revision_code,",
            "  remark,",
            "  sort_order,",
            "  created_at,",
            "  updated_at",
            ") VALUES",
            "<foreach collection='records' item='record' separator=','>",
            "  (",
            "    #{record.id},",
            "    #{record.businessType},",
            "    #{record.billId},",
            "    #{record.objectId},",
            "    #{record.objectRevisionId},",
            "    #{record.roleCode},",
            "    #{record.changeAction},",
            "    #{record.beforeRevisionCode},",
            "    #{record.afterRevisionCode},",
            "    #{record.remark},",
            "    #{record.sortOrder},",
            "    CURRENT_TIMESTAMP,",
            "    CURRENT_TIMESTAMP",
            "  )",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("records") List<PlmBillObjectLinkRecord> records);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              object_id AS objectId,
              object_revision_id AS objectRevisionId,
              role_code AS roleCode,
              change_action AS changeAction,
              before_revision_code AS beforeRevisionCode,
              after_revision_code AS afterRevisionCode,
              remark,
              sort_order AS sortOrder,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_bill_object_link
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            ORDER BY sort_order ASC, created_at ASC, id ASC
            """)
    List<PlmBillObjectLinkRecord> selectByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );
}
