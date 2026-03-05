package com.vajrapulse.agents.codeanalyzer.model

import spock.lang.Specification

class CanonicalModelSpec extends Specification {

    def "Artifact holds snapshot id and file path"() {
        when:
        def a = new Artifact(1L, 10L, "src/Foo.java")
        then:
        a.id == 1L
        a.snapshotId == 10L
        a.filePath == "src/Foo.java"
    }

    def "Symbol holds artifact id name kind visibility"() {
        when:
        def s = new Symbol(2L, 1L, "Foo", "CLASS", "public")
        then:
        s.id == 2L
        s.artifactId == 1L
        s.name == "Foo"
        s.kind == "CLASS"
        s.visibility == "public"
    }

    def "Symbol defaults kind and visibility to empty string"() {
        when:
        def s = new Symbol(null, 1L, "bar", null, null)
        then:
        s.kind == ""
        s.visibility == ""
    }

    def "Span holds file path and line column range"() {
        when:
        def span = new Span("p/Q.java", 10, 2, 15, 8)
        then:
        span.filePath == "p/Q.java"
        span.startLine == 10
        span.startColumn == 2
        span.endLine == 15
        span.endColumn == 8
    }

    def "Reference holds from and to symbol ids"() {
        when:
        def r = new Reference(3L, 1L, 2L, "CALLS")
        then:
        r.id == 3L
        r.fromSymbolId == 1L
        r.toSymbolId == 2L
        r.refType == "CALLS"
    }

    def "Reference defaults refType to empty string"() {
        when:
        def r = new Reference(1L, 1L, 2L, null)
        then:
        r.refType == ""
    }

    def "Containment holds parent and child symbol ids"() {
        when:
        def c = new Containment(10L, 20L)
        then:
        c.parentSymbolId == 10L
        c.childSymbolId == 20L
    }

    def "FileContent holds snapshot path and content"() {
        when:
        def f = new FileContent(1L, "x.java", "public class X {}")
        then:
        f.snapshotId == 1L
        f.filePath == "x.java"
        f.content == "public class X {}"
    }

    def "FileContent defaults content to empty string when null"() {
        when:
        def f = new FileContent(1L, "x.java", null)
        then:
        f.content == ""
    }
}
