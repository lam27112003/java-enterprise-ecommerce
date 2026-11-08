-- Allow SUSPENDED status for account lifecycle controls (Phase 2 remediation F-02).
-- Drop and recreate check constraint for PostgreSQL and H2 PostgreSQL mode.

ALTER TABLE users DROP CONSTRAINT ck_users_status;

ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'DISABLED', 'SUSPENDED'));
