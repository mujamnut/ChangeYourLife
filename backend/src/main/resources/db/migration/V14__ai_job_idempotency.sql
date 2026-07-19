ALTER TABLE ai_jobs
    ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

ALTER TABLE ai_jobs
    ADD COLUMN IF NOT EXISTS request_fingerprint TEXT NOT NULL DEFAULT '';

UPDATE ai_jobs
SET idempotency_key = job_id
WHERE idempotency_key IS NULL OR idempotency_key = '';

ALTER TABLE ai_jobs
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_jobs_user_idempotency_key
    ON ai_jobs (user_id, idempotency_key);
