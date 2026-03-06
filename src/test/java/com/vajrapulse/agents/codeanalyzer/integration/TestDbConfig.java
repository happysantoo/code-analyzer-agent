package com.vajrapulse.agents.codeanalyzer.integration;

import com.vajrapulse.agents.codeanalyzer.ingest.ChunkDto;
import com.vajrapulse.agents.codeanalyzer.store.CodeChunkHit;
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Test configuration for integration tests using H2. The real
 * {@link com.vajrapulse.agents.codeanalyzer.store.JdbcCodeEmbeddingRepository}
 * requires PostgreSQL and pgvector; this stub allows the full application context
 * to load with an embedded H2 database.
 */
@TestConfiguration
@Profile("test")
public class TestDbConfig {

    @Bean
    @Primary
    public CodeEmbeddingRepository codeEmbeddingRepository() {
        return new CodeEmbeddingRepository() {
            @Override
            public void deleteBySnapshotId(long snapshotId) {
                // no-op for H2 tests
            }

            @Override
            public void saveAll(long snapshotId, List<ChunkDto> chunks, List<float[]> embeddings) {
                // no-op for H2 tests
            }

            @Override
            public List<CodeChunkHit> searchBySimilarity(List<Long> snapshotIds, float[] queryEmbedding, int topK) {
                return List.of();
            }
        };
    }
}
