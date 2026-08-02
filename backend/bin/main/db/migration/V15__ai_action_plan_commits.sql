CREATE TABLE IF NOT EXISTS ai_action_plan_commits (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    audit_id TEXT NOT NULL,
    workspace_id TEXT NOT NULL,
    state TEXT NOT NULL,
    action_count INTEGER NOT NULL,
    mutation_count INTEGER NOT NULL,
    result_json TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT ai_action_plan_commits_state_check
        CHECK (state IN ('CLAIMED', 'APPLIED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_action_plan_commits_user_audit
    ON ai_action_plan_commits (user_id, audit_id);

CREATE INDEX IF NOT EXISTS idx_ai_action_plan_commits_user_updated
    ON ai_action_plan_commits (user_id, updated_at DESC);
