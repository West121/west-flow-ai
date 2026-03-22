package com.westflow.system.org.department.mapper;

import com.westflow.system.org.department.api.SystemDepartmentDetailResponse;
import com.westflow.system.org.department.api.SystemDepartmentFormOptionsResponse;
import com.westflow.system.org.department.api.SystemDepartmentListItemResponse;
import com.westflow.system.org.department.service.SystemDepartmentService.SystemDepartmentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemDepartmentMapper {

    @Select({
            "<script>",
            "SELECT",
            "  d.id AS department_id,",
            "  c.company_name,",
            "  p.department_name AS parent_department_name,",
            "  d.department_name,",
            "  CASE WHEN d.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  d.created_at",
            "FROM wf_department d",
            "INNER JOIN wf_company c ON c.id = d.company_id",
            "LEFT JOIN wf_department p ON p.id = d.parent_department_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(d.department_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(p.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND d.enabled = #{enabled}",
            "  </if>",
            "  <if test='companyId != null and companyId != \"\"'>",
            "    AND d.company_id = #{companyId}",
            "  </if>",
            "  <if test='parentDepartmentId != null'>",
            "    AND COALESCE(d.parent_department_id, '') = COALESCE(#{parentDepartmentId}, '')",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <choose>",
            "      <when test='(companyIds != null and companyIds.size() &gt; 0) or (departmentIds != null and departmentIds.size() &gt; 0)'>",
            "        <trim prefix='AND (' suffix=')' prefixOverrides='OR '>",
            "          <if test='companyIds != null and companyIds.size() &gt; 0'>",
            "            c.id IN",
            "            <foreach collection='companyIds' item='companyId' open='(' separator=',' close=')'>",
            "              #{companyId}",
            "            </foreach>",
            "          </if>",
            "          <if test='departmentIds != null and departmentIds.size() &gt; 0'>",
            "            OR d.id IN",
            "            <foreach collection='departmentIds' item='departmentId' open='(' separator=',' close=')'>",
            "              #{departmentId}",
            "            </foreach>",
            "          </if>",
            "        </trim>",
            "      </when>",
            "      <otherwise>",
            "        AND 1 = 0",
            "      </otherwise>",
            "    </choose>",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<SystemDepartmentListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("companyId") String companyId,
            @Param("parentDepartmentId") String parentDepartmentId,
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds,
            @Param("departmentIds") List<String> departmentIds,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_department d",
            "INNER JOIN wf_company c ON c.id = d.company_id",
            "LEFT JOIN wf_department p ON p.id = d.parent_department_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(d.department_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(p.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND d.enabled = #{enabled}",
            "  </if>",
            "  <if test='companyId != null and companyId != \"\"'>",
            "    AND d.company_id = #{companyId}",
            "  </if>",
            "  <if test='parentDepartmentId != null'>",
            "    AND COALESCE(d.parent_department_id, '') = COALESCE(#{parentDepartmentId}, '')",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <choose>",
            "      <when test='(companyIds != null and companyIds.size() &gt; 0) or (departmentIds != null and departmentIds.size() &gt; 0)'>",
            "        <trim prefix='AND (' suffix=')' prefixOverrides='OR '>",
            "          <if test='companyIds != null and companyIds.size() &gt; 0'>",
            "            c.id IN",
            "            <foreach collection='companyIds' item='companyId' open='(' separator=',' close=')'>",
            "              #{companyId}",
            "            </foreach>",
            "          </if>",
            "          <if test='departmentIds != null and departmentIds.size() &gt; 0'>",
            "            OR d.id IN",
            "            <foreach collection='departmentIds' item='departmentId' open='(' separator=',' close=')'>",
            "              #{departmentId}",
            "            </foreach>",
            "          </if>",
            "        </trim>",
            "      </when>",
            "      <otherwise>",
            "        AND 1 = 0",
            "      </otherwise>",
            "    </choose>",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("companyId") String companyId,
            @Param("parentDepartmentId") String parentDepartmentId,
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds,
            @Param("departmentIds") List<String> departmentIds
    );

    @Select("""
            SELECT
              d.id AS department_id,
              c.id AS company_id,
              c.company_name,
              p.id AS parent_department_id,
              p.department_name AS parent_department_name,
              d.department_name,
              d.enabled
            FROM wf_department d
            INNER JOIN wf_company c ON c.id = d.company_id
            LEFT JOIN wf_department p ON p.id = d.parent_department_id
            WHERE d.id = #{departmentId}
            """)
    SystemDepartmentDetailResponse selectDetail(@Param("departmentId") String departmentId);

    @Select("""
            SELECT
              c.id,
              c.company_name AS name,
              c.enabled
            FROM wf_company c
            ORDER BY c.company_name ASC
            """)
    List<SystemDepartmentFormOptionsResponse.CompanyOption> selectCompanyOptions();

    @Select({
            "<script>",
            "SELECT",
            "  d.id,",
            "  d.department_name AS name,",
            "  d.company_id,",
            "  c.company_name,",
            "  d.enabled",
            "FROM wf_department d",
            "INNER JOIN wf_company c ON c.id = d.company_id",
            "<where>",
            "  <if test='companyId != null and companyId != \"\"'>",
            "    AND d.company_id = #{companyId}",
            "  </if>",
            "</where>",
            "ORDER BY c.company_name ASC, d.department_name ASC",
            "</script>"
    })
    List<SystemDepartmentFormOptionsResponse.ParentDepartmentOption> selectParentDepartmentOptions(
            @Param("companyId") String companyId
    );

    @Select("""
            SELECT id
            FROM wf_department
            WHERE parent_department_id = #{departmentId}
            ORDER BY department_name ASC
            """)
    List<String> selectDepartmentIdsByParentId(@Param("departmentId") String departmentId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_department",
            "WHERE company_id = #{companyId}",
            "  AND COALESCE(parent_department_id, '') = COALESCE(#{parentDepartmentId}, '')",
            "  AND department_name = #{departmentName}",
            "  <if test='excludeDepartmentId != null and excludeDepartmentId != \"\"'>",
            "    AND id != #{excludeDepartmentId}",
            "  </if>",
            "</script>"
    })
    Long countByDepartmentName(
            @Param("companyId") String companyId,
            @Param("parentDepartmentId") String parentDepartmentId,
            @Param("departmentName") String departmentName,
            @Param("excludeDepartmentId") String excludeDepartmentId
    );

    @Insert("""
            INSERT INTO wf_department (
              id,
              company_id,
              parent_department_id,
              department_name,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{companyId},
              #{parentDepartmentId},
              #{departmentName},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertDepartment(SystemDepartmentEntity entity);

    @Update("""
            UPDATE wf_department
            SET company_id = #{companyId},
                parent_department_id = #{parentDepartmentId},
                department_name = #{departmentName},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDepartment(SystemDepartmentEntity entity);
}
