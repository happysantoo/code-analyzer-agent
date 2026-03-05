package com.vajrapulse.agents.codeanalyzer.store;

import com.vajrapulse.agents.codeanalyzer.ingest.ChunkDto;

import java.util.List;

/**
 * Persists and queries code embeddings (chunks + vectors) in the code_embeddings table.
 */
public interface CodeEmbeddingRepository {

    void deleteBySnapshotId(long snapshotId);

    void saveAll(long snapshotId, List<ChunkDto> chunks, List<float[]> embeddings);

    /**
     * Similarity search: return top-k chunk metadata for the given snapshot(s) and query embedding.
     */
    List<CodeChunkHit> searchBySimilarity(List<Long> snapshotIds, float[] queryEmbedding, int topK);
}
