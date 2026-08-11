ALTER TABLE page_table_cells
    ADD COLUMN table_id TEXT;

UPDATE page_table_cells cell
SET table_id = column_projection.table_id
FROM page_table_columns column_projection
WHERE column_projection.id = cell.column_id;

ALTER TABLE page_table_cells
    ALTER COLUMN table_id SET NOT NULL;

ALTER TABLE page_table_cells
    DROP CONSTRAINT page_table_cells_row_id_fkey;

ALTER TABLE page_table_rows
    DROP CONSTRAINT page_table_rows_pkey,
    ADD CONSTRAINT page_table_rows_pkey PRIMARY KEY (id, table_id);

ALTER TABLE page_table_cells
    ADD CONSTRAINT page_table_cells_row_table_fkey
        FOREIGN KEY (row_id, table_id)
        REFERENCES page_table_rows (id, table_id)
        ON DELETE CASCADE;

CREATE INDEX idx_page_table_cells_table_id
    ON page_table_cells (table_id);
