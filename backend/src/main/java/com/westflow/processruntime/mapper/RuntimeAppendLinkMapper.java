package com.westflow.processruntime.mapper;

import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
// 运行时追加与动态构建附属结构表的读写接口。
public interface RuntimeAppendLinkMapper {

    @Insert("""
            INSERT INTO wf_runtime_append_link (
              id,
              root_instance_id,
              parent_instance_id,
              source_task_id,
              source_node_id,
              append_type,
              runtime_link_type,
              policy,
              target_task_id,
              target_instance_id,
              target_user_id,
              called_process_key,
              called_definition_id,
              called_version_policy,
              called_version,
              resolved_target_mode,
              target_business_type,
              target_scene_code,
              status,
              trigger_mode,
              operator_user_id,
              comment_text,
              created_at,
              finished_at,
              updated_at
            ) VALUES (
              #{id},
              #{rootInstanceId},
              #{parentInstanceId},
              #{sourceTaskId},
              #{sourceNodeId},
              #{appendType},
              #{runtimeLinkType},
              #{policy},
              #{targetTaskId},
              #{targetInstanceId},
              #{targetUserId},
              #{calledProcessKey},
              #{calledDefinitionId},
              #{calledVersionPolicy},
              #{calledVersion},
              #{resolvedTargetMode},
              #{targetBusinessType},
              #{targetSceneCode},
              #{status},
              #{triggerMode},
              #{operatorUserId},
              #{commentText},
              #{createdAt},
              #{finishedAt},
              CURRENT_TIMESTAMP
            )
            """)
    int insert(RuntimeAppendLinkRecord record);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              source_task_id AS sourceTaskId,
              source_node_id AS sourceNodeId,
              append_type AS appendType,
              runtime_link_type AS runtimeLinkType,
              policy,
              target_task_id AS targetTaskId,
              target_instance_id AS targetInstanceId,
              target_user_id AS targetUserId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              called_version_policy AS calledVersionPolicy,
              called_version AS calledVersion,
              resolved_target_mode AS resolvedTargetMode,
              target_business_type AS targetBusinessType,
              target_scene_code AS targetSceneCode,
              status,
              trigger_mode AS triggerMode,
              operator_user_id AS operatorUserId,
              comment_text AS commentText,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_runtime_append_link
            WHERE root_instance_id = #{rootInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<RuntimeAppendLinkRecord> selectByRootInstanceId(@Param("rootInstanceId") String rootInstanceId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              source_task_id AS sourceTaskId,
              source_node_id AS sourceNodeId,
              append_type AS appendType,
              runtime_link_type AS runtimeLinkType,
              policy,
              target_task_id AS targetTaskId,
              target_instance_id AS targetInstanceId,
              target_user_id AS targetUserId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              called_version_policy AS calledVersionPolicy,
              called_version AS calledVersion,
              resolved_target_mode AS resolvedTargetMode,
              target_business_type AS targetBusinessType,
              target_scene_code AS targetSceneCode,
              status,
              trigger_mode AS triggerMode,
              operator_user_id AS operatorUserId,
              comment_text AS commentText,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_runtime_append_link
            WHERE parent_instance_id = #{parentInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<RuntimeAppendLinkRecord> selectByParentInstanceId(@Param("parentInstanceId") String parentInstanceId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              source_task_id AS sourceTaskId,
              source_node_id AS sourceNodeId,
              append_type AS appendType,
              runtime_link_type AS runtimeLinkType,
              policy,
              target_task_id AS targetTaskId,
              target_instance_id AS targetInstanceId,
              target_user_id AS targetUserId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              called_version_policy AS calledVersionPolicy,
              called_version AS calledVersion,
              resolved_target_mode AS resolvedTargetMode,
              target_business_type AS targetBusinessType,
              target_scene_code AS targetSceneCode,
              status,
              trigger_mode AS triggerMode,
              operator_user_id AS operatorUserId,
              comment_text AS commentText,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_runtime_append_link
            WHERE source_task_id = #{sourceTaskId}
            ORDER BY created_at ASC, id ASC
            """)
    List<RuntimeAppendLinkRecord> selectBySourceTaskId(@Param("sourceTaskId") String sourceTaskId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              source_task_id AS sourceTaskId,
              source_node_id AS sourceNodeId,
              append_type AS appendType,
              runtime_link_type AS runtimeLinkType,
              policy,
              target_task_id AS targetTaskId,
              target_instance_id AS targetInstanceId,
              target_user_id AS targetUserId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              called_version_policy AS calledVersionPolicy,
              called_version AS calledVersion,
              resolved_target_mode AS resolvedTargetMode,
              target_business_type AS targetBusinessType,
              target_scene_code AS targetSceneCode,
              status,
              trigger_mode AS triggerMode,
              operator_user_id AS operatorUserId,
              comment_text AS commentText,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_runtime_append_link
            WHERE target_task_id = #{targetTaskId}
            LIMIT 1
            """)
    RuntimeAppendLinkRecord selectByTargetTaskId(@Param("targetTaskId") String targetTaskId);

    @Select("""
            SELECT
              id,
              root_instance_id AS rootInstanceId,
              parent_instance_id AS parentInstanceId,
              source_task_id AS sourceTaskId,
              source_node_id AS sourceNodeId,
              append_type AS appendType,
              runtime_link_type AS runtimeLinkType,
              policy,
              target_task_id AS targetTaskId,
              target_instance_id AS targetInstanceId,
              target_user_id AS targetUserId,
              called_process_key AS calledProcessKey,
              called_definition_id AS calledDefinitionId,
              called_version_policy AS calledVersionPolicy,
              called_version AS calledVersion,
              resolved_target_mode AS resolvedTargetMode,
              target_business_type AS targetBusinessType,
              target_scene_code AS targetSceneCode,
              status,
              trigger_mode AS triggerMode,
              operator_user_id AS operatorUserId,
              comment_text AS commentText,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_runtime_append_link
            WHERE target_instance_id = #{targetInstanceId}
            LIMIT 1
            """)
    RuntimeAppendLinkRecord selectByTargetInstanceId(@Param("targetInstanceId") String targetInstanceId);

    @Update("""
            UPDATE wf_runtime_append_link
               SET status = #{status},
                   finished_at = #{finishedAt},
                   updated_at = CURRENT_TIMESTAMP
             WHERE target_task_id = #{targetTaskId}
            """)
    int updateStatusByTargetTaskId(
            @Param("targetTaskId") String targetTaskId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt
    );

    @Update("""
            UPDATE wf_runtime_append_link
               SET status = #{status},
                   finished_at = #{finishedAt},
                   updated_at = CURRENT_TIMESTAMP
             WHERE target_instance_id = #{targetInstanceId}
            """)
    int updateStatusByTargetInstanceId(
            @Param("targetInstanceId") String targetInstanceId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt
    );

    @Update("""
            UPDATE wf_runtime_append_link
               SET status = #{status},
                   finished_at = #{finishedAt},
                   updated_at = CURRENT_TIMESTAMP
             WHERE root_instance_id = #{rootInstanceId}
               AND status = 'RUNNING'
            """)
    int updateStatusByRootInstanceId(
            @Param("rootInstanceId") String rootInstanceId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt
    );

    @Update("""
            UPDATE wf_runtime_append_link
               SET status = #{status},
                   finished_at = #{finishedAt},
                   updated_at = CURRENT_TIMESTAMP
             WHERE parent_instance_id = #{parentInstanceId}
               AND status = 'RUNNING'
            """)
    int updateStatusByParentInstanceId(
            @Param("parentInstanceId") String parentInstanceId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt
    );
}
