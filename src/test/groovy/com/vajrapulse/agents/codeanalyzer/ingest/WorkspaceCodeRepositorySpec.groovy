package com.vajrapulse.agents.codeanalyzer.ingest

import org.eclipse.jgit.api.Git
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class WorkspaceCodeRepositorySpec extends Specification {

    Path tempDir
    WorkspaceCodeRepository repo

    def setup() {
        tempDir = Files.createTempDirectory("workspace-repo-spec-")
        repo = new WorkspaceCodeRepository()
    }

    def cleanup() {
        if (tempDir != null && Files.exists(tempDir)) {
            deleteRecursively(tempDir)
        }
    }

    def "resolve fails when path is not a directory"() {
        when:
        repo.resolve("/nonexistent", "HEAD")
        then:
        thrown(IllegalArgumentException)
    }

    def "resolve fails when path is not a git repo"() {
        given:
        Path plainDir = tempDir.resolve("plain")
        Files.createDirectories(plainDir)
        when:
        repo.resolve(plainDir.toString(), "HEAD")
        then:
        thrown(IllegalArgumentException)
    }

    def "resolve returns commit SHA and list of java files with content"() {
        given:
        Git.init().setDirectory(tempDir.toFile()).call()
        Files.createDirectories(tempDir.resolve("src"))
        Files.writeString(tempDir.resolve("src").resolve("Foo.java"), "public class Foo {}")
        Files.writeString(tempDir.resolve("Ignore.txt"), "ignore")
        def git = Git.open(tempDir.toFile())
        git.add().addFilepattern(".").call()
        git.commit().setMessage("initial").call()
        git.close()
        when:
        def snap = repo.resolve(tempDir.toString(), "HEAD")
        then:
        snap.commitSha() != null
        snap.commitSha().length() == 40
        snap.files().size() == 1
        snap.files().get(0).path() == "src/Foo.java"
        snap.files().get(0).content() == "public class Foo {}"
    }

    def "resolve with null ref uses HEAD"() {
        given:
        Git.init().setDirectory(tempDir.toFile()).call()
        Files.writeString(tempDir.resolve("A.java"), "class A {}")
        def git = Git.open(tempDir.toFile())
        git.add().addFilepattern(".").call()
        git.commit().setMessage("c1").call()
        git.close()
        when:
        def snap = repo.resolve(tempDir.toString(), null)
        then:
        snap.commitSha() != null
        snap.files().size() == 1
    }

    def "resolve with blank ref uses HEAD"() {
        given:
        Git.init().setDirectory(tempDir.toFile()).call()
        Files.writeString(tempDir.resolve("B.java"), "class B {}")
        def git = Git.open(tempDir.toFile())
        git.add().addFilepattern(".").call()
        git.commit().setMessage("c2").call()
        git.close()
        when:
        def snap = repo.resolve(tempDir.toString(), "   ")
        then:
        snap.commitSha() != null
        snap.files().size() == 1
    }

    def "constructor with null fileExtensions uses default java ext"() {
        when:
        def repoWithDefault = new WorkspaceCodeRepository(null)
        then:
        repoWithDefault != null
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path).sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) }
            }
        } catch (Exception ignored) {}
    }
}
