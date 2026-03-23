package com.westflow.processruntime.mapper;

import com.westflow.processruntime.model.ProcessLinkRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
// 主子流程实例关联表的读写接口。
public interface ProcessLinkMapper {

    @Insert("""
            INSERT INTO wf_process_link (
              id,
              root_instance_id,
              parent_instance_id,
              child_instance_id,
              parent_node_id,
              called_process_key,
              called_definition_id,
              link_type,
              status,
              terminate_policy,
              child_finish_policy,
              created_at,
              finished_at,
              updated_at
            ) VALUES (
              #{id},
              #{rootInstanceId},
              #{parentInstanceId},
              #{childInstanceId},
              #{parentNodeId},
              #{calledProcessKey},
              #{calledDefinitionId},
              #{linkType},
              #{status},
              #{terminatePolicy},
              #{childFinishPolicy},
              #{createdAt},
              #{finishedAt},
              CURRENT_TIMESTAMP
            )
            """)
    int insert(ProcessLinkRecord record);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              child_instance_id AS childInstanceId,
              parent_node_id AS parentNodeId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              link_type AS linkType,
              status,
              terminate_policy AS terminatePolicy,
              child_finish_policy AS childFinishPolicy,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_process_link
            WHERE parent_instance_id = #{parentInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ProcessLinkRecord> selectByParentInstanceId(@Param("parentInstanceId") String parentInstanceId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              child_instance_id AS childInstanceId,
              parent_node_id AS parentNodeId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              link_type AS linkType,
              status,
              terminate_policy AS terminatePolicy,
              child_finish_policy AS childFinishPolicy,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_process_link
            WHERE root_instance_id = #{rootInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ProcessLinkRecord> selectByRootInstanceId(@Param("rootInstanceId") String rootInstanceId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              child_instance_id AS childInstanceId,
              parent_node_id AS parentNodeId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              link_type AS linkType,
              status,
              terminate_policy AS terminatePolicy,
              child_finish_policy AS childFinishPolicy,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_process_link
            WHERE child_instance_id = #{childInstanceId}
            LIMIT 1
            """)
    ProcessLinkRecord selectByChildInstanceId(@Param("childInstanceId") String childInstanceId);

    @Update("""
            UPDATE wf_process_link
               SET status = #{status},
                   finished_at = #{finishedAt},
                   updated_at = CURRENT_TIMESTAMP
             WHERE child_instance_id = #{childInstanceId}
            """)
    int updateStatus(
            @Param("childInstanceId") String childInstanceId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt
    );
}
