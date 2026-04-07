ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS implementation_owner VARCHAR(128);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS implementation_summary VARCHAR(4000);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS implementation_started_at TIMESTAMP;
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS validation_owner VARCHAR(128);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS validation_summary VARCHAR(4000);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS closed_by VARCHAR(128);
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE plm_ecr_change
    ADD COLUMN IF NOT EXISTS close_comment VARCHAR(4000);

ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS implementation_owner VARCHAR(128);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS implementation_summary VARCHAR(4000);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS implementation_started_at TIMESTAMP;
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS validation_owner VARCHAR(128);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS validation_summary VARCHAR(4000);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS closed_by VARCHAR(128);
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE plm_eco_execution
    ADD COLUMN IF NOT EXISTS close_comment VARCHAR(4000);

ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS implementation_owner VARCHAR(128);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS implementation_summary VARCHAR(4000);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS implementation_started_at TIMESTAMP;
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS validation_owner VARCHAR(128);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS validation_summary VARCHAR(4000);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS closed_by VARCHAR(128);
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE plm_material_change
    ADD COLUMN IF NOT EXISTS close_comment VARCHAR(4000);

CREATE TABLE IF NOT EXISTS plm_bill_affected_item (
    id VARCHAR(64) PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    bill_id VARCHAR(64) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    item_code VARCHAR(128),
    item_name VARCHAR(255),
    before_version VARCHAR(128),
    after_version VARCHAR(128),
    change_action VARCHAR(32) NOT NULL,
    owner_user_id VARCHAR(64),
    remark VARCHAR(1000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plm_bill_affected_item_bill
    ON plm_bill_affected_item (business_type, bill_id, sort_order);
