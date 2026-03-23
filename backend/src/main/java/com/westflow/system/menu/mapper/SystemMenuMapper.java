package com.westflow.system.menu.mapper;

import com.westflow.system.menu.api.SystemMenuDetailResponse;
import com.westflow.system.menu.api.SystemMenuFormOptionsResponse;
import com.westflow.system.menu.api.SystemMenuListItemResponse;
import com.westflow.system.menu.api.SystemMenuTreeFlatNode;
import com.westflow.system.menu.api.SidebarMenuFlatNode;
import com.westflow.system.menu.service.SystemMenuService.SystemMenuEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 菜单数据访问层，负责菜单查询和写入。
 */
@Mapper
public interface SystemMenuMapper {

    @Select({
            "<script>",
            "SELECT",
            "  m.id AS menu_id,",
            "  parent.menu_name AS parent_menu_name,",
            "  m.menu_name,",
            "  m.menu_type,",
            "  m.route_path,",
            "  m.permission_code,",
            "  m.sort_order,",
            "  m.visible,",
            "  CASE WHEN m.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  m.created_at",
            "FROM wf_menu m",
            "LEFT JOIN wf_menu parent ON parent.id = m.parent_menu_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(m.menu_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(m.route_path, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(m.permission_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND m.enabled = #{enabled}",
            "  </if>",
            "  <if test='visible != null'>",
            "    AND m.visible = #{visible}",
            "  </if>",
            "  <if test='menuType != null and menuType != \"\"'>",
            "    AND m.menu_type = #{menuType}",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}, m.created_at DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<SystemMenuListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("visible") Boolean visible,
            @Param("menuType") String menuType,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_menu m",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(m.menu_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(m.route_path, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(m.permission_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND m.enabled = #{enabled}",
            "  </if>",
            "  <if test='visible != null'>",
            "    AND m.visible = #{visible}",
            "  </if>",
            "  <if test='menuType != null and menuType != \"\"'>",
            "    AND m.menu_type = #{menuType}",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("visible") Boolean visible,
            @Param("menuType") String menuType
    );

    @Select("""
            SELECT
              m.id AS menu_id,
              m.parent_menu_id,
              parent.menu_name AS parent_menu_name,
              m.menu_name,
              m.menu_type,
              m.route_path,
              m.component_path,
              m.permission_code,
              m.icon_name,
              m.sort_order,
              m.visible,
              m.enabled
            FROM wf_menu m
            LEFT JOIN wf_menu parent ON parent.id = m.parent_menu_id
            WHERE m.id = #{menuId}
            """)
    SystemMenuDetailResponse selectDetail(@Param("menuId") String menuId);

    @Select("""
            SELECT
              m.id,
              m.menu_name AS name,
              m.menu_type,
              m.enabled
            FROM wf_menu m
            ORDER BY m.sort_order ASC, m.created_at ASC
            """)
    List<SystemMenuFormOptionsResponse.ParentMenuOption> selectParentMenuOptions();

    @Select("""
            SELECT
              m.id,
              m.parent_menu_id,
              m.menu_name AS name,
              m.menu_type,
              m.enabled
            FROM wf_menu m
            ORDER BY
              CASE WHEN m.parent_menu_id IS NULL THEN 0 ELSE 1 END ASC,
              m.sort_order ASC,
              m.created_at ASC
            """)
    List<SystemMenuFormOptionsResponse.ParentMenuTreeOption> selectParentMenuTreeOptions();

    @Select("""
            SELECT parent_menu_id
            FROM wf_menu
            WHERE id = #{menuId}
            """)
    String selectParentMenuId(@Param("menuId") String menuId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_menu",
            "WHERE menu_name = #{menuName}",
            "  <choose>",
            "    <when test='parentMenuId != null and parentMenuId != \"\"'>",
            "      AND parent_menu_id = #{parentMenuId}",
            "    </when>",
            "    <otherwise>",
            "      AND parent_menu_id IS NULL",
            "    </otherwise>",
            "  </choose>",
            "  <if test='excludeMenuId != null and excludeMenuId != \"\"'>",
            "    AND id != #{excludeMenuId}",
            "  </if>",
            "</script>"
    })
    Long countByMenuNameAndParent(
            @Param("menuName") String menuName,
            @Param("parentMenuId") String parentMenuId,
            @Param("excludeMenuId") String excludeMenuId
    );

    @Select("""
            SELECT
              m.id AS menu_id,
              m.parent_menu_id,
              m.menu_name,
              m.menu_type,
              m.route_path,
              m.component_path,
              m.permission_code,
              m.icon_name,
              m.sort_order,
              m.visible,
              m.enabled
            FROM wf_menu m
            ORDER BY
              CASE WHEN m.parent_menu_id IS NULL THEN 0 ELSE 1 END ASC,
              m.sort_order ASC,
              m.created_at ASC
            """)
    List<SystemMenuTreeFlatNode> selectTreeNodes();

    @Select("""
            SELECT DISTINCT rm.menu_id
            FROM wf_user_role ur
            INNER JOIN wf_role r ON r.id = ur.role_id
            INNER JOIN wf_role_menu rm ON rm.role_id = r.id
            INNER JOIN wf_menu m ON m.id = rm.menu_id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
              AND m.enabled = TRUE
            """)
    List<String> selectGrantedMenuIdsByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
              m.id AS menu_id,
              m.parent_menu_id,
              m.menu_name AS title,
              m.menu_type,
              m.route_path,
              m.icon_name,
              m.sort_order
            FROM wf_menu m
            WHERE m.enabled = TRUE
              AND m.visible = TRUE
              AND m.menu_type IN ('DIRECTORY', 'MENU')
            ORDER BY
              CASE WHEN m.parent_menu_id IS NULL THEN 0 ELSE 1 END ASC,
              m.sort_order ASC,
              m.created_at ASC
            """)
    List<SidebarMenuFlatNode> selectNavigableSidebarNodes();

    @Insert("""
            INSERT INTO wf_menu (
              id,
              parent_menu_id,
              menu_name,
              menu_type,
              route_path,
              component_path,
              permission_code,
              icon_name,
              sort_order,
              visible,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{parentMenuId},
              #{menuName},
              #{menuType},
              #{routePath},
              #{componentPath},
              #{permissionCode},
              #{iconName},
              #{sortOrder},
              #{visible},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertMenu(SystemMenuEntity entity);

    @Update("""
            UPDATE wf_menu
            SET parent_menu_id = #{parentMenuId},
                menu_name = #{menuName},
                menu_type = #{menuType},
                route_path = #{routePath},
                component_path = #{componentPath},
                permission_code = #{permissionCode},
                icon_name = #{iconName},
                sort_order = #{sortOrder},
                visible = #{visible},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateMenu(SystemMenuEntity entity);
}
