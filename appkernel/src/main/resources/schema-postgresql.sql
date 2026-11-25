CREATE TABLE IF NOT EXISTS extensions
(
    name
    VARCHAR
(
    255
) NOT NULL,
    data BYTEA,
    version BIGINT,
    PRIMARY KEY
(
    name
)
    );
@
@
CREATE TABLE IF NOT EXISTS audit_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    trace_flow_id VARCHAR(64),
    parent_audit_id VARCHAR(64),
    step_index INT NOT NULL DEFAULT 1,
    uid VARCHAR(128),
    operation VARCHAR(64) NOT NULL,
    resource_type VARCHAR(128),
    resource_name VARCHAR(512),
    request_method VARCHAR(16),
    request_path VARCHAR(2048),
    ip VARCHAR(128),
    user_agent VARCHAR(1024),
    occurred_at TIMESTAMPTZ NOT NULL,
    result VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(128),
    duration_ms BIGINT NOT NULL,
    context_json TEXT,
    action_snapshot TEXT
);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_occurred ON audit_record (occurred_at);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_uid ON audit_record (uid);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_trace ON audit_record (trace_id);
@
@
CREATE TABLE IF NOT EXISTS audit_record_archive (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    trace_flow_id VARCHAR(64),
    parent_audit_id VARCHAR(64),
    step_index INT NOT NULL DEFAULT 1,
    uid VARCHAR(128),
    operation VARCHAR(64) NOT NULL,
    resource_type VARCHAR(128),
    resource_name VARCHAR(512),
    request_method VARCHAR(16),
    request_path VARCHAR(2048),
    ip VARCHAR(128),
    user_agent VARCHAR(1024),
    occurred_at TIMESTAMPTZ NOT NULL,
    result VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(128),
    duration_ms BIGINT NOT NULL,
    context_json TEXT,
    action_snapshot TEXT,
    is_archived_at TIMESTAMPTZ NOT NULL
);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_archive_occurred ON audit_record_archive (occurred_at);
@
@
ALTER TABLE audit_record ADD COLUMN IF NOT EXISTS trace_flow_id VARCHAR(64);
@
@
ALTER TABLE audit_record ADD COLUMN IF NOT EXISTS parent_audit_id VARCHAR(64);
@
@
ALTER TABLE audit_record ADD COLUMN IF NOT EXISTS step_index INT NOT NULL DEFAULT 1;
@
@
ALTER TABLE audit_record ADD COLUMN IF NOT EXISTS action_snapshot TEXT;
@
@
ALTER TABLE audit_record_archive ADD COLUMN IF NOT EXISTS trace_flow_id VARCHAR(64);
@
@
ALTER TABLE audit_record_archive ADD COLUMN IF NOT EXISTS parent_audit_id VARCHAR(64);
@
@
ALTER TABLE audit_record_archive ADD COLUMN IF NOT EXISTS step_index INT NOT NULL DEFAULT 1;
@
@
ALTER TABLE audit_record_archive ADD COLUMN IF NOT EXISTS action_snapshot TEXT;
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_flow ON audit_record (trace_flow_id);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_parent ON audit_record (parent_audit_id);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_record_uid_occurred ON audit_record (uid, occurred_at);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_archive_flow ON audit_record_archive (trace_flow_id);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_archive_parent ON audit_record_archive (parent_audit_id);
@
@
CREATE INDEX IF NOT EXISTS idx_audit_archive_uid_occurred ON audit_record_archive (uid, occurred_at);
@
@
CREATE TABLE IF NOT EXISTS audit_archive_guard (
    id INT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL
);
@
@
INSERT INTO audit_archive_guard (id, enabled) VALUES (1, FALSE) ON CONFLICT (id) DO NOTHING;
@
@
UPDATE audit_archive_guard SET enabled = FALSE WHERE id = 1;
@
@
CREATE OR REPLACE FUNCTION pudans_audit_append_only() RETURNS trigger AS $$
BEGIN
    IF
(TG_OP = 'DELETE') THEN
        IF NOT EXISTS (SELECT 1 FROM audit_archive_guard WHERE id = 1 AND enabled) THEN
            RAISE EXCEPTION 'audit records are append-only';
END IF;
ELSE
        RAISE EXCEPTION 'audit records are immutable';
END IF;
RETURN OLD;
END;
$$
LANGUAGE plpgsql;
@
@
DROP TRIGGER IF EXISTS audit_record_no_update ON audit_record;
@
@
CREATE TRIGGER audit_record_no_update BEFORE
UPDATE ON audit_record FOR EACH ROW EXECUTE FUNCTION pudans_audit_append_only();
@
@
DROP TRIGGER IF EXISTS audit_record_no_delete ON audit_record;
@
@
CREATE TRIGGER audit_record_no_delete BEFORE DELETE
ON audit_record FOR EACH ROW EXECUTE FUNCTION pudans_audit_append_only();
@
@
CREATE OR REPLACE FUNCTION pudans_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE
EXCEPTION 'archived audit records are immutable';
END;
$$
LANGUAGE plpgsql;
@
@
DROP TRIGGER IF EXISTS audit_archive_no_update ON audit_record_archive;
@
@
CREATE TRIGGER audit_archive_no_update BEFORE
UPDATE ON audit_record_archive FOR EACH ROW EXECUTE FUNCTION pudans_audit_immutable();
@
@
DROP TRIGGER IF EXISTS audit_archive_no_delete ON audit_record_archive;
@
@
CREATE TRIGGER audit_archive_no_delete BEFORE DELETE
ON audit_record_archive FOR EACH ROW EXECUTE FUNCTION pudans_audit_immutable();
@
ALTER TABLE audit_record ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(128);
@
ALTER TABLE audit_record_archive ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(128);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_record_fingerprint ON audit_record (fingerprint);
@
DROP INDEX IF EXISTS idx_audit_record_fingerprint;
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_archive_fingerprint ON audit_record_archive (fingerprint);
@
DROP INDEX IF EXISTS idx_audit_archive_fingerprint;
@
CREATE TABLE IF NOT EXISTS ai_channels (
    id UUID NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    protocol VARCHAR(64) NOT NULL,
    base_url TEXT,
    secret_ref VARCHAR(255),
    secret_keys_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    credential_ciphertext_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    credential_status_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    timeout_ms INT NOT NULL DEFAULT 30000,
    max_concurrency INT NOT NULL DEFAULT 1,
    weight INT NOT NULL DEFAULT 100,
    rate_limit INT,
    config_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(provider) <> ''),
    CHECK (btrim(protocol) <> ''),
    CHECK (timeout_ms > 0),
    CHECK (max_concurrency > 0),
    CHECK (weight >= 0),
    CHECK (rate_limit IS NULL OR rate_limit >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_channels_workspace_status
    ON ai_channels (workspace_id, status);
@
CREATE INDEX IF NOT EXISTS idx_ai_channels_secret_ref
    ON ai_channels (secret_ref) WHERE secret_ref IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS ai_models (
    id UUID NOT NULL PRIMARY KEY,
    logical_name VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    capabilities_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    context_window INT,
    max_output_tokens INT,
    parameter_schema_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(logical_name) <> ''),
    CHECK (btrim(display_name) <> ''),
    CHECK (context_window IS NULL OR context_window > 0),
    CHECK (max_output_tokens IS NULL OR max_output_tokens > 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_models_status ON ai_models (status);
@
CREATE TABLE IF NOT EXISTS ai_channel_models (
    id UUID NOT NULL PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES ai_channels (id),
    model_id UUID NOT NULL REFERENCES ai_models (id),
    upstream_model_key VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    weight INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    parameter_overrides_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (channel_id, model_id),
    CHECK (btrim(upstream_model_key) <> ''),
    CHECK (priority >= 0),
    CHECK (weight >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_channel_models_model
    ON ai_channel_models (model_id, enabled, priority);
@
CREATE INDEX IF NOT EXISTS idx_ai_channel_models_channel
    ON ai_channel_models (channel_id, enabled);
@
CREATE TABLE IF NOT EXISTS ai_default_models (
    id UUID NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, capability),
    CHECK (capability IN (
        'chat',
        'image',
        'video',
        'audio',
        'embedding',
        'transcription',
        'memory_extraction'
    ))
);
@
CREATE INDEX IF NOT EXISTS idx_ai_default_models_model
    ON ai_default_models (model_id);
@
CREATE TABLE IF NOT EXISTS ai_price_rules (
    id UUID NOT NULL PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(255) NOT NULL,
    model_id UUID REFERENCES ai_models (id),
    mcp_tool_key VARCHAR(255),
    pricing_mode VARCHAR(32) NOT NULL,
    input_coefficient NUMERIC(20, 4) NOT NULL DEFAULT 0,
    cached_input_coefficient NUMERIC(20, 4) NOT NULL DEFAULT 0,
    output_coefficient NUMERIC(20, 4) NOT NULL DEFAULT 0,
    mcp_call_coefficient NUMERIC(20, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(32),
    unit_credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    base_credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    minimum_credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    peak_windows_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    peak_multiplier NUMERIC(10, 4) NOT NULL DEFAULT 1,
    offpeak_multiplier NUMERIC(10, 4) NOT NULL DEFAULT 1,
    quota_bucket VARCHAR(64),
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (target_type, target_key, effective_from),
    CHECK (target_type IN ('model', 'mcp_tool')),
    CHECK (
        (target_type = 'model' AND model_id IS NOT NULL AND mcp_tool_key IS NULL)
        OR (
            target_type = 'mcp_tool'
            AND model_id IS NULL
            AND mcp_tool_key IS NOT NULL
            AND btrim(mcp_tool_key) <> ''
        )
    ),
    CHECK (pricing_mode IN ('token', 'unit_credit', 'mcp_call')),
    CHECK (unit IS NULL OR unit IN (
        'request',
        'image',
        'video_second',
        'audio_second',
        'megapixel'
    )),
    CHECK (input_coefficient >= 0),
    CHECK (cached_input_coefficient >= 0),
    CHECK (output_coefficient >= 0),
    CHECK (mcp_call_coefficient >= 0),
    CHECK (unit_credits >= 0),
    CHECK (base_credits >= 0),
    CHECK (minimum_credits >= 0),
    CHECK (peak_multiplier >= 0),
    CHECK (offpeak_multiplier >= 0),
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_price_rules_model
    ON ai_price_rules (model_id, effective_from DESC) WHERE model_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_price_rules_mcp_tool
    ON ai_price_rules (mcp_tool_key, effective_from DESC) WHERE mcp_tool_key IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_price_rules_bucket
    ON ai_price_rules (quota_bucket, effective_from DESC) WHERE quota_bucket IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS ai_quota_plans (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    five_hour_credits NUMERIC(20, 4) NOT NULL,
    weekly_credits NUMERIC(20, 4) NOT NULL,
    five_hour_tool_calls INT NOT NULL,
    weekly_tool_calls INT NOT NULL,
    five_hour_mcp_calls INT NOT NULL,
    weekly_mcp_calls INT NOT NULL,
    five_hour_window_seconds INT NOT NULL DEFAULT 18000,
    weekly_window_days INT NOT NULL DEFAULT 7,
    weekly_anchor_mode VARCHAR(32) NOT NULL DEFAULT 'assignment_start',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(name) <> ''),
    CHECK (btrim(display_name) <> ''),
    CHECK (five_hour_credits >= 0),
    CHECK (weekly_credits >= 0),
    CHECK (five_hour_tool_calls >= 0),
    CHECK (weekly_tool_calls >= 0),
    CHECK (five_hour_mcp_calls >= 0),
    CHECK (weekly_mcp_calls >= 0),
    CHECK (five_hour_window_seconds = 18000),
    CHECK (weekly_window_days = 7),
    CHECK (weekly_anchor_mode = 'assignment_start')
);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_plans_status ON ai_quota_plans (status);
@
CREATE TABLE IF NOT EXISTS ai_quota_assignments (
    id UUID NOT NULL PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    plan_id UUID NOT NULL REFERENCES ai_quota_plans (id),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    weekly_anchor_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_type, subject_id, effective_from),
    CHECK (subject_type IN ('user', 'workspace', 'role')),
    CHECK (btrim(subject_id) <> ''),
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_assignments_subject
    ON ai_quota_assignments (subject_type, subject_id, status, effective_from DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_assignments_plan
    ON ai_quota_assignments (plan_id, status);
@
CREATE TABLE IF NOT EXISTS ai_quota_reservations (
    id UUID NOT NULL PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    plan_id UUID NOT NULL REFERENCES ai_quota_plans (id),
    run_id UUID,
    task_id UUID,
    reserved_credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    actual_credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    reserved_tool_calls INT NOT NULL DEFAULT 0,
    actual_tool_calls INT NOT NULL DEFAULT 0,
    reserved_mcp_calls INT NOT NULL DEFAULT 0,
    actual_mcp_calls INT NOT NULL DEFAULT 0,
    five_hour_bucket_at TIMESTAMPTZ NOT NULL,
    weekly_period_start TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at TIMESTAMPTZ,
    CHECK (subject_type IN ('user', 'workspace', 'role')),
    CHECK (btrim(subject_id) <> ''),
    CHECK (btrim(idempotency_key) <> ''),
    CHECK (reserved_credits >= 0),
    CHECK (actual_credits >= 0),
    CHECK (reserved_tool_calls >= 0),
    CHECK (actual_tool_calls >= 0),
    CHECK (reserved_mcp_calls >= 0),
    CHECK (actual_mcp_calls >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_reservations_subject
    ON ai_quota_reservations (subject_type, subject_id, status, weekly_period_start DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_reservations_expiry
    ON ai_quota_reservations (status, expires_at);
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_reservations_run
    ON ai_quota_reservations (run_id) WHERE run_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_quota_reservations_task
    ON ai_quota_reservations (task_id) WHERE task_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS catalog_entries (
    id UUID NOT NULL PRIMARY KEY,
    catalog_kind VARCHAR(64) NOT NULL,
    entry_key VARCHAR(128) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    parent_key VARCHAR(128),
    sort_order INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (catalog_kind, entry_key),
    CHECK (btrim(catalog_kind) <> ''),
    CHECK (btrim(entry_key) <> ''),
    CHECK (btrim(label) <> ''),
    CHECK (sort_order >= 0),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);
@
CREATE INDEX IF NOT EXISTS idx_catalog_entries_kind_enabled
    ON catalog_entries (catalog_kind, enabled, sort_order);
@
CREATE INDEX IF NOT EXISTS idx_catalog_entries_parent
    ON catalog_entries (catalog_kind, parent_key) WHERE parent_key IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS agent_sessions (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    title VARCHAR(512),
    profile VARCHAR(128),
    default_model_id UUID REFERENCES ai_models (id),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> '')
);
@
CREATE INDEX IF NOT EXISTS idx_agent_sessions_owner_workspace
    ON agent_sessions (owner_id, workspace_id, status, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_agent_sessions_last_message
    ON agent_sessions (last_message_at DESC) WHERE last_message_at IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS agent_messages (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES agent_sessions (id),
    run_id UUID,
    role VARCHAR(32) NOT NULL,
    content_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    asset_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    tool_calls_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    usage_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (role IN ('system', 'user', 'assistant', 'tool'))
);
@
CREATE INDEX IF NOT EXISTS idx_agent_messages_session
    ON agent_messages (session_id, created_at, id);
@
CREATE INDEX IF NOT EXISTS idx_agent_messages_run
    ON agent_messages (run_id, created_at) WHERE run_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS agent_runs (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES agent_sessions (id),
    input_message_id UUID,
    output_message_id UUID,
    status VARCHAR(32) NOT NULL,
    model_id UUID REFERENCES ai_models (id),
    answer_mode VARCHAR(32),
    reasoning VARCHAR(32),
    tool_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    quota_reservation_id UUID,
    provider_request_sent BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CHECK (started_at IS NULL OR started_at >= created_at),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_agent_runs_session
    ON agent_runs (session_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_agent_runs_status
    ON agent_runs (status, created_at);
@
CREATE INDEX IF NOT EXISTS idx_agent_runs_quota_reservation
    ON agent_runs (quota_reservation_id) WHERE quota_reservation_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS agent_approvals (
    id UUID NOT NULL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    tool_call_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    request_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    decision_jsonb JSONB,
    decided_by VARCHAR(255),
    decided_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (btrim(status) <> ''),
    CHECK (decided_at IS NULL OR decided_by IS NOT NULL)
);
@
CREATE INDEX IF NOT EXISTS idx_agent_approvals_run
    ON agent_approvals (run_id, status, expires_at);
@
CREATE INDEX IF NOT EXISTS idx_agent_approvals_tool_call
    ON agent_approvals (tool_call_id) WHERE tool_call_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_agent_approvals_expiry
    ON agent_approvals (status, expires_at);
@
CREATE TABLE IF NOT EXISTS agent_tool_calls (
    id UUID NOT NULL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    session_id UUID NOT NULL REFERENCES agent_sessions (id),
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    mcp BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(tool_name) <> ''),
    CHECK (btrim(idempotency_key) <> ''),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_run
    ON agent_tool_calls (run_id, started_at);
@
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_session
    ON agent_tool_calls (session_id, started_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_owner_workspace
    ON agent_tool_calls (owner_id, workspace_id, mcp, started_at DESC);
@
CREATE TABLE IF NOT EXISTS generation_tasks (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    session_id UUID REFERENCES agent_sessions (id),
    run_id UUID REFERENCES agent_runs (id),
    parent_task_id UUID REFERENCES generation_tasks (id),
    parent_asset_id UUID,
    media_type VARCHAR(32) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models (id),
    prompt TEXT,
    settings_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_asset_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    provider_request_sent BOOLEAN NOT NULL DEFAULT FALSE,
    provider_task_id VARCHAR(255),
    result_asset_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code VARCHAR(128),
    error_message TEXT,
    quota_reservation_id UUID,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(media_type) <> ''),
    CHECK (btrim(operation) <> ''),
    CHECK (btrim(idempotency_key) <> ''),
    CHECK (started_at IS NULL OR started_at >= created_at),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_generation_tasks_owner_workspace
    ON generation_tasks (owner_id, workspace_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_generation_tasks_status
    ON generation_tasks (status, created_at);
@
CREATE INDEX IF NOT EXISTS idx_generation_tasks_session
    ON generation_tasks (session_id, created_at DESC) WHERE session_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_generation_tasks_run
    ON generation_tasks (run_id) WHERE run_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_generation_tasks_parent
    ON generation_tasks (parent_task_id) WHERE parent_task_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS generation_attempts (
    id UUID NOT NULL PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES generation_tasks (id) ON DELETE CASCADE,
    attempt_no INT NOT NULL,
    channel_id UUID REFERENCES ai_channels (id),
    model_id UUID REFERENCES ai_models (id),
    provider_task_id VARCHAR(255),
    provider_request_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(128),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    UNIQUE (task_id, attempt_no),
    CHECK (attempt_no > 0),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_generation_attempts_task
    ON generation_attempts (task_id, started_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_generation_attempts_status
    ON generation_attempts (status, started_at);
@
CREATE TABLE IF NOT EXISTS assets (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    storage_file_id VARCHAR(255),
    generation_task_id UUID REFERENCES generation_tasks (id),
    parent_asset_id UUID REFERENCES assets (id),
    name VARCHAR(512),
    description TEXT,
    prompt TEXT,
    settings_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_type VARCHAR(255),
    size BIGINT,
    width INT,
    height INT,
    duration_ms BIGINT,
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (kind IN ('image', 'video', 'audio', 'document', 'other')),
    CHECK (source IN ('creation', 'chat', 'upload')),
    CHECK (size IS NULL OR size >= 0),
    CHECK (width IS NULL OR width >= 0),
    CHECK (height IS NULL OR height >= 0),
    CHECK (duration_ms IS NULL OR duration_ms >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_assets_owner_workspace
    ON assets (owner_id, workspace_id, kind, status, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_assets_generation_task
    ON assets (generation_task_id) WHERE generation_task_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_assets_parent
    ON assets (parent_asset_id) WHERE parent_asset_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_assets_storage_file
    ON assets (storage_file_id) WHERE storage_file_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS ai_usage_records (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    run_id UUID,
    task_id UUID,
    tool_call_id UUID,
    model_id UUID REFERENCES ai_models (id),
    channel_id UUID REFERENCES ai_channels (id),
    mcp_tool_key VARCHAR(255),
    units NUMERIC(20, 4) NOT NULL DEFAULT 0,
    credits NUMERIC(20, 4) NOT NULL DEFAULT 0,
    tool_calls INT NOT NULL DEFAULT 0,
    mcp_calls INT NOT NULL DEFAULT 0,
    pricing_rule_id UUID REFERENCES ai_price_rules (id),
    window_kind VARCHAR(32),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (units >= 0),
    CHECK (credits >= 0),
    CHECK (tool_calls >= 0),
    CHECK (mcp_calls >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_usage_records_owner_workspace
    ON ai_usage_records (owner_id, workspace_id, occurred_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_usage_records_run
    ON ai_usage_records (run_id, occurred_at) WHERE run_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_usage_records_task
    ON ai_usage_records (task_id, occurred_at) WHERE task_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_usage_records_tool_call
    ON ai_usage_records (tool_call_id) WHERE tool_call_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_ai_usage_records_model_channel
    ON ai_usage_records (model_id, channel_id, occurred_at DESC);
@
CREATE TABLE IF NOT EXISTS memory_sources (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    source_kind VARCHAR(64) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    session_id UUID,
    run_id UUID,
    task_id UUID,
    message_start_id UUID,
    message_end_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    content_summary TEXT,
    content_digest VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(source_kind) <> ''),
    CHECK (btrim(source_id) <> ''),
    CHECK (btrim(content_digest) <> '')
);
@
CREATE INDEX IF NOT EXISTS idx_memory_sources_owner_workspace
    ON memory_sources (owner_id, workspace_id, occurred_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_memory_sources_source
    ON memory_sources (source_kind, source_id);
@
CREATE INDEX IF NOT EXISTS idx_memory_sources_session
    ON memory_sources (session_id, occurred_at) WHERE session_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS personal_memory_items (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    layer VARCHAR(8) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    canonical_key VARCHAR(255),
    content TEXT NOT NULL,
    summary TEXT,
    evidence_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    content_hash VARCHAR(128),
    simhash BIGINT,
    canonical_id UUID REFERENCES personal_memory_items (id),
    superseded_by UUID REFERENCES personal_memory_items (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (layer IN ('L0', 'L1', 'L2', 'L3')),
    CHECK (memory_type IN (
        'intent',
        'preference',
        'fact',
        'project',
        'subject',
        'conversation',
        'creation'
    )),
    CHECK (status IN ('active', 'superseded', 'rejected', 'expired', 'needs_review')),
    CHECK (confidence >= 0 AND confidence <= 1),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);
@
CREATE INDEX IF NOT EXISTS idx_personal_memory_items_owner_layer
    ON personal_memory_items (owner_id, workspace_id, layer, status, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_personal_memory_items_canonical
    ON personal_memory_items (owner_id, workspace_id, canonical_key)
    WHERE canonical_key IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_personal_memory_items_validity
    ON personal_memory_items (owner_id, workspace_id, valid_to)
    WHERE status = 'active';
@
CREATE TABLE IF NOT EXISTS memory_pool_items (
    id UUID NOT NULL PRIMARY KEY,
    scope VARCHAR(32) NOT NULL DEFAULT 'global',
    layer VARCHAR(8) NOT NULL DEFAULT 'L4',
    memory_type VARCHAR(32) NOT NULL,
    title VARCHAR(512),
    content TEXT NOT NULL,
    summary TEXT,
    generalized_content TEXT,
    support_count INT NOT NULL DEFAULT 0,
    source_owner_count INT NOT NULL DEFAULT 0,
    source_workspace_count INT NOT NULL DEFAULT 0,
    source_workspace_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_summary_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    safety_class VARCHAR(32) NOT NULL DEFAULT 'general',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'candidate',
    visibility VARCHAR(32) NOT NULL DEFAULT 'global',
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    content_hash VARCHAR(128),
    simhash BIGINT,
    canonical_id UUID,
    superseded_by UUID REFERENCES memory_pool_items (id),
    reviewer_id VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    next_review_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (scope = 'global'),
    CHECK (layer = 'L4'),
    CHECK (visibility = 'global'),
    CHECK (memory_type IN (
        'intent',
        'preference',
        'fact',
        'project',
        'subject',
        'conversation',
        'creation'
    )),
    CHECK (safety_class IN ('general', 'sensitive')),
    CHECK (status IN ('candidate', 'approved', 'published', 'rejected', 'superseded')),
    CHECK (support_count >= 0),
    CHECK (source_owner_count >= 0),
    CHECK (source_workspace_count >= 0),
    CHECK (confidence >= 0 AND confidence <= 1),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (status <> 'published' OR safety_class = 'general'),
    CHECK (
        status <> 'published'
        OR (generalized_content IS NOT NULL AND btrim(generalized_content) <> '')
    )
);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_memory_pool_items_published_canonical
    ON memory_pool_items (layer, canonical_id)
    WHERE canonical_id IS NOT NULL AND status = 'published';
@
CREATE INDEX IF NOT EXISTS idx_memory_pool_items_status
    ON memory_pool_items (status, visibility, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_memory_pool_items_review
    ON memory_pool_items (next_review_at)
    WHERE status = 'published';
@
CREATE INDEX IF NOT EXISTS idx_memory_pool_items_validity
    ON memory_pool_items (status, valid_to)
    WHERE status = 'published';
@
CREATE TABLE IF NOT EXISTS memory_lineage (
    id UUID NOT NULL PRIMARY KEY,
    memory_id UUID NOT NULL,
    memory_layer VARCHAR(8) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    relation VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (memory_layer IN ('L0', 'L1', 'L2', 'L3', 'L4')),
    CHECK (btrim(source_type) <> ''),
    CHECK (btrim(source_id) <> ''),
    CHECK (btrim(relation) <> '')
);
@
CREATE INDEX IF NOT EXISTS idx_memory_lineage_memory
    ON memory_lineage (memory_id, memory_layer, created_at);
@
CREATE INDEX IF NOT EXISTS idx_memory_lineage_source
    ON memory_lineage (source_type, source_id);
@
CREATE TABLE IF NOT EXISTS memory_vector_spaces (
    id UUID NOT NULL PRIMARY KEY,
    embedding_model_id UUID NOT NULL UNIQUE REFERENCES ai_models (id),
    model_key VARCHAR(40) NOT NULL UNIQUE,
    table_name VARCHAR(64) NOT NULL UNIQUE,
    storage_type VARCHAR(16) NOT NULL,
    dimension INT NOT NULL,
    metric VARCHAR(16) NOT NULL DEFAULT 'cosine',
    hnsw_index_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (model_key ~ '^[a-z0-9_]{1,40}$'),
    CHECK (table_name ~ '^memory_vectors_[a-z0-9_]{1,40}$'),
    CHECK (storage_type IN ('vector', 'halfvec')),
    CHECK (dimension > 0),
    CHECK (metric = 'cosine'),
    CHECK (btrim(hnsw_index_name) <> ''),
    CHECK (status IN ('active', 'indexing', 'disabled', 'missing'))
);
@
CREATE INDEX IF NOT EXISTS idx_memory_vector_spaces_status
    ON memory_vector_spaces (status, model_key);
@
CREATE TABLE IF NOT EXISTS memory_jobs (
    id UUID NOT NULL PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(255),
    watermark_from TIMESTAMPTZ,
    watermark_to TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    processed_items INT NOT NULL DEFAULT 0,
    failed_items INT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(128),
    error_message TEXT,
    CHECK (btrim(job_type) <> ''),
    CHECK (scope_type IN ('owner', 'global')),
    CHECK (scope_type <> 'owner' OR (scope_id IS NOT NULL AND btrim(scope_id) <> '')),
    CHECK (scope_type <> 'global' OR scope_id IS NULL),
    CHECK (btrim(idempotency_key) <> ''),
    CHECK (total_items >= 0),
    CHECK (processed_items >= 0),
    CHECK (failed_items >= 0),
    CHECK (watermark_to IS NULL OR watermark_from IS NULL OR watermark_to >= watermark_from),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_memory_jobs_status
    ON memory_jobs (status, job_type, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_memory_jobs_scope
    ON memory_jobs (scope_type, scope_id, created_at DESC);
@
CREATE TABLE IF NOT EXISTS memory_job_items (
    id UUID NOT NULL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES memory_jobs (id) ON DELETE CASCADE,
    source_id VARCHAR(255),
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(stage) <> ''),
    CHECK (attempt >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_memory_job_items_job
    ON memory_job_items (job_id, status);
@
CREATE INDEX IF NOT EXISTS idx_memory_job_items_source
    ON memory_job_items (source_id) WHERE source_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS memory_dedup_decisions (
    id UUID NOT NULL PRIMARY KEY,
    candidate_memory_id UUID NOT NULL,
    matched_memory_id UUID,
    decision VARCHAR(32) NOT NULL,
    exact_score DOUBLE PRECISION,
    simhash_score DOUBLE PRECISION,
    vector_score DOUBLE PRECISION,
    lexical_score DOUBLE PRECISION,
    llm_verdict_jsonb JSONB,
    model_id UUID REFERENCES ai_models (id),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (decision IN ('duplicate', 'refines', 'contradicts', 'unrelated')),
    CHECK (exact_score IS NULL OR (exact_score >= 0 AND exact_score <= 1)),
    CHECK (simhash_score IS NULL OR (simhash_score >= 0 AND simhash_score <= 1)),
    CHECK (vector_score IS NULL OR (vector_score >= 0 AND vector_score <= 1)),
    CHECK (lexical_score IS NULL OR (lexical_score >= 0 AND lexical_score <= 1))
);
@
CREATE INDEX IF NOT EXISTS idx_memory_dedup_candidate
    ON memory_dedup_decisions (candidate_memory_id, decided_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_memory_dedup_matched
    ON memory_dedup_decisions (matched_memory_id, decided_at DESC)
    WHERE matched_memory_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS memory_acl (
    id UUID NOT NULL PRIMARY KEY,
    memory_id UUID NOT NULL,
    memory_layer VARCHAR(8) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (memory_id, memory_layer, subject_type, subject_id, permission),
    CHECK (memory_layer = 'L4'),
    CHECK (subject_type IN ('user', 'role')),
    CHECK (btrim(subject_id) <> ''),
    CHECK (permission IN ('review', 'publish', 'admin'))
);
@
CREATE INDEX IF NOT EXISTS idx_memory_acl_subject
    ON memory_acl (subject_type, subject_id, permission);
@
CREATE TABLE IF NOT EXISTS memory_retrieval_logs (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    query_hash VARCHAR(128) NOT NULL,
    namespaces_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_ids_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    scores_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    latency_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(scope) <> ''),
    CHECK (btrim(query_hash) <> ''),
    CHECK (latency_ms >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_memory_retrieval_logs_owner
    ON memory_retrieval_logs (owner_id, workspace_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_memory_retrieval_logs_query
    ON memory_retrieval_logs (query_hash, created_at DESC);
@
CREATE TABLE IF NOT EXISTS sandbox_sessions (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    agent_session_id UUID NOT NULL REFERENCES agent_sessions (id),
    run_id UUID REFERENCES agent_runs (id),
    provider VARCHAR(64) NOT NULL,
    template VARCHAR(128) NOT NULL,
    region VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    workspace_root TEXT,
    resource_profile_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    network_policy_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_sandbox_id VARCHAR(255),
    provider_state_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    error_code VARCHAR(128),
    error_message TEXT,
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(provider) <> ''),
    CHECK (btrim(template) <> ''),
    CHECK (status IN (
        'requested',
        'provisioning',
        'ready',
        'running',
        'snapshotting',
        'stopped',
        'expired',
        'failed',
        'cancelled',
        'resuming'
    )),
    CHECK (stopped_at IS NULL OR started_at IS NULL OR stopped_at >= started_at)
);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_sandbox_sessions_provider_id
    ON sandbox_sessions (provider, provider_sandbox_id)
    WHERE provider_sandbox_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_sandbox_sessions_owner_workspace
    ON sandbox_sessions (owner_id, workspace_id, status, expires_at);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_sessions_expiry
    ON sandbox_sessions (status, expires_at);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_sessions_agent_session
    ON sandbox_sessions (agent_session_id, status);
@
CREATE TABLE IF NOT EXISTS sandbox_operations (
    id UUID NOT NULL PRIMARY KEY,
    sandbox_id UUID NOT NULL REFERENCES sandbox_sessions (id) ON DELETE CASCADE,
    operation_type VARCHAR(64) NOT NULL,
    command TEXT,
    status VARCHAR(32) NOT NULL,
    exit_code INT,
    stdout_ref VARCHAR(255),
    stderr_ref VARCHAR(255),
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    approval_id UUID REFERENCES agent_approvals (id),
    timeout_seconds INT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(128),
    error_message TEXT,
    CHECK (btrim(operation_type) <> ''),
    CHECK (btrim(status) <> ''),
    CHECK (timeout_seconds > 0),
    CHECK (finished_at IS NULL OR finished_at >= started_at)
);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_operations_sandbox
    ON sandbox_operations (sandbox_id, started_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_operations_approval
    ON sandbox_operations (approval_id) WHERE approval_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS sandbox_snapshots (
    id UUID NOT NULL PRIMARY KEY,
    sandbox_id UUID NOT NULL REFERENCES sandbox_sessions (id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    snapshot_id VARCHAR(255),
    storage_kind VARCHAR(64) NOT NULL,
    storage_route_purpose VARCHAR(32) NOT NULL,
    storage_policy_id VARCHAR(255) NOT NULL,
    object_key TEXT NOT NULL,
    size_bytes BIGINT,
    checksum VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    CHECK (btrim(provider) <> ''),
    CHECK (btrim(storage_kind) <> ''),
    CHECK (storage_route_purpose IN ('creation_result', 'sandbox_snapshot')),
    CHECK (btrim(storage_policy_id) <> ''),
    CHECK (btrim(object_key) <> ''),
    CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CHECK (status IN ('creating', 'ready', 'failed', 'expired', 'deleted'))
);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_sandbox_snapshots_provider_id
    ON sandbox_snapshots (provider, snapshot_id)
    WHERE snapshot_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_sandbox_snapshots_sandbox
    ON sandbox_snapshots (sandbox_id, status, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_snapshots_expiry
    ON sandbox_snapshots (status, expires_at);
@
CREATE TABLE IF NOT EXISTS sandbox_artifacts (
    id UUID NOT NULL PRIMARY KEY,
    sandbox_id UUID NOT NULL REFERENCES sandbox_sessions (id) ON DELETE CASCADE,
    operation_id UUID REFERENCES sandbox_operations (id),
    storage_file_id VARCHAR(255),
    asset_id UUID REFERENCES assets (id),
    file_name VARCHAR(512) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(file_name) <> ''),
    CHECK (size_bytes IS NULL OR size_bytes >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_artifacts_sandbox
    ON sandbox_artifacts (sandbox_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_sandbox_artifacts_storage_file
    ON sandbox_artifacts (storage_file_id) WHERE storage_file_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_sandbox_artifacts_asset
    ON sandbox_artifacts (asset_id) WHERE asset_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS creation_templates (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    media_type VARCHAR(32) NOT NULL,
    prompt TEXT,
    settings_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    parameter_schema_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    cover_asset_id UUID REFERENCES assets (id),
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, workspace_id, name),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(name) <> ''),
    CHECK (btrim(title) <> ''),
    CHECK (btrim(media_type) <> ''),
    CHECK (visibility IN ('private', 'workspace', 'public'))
);
@
CREATE INDEX IF NOT EXISTS idx_creation_templates_feed
    ON creation_templates (status, visibility, published_at DESC, id);
@
CREATE INDEX IF NOT EXISTS idx_creation_templates_owner
    ON creation_templates (owner_id, workspace_id, updated_at DESC);
@
CREATE TABLE IF NOT EXISTS activities (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    rules TEXT,
    cover_asset_id UUID REFERENCES assets (id),
    template_id UUID REFERENCES creation_templates (id),
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(title) <> ''),
    CHECK (visibility IN ('private', 'workspace', 'public')),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
@
CREATE INDEX IF NOT EXISTS idx_activities_feed
    ON activities (status, visibility, starts_at DESC, id);
@
CREATE INDEX IF NOT EXISTS idx_activities_owner
    ON activities (owner_id, workspace_id, updated_at DESC);
@
CREATE TABLE IF NOT EXISTS works (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    cover_asset_id UUID REFERENCES assets (id),
    primary_asset_id UUID REFERENCES assets (id),
    template_id UUID REFERENCES creation_templates (id),
    activity_id UUID REFERENCES activities (id),
    remixed_from_work_id UUID REFERENCES works (id),
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    favorite_count BIGINT NOT NULL DEFAULT 0,
    share_count BIGINT NOT NULL DEFAULT 0,
    remix_count BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(title) <> ''),
    CHECK (visibility IN ('private', 'workspace', 'public')),
    CHECK (view_count >= 0),
    CHECK (like_count >= 0),
    CHECK (favorite_count >= 0),
    CHECK (share_count >= 0),
    CHECK (remix_count >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_works_feed
    ON works (status, visibility, published_at DESC, id);
@
CREATE INDEX IF NOT EXISTS idx_works_owner
    ON works (owner_id, workspace_id, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_works_template
    ON works (template_id) WHERE template_id IS NOT NULL;
@
CREATE INDEX IF NOT EXISTS idx_works_activity
    ON works (activity_id) WHERE activity_id IS NOT NULL;
@
CREATE TABLE IF NOT EXISTS activity_submissions (
    id UUID NOT NULL PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES activities (id),
    work_id UUID NOT NULL REFERENCES works (id),
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'submitted',
    review_note TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (reviewed_at IS NULL OR reviewed_at >= submitted_at)
);
@
CREATE INDEX IF NOT EXISTS idx_activity_submissions_activity
    ON activity_submissions (activity_id, status, submitted_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_activity_submissions_owner
    ON activity_submissions (owner_id, workspace_id, submitted_at DESC);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_submissions_work
    ON activity_submissions (activity_id, work_id);
@
CREATE TABLE IF NOT EXISTS subjects (
    id UUID NOT NULL PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    kind VARCHAR(32) NOT NULL DEFAULT 'character',
    avatar_asset_id UUID REFERENCES assets (id),
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, workspace_id, name),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (btrim(name) <> ''),
    CHECK (btrim(kind) <> ''),
    CHECK (visibility IN ('private', 'workspace', 'public'))
);
@
CREATE INDEX IF NOT EXISTS idx_subjects_owner
    ON subjects (owner_id, workspace_id, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_subjects_visibility
    ON subjects (status, visibility, updated_at DESC);
@
CREATE TABLE IF NOT EXISTS work_interactions (
    id UUID NOT NULL PRIMARY KEY,
    work_id UUID NOT NULL REFERENCES works (id) ON DELETE CASCADE,
    owner_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    interaction_type VARCHAR(32) NOT NULL,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (btrim(owner_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (interaction_type IN ('like', 'favorite', 'share', 'report', 'remix', 'view'))
);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_work_interactions_toggle
    ON work_interactions (work_id, owner_id, interaction_type)
    WHERE interaction_type IN ('like', 'favorite');
@
CREATE INDEX IF NOT EXISTS idx_work_interactions_work
    ON work_interactions (work_id, interaction_type, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_work_interactions_owner
    ON work_interactions (owner_id, interaction_type, created_at DESC);
@
CREATE TABLE IF NOT EXISTS creator_follows (
    id UUID NOT NULL PRIMARY KEY,
    follower_id VARCHAR(255) NOT NULL,
    creator_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_id, creator_id),
    CHECK (btrim(follower_id) <> ''),
    CHECK (btrim(creator_id) <> ''),
    CHECK (btrim(workspace_id) <> ''),
    CHECK (follower_id <> creator_id)
);
@
CREATE INDEX IF NOT EXISTS idx_creator_follows_creator
    ON creator_follows (creator_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_creator_follows_workspace
    ON creator_follows (workspace_id, follower_id);
@
