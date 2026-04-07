package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmRevisionDiffRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PLM 版本差异持久化接口。
 */
@Mapper
public interface PlmRevisionDiffMapper {

    @Delete("""
            DELETE FROM plm_revision_diff
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            """)
    int deleteByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );

    @Insert({
            "<script>",
            "INSERT INTO plm_revision_diff (",
            "  id,",
            "  business_type,",
            "  bill_id,",
            "  object_id,",
            "  before_revision_id,",
            "  after_revision_id,",
            "  diff_kind,",
            "  diff_summary,",
            "  diff_payload_json,",
            "  created_at,",
            "  updated_at",
            ") VALUES",
            "<foreach collection='records' item='record' separator=','>",
            "  (",
            "    #{record.id},",
            "    #{record.businessType},",
            "    #{record.billId},",
            "    #{record.objectId},",
            "    #{record.beforeRevisionId},",
            "    #{record.afterRevisionId},",
            "    #{record.diffKind},",
            "    #{record.diffSummary},",
            "    #{record.diffPayloadJson},",
            "    CURRENT_TIMESTAMP,",
            "    CURRENT_TIMESTAMP",
            "  )",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("records") List<PlmRevisionDiffRecord> records);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              object_id AS objectId,
              before_revision_id AS beforeRevisionId,
              after_revision_id AS afterRevisionId,
              diff_kind AS diffKind,
              diff_summary AS diffSummary,
              diff_payload_json AS diffPayloadJson,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_revision_diff
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            ORDER BY created_at ASC, id ASC
            """)
    List<PlmRevisionDiffRecord> selectByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );
}
