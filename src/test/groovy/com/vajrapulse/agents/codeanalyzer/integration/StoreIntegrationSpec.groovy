package com.vajrapulse.agents.codeanalyzer.integration

import com.vajrapulse.agents.codeanalyzer.store.Artifact
import com.vajrapulse.agents.codeanalyzer.store.ArtifactRepository
import com.vajrapulse.agents.codeanalyzer.store.Snapshot
import com.vajrapulse.agents.codeanalyzer.store.SnapshotRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

/**
 * Integration tests for JDBC repositories against embedded H2 (relational schema only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestDbConfig)
class StoreIntegrationSpec extends Specification {

    @Autowired
    SnapshotRepository snapshotRepository

    @Autowired
    ArtifactRepository artifactRepository

    def "snapshot save and find"() {
        when:
        Snapshot saved = snapshotRepository.save(new Snapshot(null, "https://github.com/foo/bar.git", "abc123", null))

        then:
        saved.id() != null

        when:
        def byId = snapshotRepository.findById(saved.id())
        def byRepoAndSha = snapshotRepository.findByRepoUrlAndCommitSha("https://github.com/foo/bar.git", "abc123")

        then:
        byId.isPresent()
        byId.get().repoUrl() == "https://github.com/foo/bar.git"
        byId.get().commitSha() == "abc123"
        byRepoAndSha.isPresent()
        byRepoAndSha.get().id() == saved.id()
    }

    def "artifact save and find by snapshot"() {
        given:
        Snapshot snap = snapshotRepository.save(new Snapshot(null, "file:///repo", "sha1", null))

        when:
        Artifact a1 = artifactRepository.save(new Artifact(null, snap.id(), "src/Main.java"))
        Artifact a2 = artifactRepository.save(new Artifact(null, snap.id(), "src/Util.java"))

        then:
        a1.id() != null
        a2.id() != null

        when:
        def list = artifactRepository.findBySnapshotIdOrderByFilePath(snap.id())

        then:
        list.size() == 2
        list[0].filePath() == "src/Main.java"
        list[1].filePath() == "src/Util.java"
    }

    def "delete artifacts by snapshot id"() {
        given:
        Snapshot snap = snapshotRepository.save(new Snapshot(null, "file:///x", "s2", null))
        artifactRepository.save(new Artifact(null, snap.id(), "a.java"))

        expect:
        artifactRepository.findBySnapshotIdOrderByFilePath(snap.id()).size() == 1

        when:
        snapshotRepository.deleteArtifactsBySnapshotId(snap.id())

        then:
        artifactRepository.findBySnapshotIdOrderByFilePath(snap.id()).isEmpty()
    }
}
