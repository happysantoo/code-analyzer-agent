package com.vajrapulse.agents.codeanalyzer.query

import com.vajrapulse.agents.codeanalyzer.ingest.Embedder
import com.vajrapulse.agents.codeanalyzer.store.CodeChunkHit
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository
import spock.lang.Specification

class AskQuestionServiceSpec extends Specification {

    def embedder = Mock(Embedder)
    def codeEmbeddingRepository = Mock(CodeEmbeddingRepository)
    def projectService = Mock(ProjectService)
    def service = new AskQuestionService(embedder, codeEmbeddingRepository, projectService)

    def "ask returns error when question is blank"() {
        when:
        def result = service.ask(1L, "  ", 10)
        then:
        result.hasError()
        result.errorMessage() != null
        0 * embedder.embed(_)
        0 * codeEmbeddingRepository.searchBySimilarity(_, _, _)
    }

    def "ask embeds question and returns hits"() {
        given:
        def hit = new CodeChunkHit("CLASS public Foo", 1L, 10L, 100L, "Foo.java", "1:0-5:0", "CLASS")
        embedder.embed(_) >> [new float[1536]]
        codeEmbeddingRepository.searchBySimilarity(_, _, _) >> [hit]
        when:
        def result = service.ask(1L, "where is Foo?", 10)
        then:
        result != null
        !result.hasError()
        result.chunks() != null
        result.chunks().size() == 1
        result.chunks().get(0).content() == "CLASS public Foo"
    }

    def "ask caps topK at MAX_TOP_K"() {
        given:
        embedder.embed(["q"]) >> [new float[1536]]
        codeEmbeddingRepository.searchBySimilarity([1L], _, 50) >> []
        when:
        service.ask(1L, "q", 100)
        then:
        1 * codeEmbeddingRepository.searchBySimilarity([1L], _, 50)
    }

    def "AskQuestionResult hasError when errorMessage set"() {
        expect:
        new AskQuestionResult([], "error").hasError()
        !new AskQuestionResult([], null).hasError()
    }

    def "askByProject returns error when project has no linked snapshots"() {
        given:
        projectService.getSnapshotIdsForProject(5L) >> []
        when:
        def result = service.askByProject(5L, "question?", 10)
        then:
        result.hasError()
        result.errorMessage().contains("no linked snapshots")
        0 * embedder.embed(_)
    }
}
