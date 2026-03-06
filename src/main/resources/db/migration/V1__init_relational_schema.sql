-- Snapshots: one per (repo_url, commit_sha) analysis run
CREATE TABLE snapshots (
    id BIGSERIAL PRIMARY KEY,
    repo_url VARCHAR(2048) NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (repo_url, commit_sha)
);

CREATE INDEX idx_snapshots_repo_commit ON snapshots (repo_url, commit_sha);

-- Artifacts: one per file in a snapshot
CREATE TABLE artifacts (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    file_path VARCHAR(4096) NOT NULL
);

CREATE INDEX idx_artifacts_snapshot_id ON artifacts (snapshot_id);
CREATE INDEX idx_artifacts_file_path ON artifacts (snapshot_id, file_path);

-- Symbols: named entities (type, method, field, etc.)
CREATE TABLE symbols (
    id BIGSERIAL PRIMARY KEY,
    artifact_id BIGINT NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    name VARCHAR(1024) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT ''
);

CREATE INDEX idx_symbols_artifact_id ON symbols (artifact_id);
CREATE INDEX idx_symbols_name ON symbols (name);
CREATE INDEX idx_symbols_kind ON symbols (kind);

-- Symbol spans: source location per symbol
CREATE TABLE symbol_spans (
    symbol_id BIGINT NOT NULL PRIMARY KEY REFERENCES symbols(id) ON DELETE CASCADE,
    file_path VARCHAR(4096) NOT NULL,
    start_line INT NOT NULL,
    start_column INT NOT NULL,
    end_line INT NOT NULL,
    end_column INT NOT NULL
);

CREATE INDEX idx_symbol_spans_file_path ON symbol_spans (file_path);

-- References: from_symbol_id -> to_symbol_id ("references" is a reserved keyword in PostgreSQL)
CREATE TABLE "references" (
    id BIGSERIAL PRIMARY KEY,
    from_symbol_id BIGINT NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    to_symbol_id BIGINT NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    ref_type VARCHAR(64) NOT NULL DEFAULT ''
);

CREATE INDEX idx_references_from ON "references" (from_symbol_id);
CREATE INDEX idx_references_to ON "references" (to_symbol_id);

-- Containment: parent_symbol_id contains child_symbol_id
CREATE TABLE containment (
    id BIGSERIAL PRIMARY KEY,
    parent_symbol_id BIGINT NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    child_symbol_id BIGINT NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    UNIQUE (parent_symbol_id, child_symbol_id)
);

CREATE INDEX idx_containment_parent ON containment (parent_symbol_id);
CREATE INDEX idx_containment_child ON containment (child_symbol_id);

-- File contents: raw content per snapshot + path
CREATE TABLE file_contents (
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    file_path VARCHAR(4096) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, file_path)
);

CREATE INDEX idx_file_contents_snapshot_id ON file_contents (snapshot_id);

-- Projects: named grouping of snapshots for linked codebases
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT
);

-- Project-snapshot linkage
CREATE TABLE project_snapshots (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, snapshot_id)
);

CREATE INDEX idx_project_snapshots_snapshot ON project_snapshots (snapshot_id);
