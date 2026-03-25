UPDATE wf_menu
SET route_path = '/oa/leave/list',
    component_path = 'oa/leave/list',
    permission_code = 'oa:leave:list',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_oa_leave';

UPDATE wf_menu
SET route_path = '/oa/expense/list',
    component_path = 'oa/expense/list',
    permission_code = 'oa:expense:list',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_oa_expense';

UPDATE wf_menu
SET route_path = '/oa/common/list',
    component_path = 'oa/common/list',
    permission_code = 'oa:common:list',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_oa_common';
