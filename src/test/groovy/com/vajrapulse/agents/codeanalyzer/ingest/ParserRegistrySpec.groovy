package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class ParserRegistrySpec extends Specification {

    def "default registry has Java parser for .java"() {
        given:
        def registry = new ParserRegistry()
        expect:
        registry.getParserFor("src/Foo.java").isPresent()
        registry.getParserFor("x.java").get() instanceof JavaSemanticParser
    }

    def "getParserFor returns empty for null path"() {
        given:
        def registry = new ParserRegistry()
        expect:
        registry.getParserFor(null).isEmpty()
    }

    def "getParserFor returns empty for path without extension"() {
        given:
        def registry = new ParserRegistry()
        expect:
        registry.getParserFor("Makefile").isEmpty()
    }

    def "register adds parser for extension"() {
        given:
        def registry = new ParserRegistry()
        def custom = Mock(SemanticParser)
        when:
        registry.register(".groovy", custom)
        then:
        registry.getParserFor("Script.groovy").get() == custom
    }
}
