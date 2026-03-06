package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;

import java.util.List;

/**
 * Embeds chunks using the provided Embedder and persists to CodeEmbeddingRepository.
 * Replaces existing vectors for the snapshot before insert.
 */
public class DefaultEmbeddingPipeline implements EmbeddingPipeline {

    private static final int BATCH_SIZE = 100;

    private final Embedder embedder;
    private final CodeEmbeddingRepository embeddingRepository;

    public DefaultEmbeddingPipeline(Embedder embedder, CodeEmbeddingRepository embeddingRepository) {
        this.embedder = embedder;
        this.embeddingRepository = embeddingRepository;
    }

    @Override
    public void embedAndStore(long snapshotId, List<ChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            embeddingRepository.deleteBySnapshotId(snapshotId);
            return;
        }
        embeddingRepository.deleteBySnapshotId(snapshotId);
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            List<ChunkDto> batch = chunks.subList(i, end);
            List<String> texts = batch.stream().map(ChunkDto::textToEmbed).toList();
            List<float[]> vectors = embedder.embed(texts);
            embeddingRepository.saveAll(snapshotId, batch, vectors);
        }
    }
}
