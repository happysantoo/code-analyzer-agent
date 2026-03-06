package com.vajrapulse.agents.codeanalyzer.ingest

import com.vajrapulse.agents.codeanalyzer.store.*
import spock.lang.Specification

class IngestionServiceSpec extends Specification {

    def codeRepository = Mock(CodeRepository)
    def parserRegistry = Mock(ParserRegistry)
    def snapshotRepository = Mock(SnapshotRepository)
    def artifactRepository = Mock(ArtifactRepository)
    def symbolRepository = Mock(SymbolRepository)
    def symbolSpanRepository = Mock(SymbolSpanRepository)
    def referenceRepository = Mock(ReferenceRepository)
    def containmentRepository = Mock(ContainmentRepository)
    def fileContentRepository = Mock(FileContentRepository)
    def chunkingStrategy = Mock(ChunkingStrategy)
    def embeddingPipeline = Mock(EmbeddingPipeline)

    def service = new IngestionService(
        codeRepository, parserRegistry, snapshotRepository, artifactRepository,
        symbolRepository, symbolSpanRepository, referenceRepository, containmentRepository,
        fileContentRepository, chunkingStrategy, embeddingPipeline)

    def "analyze resolves repo and creates snapshot then embeds"() {
        given:
        def snapshot = RepoSnapshot.of("abc123", [FileEntry.of("Foo.java", "public class Foo {}")])
        codeRepository.resolve("path", "HEAD") >> snapshot
        parserRegistry.getParserFor("Foo.java") >> Optional.of(new JavaSemanticParser())
        snapshotRepository.findByRepoUrlAndCommitSha("path", "abc123") >> Optional.empty()
        snapshotRepository.save(_) >> new Snapshot(1L, "path", "abc123", null)
        artifactRepository.save(_) >> new Artifact(10L, 1L, "Foo.java")
        symbolRepository.save(_) >> new SymbolRow(100L, 10L, "Foo", "CLASS", "public")
        chunkingStrategy.chunk(1L, "Foo.java", _, _) >> [
            new ChunkDto("CLASS public Foo", 1L, 10L, 100L, "Foo.java", "1:0-5:0", "CLASS")
        ]
        when:
        def snapshotId = service.analyze("path", "HEAD")
        then:
        snapshotId == 1L
        1 * snapshotRepository.deleteArtifactsBySnapshotId(1L)
        1 * fileContentRepository.deleteBySnapshotId(1L)
        1 * embeddingPipeline.embedAndStore(1L, _)
    }

    def "analyze with no parser skips file"() {
        given:
        def snapshot = RepoSnapshot.of("sha1", [FileEntry.of("readme.txt", "content")])
        codeRepository.resolve("repo", null) >> snapshot
        parserRegistry.getParserFor("readme.txt") >> Optional.empty()
        snapshotRepository.findByRepoUrlAndCommitSha("repo", "sha1") >> Optional.empty()
        snapshotRepository.save(_) >> new Snapshot(2L, "repo", "sha1", null)
        when:
        def snapshotId = service.analyze("repo", null)
        then:
        snapshotId == 2L
        0 * artifactRepository.save(_)
        1 * embeddingPipeline.embedAndStore(2L, [])
    }

    def "analyze saves file content when parser returns empty symbols"() {
        given:
        def emptyParser = Mock(SemanticParser)
        emptyParser.parse(_, _) >> new ParseResult([], [], [], [])
        def snapshot = RepoSnapshot.of("sha2", [FileEntry.of("empty.java", "// comment only")])
        codeRepository.resolve("path", "HEAD") >> snapshot
        parserRegistry.getParserFor("empty.java") >> Optional.of(emptyParser)
        snapshotRepository.findByRepoUrlAndCommitSha("path", "sha2") >> Optional.empty()
        snapshotRepository.save(_) >> new Snapshot(3L, "path", "sha2", null)
        when:
        def snapshotId = service.analyze("path", "HEAD")
        then:
        snapshotId == 3L
        1 * fileContentRepository.save(_)
        1 * embeddingPipeline.embedAndStore(3L, [])
    }

    def "analyze reuses existing snapshot when repo and commit match"() {
        given:
        def snapshot = RepoSnapshot.of("cafe", [FileEntry.of("A.java", "class A {}")])
        codeRepository.resolve("url", "main") >> snapshot
        parserRegistry.getParserFor("A.java") >> Optional.of(new JavaSemanticParser())
        def existing = new Snapshot(5L, "url", "cafe", null)
        snapshotRepository.findByRepoUrlAndCommitSha("url", "cafe") >> Optional.of(existing)
        artifactRepository.save(_) >> new Artifact(50L, 5L, "A.java")
        symbolRepository.save(_) >> new SymbolRow(500L, 50L, "A", "CLASS", "package")
        chunkingStrategy.chunk(5L, "A.java", _, _) >> []
        when:
        def snapshotId = service.analyze("url", "main")
        then:
        snapshotId == 5L
        0 * snapshotRepository.save(_)
        1 * embeddingPipeline.embedAndStore(5L, _)
    }

    def "analyze saves symbol spans references and containments when parser returns them"() {
        given:
        def customParser = Mock(SemanticParser)
        def span0 = new com.vajrapulse.agents.codeanalyzer.model.Span("B.java", 1, 0, 2, 0)
        def span1 = new com.vajrapulse.agents.codeanalyzer.model.Span("B.java", 2, 0, 4, 0)
        def symbols = [
            new SymbolInfo("B", "CLASS", "public"),
            new SymbolInfo("m", "METHOD", "public")
        ]
        def spans = [span0, span1]
        def refs = [new ReferenceByIndex(0, 1, "CALLS")]
        def conts = [new ContainmentByIndex(0, 1)]
        customParser.parse("B.java", _) >> new ParseResult(symbols, spans, refs, conts)
        def snapshot = RepoSnapshot.of("sha3", [FileEntry.of("B.java", "class B { void m() {} }")])
        codeRepository.resolve("path", "HEAD") >> snapshot
        parserRegistry.getParserFor("B.java") >> Optional.of(customParser)
        snapshotRepository.findByRepoUrlAndCommitSha("path", "sha3") >> Optional.empty()
        snapshotRepository.save(_) >> new Snapshot(4L, "path", "sha3", null)
        artifactRepository.save(_) >> new Artifact(40L, 4L, "B.java")
        symbolRepository.save(_) >>> [new SymbolRow(401L, 40L, "B", "CLASS", "public"), new SymbolRow(402L, 40L, "m", "METHOD", "public")]
        chunkingStrategy.chunk(4L, "B.java", _, _) >> [new ChunkDto("CLASS public B", 4L, 40L, 401L, "B.java", "1:0-2:0", "CLASS"), new ChunkDto("METHOD public m", 4L, 40L, 402L, "B.java", "2:0-4:0", "METHOD")]
        when:
        def snapshotId = service.analyze("path", "HEAD")
        then:
        snapshotId == 4L
        2 * symbolSpanRepository.save(_)
        1 * referenceRepository.save(_)
        1 * containmentRepository.save(_)
        1 * embeddingPipeline.embedAndStore(4L, _)
    }
}
