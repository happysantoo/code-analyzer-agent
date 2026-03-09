package com.vajrapulse.agents.codeanalyzer.ingest

import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import spock.lang.Specification

class SpringAiEmbedderSpec extends Specification {

    EmbeddingResponse fixedResponse

    def embeddingModel = Stub(EmbeddingModel) {
        call(_) >> { fixedResponse }
    }

    def "embed returns vectors from model with any dimension"() {
        given:
        def v1 = new float[768]
        v1[0] = 0.1f
        def v2 = new float[768]
        v2[1] = 0.2f
        fixedResponse = new EmbeddingResponse([
                new Embedding(v1, 0),
                new Embedding(v2, 1)
        ])

        when:
        def adapter = new SpringAiEmbedder(embeddingModel)
        def result = adapter.embed(["a", "b"])

        then:
        result.size() == 2
        result[0].length == 768
        result[1].length == 768
        result[0][0] == 0.1f
        result[1][1] == 0.2f
    }

    def "embed empty list returns empty list and does not call model"() {
        when:
        def result = new SpringAiEmbedder(embeddingModel).embed([])

        then:
        result.isEmpty()
    }

    def "embed null list returns empty list"() {
        when:
        def result = new SpringAiEmbedder(embeddingModel).embed(null)

        then:
        result.isEmpty()
    }

    def "embed throws when model returns null vector"() {
        given:
        fixedResponse = new EmbeddingResponse([new Embedding(null as float[], 0)])

        when:
        new SpringAiEmbedder(embeddingModel).embed(["x"])

        then:
        thrown(IllegalArgumentException)
    }

    def "embed throws when model returns empty vector"() {
        given:
        fixedResponse = new EmbeddingResponse([new Embedding(new float[0], 0)])

        when:
        new SpringAiEmbedder(embeddingModel).embed(["x"])

        then:
        thrown(IllegalArgumentException)
    }

    def "embed throws when model returns wrong count"() {
        given:
        fixedResponse = new EmbeddingResponse([new Embedding(new float[768], 0)])

        when:
        new SpringAiEmbedder(embeddingModel).embed(["a", "b"])

        then:
        thrown(IllegalStateException)
    }
}
