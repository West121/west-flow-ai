package com.westflow.system.role.mapper;

import com.westflow.system.role.api.SystemRoleDetailResponse;
import com.westflow.system.role.api.SystemRoleFormOptionsResponse;
import com.westflow.system.role.api.SystemRoleListItemResponse;
import com.westflow.system.role.service.SystemRoleService.RoleDataScopeEntity;
import com.westflow.system.role.service.SystemRoleService.RoleMenuBinding;
import com.westflow.system.role.service.SystemRoleService.SystemRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemRoleMapper {

    @Select({
            "<script>",
            "SELECT",
            "  r.id AS role_id,",
            "  r.role_code,",
            "  r.role_name,",
            "  r.role_category,",
            "  COALESCE((",
            "    SELECT MIN(rds.scope_type)",
            "    FROM wf_role_data_scope rds",
            "    WHERE rds.role_id = r.id",
            "  ), 'NONE') AS data_scope_summary,",
            "  (SELECT COUNT(1) FROM wf_role_menu rm WHERE rm.role_id = r.id) AS menu_count,",
            "  CASE WHEN r.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  r.created_at",
            "FROM wf_role r",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(r.role_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(r.role_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(r.description, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND r.enabled = #{enabled}",
            "  </if>",
            "  <if test='roleCategory != null and roleCategory != \"\"'>",
            "    AND r.role_category = #{roleCategory}",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<SystemRoleListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("roleCategory") String roleCategory,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_role r",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(r.role_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(r.role_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(r.description, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND r.enabled = #{enabled}",
            "  </if>",
            "  <if test='roleCategory != null and roleCategory != \"\"'>",
            "    AND r.role_category = #{roleCategory}",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("roleCategory") String roleCategory
    );

    @Select("""
            SELECT
              r.id AS role_id,
              r.role_code,
              r.role_name,
              r.role_category,
              r.description,
              r.enabled
            FROM wf_role r
            WHERE r.id = #{roleId}
            """)
    SystemRoleBaseDetailRecord selectRole(@Param("roleId") String roleId);

    @Select("""
            SELECT menu_id
            FROM wf_role_menu
            WHERE role_id = #{roleId}
            ORDER BY created_at ASC
            """)
    List<String> selectMenuIdsByRoleId(@Param("roleId") String roleId);

    @Select("""
            SELECT
              scope_type,
              scope_value
            FROM wf_role_data_scope
            WHERE role_id = #{roleId}
            ORDER BY created_at ASC
            """)
    List<SystemRoleDetailResponse.RoleDataScopeItem> selectDataScopesByRoleId(@Param("roleId") String roleId);

    @Select("""
            SELECT
              m.id,
              m.menu_name AS name,
              m.menu_type,
              parent.menu_name AS parent_menu_name
            FROM wf_menu m
            LEFT JOIN wf_menu parent ON parent.id = m.parent_menu_id
            WHERE m.enabled = TRUE
            ORDER BY m.sort_order ASC, m.created_at ASC
            """)
    List<SystemRoleFormOptionsResponse.MenuOption> selectMenuOptions();

    @Select("""
            SELECT id, company_name AS name
            FROM wf_company
            WHERE enabled = TRUE
            ORDER BY company_name ASC
            """)
    List<SystemRoleFormOptionsResponse.CompanyOption> selectCompanyOptions();

    @Select("""
            SELECT
              d.id,
              d.department_name AS name,
              c.id AS company_id,
              c.company_name
            FROM wf_department d
            INNER JOIN wf_company c ON c.id = d.company_id
            WHERE d.enabled = TRUE
            ORDER BY c.company_name ASC, d.department_name ASC
            """)
    List<SystemRoleFormOptionsResponse.DepartmentOption> selectDepartmentOptions();

    @Select("""
            SELECT
              u.id,
              u.display_name AS name,
              d.id AS department_id,
              d.department_name
            FROM wf_user u
            LEFT JOIN wf_department d ON d.id = u.active_department_id
            ORDER BY u.display_name ASC
            """)
    List<SystemRoleFormOptionsResponse.UserOption> selectUserOptions();

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_role",
            "WHERE role_code = #{roleCode}",
            "  <if test='excludeRoleId != null and excludeRoleId != \"\"'>",
            "    AND id != #{excludeRoleId}",
            "  </if>",
            "</script>"
    })
    Long countByRoleCode(@Param("roleCode") String roleCode, @Param("excludeRoleId") String excludeRoleId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_menu",
            "WHERE id IN",
            "  <foreach collection='menuIds' item='menuId' open='(' separator=',' close=')'>",
            "    #{menuId}",
            "  </foreach>",
            "</script>"
    })
    long countExistingMenus(@Param("menuIds") List<String> menuIds);

    @Select("SELECT COUNT(1) FROM wf_company WHERE id = #{companyId}")
    long countCompanyById(@Param("companyId") String companyId);

    @Select("SELECT COUNT(1) FROM wf_department WHERE id = #{departmentId}")
    long countDepartmentById(@Param("departmentId") String departmentId);

    @Select("SELECT COUNT(1) FROM wf_user WHERE id = #{userId}")
    long countUserById(@Param("userId") String userId);

    @Insert("""
            INSERT INTO wf_role (
              id,
              role_code,
              role_name,
              role_category,
              description,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{roleCode},
              #{roleName},
              #{roleCategory},
              #{description},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertRole(SystemRoleEntity entity);

    @Update("""
            UPDATE wf_role
            SET role_code = #{roleCode},
                role_name = #{roleName},
                role_category = #{roleCategory},
                description = #{description},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateRole(SystemRoleEntity entity);

    @Delete("DELETE FROM wf_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") String roleId);

    @Delete("DELETE FROM wf_role_data_scope WHERE role_id = #{roleId}")
    int deleteRoleDataScopes(@Param("roleId") String roleId);

    @Insert("""
            INSERT INTO wf_role_menu (
              id,
              role_id,
              menu_id,
              created_at
            ) VALUES (
              #{id},
              #{roleId},
              #{menuId},
              CURRENT_TIMESTAMP
            )
            """)
    int insertRoleMenu(RoleMenuBinding binding);

    @Insert("""
            INSERT INTO wf_role_data_scope (
              id,
              role_id,
              scope_type,
              scope_value,
              created_at
            ) VALUES (
              #{id},
              #{roleId},
              #{scopeType},
              #{scopeValue},
              CURRENT_TIMESTAMP
            )
            """)
    int insertRoleDataScope(RoleDataScopeEntity entity);

    record SystemRoleBaseDetailRecord(
            String roleId,
            String roleCode,
            String roleName,
            String roleCategory,
            String description,
            boolean enabled
    ) {
    }
}
