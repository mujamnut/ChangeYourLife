CREATE TABLE IF NOT EXISTS chat_attachments (
    id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_client_id TEXT NOT NULL,
    message_client_id TEXT,
    kind TEXT NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    mime_type TEXT NOT NULL,
    original_name TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_ms BIGINT NOT NULL,
    sha256 TEXT NOT NULL,
    status TEXT NOT NULL,
    transcript TEXT,
    transcript_language TEXT,
    transcription_provider TEXT,
    transcription_model TEXT,
    transcription_version TEXT,
    error_code TEXT,
    idempotency_key TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    storage_deleted_at BIGINT,
    PRIMARY KEY (user_id, id),
    CONSTRAINT uq_chat_attachments_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_chat_attachments_kind CHECK (kind IN ('image', 'text', 'audio', 'unknown')),
    CONSTRAINT chk_chat_attachments_status CHECK (
        status IN (
            'local_ready',
            'upload_queued',
            'uploading',
            'pending_upload',
            'uploaded',
            'transcribing',
            'ready',
            'ai_queued',
            'ai_processing',
            'completed',
            'retryable_failure',
            'permanent_failure',
            'deleted'
        )
    ),
    CONSTRAINT chk_chat_attachments_size CHECK (size_bytes >= 0),
    CONSTRAINT chk_chat_attachments_duration CHECK (duration_ms >= 0),
    CONSTRAINT chk_chat_attachments_sha256 CHECK (sha256 ~ '^[A-Fa-f0-9]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_user_session_updated
    ON chat_attachments (user_id, session_client_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_user_status_updated
    ON chat_attachments (user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_orphan_uploads
    ON chat_attachments (created_at)
    WHERE status IN ('pending_upload', 'retryable_failure') AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_attachments_pending_storage_delete
    ON chat_attachments (deleted_at)
    WHERE deleted_at IS NOT NULL AND storage_deleted_at IS NULL;
