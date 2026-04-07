package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmAffectedItemRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PLM 受影响对象持久化接口。
 */
@Mapper
public interface PlmAffectedItemMapper {

    @Delete("""
            DELETE FROM plm_bill_affected_item
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            """)
    int deleteByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );

    @Insert({
            "<script>",
            "INSERT INTO plm_bill_affected_item (",
            "  id,",
            "  business_type,",
            "  bill_id,",
            "  item_type,",
            "  item_code,",
            "  item_name,",
            "  before_version,",
            "  after_version,",
            "  change_action,",
            "  owner_user_id,",
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
            "    #{record.itemType},",
            "    #{record.itemCode},",
            "    #{record.itemName},",
            "    #{record.beforeVersion},",
            "    #{record.afterVersion},",
            "    #{record.changeAction},",
            "    #{record.ownerUserId},",
            "    #{record.remark},",
            "    #{record.sortOrder},",
            "    CURRENT_TIMESTAMP,",
            "    CURRENT_TIMESTAMP",
            "  )",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("records") List<PlmAffectedItemRecord> records);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              item_type AS itemType,
              item_code AS itemCode,
              item_name AS itemName,
              before_version AS beforeVersion,
              after_version AS afterVersion,
              change_action AS changeAction,
              owner_user_id AS ownerUserId,
              remark,
              sort_order AS sortOrder
            FROM plm_bill_affected_item
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            ORDER BY sort_order ASC, created_at ASC, id ASC
            """)
    List<PlmAffectedItemRecord> selectByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );
}
