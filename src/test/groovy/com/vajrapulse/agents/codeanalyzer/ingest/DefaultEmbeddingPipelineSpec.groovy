package com.vajrapulse.agents.codeanalyzer.ingest

import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository
import spock.lang.Specification

class DefaultEmbeddingPipelineSpec extends Specification {

    def embedder = Mock(Embedder)
    def repo = Mock(CodeEmbeddingRepository)
    def pipeline = new DefaultEmbeddingPipeline(embedder, repo)

    def "empty chunks deletes by snapshot and does not call embedder"() {
        when:
        pipeline.embedAndStore(1L, [])
        then:
        1 * repo.deleteBySnapshotId(1L)
        0 * embedder.embed(_)
        0 * repo.saveAll(_, _, _)
    }

    def "embedAndStore deletes then embeds and saves in batches"() {
        given:
        def chunks = [
            new ChunkDto("a", 1L, 10L, 100L, "p.java", "1:0-2:0", "CLASS"),
            new ChunkDto("b", 1L, 10L, 101L, "p.java", "3:0-4:0", "METHOD")
        ]
        embedder.embed(["a", "b"]) >> [new float[1536], new float[1536]]
        when:
        pipeline.embedAndStore(1L, chunks)
        then:
        1 * repo.deleteBySnapshotId(1L)
        1 * embedder.embed(["a", "b"])
        1 * repo.saveAll(1L, _, _)
    }
}
