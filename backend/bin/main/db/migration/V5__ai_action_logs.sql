CREATE TABLE IF NOT EXISTS ai_action_logs (
    audit_id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_message_id TEXT NOT NULL,
    response_message_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    workspace_id TEXT NOT NULL,
    mode TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    schema_name TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    proposed_actions_json TEXT NOT NULL,
    executed_actions_json TEXT NOT NULL,
    validation_issues_json TEXT NOT NULL,
    execution_messages_json TEXT NOT NULL,
    undo_commands_json TEXT NOT NULL DEFAULT '[]',
    undo_state TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, audit_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_action_logs_user_workspace_updated
    ON ai_action_logs (user_id, workspace_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ai_action_logs_user_session_created
    ON ai_action_logs (user_id, session_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_action_logs_user_request_message
    ON ai_action_logs (user_id, request_message_id);

CREATE INDEX IF NOT EXISTS idx_ai_action_logs_user_response_message
    ON ai_action_logs (user_id, response_message_id);
