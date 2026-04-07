package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmObjectRevisionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM 深度对象版本持久化接口。
 */
@Mapper
public interface PlmObjectRevisionMapper {

    @Insert("""
            INSERT INTO plm_object_revision (
              id,
              object_id,
              revision_code,
              version_label,
              version_status,
              checksum,
              summary_json,
              snapshot_json,
              created_by,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{objectId},
              #{revisionCode},
              #{versionLabel},
              #{versionStatus},
              #{checksum},
              #{summaryJson},
              #{snapshotJson},
              #{createdBy},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmObjectRevisionRecord record);

    @Update("""
            UPDATE plm_object_revision
            SET version_label = #{versionLabel},
                version_status = #{versionStatus},
                checksum = #{checksum},
                summary_json = #{summaryJson},
                snapshot_json = #{snapshotJson},
                created_by = #{createdBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(PlmObjectRevisionRecord record);

    @Select("""
            SELECT
              id,
              object_id AS objectId,
              revision_code AS revisionCode,
              version_label AS versionLabel,
              version_status AS versionStatus,
              checksum,
              summary_json AS summaryJson,
              snapshot_json AS snapshotJson,
              created_by AS createdBy,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_object_revision
            WHERE object_id = #{objectId}
              AND revision_code = #{revisionCode}
            """)
    PlmObjectRevisionRecord selectByObjectIdAndRevisionCode(
            @Param("objectId") String objectId,
            @Param("revisionCode") String revisionCode
    );

    @Select("""
            SELECT
              id,
              object_id AS objectId,
              revision_code AS revisionCode,
              version_label AS versionLabel,
              version_status AS versionStatus,
              checksum,
              summary_json AS summaryJson,
              snapshot_json AS snapshotJson,
              created_by AS createdBy,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_object_revision
            WHERE id = #{id}
            """)
    PlmObjectRevisionRecord selectById(@Param("id") String id);
}
