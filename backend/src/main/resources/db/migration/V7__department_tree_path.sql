ALTER TABLE wf_department ADD COLUMN IF NOT EXISTS root_department_id VARCHAR(64);
ALTER TABLE wf_department ADD COLUMN IF NOT EXISTS tree_level INTEGER;
ALTER TABLE wf_department ADD COLUMN IF NOT EXISTS tree_path VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_wf_department_parent_department_id ON wf_department (parent_department_id);
CREATE INDEX IF NOT EXISTS idx_wf_department_root_department_id ON wf_department (root_department_id);
CREATE INDEX IF NOT EXISTS idx_wf_department_tree_level ON wf_department (tree_level);
CREATE INDEX IF NOT EXISTS idx_wf_department_tree_path ON wf_department (tree_path);
CREATE INDEX IF NOT EXISTS idx_wf_department_company_tree_path ON wf_department (company_id, tree_path);
