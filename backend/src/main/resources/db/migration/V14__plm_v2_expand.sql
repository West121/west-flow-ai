ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS change_category VARCHAR(64);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS target_version VARCHAR(128);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS affected_objects_text VARCHAR(4000);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS impact_scope VARCHAR(1000);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32);

ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS implementation_owner VARCHAR(128);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS target_version VARCHAR(128);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS rollout_scope VARCHAR(1000);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS validation_plan VARCHAR(4000);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS rollback_plan VARCHAR(4000);

ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS specification_change VARCHAR(2000);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS old_value VARCHAR(1000);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS new_value VARCHAR(1000);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS uom VARCHAR(64);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS affected_systems_text VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_plm_ecr_creator_status_updated
    ON plm_ecr_change (creator_user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_plm_ecr_scene_created
    ON plm_ecr_change (scene_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_plm_eco_creator_status_updated
    ON plm_eco_execution (creator_user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_plm_eco_scene_created
    ON plm_eco_execution (scene_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_plm_material_creator_status_updated
    ON plm_material_change (creator_user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_plm_material_scene_created
    ON plm_material_change (scene_code, created_at DESC);
