ALTER TABLE page_table_rows
    ADD COLUMN IF NOT EXISTS icon TEXT NOT NULL DEFAULT '';

ALTER TABLE page_table_rows
    ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE page_table_rows
    ADD COLUMN IF NOT EXISTS metadata_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE page_table_cells
    ADD COLUMN IF NOT EXISTS value_type TEXT NOT NULL DEFAULT 'Text';

CREATE INDEX IF NOT EXISTS idx_page_table_rows_table_id_favorite
    ON page_table_rows (table_id, is_favorite);
