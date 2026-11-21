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
