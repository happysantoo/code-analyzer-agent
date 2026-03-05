package com.vajrapulse.agents.codeanalyzer.ingest

import com.vajrapulse.agents.codeanalyzer.model.Span
import com.vajrapulse.agents.codeanalyzer.model.Symbol
import spock.lang.Specification

class ChunkingStrategySpec extends Specification {

    def strategy = new DefaultChunkingStrategy()

    def "empty symbols returns empty chunks"() {
        when:
        def chunks = strategy.chunk(1L, "p/Foo.java", [], [])
        then:
        chunks.isEmpty()
    }

    def "null symbols treated as empty"() {
        when:
        def chunks = strategy.chunk(1L, "x.java", null, null)
        then:
        chunks.isEmpty()
    }

    def "single symbol produces one chunk with kind visibility name"() {
        given:
        def symbols = [new Symbol(10L, 5L, "Foo", "CLASS", "public")]
        def spans = [new Span("p/Foo.java", 1, 0, 10, 2)]
        when:
        def chunks = strategy.chunk(100L, "p/Foo.java", symbols, spans)
        then:
        chunks.size() == 1
        chunks[0].textToEmbed() == "CLASS public Foo"
        chunks[0].snapshotId() == 100L
        chunks[0].artifactId() == 5L
        chunks[0].symbolId() == 10L
        chunks[0].filePath() == "p/Foo.java"
        chunks[0].span() == "1:0-10:2"
        chunks[0].kind() == "CLASS"
    }

    def "multiple symbols produce multiple chunks"() {
        given:
        def symbols = [
            new Symbol(1L, 1L, "Bar", "CLASS", "public"),
            new Symbol(2L, 1L, "run", "METHOD", "public")
        ]
        def spans = [
            new Span("Bar.java", 1, 0, 5, 0),
            new Span("Bar.java", 7, 4, 10, 4)
        ]
        when:
        def chunks = strategy.chunk(1L, "Bar.java", symbols, spans)
        then:
        chunks.size() == 2
        chunks[0].textToEmbed() == "CLASS public Bar"
        chunks[1].textToEmbed() == "METHOD public run"
        chunks[1].span() == "7:4-10:4"
    }

    def "symbol with null id has null artifactId and symbolId in chunk"() {
        given:
        def symbols = [new Symbol(null, 0L, "Baz", "FIELD", "private")]
        def spans = [new Span("Baz.java", 2, 2, 2, 10)]
        when:
        def chunks = strategy.chunk(1L, "Baz.java", symbols, spans)
        then:
        chunks.size() == 1
        chunks[0].artifactId() == null
        chunks[0].symbolId() == null
        chunks[0].textToEmbed() == "FIELD private Baz"
    }

    def "missing span uses empty span string"() {
        given:
        def symbols = [new Symbol(1L, 1L, "Q", "CLASS", "")]
        def spans = [] as List
        when:
        def chunks = strategy.chunk(1L, "Q.java", symbols, spans)
        then:
        chunks.size() == 1
        chunks[0].span() == ""
    }
}
