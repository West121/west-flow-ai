ALTER TABLE wf_process_link ADD child_start_decision_reason VARCHAR(128);

ALTER TABLE wf_runtime_append_link ADD called_version_policy VARCHAR(64);
ALTER TABLE wf_runtime_append_link ADD called_version INTEGER;
ALTER TABLE wf_runtime_append_link ADD resolved_target_mode VARCHAR(64);
ALTER TABLE wf_runtime_append_link ADD target_business_type VARCHAR(64);
ALTER TABLE wf_runtime_append_link ADD target_scene_code VARCHAR(128);
