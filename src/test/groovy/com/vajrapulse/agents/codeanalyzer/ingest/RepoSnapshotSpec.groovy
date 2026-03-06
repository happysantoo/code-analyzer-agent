package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class RepoSnapshotSpec extends Specification {

    def "RepoSnapshot requires non-blank commitSha"() {
        when:
        RepoSnapshot.of(" ", [])
        then:
        thrown(IllegalArgumentException)
    }

    def "RepoSnapshot with null files uses empty list"() {
        when:
        def snap = RepoSnapshot.of("sha1", null)
        then:
        snap.commitSha() == "sha1"
        snap.files() != null
        snap.files().isEmpty()
    }

    def "RepoSnapshot copies files list"() {
        when:
        def entries = [FileEntry.of("a.java"), FileEntry.of("b.java", "content")]
        def snap = RepoSnapshot.of("abc123", entries)
        then:
        snap.commitSha() == "abc123"
        snap.files().size() == 2
        snap.files().get(0).path() == "a.java"
        snap.files().get(1).content() == "content"
    }

    def "FileEntry of path only has null content"() {
        expect:
        FileEntry.of("x.java").content() == null
        FileEntry.of("x.java").path() == "x.java"
    }

    def "FileEntry of path and content"() {
        expect:
        FileEntry.of("x.java", "code").path() == "x.java"
        FileEntry.of("x.java", "code").content() == "code"
    }

    def "FileEntry rejects null path"() {
        when:
        new FileEntry(null, null)
        then:
        thrown(NullPointerException)
    }
}
