package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class SymbolInfoSpec extends Specification {

    def "compact constructor normalizes null name kind visibility to empty string"() {
        when:
        def info = new SymbolInfo(null, null, null)
        then:
        info.name() == ""
        info.kind() == ""
        info.visibility() == ""
    }

    def "preserves non-null values"() {
        when:
        def info = new SymbolInfo("Foo", "CLASS", "public")
        then:
        info.name() == "Foo"
        info.kind() == "CLASS"
        info.visibility() == "public"
    }
}
