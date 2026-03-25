DELETE FROM wf_role_menu
WHERE menu_id IN ('menu_system_dict_type', 'menu_system_dict_item');

DELETE FROM wf_menu
WHERE id IN ('menu_system_dict_type', 'menu_system_dict_item');

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
)
SELECT
    'menu_system_dict',
    'menu_system',
    '字典管理',
    'MENU',
    '/system/dict-types/list',
    'system/dict-types/list',
    'system:dict:view',
    'BookText',
    90,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_menu WHERE id = 'menu_system_dict');

INSERT INTO wf_role_menu (id, role_id, menu_id, created_at)
SELECT 'rm_011', 'role_process_admin', 'menu_system_dict', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_role_menu WHERE id = 'rm_011');
