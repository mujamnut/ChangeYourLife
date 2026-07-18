ALTER TABLE pages
    ADD COLUMN IF NOT EXISTS content_projection_updated_at BIGINT;

CREATE INDEX IF NOT EXISTS idx_pages_content_projection_pending
    ON pages (updated_at)
    WHERE content_projection_updated_at IS NULL;
