-- Enable pgvector extension (requires superuser or extension already installed)
CREATE EXTENSION IF NOT EXISTS vector;

-- Code embeddings: chunks with metadata and linkages for semantic search / ask_question
-- Dimension 1536 matches common embedding models (e.g. OpenAI text-embedding-ada-002); adjust if using a different model
CREATE TABLE code_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    artifact_id BIGINT,
    symbol_id BIGINT,
    file_path VARCHAR(4096),
    span TEXT,
    kind VARCHAR(64)
);

CREATE INDEX idx_code_embeddings_snapshot_id ON code_embeddings (snapshot_id);
CREATE INDEX idx_code_embeddings_hnsw ON code_embeddings USING hnsw (embedding vector_cosine_ops);
