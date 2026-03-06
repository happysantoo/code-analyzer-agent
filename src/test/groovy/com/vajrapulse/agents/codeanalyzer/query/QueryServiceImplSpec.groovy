package com.vajrapulse.agents.codeanalyzer.query

import com.vajrapulse.agents.codeanalyzer.store.*
import spock.lang.Specification

class QueryServiceImplSpec extends Specification {

    def snapshotRepository = Mock(SnapshotRepository)
    def artifactRepository = Mock(ArtifactRepository)
    def symbolRepository = Mock(SymbolRepository)
    def symbolSpanRepository = Mock(SymbolSpanRepository)
    def referenceRepository = Mock(ReferenceRepository)
    def containmentRepository = Mock(ContainmentRepository)
    def fileContentRepository = Mock(FileContentRepository)

    def service = new QueryServiceImpl(
        snapshotRepository, artifactRepository, symbolRepository, symbolSpanRepository,
        referenceRepository, containmentRepository, fileContentRepository)

    def "getSnapshot returns empty when not found"() {
        when:
        def result = service.getSnapshot(999L)
        then:
        1 * snapshotRepository.findById(999L) >> Optional.empty()
        result.isEmpty()
    }

    def "getSnapshot returns snapshot when found"() {
        given:
        def snap = new Snapshot(1L, "url", "abc", null)
        when:
        def result = service.getSnapshot(1L)
        then:
        1 * snapshotRepository.findById(1L) >> Optional.of(snap)
        result.get().id() == 1L
        result.get().commitSha() == "abc"
    }

    def "listSnapshots returns all when no filter"() {
        given:
        def s = new Snapshot(1L, "url", "sha", null)
        when:
        def result = service.listSnapshots(null)
        then:
        1 * snapshotRepository.findAll() >> [s]
        result.size() == 1
    }

    def "getFileContent returns content when found"() {
        given:
        def fc = new FileContent(1L, "p/Foo.java", "public class Foo {}")
        when:
        def result = service.getFileContent(1L, "p/Foo.java")
        then:
        1 * fileContentRepository.findBySnapshotIdAndFilePath(1L, "p/Foo.java") >> Optional.of(fc)
        result.get() == "public class Foo {}"
    }

    def "listSnapshots filters by repoUrl when filter set"() {
        given:
        def s1 = new Snapshot(1L, "https://github.com/a/repo", "sha1", null)
        def s2 = new Snapshot(2L, "https://github.com/b/other", "sha2", null)
        when:
        def result = service.listSnapshots("https://github.com/a/repo")
        then:
        1 * snapshotRepository.findAll() >> [s1, s2]
        result.size() == 1
        result[0].id() == 1L
    }

    def "searchSymbols returns symbols with filters and pagination"() {
        given:
        def art = new Artifact(10L, 1L, "p/Foo.java")
        def sym = new SymbolRow(100L, 10L, "Foo", "CLASS", "public")
        artifactRepository.findBySnapshotIdOrderByFilePath(1L) >> [art]
        symbolRepository.findByArtifactIdOrderById(10L) >> [sym]
        when:
        def result = service.searchSymbols(1L, null, null, null, 10, 0)
        then:
        result.size() == 1
        result[0].name() == "Foo"
        result[0].kind() == "CLASS"
        result[0].filePath() == "p/Foo.java"
    }

    def "searchSymbols filters by name and kind"() {
        given:
        def art = new Artifact(10L, 1L, "p/Foo.java")
        def symMatch = new SymbolRow(100L, 10L, "run", "METHOD", "public")
        def symSkip = new SymbolRow(101L, 10L, "id", "FIELD", "private")
        artifactRepository.findBySnapshotIdOrderByFilePath(1L) >> [art]
        symbolRepository.findByArtifactIdOrderById(10L) >> [symMatch, symSkip]
        when:
        def result = service.searchSymbols(1L, "run", "METHOD", null, 10, 0)
        then:
        result.size() == 1
        result[0].name() == "run"
    }

    def "searchSymbols filters by path"() {
        given:
        def art1 = new Artifact(10L, 1L, "p/Foo.java")
        def art2 = new Artifact(11L, 1L, "q/Bar.java")
        artifactRepository.findBySnapshotIdOrderByFilePath(1L) >> [art1, art2]
        symbolRepository.findByArtifactIdOrderById(10L) >> []
        symbolRepository.findByArtifactIdOrderById(11L) >> [new SymbolRow(100L, 11L, "Bar", "CLASS", "public")]
        when:
        def result = service.searchSymbols(1L, null, null, "q/", 10, 0)
        then:
        result.size() == 1
        result[0].filePath() == "q/Bar.java"
    }

    def "getSymbol returns empty when symbol not found"() {
        when:
        def result = service.getSymbol(1L, 999L)
        then:
        1 * symbolRepository.findById(999L) >> Optional.empty()
        result.isEmpty()
    }

    def "getSymbol returns empty when artifact not in snapshot"() {
        given:
        def sym = new SymbolRow(100L, 10L, "Foo", "CLASS", "public")
        symbolRepository.findById(100L) >> Optional.of(sym)
        artifactRepository.findById(10L) >> Optional.of(new Artifact(10L, 2L, "p/Foo.java"))
        when:
        def result = service.getSymbol(1L, 100L)
        then:
        result.isEmpty()
    }

    def "getSymbol returns detail when found"() {
        given:
        def sym = new SymbolRow(100L, 10L, "Foo", "CLASS", "public")
        def art = new Artifact(10L, 1L, "p/Foo.java")
        def span = new SymbolSpan(100L, "p/Foo.java", 1, 0, 10, 5)
        symbolRepository.findById(100L) >> Optional.of(sym)
        artifactRepository.findById(10L) >> Optional.of(art)
        symbolSpanRepository.findById(100L) >> Optional.of(span)
        when:
        def result = service.getSymbol(1L, 100L)
        then:
        result.isPresent()
        result.get().name() == "Foo"
        result.get().filePath() == "p/Foo.java"
        result.get().span().getStartLine() == 1
    }

    def "findReferences returns summaries for direction from"() {
        given:
        def ref = new Reference(1L, 100L, 200L, "CALL")
        referenceRepository.findByFromSymbolIdOrToSymbolId(100L) >> [ref]
        when:
        def result = service.findReferences(1L, 100L, "from")
        then:
        result.size() == 1
        result[0].fromSymbolId() == 100L
        result[0].toSymbolId() == 200L
    }

    def "findReferences returns summaries for direction to"() {
        given:
        def ref = new Reference(1L, 100L, 200L, "CALL")
        referenceRepository.findByFromSymbolIdOrToSymbolId(200L) >> [ref]
        when:
        def result = service.findReferences(1L, 200L, "to")
        then:
        result.size() == 1
    }

    def "getContainment by symbolId returns single node"() {
        given:
        def sym = new SymbolRow(50L, 10L, "Foo", "CLASS", "public")
        symbolRepository.findById(50L) >> Optional.of(sym)
        containmentRepository.findByParentSymbolId(50L) >> []
        when:
        def result = service.getContainment(1L, null, 50L)
        then:
        result.size() == 1
        result[0].symbolId() == 50L
        result[0].name() == "Foo"
    }

    def "getContainment by artifactId returns roots and builds tree"() {
        given:
        def s1 = new SymbolRow(10L, 1L, "Foo", "CLASS", "public")
        def s2 = new SymbolRow(11L, 1L, "run", "METHOD", "public")
        symbolRepository.findByArtifactIdOrderById(1L) >> [s1, s2]
        containmentRepository.findAll() >> [new Containment(1L, 10L, 11L)]
        containmentRepository.findByParentSymbolId(10L) >> [new Containment(1L, 10L, 11L)]
        containmentRepository.findByParentSymbolId(11L) >> []
        symbolRepository.findById(10L) >> Optional.of(s1)
        symbolRepository.findById(11L) >> Optional.of(s2)
        when:
        def result = service.getContainment(1L, 1L, null)
        then:
        result.size() == 1
        result[0].name() == "Foo"
        result[0].children().size() == 1
        result[0].children()[0].name() == "run"
    }

    def "getContainment returns empty when both artifactId and symbolId null"() {
        when:
        def result = service.getContainment(1L, null, null)
        then:
        result.isEmpty()
    }

    def "getFileContent returns empty when not found"() {
        when:
        def result = service.getFileContent(1L, "missing.java")
        then:
        1 * fileContentRepository.findBySnapshotIdAndFilePath(1L, "missing.java") >> Optional.empty()
        result.isEmpty()
    }
}
