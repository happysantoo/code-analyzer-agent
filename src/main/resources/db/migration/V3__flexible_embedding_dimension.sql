-- Allow embedding vectors of any dimension (not just 1536).
-- Different embedding models produce different dimensions (e.g. nomic-embed-text=768, mxbai-embed-large=1024).
-- The HNSW index is dropped because it was built for vector(1536); brute-force cosine scan
-- is fine for moderate dataset sizes. For production, recreate the index after choosing a model.

DROP INDEX IF EXISTS idx_code_embeddings_hnsw;

ALTER TABLE code_embeddings ALTER COLUMN embedding TYPE vector USING embedding::vector;
