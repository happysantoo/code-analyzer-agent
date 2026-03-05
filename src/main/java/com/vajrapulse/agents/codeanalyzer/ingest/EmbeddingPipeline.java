package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.List;

/**
 * Computes embeddings for chunks and persists them to the vector store (code_embeddings).
 */
public interface EmbeddingPipeline {

    /**
     * For the given snapshot: produce chunks (if not already provided), embed them, and persist to code_embeddings.
     * Replaces any existing vectors for this snapshot.
     *
     * @param snapshotId snapshot id
     * @param chunks     chunks to embed and store (must have snapshotId set)
     */
    void embedAndStore(long snapshotId, List<ChunkDto> chunks);
}
