package com.westflow.identity.mapper;

import com.westflow.identity.dto.CurrentUserResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 登录态权限与数据范围查询映射。
 */
@Mapper
public interface IdentityAccessMapper {

    @Select("""
            SELECT DISTINCT r.role_code
            FROM wf_user_role ur
            INNER JOIN wf_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
            ORDER BY r.role_code ASC
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") String userId);

    @Select("""
            SELECT DISTINCT m.permission_code
            FROM wf_user_role ur
            INNER JOIN wf_role r ON r.id = ur.role_id
            INNER JOIN wf_role_menu rm ON rm.role_id = r.id
            INNER JOIN wf_menu m ON m.id = rm.menu_id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
              AND m.enabled = TRUE
              AND m.permission_code IS NOT NULL
              AND m.permission_code <> ''
            ORDER BY m.permission_code ASC
            """)
    List<String> selectPermissionsByUserId(@Param("userId") String userId);

    @Select("""
            SELECT DISTINCT
              rds.scope_type,
              rds.scope_value
            FROM wf_user_role ur
            INNER JOIN wf_role r ON r.id = ur.role_id
            INNER JOIN wf_role_data_scope rds ON rds.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
            ORDER BY rds.scope_type ASC, rds.scope_value ASC
            """)
    List<CurrentUserResponse.DataScope> selectDataScopesByUserId(@Param("userId") String userId);

    @Select("""
            SELECT DISTINCT
              m.id,
              m.menu_name AS title,
              m.route_path AS path
            FROM wf_user_role ur
            INNER JOIN wf_role r ON r.id = ur.role_id
            INNER JOIN wf_role_menu rm ON rm.role_id = r.id
            INNER JOIN wf_menu m ON m.id = rm.menu_id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
              AND m.enabled = TRUE
              AND m.visible = TRUE
              AND m.route_path IS NOT NULL
              AND m.route_path <> ''
            ORDER BY path ASC, title ASC
            """)
    List<CurrentUserResponse.MenuItem> selectMenusByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
              principal_user_id AS principalUserId,
              delegate_user_id AS delegateUserId,
              status
            FROM wf_delegation
            WHERE delegate_user_id = #{userId}
            ORDER BY created_at ASC
            """)
    List<CurrentUserResponse.Delegation> selectDelegationsByDelegateUserId(@Param("userId") String userId);
}
