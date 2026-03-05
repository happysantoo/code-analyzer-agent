package com.vajrapulse.agents.codeanalyzer.store;

import com.vajrapulse.agents.codeanalyzer.ingest.ChunkDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JDBC implementation for code_embeddings table (pgvector).
 * Vector is passed as PostgreSQL array literal format.
 */
public class JdbcCodeEmbeddingRepository implements CodeEmbeddingRepository {

    private static final String DELETE_BY_SNAPSHOT = "DELETE FROM code_embeddings WHERE snapshot_id = ?";
    private static final String INSERT = """
        INSERT INTO code_embeddings (id, snapshot_id, content, embedding, artifact_id, symbol_id, file_path, span, kind)
        VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?)
        """;
    private static final String SEARCH_PREFIX = """
        SELECT content, snapshot_id, artifact_id, symbol_id, file_path, span, kind
        FROM code_embeddings
        WHERE snapshot_id IN (
        """;
    private static final String SEARCH_SUFFIX = """
        )
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """;

    private static final RowMapper<CodeChunkHit> HIT_MAPPER = (rs, i) -> new CodeChunkHit(
            rs.getString("content"),
            rs.getLong("snapshot_id"),
            (Long) rs.getObject("artifact_id"),
            (Long) rs.getObject("symbol_id"),
            rs.getString("file_path"),
            rs.getString("span"),
            rs.getString("kind")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcCodeEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void deleteBySnapshotId(long snapshotId) {
        jdbcTemplate.update(DELETE_BY_SNAPSHOT, snapshotId);
    }

    @Override
    public void saveAll(long snapshotId, List<ChunkDto> chunks, List<float[]> embeddings) {
        if (chunks == null || chunks.isEmpty()) return;
        if (embeddings != null && embeddings.size() != chunks.size()) {
            throw new IllegalArgumentException("chunks and embeddings size must match");
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDto c = chunks.get(i);
            float[] vec = embeddings != null ? embeddings.get(i) : new float[0];
            String vectorLiteral = formatVector(vec);
            jdbcTemplate.update(INSERT,
                    UUID.randomUUID(),
                    snapshotId,
                    c.textToEmbed(),
                    vectorLiteral,
                    c.artifactId(),
                    c.symbolId(),
                    c.filePath(),
                    c.span(),
                    c.kind());
        }
    }

    @Override
    public List<CodeChunkHit> searchBySimilarity(List<Long> snapshotIds, float[] queryEmbedding, int topK) {
        if (snapshotIds == null || snapshotIds.isEmpty()) return List.of();
        String placeholders = IntStream.range(0, snapshotIds.size()).mapToObj(i -> "?").collect(Collectors.joining(","));
        String sql = SEARCH_PREFIX + placeholders + SEARCH_SUFFIX;
        Object[] args = new Object[snapshotIds.size() + 2];
        for (int i = 0; i < snapshotIds.size(); i++) args[i] = snapshotIds.get(i);
        args[snapshotIds.size()] = formatVector(queryEmbedding);
        args[snapshotIds.size() + 1] = topK;
        return jdbcTemplate.query(sql, HIT_MAPPER, args);
    }

    private static String formatVector(float[] v) {
        if (v == null || v.length == 0) return "[]";
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
