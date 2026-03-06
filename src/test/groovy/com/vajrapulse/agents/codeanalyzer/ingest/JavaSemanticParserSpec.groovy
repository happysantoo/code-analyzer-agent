package com.vajrapulse.agents.codeanalyzer.ingest

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import spock.lang.Specification

class JavaSemanticParserSpec extends Specification {

    def parser = new JavaSemanticParser()

    def "parser with custom JavaParser returning programmatic CU handles types and produces span with default when range empty"() {
        given:
        def cu = new CompilationUnit()
        cu.addClass("ProgrammaticClass")
        def stubParser = Stub(JavaParser)
        def parseResult = Stub(com.github.javaparser.ParseResult)
        parseResult.getResult() >> Optional.of(cu)
        stubParser.parse(_) >> parseResult
        def customParser = new JavaSemanticParser(stubParser)
        when:
        def result = customParser.parse("Prog.java", "ignored")
        then:
        result.symbols().size() >= 1
        result.symbols().get(0).name() == "ProgrammaticClass"
        result.spans().size() >= 1
    }

    def "null content returns empty result"() {
        when:
        def result = parser.parse("Foo.java", null)
        then:
        result.symbols().isEmpty()
        result.references().isEmpty()
        result.containments().isEmpty()
    }

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

    def "package-private class produces package visibility"() {
        given:
        def content = "class PackagePrivate { void m() {} }"
        when:
        def result = parser.parse("P.java", content)
        then:
        result.symbols().size() >= 1
        result.symbols().get(0).visibility() == "package"
    }
}
