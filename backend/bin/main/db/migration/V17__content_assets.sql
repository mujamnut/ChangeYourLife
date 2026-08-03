CREATE UNIQUE INDEX IF NOT EXISTS uq_workspaces_id_user_id
    ON workspaces (id, user_id);

CREATE TABLE IF NOT EXISTS content_assets (
    id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL,
    page_id TEXT REFERENCES pages(id) ON DELETE SET NULL,
    kind TEXT NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    mime_type TEXT NOT NULL,
    original_name TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 TEXT NOT NULL,
    status TEXT NOT NULL,
    error_code TEXT,
    idempotency_key TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    storage_deleted_at BIGINT,
    PRIMARY KEY (user_id, id),
    CONSTRAINT fk_content_assets_workspace_owner
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspaces(id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_content_assets_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_content_assets_kind CHECK (kind IN ('image', 'pdf', 'text', 'file')),
    CONSTRAINT chk_content_assets_status CHECK (
        status IN ('upload_queued', 'remote_ready', 'retryable_failure', 'permanent_failure', 'deleted')
    ),
    CONSTRAINT chk_content_assets_size CHECK (size_bytes > 0),
    CONSTRAINT chk_content_assets_sha256 CHECK (sha256 ~ '^[A-Fa-f0-9]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_content_assets_user_workspace_updated
    ON content_assets (user_id, workspace_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_assets_user_page_updated
    ON content_assets (user_id, page_id, updated_at DESC)
    WHERE page_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_content_assets_user_status_updated
    ON content_assets (user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_assets_sha256
    ON content_assets (sha256);

CREATE INDEX IF NOT EXISTS idx_content_assets_orphan_uploads
    ON content_assets (created_at)
    WHERE status IN ('upload_queued', 'retryable_failure') AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_content_assets_pending_storage_delete
    ON content_assets (deleted_at)
    WHERE deleted_at IS NOT NULL AND storage_deleted_at IS NULL;
