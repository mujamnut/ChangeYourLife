CREATE TABLE IF NOT EXISTS page_blocks (
    id TEXT PRIMARY KEY,
    page_id TEXT NOT NULL REFERENCES pages(id) ON DELETE CASCADE,
    parent_block_id TEXT REFERENCES page_blocks(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    text TEXT NOT NULL DEFAULT '',
    rich_text_json TEXT NOT NULL DEFAULT '[]',
    media_json TEXT NOT NULL DEFAULT '[]',
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_page_blocks_page_id_sort ON page_blocks (page_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_page_blocks_parent_block_id ON page_blocks (parent_block_id);

CREATE TABLE IF NOT EXISTS page_properties (
    id TEXT PRIMARY KEY,
    page_id TEXT NOT NULL REFERENCES pages(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    value TEXT NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_page_properties_page_id_sort ON page_properties (page_id, sort_order);

CREATE TABLE IF NOT EXISTS page_tables (
    id TEXT PRIMARY KEY,
    page_id TEXT NOT NULL REFERENCES pages(id) ON DELETE CASCADE,
    block_id TEXT NOT NULL REFERENCES page_blocks(id) ON DELETE CASCADE,
    title TEXT NOT NULL DEFAULT '',
    view TEXT NOT NULL DEFAULT 'Table',
    view_config_json TEXT NOT NULL DEFAULT '{}',
    sort_json TEXT NOT NULL DEFAULT '{}',
    filter_json TEXT NOT NULL DEFAULT '{}',
    group_by_column_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_page_tables_page_id ON page_tables (page_id);
CREATE INDEX IF NOT EXISTS idx_page_tables_block_id ON page_tables (block_id);

CREATE TABLE IF NOT EXISTS page_table_columns (
    id TEXT PRIMARY KEY,
    table_id TEXT NOT NULL REFERENCES page_tables(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    config_json TEXT NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_page_table_columns_table_id_sort ON page_table_columns (table_id, sort_order);

CREATE TABLE IF NOT EXISTS page_table_rows (
    id TEXT PRIMARY KEY,
    table_id TEXT NOT NULL REFERENCES page_tables(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    content_json TEXT NOT NULL DEFAULT '[]',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_page_table_rows_table_id_sort ON page_table_rows (table_id, sort_order);

CREATE TABLE IF NOT EXISTS page_table_cells (
    row_id TEXT NOT NULL REFERENCES page_table_rows(id) ON DELETE CASCADE,
    column_id TEXT NOT NULL REFERENCES page_table_columns(id) ON DELETE CASCADE,
    value TEXT NOT NULL DEFAULT '',
    value_json TEXT NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    PRIMARY KEY(row_id, column_id)
);

CREATE INDEX IF NOT EXISTS idx_page_table_cells_column_id ON page_table_cells (column_id);
