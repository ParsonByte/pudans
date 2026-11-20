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

-- AI kernel tables
CREATE EXTENSION IF NOT EXISTS vector;
@
CREATE EXTENSION IF NOT EXISTS pg_trgm;
@
-- 会话（内部 Chat + Agent 网关共用）
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGSERIAL PRIMARY KEY,
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128) NOT NULL,
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    agent_source VARCHAR(32) NOT NULL DEFAULT 'pudans-chat',
    session_key VARCHAR(256) NOT NULL,
    task_id VARCHAR(128),
    title VARCHAR(512),
    mode_code VARCHAR(64),
    route_group VARCHAR(64),
    knowledge_base_codes TEXT[] NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    l1_cursor_message_id BIGINT,
    l2_cursor_l1_id BIGINT,
    pending_count INT NOT NULL DEFAULT 0,
    pipeline_version BIGINT NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (space_id, user_id, agent_source, session_key)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation (user_id, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_conversation_team_agent ON ai_conversation (team_id, agent_id, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_conversation_activity ON ai_conversation (status, last_activity_at);
@
-- L0 消息
CREATE TABLE IF NOT EXISTS ai_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversation(id),
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128) NOT NULL,
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(256),
    task_id VARCHAR(128),
    request_id VARCHAR(64),
    turn_index INT NOT NULL,
    message_index INT NOT NULL DEFAULT 0,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    content_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    token_count INT,
    model_code VARCHAR(128),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (conversation_id, turn_index, message_index)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_message_conversation ON ai_message (conversation_id, id);
@
CREATE INDEX IF NOT EXISTS idx_ai_message_request ON ai_message (request_id);
@
CREATE INDEX IF NOT EXISTS idx_ai_message_scope ON ai_message (team_id, agent_id, user_id, session_id);
@
CREATE INDEX IF NOT EXISTS idx_ai_message_hnsw
    ON ai_message USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
CREATE INDEX IF NOT EXISTS idx_ai_message_trgm
    ON ai_message USING gin (content gin_trgm_ops);
@
-- 聊天请求轨迹与计费结果
CREATE TABLE IF NOT EXISTS ai_chat_request (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128) NOT NULL,
    agent_source VARCHAR(32) NOT NULL DEFAULT 'pudans-chat',
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    session_key VARCHAR(256),
    conversation_id BIGINT REFERENCES ai_conversation(id),
    idempotency_key VARCHAR(64) NOT NULL,
    mode_code VARCHAR(64),
    route_group VARCHAR(64),
    selected_provider VARCHAR(64),
    selected_account VARCHAR(128),
    selected_model VARCHAR(128),
    tier VARCHAR(16),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    error_code VARCHAR(64),
    input_tokens INT,
    cache_tokens INT,
    output_tokens INT,
    total_tokens INT,
    points NUMERIC(12,2),
    overdraft NUMERIC(12,2) NOT NULL DEFAULT 0,
    billing_source VARCHAR(16),
    billing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    latency_ms INT,
    ttft_ms INT,
    tps NUMERIC(10,2),
    usage_source VARCHAR(16),
    upstream_request_id VARCHAR(128),
    route_trace JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieval_trace JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (space_id, user_id, agent_source, idempotency_key)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_user ON ai_chat_request (user_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_model ON ai_chat_request (selected_model, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_account ON ai_chat_request (selected_provider, selected_account, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_status ON ai_chat_request (status, created_at);
@
CREATE TABLE IF NOT EXISTS ai_model_request (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128) NOT NULL,
    team_id VARCHAR(128),
    agent_id VARCHAR(128),
    session_id VARCHAR(256),
    idempotency_key VARCHAR(64) NOT NULL,
    route_group VARCHAR(64),
    model_type VARCHAR(16) NOT NULL,
    capability VARCHAR(32) NOT NULL,
    selected_provider VARCHAR(64),
    selected_account VARCHAR(128),
    selected_model VARCHAR(128),
    tier VARCHAR(16),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    error_code VARCHAR(64),
    error_message VARCHAR(1024),
    provider_task_id VARCHAR(256),
    input_tokens INT,
    cache_tokens INT,
    output_tokens INT,
    image_count INT,
    video_seconds INT,
    audio_seconds INT,
    speech_characters INT,
    task_count INT,
    hold_points NUMERIC(12,2),
    points NUMERIC(12,2),
    billing_source VARCHAR(16),
    billing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    latency_ms INT,
    usage_source VARCHAR(16),
    upstream_request_id VARCHAR(128),
    request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    route_trace JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (space_id, user_id, idempotency_key)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_model_request_user ON ai_model_request (user_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_model_request_model ON ai_model_request (selected_model, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_model_request_status ON ai_model_request (status, created_at);
@
-- Agent 网关会话绑定
CREATE TABLE IF NOT EXISTS ai_gateway_session (
    id BIGSERIAL PRIMARY KEY,
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128) NOT NULL,
    agent_source VARCHAR(32) NOT NULL,
    session_key VARCHAR(256) NOT NULL,
    session_id VARCHAR(256),
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128),
    conversation_id BIGINT REFERENCES ai_conversation(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (space_id, user_id, agent_source, session_key)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_gateway_session_team_agent ON ai_gateway_session (team_id, agent_id, updated_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_gateway_session_session ON ai_gateway_session (session_id);
@
-- Agent 状态快照
CREATE TABLE IF NOT EXISTS ai_agent_state (
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    state JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, session_id)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_agent_state_updated ON ai_agent_state (updated_at);
@
-- Harness 线程日志头
CREATE TABLE IF NOT EXISTS ai_harness_journal_head (
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    last_seq BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, session_id)
);
@
-- Harness 线程日志事件
CREATE TABLE IF NOT EXISTS ai_harness_journal_event (
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    seq BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    run_id VARCHAR(128),
    turn_id VARCHAR(128),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, session_id, seq),
    UNIQUE (user_id, session_id, event_id)
);
@
-- 钱包
CREATE TABLE IF NOT EXISTS ai_wallet (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL UNIQUE,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    frozen NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_granted NUMERIC(16,2) NOT NULL DEFAULT 0,
    total_consumed NUMERIC(16,2) NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    daily_limit NUMERIC(12,2),
    min_balance_alert NUMERIC(12,2) DEFAULT 10,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_wallet_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ai_wallet_frozen_non_negative CHECK (frozen >= 0),
    CONSTRAINT ai_wallet_frozen_le_balance CHECK (frozen <= balance)
);
@
CREATE TABLE IF NOT EXISTS ai_wallet_hold (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(128) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'CHAT',
    status VARCHAR(16) NOT NULL DEFAULT 'HELD',
    expires_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_wallet_hold_amount_non_negative CHECK (amount >= 0)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_wallet_hold_expiry ON ai_wallet_hold (status, expires_at);
@
CREATE TABLE IF NOT EXISTS ai_wallet_txn (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    txn_type VARCHAR(16) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    request_id VARCHAR(64),
    provider_account VARCHAR(128),
    model_code VARCHAR(128),
    tier VARCHAR(16),
    input_tokens INT,
    cache_tokens INT,
    output_tokens INT,
    trace_id VARCHAR(64),
    billing_source VARCHAR(16),
    remark VARCHAR(512),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_wallet_txn_user ON ai_wallet_txn (user_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_wallet_txn_request ON ai_wallet_txn (request_id);
@
-- L1 原子事实（scene 切分 + 批量冲突判定后）
CREATE TABLE IF NOT EXISTS ai_memory_l1 (
    id BIGSERIAL PRIMARY KEY,
    record_id VARCHAR(64) NOT NULL UNIQUE,
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    task_id VARCHAR(128),
    conversation_id BIGINT,
    content TEXT NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 50,
    scene_name VARCHAR(256),
    source_message_ids BIGINT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    dedup_hash VARCHAR(64) NOT NULL,
    scope VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    org_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,
    superseded_by BIGINT,
    timestamps TIMESTAMPTZ[] NOT NULL DEFAULT '{}',
    decay_score NUMERIC(5,2) NOT NULL DEFAULT 100,
    embedding vector(1536),
    last_accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (team_id, agent_id, user_id, dedup_hash)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l1_scope ON ai_memory_l1 (team_id, agent_id, user_id, session_id, status, decay_score);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l1_scene ON ai_memory_l1 (team_id, agent_id, scene_name);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l1_hnsw
    ON ai_memory_l1 USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l1_trgm
    ON ai_memory_l1 USING gin (content gin_trgm_ops);
@
-- L2 场景块（team+agent 作用域）
CREATE TABLE IF NOT EXISTS ai_memory_l2 (
    id BIGSERIAL PRIMARY KEY,
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    scene_key VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    summary TEXT NOT NULL,
    l1_ids BIGINT[] NOT NULL DEFAULT '{}',
    heat INT NOT NULL DEFAULT 0,
    confidence NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    decay_score NUMERIC(5,2) NOT NULL DEFAULT 100,
    scope VARCHAR(32) NOT NULL DEFAULT 'TEAM',
    embedding vector(1536),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (team_id, agent_id, scene_key)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l2_scope ON ai_memory_l2 (team_id, agent_id, last_active_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l2_hnsw
    ON ai_memory_l2 USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
-- L3 团队/Agent 画像
CREATE TABLE IF NOT EXISTS ai_memory_l3 (
    id BIGSERIAL PRIMARY KEY,
    team_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    persona_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    persona_text TEXT,
    embedding vector(1536),
    source_l1_count INT NOT NULL DEFAULT 0,
    source_l2_count INT NOT NULL DEFAULT 0,
    confidence NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    last_refreshed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (team_id, agent_id)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_l3_hnsw
    ON ai_memory_l3 USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
-- 记忆生成溯源
CREATE TABLE IF NOT EXISTS ai_memory_generation_log (
    id BIGSERIAL PRIMARY KEY,
    memory_level VARCHAR(8) NOT NULL,
    memory_id BIGINT,
    team_id VARCHAR(128),
    agent_id VARCHAR(128),
    user_id VARCHAR(128),
    session_id VARCHAR(256),
    prompt_id VARCHAR(64),
    prompt_version INT,
    prompt_source VARCHAR(16),
    prompt_hash VARCHAR(64),
    model_code VARCHAR(128),
    input_ref VARCHAR(512),
    output_ref VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_gen_log_memory ON ai_memory_generation_log (memory_level, memory_id);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_gen_log_scope ON ai_memory_generation_log (team_id, agent_id, created_at DESC);
@
-- 记忆 Prompt 操作日志
CREATE TABLE IF NOT EXISTS ai_memory_prompt_log (
    id BIGSERIAL PRIMARY KEY,
    prompt_id VARCHAR(128) NOT NULL,
    version INT,
    action VARCHAR(16) NOT NULL,
    target_type VARCHAR(16),
    team VARCHAR(128),
    agent VARCHAR(128),
    layer VARCHAR(8),
    actor VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_prompt_log_prompt ON ai_memory_prompt_log (prompt_id, id DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_prompt_log_created ON ai_memory_prompt_log (created_at DESC, id DESC);
@
-- 用户更正/删除申请
CREATE TABLE IF NOT EXISTS ai_memory_request (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    team_id VARCHAR(128),
    agent_id VARCHAR(128),
    memory_level VARCHAR(8) NOT NULL DEFAULT 'L1',
    memory_id BIGINT NOT NULL,
    request_type VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    suggested_content TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(128),
    reviewed_at TIMESTAMPTZ,
    review_comment VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_request_status ON ai_memory_request (status, created_at);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_request_user ON ai_memory_request (user_id, created_at DESC);
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_memory_request_pending
    ON ai_memory_request (user_id, memory_level, memory_id, request_type)
    WHERE status = 'PENDING';
@
-- 记忆恢复快照
CREATE TABLE IF NOT EXISTS ai_memory_snapshot (
    id BIGSERIAL PRIMARY KEY,
    audit_id VARCHAR(64),
    memory_level VARCHAR(8) NOT NULL,
    memory_id BIGINT NOT NULL,
    owner_user_id VARCHAR(128) NOT NULL,
    team_id VARCHAR(128),
    agent_id VARCHAR(128),
    snapshot JSONB NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_snapshot_memory ON ai_memory_snapshot (memory_level, memory_id);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_snapshot_owner ON ai_memory_snapshot (owner_user_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_memory_snapshot_expiry ON ai_memory_snapshot (expires_at);
@
-- 组织综合记忆池（跨团队）
CREATE TABLE IF NOT EXISTS ai_org_memory (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    dedup_hash VARCHAR(64) NOT NULL UNIQUE,
    trust_level VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    source_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    source_team_ids TEXT[] NOT NULL DEFAULT '{}',
    source_user_ids TEXT[] NOT NULL DEFAULT '{}',
    source_memory_ids BIGINT[] NOT NULL DEFAULT '{}',
    cross_vote_count INT NOT NULL DEFAULT 1,
    approved_by VARCHAR(128),
    approved_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    embedding vector(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_org_memory_trust ON ai_org_memory (trust_level, is_active);
@
CREATE INDEX IF NOT EXISTS idx_ai_org_memory_hnsw
    ON ai_org_memory USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
-- 异步任务队列
CREATE TABLE IF NOT EXISTS ai_task (
    id BIGSERIAL PRIMARY KEY,
    task_type VARCHAR(32) NOT NULL,
    dedup_key VARCHAR(128),
    space_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(128),
    team_id VARCHAR(128),
    agent_id VARCHAR(128),
    session_id VARCHAR(256),
    conversation_id BIGINT,
    status SMALLINT NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    next_run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    last_error VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_task_ready ON ai_task (status, next_run_at, priority DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_task_locked ON ai_task (locked_until) WHERE status = 1;
@
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_task_dedup
    ON ai_task (dedup_key) WHERE dedup_key IS NOT NULL AND status IN (0, 1);
@
-- 知识库文档
CREATE TABLE IF NOT EXISTS ai_kb_document (
    id BIGSERIAL PRIMARY KEY,
    kb_name VARCHAR(128) NOT NULL,
    storage_file_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128),
    file_size BIGINT,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    token_count INT NOT NULL DEFAULT 0,
    error_msg VARCHAR(1024),
    version INT NOT NULL DEFAULT 1,
    uploaded_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (kb_name, checksum)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_document_status ON ai_kb_document (kb_name, status);
@
-- 知识库切片
CREATE TABLE IF NOT EXISTS ai_kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    kb_name VARCHAR(128) NOT NULL,
    document_id BIGINT NOT NULL REFERENCES ai_kb_document(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    page_no INT,
    heading_path VARCHAR(1024),
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, chunk_index)
);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_kb ON ai_kb_chunk (kb_name);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_document ON ai_kb_chunk (document_id, chunk_index);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_hnsw
    ON ai_kb_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_trgm
    ON ai_kb_chunk USING gin (content gin_trgm_ops);
@
-- 知识库检索日志
CREATE TABLE IF NOT EXISTS ai_kb_query_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(64),
    kb_names TEXT[] NOT NULL DEFAULT '{}',
    query TEXT NOT NULL,
    hit_chunk_ids BIGINT[] NOT NULL DEFAULT '{}',
    hit_count INT NOT NULL DEFAULT 0,
    top_score NUMERIC(6,4),
    rerank_used BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_query_log_user ON ai_kb_query_log (user_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_kb_query_log_kb ON ai_kb_query_log USING gin (kb_names);
@
-- 知识库 ACL 只读视图（从扩展资源展开）
CREATE INDEX IF NOT EXISTS idx_extensions_ai_kb_name
    ON extensions (name text_pattern_ops)
    WHERE name LIKE '/registry/pudans.tolim.cn/knowledgebases/%';
@
CREATE OR REPLACE VIEW ai_kb_access_view AS
SELECT
    regexp_replace(e.name, '^/registry/pudans\.tolim\.cn/knowledgebases/', '') AS kb_name,
    convert_from(e.data, 'UTF8')::jsonb -> 'spec' ->> 'status' AS status,
    convert_from(e.data, 'UTF8')::jsonb -> 'spec' ->> 'visibility' AS visibility,
    convert_from(e.data, 'UTF8')::jsonb -> 'spec' ->> 'ownerUserId' AS owner_user_id,
    convert_from(e.data, 'UTF8')::jsonb -> 'spec' ->> 'ownerDeptId' AS owner_dept_id,
    a ->> 'subjectType' AS subject_type,
    a ->> 'subjectId' AS subject_id,
    a ->> 'permission' AS permission
FROM extensions e
LEFT JOIN LATERAL jsonb_array_elements(
    COALESCE(convert_from(e.data, 'UTF8')::jsonb -> 'spec' -> 'acl', '[]'::jsonb)
) AS a ON TRUE
WHERE e.name LIKE '/registry/pudans.tolim.cn/knowledgebases/%';
@
-- Agent 运行时实例
CREATE TABLE IF NOT EXISTS ai_agent_runtime (
    agent_name VARCHAR(255) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    sandbox_id VARCHAR(255),
    state VARCHAR(32) NOT NULL,
    image VARCHAR(512),
    entrypoint TEXT,
    cpu DOUBLE PRECISION,
    memory_mb INTEGER,
    timeout_seconds INTEGER,
    endpoint VARCHAR(1024),
    last_error TEXT,
    config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ
);
@
CREATE INDEX IF NOT EXISTS idx_ai_agent_runtime_state ON ai_agent_runtime (state);
@
CREATE INDEX IF NOT EXISTS idx_ai_agent_runtime_updated ON ai_agent_runtime (updated_at DESC, agent_name);
@
-- 网关尝试轨迹与调用密钥归因
ALTER TABLE ai_chat_request ADD COLUMN IF NOT EXISTS attempts JSONB;
@
ALTER TABLE ai_chat_request ADD COLUMN IF NOT EXISTS caller_key VARCHAR(128);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_caller_key ON ai_chat_request (caller_key, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_created ON ai_chat_request (created_at DESC, id DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_chat_request_conversation ON ai_chat_request (conversation_id, created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_model_request_created ON ai_model_request (created_at DESC);
@
CREATE INDEX IF NOT EXISTS idx_ai_conversation_activity ON ai_conversation (last_activity_at DESC, id DESC);
@
