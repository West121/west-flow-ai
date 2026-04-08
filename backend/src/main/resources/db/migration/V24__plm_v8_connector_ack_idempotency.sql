ALTER TABLE plm_external_ack
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plm_external_ack_job_idempotency
    ON plm_external_ack (job_id, idempotency_key);
