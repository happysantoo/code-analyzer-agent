package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class StubEmbedderSpec extends Specification {

    def embedder = new StubEmbedder()

    def "embed returns one zero vector per text"() {
        when:
        def result = embedder.embed(["a", "b"])
        then:
        result.size() == 2
        result[0].length == 1536
        result[1].length == 1536
        result[0].every { it == 0.0f }
    }
}
