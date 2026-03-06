package com.vajrapulse.agents.codeanalyzer.ingest

import org.eclipse.jgit.api.Git
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class CloneCodeRepositorySpec extends Specification {

    Path tempBase
    Path sourceRepo
    CloneCodeRepository repo

    def setup() {
        tempBase = Files.createTempDirectory("clone-spec-base-")
        sourceRepo = Files.createTempDirectory("clone-spec-source-")
        Git.init().setDirectory(sourceRepo.toFile()).call()
        Files.writeString(sourceRepo.resolve("Only.java"), "public class Only {}")
        def git = Git.open(sourceRepo.toFile())
        git.add().addFilepattern(".").call()
        git.commit().setMessage("init").call()
        git.close()
        repo = new CloneCodeRepository(tempBase)
    }

    def cleanup() {
        [tempBase, sourceRepo].each { path ->
            if (path != null && Files.exists(path)) {
                deleteRecursively(path)
            }
        }
    }

    def "resolve rejects blank repo URL"() {
        when:
        repo.resolve(" ", "HEAD")
        then:
        thrown(IllegalArgumentException)
    }

    def "resolve rejects null repo URL"() {
        when:
        repo.resolve(null, "HEAD")
        then:
        thrown(IllegalArgumentException)
    }

    def "constructor with null tempBase uses system temp dir"() {
        when:
        def r = new CloneCodeRepository(null)
        then:
        r != null
    }

    def "resolve clones from file URL and returns commit and files"() {
        given:
        String fileUri = sourceRepo.toUri().toString()
        when:
        def snap = repo.resolve(fileUri, "HEAD")
        then:
        snap.commitSha() != null
        snap.commitSha().length() == 40
        snap.files().size() == 1
        snap.files().get(0).path() == "Only.java"
        snap.files().get(0).content() == "public class Only {}"
    }

    def "resolve with null ref uses HEAD"() {
        when:
        def snap = repo.resolve(sourceRepo.toUri().toString(), null)
        then:
        snap.commitSha() != null
        snap.files().size() == 1
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path).sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) }
            }
        } catch (Exception ignored) {}
    }
}
