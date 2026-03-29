CREATE INDEX IF NOT EXISTS idx_wf_task_group_instance_node_status_created
    ON wf_task_group (process_instance_id, node_id, group_status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_wf_task_group_member_group_sequence
    ON wf_task_group_member (task_group_id, sequence_no);

CREATE INDEX IF NOT EXISTS idx_wf_task_group_member_instance_node_task
    ON wf_task_group_member (process_instance_id, node_id, task_id);

CREATE INDEX IF NOT EXISTS idx_wf_task_group_member_assignee_status
    ON wf_task_group_member (assignee_user_id, member_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wf_business_process_link_business
    ON wf_business_process_link (business_type, business_id, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wf_business_process_link_instance
    ON wf_business_process_link (process_instance_id, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wf_process_link_parent_created
    ON wf_process_link (parent_instance_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_wf_process_link_root_created
    ON wf_process_link (root_instance_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_wf_runtime_append_link_root_created
    ON wf_runtime_append_link (root_instance_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_wf_runtime_append_link_parent_created
    ON wf_runtime_append_link (parent_instance_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_wf_runtime_append_link_source_task
    ON wf_runtime_append_link (source_task_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_wf_runtime_append_link_target_task
    ON wf_runtime_append_link (target_task_id);

CREATE INDEX IF NOT EXISTS idx_wf_runtime_append_link_target_instance
    ON wf_runtime_append_link (target_instance_id);

CREATE INDEX IF NOT EXISTS idx_wf_workflow_operation_log_instance_created
    ON wf_workflow_operation_log (process_instance_id, created_at ASC, id ASC);
