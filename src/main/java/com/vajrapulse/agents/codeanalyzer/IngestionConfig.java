package com.vajrapulse.agents.codeanalyzer;

import com.vajrapulse.agents.codeanalyzer.ingest.*;
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;
import com.vajrapulse.agents.codeanalyzer.store.JdbcCodeEmbeddingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class IngestionConfig {

    @Bean
    public CodeRepository codeRepository() {
        return new WorkspaceCodeRepository();
    }

    @Bean
    public ParserRegistry parserRegistry() {
        return new ParserRegistry();
    }

    @Bean
    public ChunkingStrategy chunkingStrategy() {
        return new DefaultChunkingStrategy();
    }

    @Bean
    public Embedder embedder() {
        return new StubEmbedder();
    }

    @Bean
    public CodeEmbeddingRepository codeEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCodeEmbeddingRepository(jdbcTemplate);
    }

    @Bean
    public EmbeddingPipeline embeddingPipeline(Embedder embedder, CodeEmbeddingRepository codeEmbeddingRepository) {
        return new DefaultEmbeddingPipeline(embedder, codeEmbeddingRepository);
    }
}
