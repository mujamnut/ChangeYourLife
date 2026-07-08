CREATE TABLE IF NOT EXISTS ai_jobs (
    job_id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    phase TEXT NOT NULL,
    diagnostics_json TEXT NOT NULL DEFAULT '{}',
    result_json TEXT,
    error TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, job_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_user_status_updated
    ON ai_jobs (user_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_created
    ON ai_jobs (created_at);
