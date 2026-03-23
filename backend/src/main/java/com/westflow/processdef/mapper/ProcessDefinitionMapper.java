package com.westflow.processdef.mapper;

import com.westflow.processdef.model.ProcessDefinitionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
// 流程定义表的 MyBatis 映射接口。
public interface ProcessDefinitionMapper {

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE id = #{processDefinitionId}
            """)
    ProcessDefinitionRecord selectById(@Param("processDefinitionId") String processDefinitionId);

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE flowable_definition_id = #{flowableDefinitionId}
            LIMIT 1
            """)
    ProcessDefinitionRecord selectByFlowableDefinitionId(@Param("flowableDefinitionId") String flowableDefinitionId);

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE process_key = #{processKey}
              AND status = 'DRAFT'
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """)
    ProcessDefinitionRecord selectDraftByProcessKey(@Param("processKey") String processKey);

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE process_key = #{processKey}
              AND status = 'PUBLISHED'
            ORDER BY version DESC, updated_at DESC, created_at DESC, id DESC
            LIMIT 1
            """)
    ProcessDefinitionRecord selectLatestPublishedByProcessKey(@Param("processKey") String processKey);

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE process_key = #{processKey}
              AND version = #{version}
              AND status = 'PUBLISHED'
            LIMIT 1
            """)
    ProcessDefinitionRecord selectPublishedByProcessKeyAndVersion(
            @Param("processKey") String processKey,
            @Param("version") Integer version
    );

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM wf_process_definition
            WHERE process_key = #{processKey}
            """)
    Integer selectMaxVersionByProcessKey(@Param("processKey") String processKey);

    @Select("""
            SELECT
              id AS process_definition_id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            FROM wf_process_definition
            WHERE status = 'PUBLISHED'
            ORDER BY process_key ASC, version DESC, updated_at DESC, created_at DESC, id DESC
            """)
    List<ProcessDefinitionRecord> selectAllPublished();

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_process_definition",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(id) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(process_key) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(process_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(category, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND status = #{status}",
            "  </if>",
            "  <if test='category != null and category != \"\"'>",
            "    AND category = #{category}",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category
    );

    @Select({
            "<script>",
            "SELECT",
            "  id AS process_definition_id,",
            "  process_key,",
            "  process_name,",
            "  category,",
            "  version,",
            "  status,",
            "  dsl_json,",
            "  bpmn_xml,",
            "  publisher_user_id,",
            "  deployment_id,",
            "  flowable_definition_id,",
            "  created_at,",
            "  updated_at",
            "FROM wf_process_definition",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(id) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(process_key) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(process_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(category, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND status = #{status}",
            "  </if>",
            "  <if test='category != null and category != \"\"'>",
            "    AND category = #{category}",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}, created_at DESC, version DESC, id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<ProcessDefinitionRecord> selectPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Insert("""
            INSERT INTO wf_process_definition (
              id,
              process_key,
              process_name,
              category,
              version,
              status,
              dsl_json,
              bpmn_xml,
              publisher_user_id,
              deployment_id,
              flowable_definition_id,
              created_at,
              updated_at
            ) VALUES (
              #{processDefinitionId},
              #{processKey},
              #{processName},
              #{category},
              #{version},
              #{status},
              #{dslJson},
              #{bpmnXml},
              #{publisherUserId},
              #{deploymentId},
              #{flowableDefinitionId},
              #{createdAt},
              #{updatedAt}
            )
            """)
    int insertDefinition(ProcessDefinitionRecord record);

    @Update("""
            UPDATE wf_process_definition
            SET process_key = #{processKey},
                process_name = #{processName},
                category = #{category},
                version = #{version},
                status = #{status},
                dsl_json = #{dslJson},
                bpmn_xml = #{bpmnXml},
                publisher_user_id = #{publisherUserId},
                deployment_id = #{deploymentId},
                flowable_definition_id = #{flowableDefinitionId},
                updated_at = #{updatedAt}
            WHERE id = #{processDefinitionId}
            """)
    int updateDefinition(ProcessDefinitionRecord record);
}
