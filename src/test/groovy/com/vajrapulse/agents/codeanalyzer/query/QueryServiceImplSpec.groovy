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
}
