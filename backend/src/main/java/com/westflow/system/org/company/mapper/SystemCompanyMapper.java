package com.westflow.system.org.company.mapper;

import com.westflow.system.org.company.api.SystemCompanyDetailResponse;
import com.westflow.system.org.company.api.SystemCompanyFormOptionsResponse;
import com.westflow.system.org.company.api.SystemCompanyListItemResponse;
import com.westflow.system.org.company.service.SystemCompanyService.SystemCompanyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemCompanyMapper {

    @Select({
            "<script>",
            "SELECT",
            "  c.id AS company_id,",
            "  c.company_name,",
            "  CASE WHEN c.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  c.created_at",
            "FROM wf_company c",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND c.enabled = #{enabled}",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <choose>",
            "      <when test='companyIds != null and companyIds.size() &gt; 0'>",
            "        AND c.id IN",
            "        <foreach collection='companyIds' item='companyId' open='(' separator=',' close=')'>",
            "          #{companyId}",
            "        </foreach>",
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
    List<SystemCompanyListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_company c",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND LOWER(c.company_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND c.enabled = #{enabled}",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <choose>",
            "      <when test='companyIds != null and companyIds.size() &gt; 0'>",
            "        AND c.id IN",
            "        <foreach collection='companyIds' item='companyId' open='(' separator=',' close=')'>",
            "          #{companyId}",
            "        </foreach>",
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
            @Param("allAccess") boolean allAccess,
            @Param("companyIds") List<String> companyIds
    );

    @Select("""
            SELECT
              c.id AS company_id,
              c.company_name,
              c.enabled
            FROM wf_company c
            WHERE c.id = #{companyId}
            """)
    SystemCompanyDetailResponse selectDetail(@Param("companyId") String companyId);

    @Select("""
            SELECT
              c.id,
              c.company_name AS name,
              c.enabled
            FROM wf_company c
            ORDER BY c.company_name ASC
            """)
    List<SystemCompanyFormOptionsResponse.CompanyOption> selectCompanyOptions();

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_company",
            "WHERE company_name = #{companyName}",
            "  <if test='excludeCompanyId != null and excludeCompanyId != \"\"'>",
            "    AND id != #{excludeCompanyId}",
            "  </if>",
            "</script>"
    })
    Long countByCompanyName(@Param("companyName") String companyName, @Param("excludeCompanyId") String excludeCompanyId);

    @Insert("""
            INSERT INTO wf_company (
              id,
              company_name,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{companyName},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertCompany(SystemCompanyEntity entity);

    @Update("""
            UPDATE wf_company
            SET company_name = #{companyName},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateCompany(SystemCompanyEntity entity);
}
