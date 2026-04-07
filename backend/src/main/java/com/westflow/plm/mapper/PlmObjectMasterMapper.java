package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmObjectMasterRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM 深度对象主数据持久化接口。
 */
@Mapper
public interface PlmObjectMasterMapper {

    @Insert("""
            INSERT INTO plm_object_master (
              id,
              object_type,
              object_code,
              object_name,
              owner_user_id,
              domain_code,
              lifecycle_state,
              source_system,
              external_ref,
              latest_revision,
              latest_version_label,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{objectType},
              #{objectCode},
              #{objectName},
              #{ownerUserId},
              #{domainCode},
              #{lifecycleState},
              #{sourceSystem},
              #{externalRef},
              #{latestRevision},
              #{latestVersionLabel},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmObjectMasterRecord record);

    @Update("""
            UPDATE plm_object_master
            SET object_name = #{objectName},
                owner_user_id = #{ownerUserId},
                lifecycle_state = #{lifecycleState},
                source_system = #{sourceSystem},
                external_ref = #{externalRef},
                latest_revision = #{latestRevision},
                latest_version_label = #{latestVersionLabel},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateLatestState(PlmObjectMasterRecord record);

    @Select("""
            SELECT
              id,
              object_type AS objectType,
              object_code AS objectCode,
              object_name AS objectName,
              owner_user_id AS ownerUserId,
              domain_code AS domainCode,
              lifecycle_state AS lifecycleState,
              source_system AS sourceSystem,
              external_ref AS externalRef,
              latest_revision AS latestRevision,
              latest_version_label AS latestVersionLabel,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_object_master
            WHERE domain_code = #{domainCode}
              AND object_type = #{objectType}
              AND object_code = #{objectCode}
            """)
    PlmObjectMasterRecord selectByDomainTypeAndCode(
            @Param("domainCode") String domainCode,
            @Param("objectType") String objectType,
            @Param("objectCode") String objectCode
    );

    @Select("""
            SELECT
              id,
              object_type AS objectType,
              object_code AS objectCode,
              object_name AS objectName,
              owner_user_id AS ownerUserId,
              domain_code AS domainCode,
              lifecycle_state AS lifecycleState,
              source_system AS sourceSystem,
              external_ref AS externalRef,
              latest_revision AS latestRevision,
              latest_version_label AS latestVersionLabel,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_object_master
            WHERE id = #{id}
            """)
    PlmObjectMasterRecord selectById(@Param("id") String id);
}
