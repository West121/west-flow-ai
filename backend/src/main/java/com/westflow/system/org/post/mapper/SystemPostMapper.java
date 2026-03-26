package com.westflow.system.org.post.mapper;

import com.westflow.system.org.post.response.SystemPostDetailResponse;
import com.westflow.system.org.post.response.SystemPostFormOptionsResponse;
import com.westflow.system.org.post.response.SystemPostListItemResponse;
import com.westflow.system.org.post.model.SystemPostRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 岗位数据访问层，负责岗位查询和写入。
 */
@Mapper
public interface SystemPostMapper {

    @Select({
            "<script>",
            "SELECT",
            "  p.id AS post_id,",
            "  c.company_name,",
            "  d.department_name,",
            "  p.post_name,",
            "  CASE WHEN p.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  p.created_at",
            "FROM wf_post p",
            "INNER JOIN wf_department d ON d.id = p.department_id",
            "INNER JOIN wf_company c ON c.id = d.company_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(p.post_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(d.department_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND p.enabled = #{enabled}",
            "  </if>",
            "  <if test='companyId != null and companyId != \"\"'>",
            "    AND d.company_id = #{companyId}",
            "  </if>",
            "  <if test='departmentId != null and departmentId != \"\"'>",
            "    AND p.department_id = #{departmentId}",
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
    List<SystemPostListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("companyId") String companyId,
            @Param("departmentId") String departmentId,
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds,
            @Param("departmentIds") List<String> departmentIds,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    default List<SystemPostListItemResponse> selectPage(
            String keyword,
            Boolean enabled,
            String companyId,
            String departmentId,
            String orderBy,
            String orderDirection,
            long limit,
            long offset
    ) {
        return selectPage(
                keyword,
                enabled,
                companyId,
                departmentId,
                true,
                List.of(),
                List.of(),
                orderBy,
                orderDirection,
                limit,
                offset
        );
    }

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_post p",
            "INNER JOIN wf_department d ON d.id = p.department_id",
            "INNER JOIN wf_company c ON c.id = d.company_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(p.post_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(d.department_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND p.enabled = #{enabled}",
            "  </if>",
            "  <if test='companyId != null and companyId != \"\"'>",
            "    AND d.company_id = #{companyId}",
            "  </if>",
            "  <if test='departmentId != null and departmentId != \"\"'>",
            "    AND p.department_id = #{departmentId}",
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
            @Param("departmentId") String departmentId,
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds,
            @Param("departmentIds") List<String> departmentIds
    );

    default long countPage(
            String keyword,
            Boolean enabled,
            String companyId,
            String departmentId
    ) {
        return countPage(keyword, enabled, companyId, departmentId, true, List.of(), List.of());
    }

    @Select("""
            SELECT
              p.id AS post_id,
              c.id AS company_id,
              c.company_name,
              d.id AS department_id,
              d.department_name,
              p.post_name,
              p.enabled
            FROM wf_post p
            INNER JOIN wf_department d ON d.id = p.department_id
            INNER JOIN wf_company c ON c.id = d.company_id
            WHERE p.id = #{postId}
            """)
    SystemPostDetailResponse selectDetail(@Param("postId") String postId);

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
    List<SystemPostFormOptionsResponse.DepartmentOption> selectDepartmentOptions(
            @Param("companyId") String companyId
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_post",
            "WHERE department_id = #{departmentId}",
            "  AND post_name = #{postName}",
            "  <if test='excludePostId != null and excludePostId != \"\"'>",
            "    AND id != #{excludePostId}",
            "  </if>",
            "</script>"
    })
    Long countByPostName(
            @Param("departmentId") String departmentId,
            @Param("postName") String postName,
            @Param("excludePostId") String excludePostId
    );

    @Select("""
            SELECT
              p.id AS post_id,
              p.department_id,
              p.post_name,
              p.enabled
            FROM wf_post p
            WHERE p.id = #{postId}
            """)
    SystemPostRecord selectPostEntity(@Param("postId") String postId);

    @Insert("""
            INSERT INTO wf_post (
              id,
              department_id,
              post_name,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{departmentId},
              #{postName},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertPost(SystemPostRecord entity);

    @Update("""
            UPDATE wf_post
            SET department_id = #{departmentId},
                post_name = #{postName},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updatePost(SystemPostRecord entity);

    @Select("SELECT COUNT(1) FROM wf_user_post WHERE post_id = #{postId}")
    long countUserAssignmentsByPostId(@Param("postId") String postId);

    @Delete("DELETE FROM wf_post WHERE id = #{postId}")
    int deletePost(@Param("postId") String postId);
}
