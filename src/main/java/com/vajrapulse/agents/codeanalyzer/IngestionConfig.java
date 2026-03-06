package com.vajrapulse.agents.codeanalyzer;

import com.vajrapulse.agents.codeanalyzer.ingest.ChunkingStrategy;
import com.vajrapulse.agents.codeanalyzer.ingest.CloneCodeRepository;
import com.vajrapulse.agents.codeanalyzer.ingest.CodeRepository;
import com.vajrapulse.agents.codeanalyzer.ingest.DefaultChunkingStrategy;
import com.vajrapulse.agents.codeanalyzer.ingest.DefaultEmbeddingPipeline;
import com.vajrapulse.agents.codeanalyzer.ingest.Embedder;
import com.vajrapulse.agents.codeanalyzer.ingest.EmbeddingPipeline;
import com.vajrapulse.agents.codeanalyzer.ingest.ParserRegistry;
import com.vajrapulse.agents.codeanalyzer.ingest.StubEmbedder;
import com.vajrapulse.agents.codeanalyzer.ingest.UrlOrPathCodeRepository;
import com.vajrapulse.agents.codeanalyzer.ingest.WorkspaceCodeRepository;
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;
import com.vajrapulse.agents.codeanalyzer.store.JdbcCodeEmbeddingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

@Configuration
public class IngestionConfig {

    @Bean
    public WorkspaceCodeRepository workspaceCodeRepository() {
        return new WorkspaceCodeRepository();
    }

    @Bean
    public CloneCodeRepository cloneCodeRepository(
            @Value("${codeanalyzer.clone.temp-dir:}") String cloneTempDir) {
        Path base = (cloneTempDir == null || cloneTempDir.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(cloneTempDir);
        return new CloneCodeRepository(base);
    }

    @Bean
    public CodeRepository codeRepository(WorkspaceCodeRepository workspaceCodeRepository,
                                        CloneCodeRepository cloneCodeRepository) {
        return new UrlOrPathCodeRepository(workspaceCodeRepository, cloneCodeRepository);
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
