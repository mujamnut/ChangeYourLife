CREATE INDEX IF NOT EXISTS idx_pages_workspace_deleted_updated
    ON pages (workspace_id, deleted_at, updated_at);

CREATE INDEX IF NOT EXISTS idx_pages_search_tsv
    ON pages USING GIN (
        to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(content, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_blocks_search_tsv
    ON page_blocks USING GIN (
        to_tsvector('simple', COALESCE(text, '') || ' ' || COALESCE(metadata_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_properties_search_tsv
    ON page_properties USING GIN (
        to_tsvector('simple', COALESCE(name, '') || ' ' || COALESCE(value, '') || ' ' || COALESCE(metadata_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_tables_search_tsv
    ON page_tables USING GIN (
        to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(view_config_json, '') || ' ' || COALESCE(filter_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_table_columns_search_tsv
    ON page_table_columns USING GIN (
        to_tsvector('simple', COALESCE(name, '') || ' ' || COALESCE(type, '') || ' ' || COALESCE(config_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_table_rows_search_tsv
    ON page_table_rows USING GIN (
        to_tsvector('simple', COALESCE(metadata_json, '') || ' ' || COALESCE(content_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_page_table_cells_search_tsv
    ON page_table_cells USING GIN (
        to_tsvector('simple', COALESCE(value, '') || ' ' || COALESCE(value_json, ''))
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_search_tsv
    ON chat_messages USING GIN (
        to_tsvector('simple', COALESCE(content, ''))
    );
