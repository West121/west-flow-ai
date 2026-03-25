ALTER TABLE oa_leave_bill
    ADD COLUMN IF NOT EXISTS leave_type VARCHAR(32);

ALTER TABLE oa_leave_bill
    ADD COLUMN IF NOT EXISTS urgent BOOLEAN;

ALTER TABLE oa_leave_bill
    ADD COLUMN IF NOT EXISTS manager_user_id VARCHAR(64);

UPDATE oa_leave_bill
SET leave_type = COALESCE(NULLIF(leave_type, ''), 'ANNUAL'),
    urgent = COALESCE(urgent, FALSE),
    manager_user_id = COALESCE(NULLIF(manager_user_id, ''), 'usr_002')
WHERE leave_type IS NULL
   OR urgent IS NULL
   OR manager_user_id IS NULL
   OR manager_user_id = '';

UPDATE wf_process_definition
SET dsl_json = REPLACE(
        REPLACE(dsl_json, '"fieldKey":"leaveDays"', '"fieldKey":"days"'),
        'leaveDays >= 5',
        'days >= 5'
    ),
    bpmn_xml = REPLACE(bpmn_xml, 'leaveDays', 'days')
WHERE process_key = 'oa_leave';

UPDATE wf_process_definition
SET dsl_json = REPLACE(
        dsl_json,
        '{"fieldKey":"managerUserId","label":"直属负责人","valueType":"USER","required":false}]',
        '{"fieldKey":"reason","label":"请假原因","valueType":"STRING","required":true},{"fieldKey":"managerUserId","label":"直属负责人","valueType":"USER","required":true}]'
    )
WHERE process_key = 'oa_leave'
  AND dsl_json NOT LIKE '%"fieldKey":"reason","label":"请假原因"%';
