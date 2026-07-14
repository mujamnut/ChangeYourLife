CREATE TABLE IF NOT EXISTS ai_skills (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 64),
    when_to_use TEXT NOT NULL CHECK (char_length(when_to_use) BETWEEN 1 AND 320),
    instructions TEXT NOT NULL CHECK (char_length(instructions) BETWEEN 1 AND 2000),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    CONSTRAINT ai_skills_updated_after_created CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_ai_skills_workspace_updated
    ON ai_skills (workspace_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ai_skills_workspace_active
    ON ai_skills (workspace_id, is_enabled, name)
    WHERE deleted_at IS NULL;
