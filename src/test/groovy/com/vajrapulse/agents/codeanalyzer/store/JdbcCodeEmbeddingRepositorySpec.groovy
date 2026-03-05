package com.vajrapulse.agents.codeanalyzer.store

import com.vajrapulse.agents.codeanalyzer.ingest.ChunkDto
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

class JdbcCodeEmbeddingRepositorySpec extends Specification {

    def jdbcTemplate = Mock(JdbcTemplate)
    def repo = new JdbcCodeEmbeddingRepository(jdbcTemplate)

    def "deleteBySnapshotId executes delete"() {
        when:
        repo.deleteBySnapshotId(99L)
        then:
        1 * jdbcTemplate.update("DELETE FROM code_embeddings WHERE snapshot_id = ?", 99L)
    }

    def "saveAll with empty list does nothing"() {
        when:
        repo.saveAll(1L, [], null)
        then:
        0 * jdbcTemplate.update(_, _)
    }

    def "saveAll with chunks and embeddings calls update per chunk"() {
        given:
        def chunks = [new ChunkDto("text", 1L, 10L, 100L, "p.java", "1-2", "CLASS")]
        def embeddings = [new float[1536]]
        when:
        repo.saveAll(1L, chunks, embeddings)
        then:
        1 * jdbcTemplate.update(_, _)
    }

    def "saveAll throws when chunks and embeddings size mismatch"() {
        when:
        repo.saveAll(1L, [new ChunkDto("a", 1L, null, null, "x.java", "", "")], [new float[1], new float[1]])
        then:
        thrown(IllegalArgumentException)
    }

    def "searchBySimilarity returns empty when snapshotIds empty"() {
        when:
        def result = repo.searchBySimilarity([], new float[1536], 10)
        then:
        result.isEmpty()
        0 * jdbcTemplate.query(_, _, _)
    }

    def "searchBySimilarity returns list from query"() {
        given:
        def hit = new CodeChunkHit("text", 1L, 10L, 100L, "p.java", "1-2", "CLASS")
        jdbcTemplate.query(_, _, _) >> [hit]
        when:
        def result = repo.searchBySimilarity([1L], new float[1536], 5)
        then:
        result != null
        result.size() == 1
        result[0].content() == "text"
    }
}
