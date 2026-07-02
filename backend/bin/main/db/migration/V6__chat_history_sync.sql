CREATE TABLE IF NOT EXISTS chat_sessions (
    id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope_id TEXT NOT NULL,
    title TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    PRIMARY KEY (user_id, id)
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_scope_updated
    ON chat_sessions (user_id, scope_id, updated_at);

CREATE TABLE IF NOT EXISTS chat_messages (
    id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    scope_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    page_links_json TEXT NOT NULL DEFAULT '[]',
    action_metadata_json TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, id),
    FOREIGN KEY (user_id, session_id)
        REFERENCES chat_sessions(user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user_session_created
    ON chat_messages (user_id, session_id, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user_scope_updated
    ON chat_messages (user_id, scope_id, updated_at);
