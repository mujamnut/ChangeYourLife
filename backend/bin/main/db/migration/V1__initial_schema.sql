CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS workspaces (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_workspaces_user_id ON workspaces (user_id);

CREATE TABLE IF NOT EXISTS pages (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    parent_page_id TEXT REFERENCES pages(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_pages_workspace_id ON pages (workspace_id);
CREATE INDEX IF NOT EXISTS idx_pages_parent_page_id ON pages (parent_page_id);
CREATE INDEX IF NOT EXISTS idx_pages_updated_at ON pages (updated_at);

CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    page_id TEXT REFERENCES pages(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    due_at BIGINT,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_tasks_workspace_id ON tasks (workspace_id);
CREATE INDEX IF NOT EXISTS idx_tasks_page_id ON tasks (page_id);
CREATE INDEX IF NOT EXISTS idx_tasks_due_at ON tasks (due_at);
CREATE INDEX IF NOT EXISTS idx_tasks_updated_at ON tasks (updated_at);

CREATE TABLE IF NOT EXISTS reminders (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    page_id TEXT REFERENCES pages(id) ON DELETE SET NULL,
    task_id TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    remind_at BIGINT NOT NULL,
    is_done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_reminders_workspace_id ON reminders (workspace_id);
CREATE INDEX IF NOT EXISTS idx_reminders_page_id ON reminders (page_id);
CREATE INDEX IF NOT EXISTS idx_reminders_task_id ON reminders (task_id);
CREATE INDEX IF NOT EXISTS idx_reminders_remind_at ON reminders (remind_at);

