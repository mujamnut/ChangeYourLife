ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS client_id TEXT;

UPDATE workspaces
SET client_id = id
WHERE client_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspaces_user_client_id
    ON workspaces (user_id, client_id);
