package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class ChunkDtoSpec extends Specification {

    def "ChunkDto requires non-null textToEmbed and filePath"() {
        when:
        new ChunkDto(null, 1L, null, null, "p.java", "", "CLASS")
        then:
        thrown(NullPointerException)

        when:
        new ChunkDto("text", 1L, null, null, null, "", "METHOD")
        then:
        thrown(NullPointerException)
    }

    def "ChunkDto defaults span and kind to empty string"() {
        when:
        def dto = new ChunkDto("t", 1L, 2L, 3L, "x.java", null, null)
        then:
        dto.span() == ""
        dto.kind() == ""
    }
}
