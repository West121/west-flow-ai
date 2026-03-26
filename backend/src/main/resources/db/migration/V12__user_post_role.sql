ALTER TABLE wf_user_post
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS wf_user_post_role (
    id VARCHAR(64) PRIMARY KEY,
    user_post_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_user_post_role_post_role
    ON wf_user_post_role (user_post_id, role_id);

CREATE INDEX IF NOT EXISTS idx_wf_user_post_role_post
    ON wf_user_post_role (user_post_id);

CREATE INDEX IF NOT EXISTS idx_wf_user_post_role_role
    ON wf_user_post_role (role_id);

INSERT INTO wf_user_post_role (id, user_post_id, role_id, created_at)
SELECT
    'upr_boot_' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY up.id, ur.role_id) AS VARCHAR), 4, '0'),
    up.id,
    ur.role_id,
    CURRENT_TIMESTAMP
FROM wf_user_post up
INNER JOIN wf_user_role ur ON ur.user_id = up.user_id
WHERE up.is_primary = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM wf_user_post_role upr
      WHERE upr.user_post_id = up.id
        AND upr.role_id = ur.role_id
  );
