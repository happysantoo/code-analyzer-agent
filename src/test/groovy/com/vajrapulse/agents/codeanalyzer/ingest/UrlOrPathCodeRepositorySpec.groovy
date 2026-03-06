package com.vajrapulse.agents.codeanalyzer.ingest

import spock.lang.Specification

class UrlOrPathCodeRepositorySpec extends Specification {

    def workspace = Mock(WorkspaceCodeRepository)
    def cloneRepo = Mock(CloneCodeRepository)
    def resolver = new UrlOrPathCodeRepository(workspace, cloneRepo)

    def "resolve delegates to CloneCodeRepository for https URL"() {
        given:
        def snap = RepoSnapshot.of("abc123", [])
        when:
        def result = resolver.resolve("https://github.com/org/repo.git", "HEAD")
        then:
        1 * cloneRepo.resolve("https://github.com/org/repo.git", "HEAD") >> snap
        0 * workspace.resolve(_, _)
        result == snap
    }

    def "resolve delegates to CloneCodeRepository for http URL"() {
        given:
        def snap = RepoSnapshot.of("def456", [])
        when:
        def result = resolver.resolve("http://git.example.com/repo.git", "main")
        then:
        1 * cloneRepo.resolve("http://git.example.com/repo.git", "main") >> snap
        0 * workspace.resolve(_, _)
        result == snap
    }

    def "resolve delegates to CloneCodeRepository for git@ URL"() {
        given:
        def snap = RepoSnapshot.of("sha789", [])
        when:
        def result = resolver.resolve("git@github.com:org/repo.git", "HEAD")
        then:
        1 * cloneRepo.resolve("git@github.com:org/repo.git", "HEAD") >> snap
        0 * workspace.resolve(_, _)
        result == snap
    }

    def "resolve delegates to WorkspaceCodeRepository for local path"() {
        given:
        def snap = RepoSnapshot.of("local1", [])
        when:
        def result = resolver.resolve("/tmp/my-repo", "HEAD")
        then:
        1 * workspace.resolve("/tmp/my-repo", "HEAD") >> snap
        0 * cloneRepo.resolve(_, _)
        result == snap
    }

    def "resolve delegates to WorkspaceCodeRepository for relative-looking path"() {
        given:
        def snap = RepoSnapshot.of("local2", [])
        when:
        def result = resolver.resolve("/home/user/projects/app", null)
        then:
        1 * workspace.resolve("/home/user/projects/app", null) >> snap
        0 * cloneRepo.resolve(_, _)
        result == snap
    }
}
