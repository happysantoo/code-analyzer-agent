package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class JavaSemanticParserSpec extends Specification {

    def parser = new JavaSemanticParser()

    def "empty content returns empty result"() {
        when:
        def result = parser.parse("Foo.java", "")
        then:
        result.symbols().isEmpty()
        result.references().isEmpty()
        result.containments().isEmpty()
    }

    def "blank content returns empty result"() {
        when:
        def result = parser.parse("x.java", "   \n  ")
        then:
        result.symbols().isEmpty()
    }

    def "single class produces one symbol and span"() {
        given:
        def content = """
public class Foo {
}
"""
        when:
        def result = parser.parse("p/Foo.java", content)
        then:
        result.symbols().size() == 1
        result.symbols().get(0).name() == "Foo"
        result.symbols().get(0).kind() == "CLASS"
        result.spans().size() == 1
        result.spans().get(0).filePath == "p/Foo.java"
    }

    def "class with method produces symbols and containment"() {
        given:
        def content = """
public class Bar {
    public void doSomething() {}
}
"""
        when:
        def result = parser.parse("Bar.java", content)
        then:
        result.symbols().size() == 2
        result.symbols().get(0).name() == "Bar"
        result.symbols().get(1).name() == "doSomething"
        result.symbols().get(1).kind() == "METHOD"
        result.containments().size() == 1
        result.containments().get(0).parentIndex() == 0
        result.containments().get(0).childIndex() == 1
    }

    def "class with field produces field symbol and containment"() {
        given:
        def content = """
public class Baz {
    private String name;
}
"""
        when:
        def result = parser.parse("Baz.java", content)
        then:
        result.symbols().size() == 2
        result.symbols().get(1).name() == "name"
        result.symbols().get(1).kind() == "FIELD"
        result.containments().size() == 1
    }

    def "invalid Java returns empty result"() {
        when:
        def result = parser.parse("bad.java", "not valid { java")
        then:
        result.symbols().isEmpty()
    }

    def "interface produces INTERFACE kind"() {
        given:
        def content = "public interface I { void m(); }"
        when:
        def result = parser.parse("I.java", content)
        then:
        result.symbols().size() >= 1
        result.symbols().get(0).kind() == "INTERFACE"
    }
}
