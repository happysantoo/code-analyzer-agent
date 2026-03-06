package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class ReferenceByIndexSpec extends Specification {

    def "compact constructor normalizes null refType to empty string"() {
        when:
        def ref = new ReferenceByIndex(0, 1, null)
        then:
        ref.fromIndex() == 0
        ref.toIndex() == 1
        ref.refType() == ""
    }

    def "preserves non-null refType"() {
        when:
        def ref = new ReferenceByIndex(1, 2, "REFERENCES")
        then:
        ref.refType() == "REFERENCES"
    }
}
