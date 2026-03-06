package com.vajrapulse.agents.codeanalyzer.query

import com.vajrapulse.agents.codeanalyzer.store.Project
import com.vajrapulse.agents.codeanalyzer.store.ProjectRepository
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshot
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshotRepository
import spock.lang.Specification

import java.util.Optional

class ProjectServiceSpec extends Specification {

    def projectRepository = Mock(ProjectRepository)
    def projectSnapshotRepository = Mock(ProjectSnapshotRepository)
    def service = new ProjectService(projectRepository, projectSnapshotRepository)

    def "createProject saves and returns id"() {
        given:
        projectRepository.save(_) >> new Project(1L, "my-project", "desc")
        when:
        def id = service.createProject("my-project", "desc")
        then:
        id == 1L
    }

    def "getSnapshotIdsForProject returns linked snapshot ids"() {
        given:
        projectSnapshotRepository.findByProjectIdOrderBySnapshotId(1L) >> [
            new ProjectSnapshot(1L, 10L),
            new ProjectSnapshot(1L, 20L)
        ]
        when:
        def ids = service.getSnapshotIdsForProject(1L)
        then:
        ids == [10L, 20L]
    }

    def "getSnapshotIdsForProject returns empty when none linked"() {
        given:
        projectSnapshotRepository.findByProjectIdOrderBySnapshotId(1L) >> []
        when:
        def result = service.getSnapshotIdsForProject(1L)
        then:
        result.isEmpty()
    }

    def "listProjects returns summaries"() {
        given:
        projectRepository.findAll() >> [
            new Project(1L, "p1", "d1"),
            new Project(2L, "p2", null)
        ]
        when:
        def result = service.listProjects()
        then:
        result.size() == 2
        result[0].id() == 1L
        result[0].name() == "p1"
        result[1].name() == "p2"
    }

    def "getProject returns empty when not found"() {
        when:
        def result = service.getProject(999L)
        then:
        1 * projectRepository.findById(999L) >> Optional.empty()
        result.isEmpty()
    }

    def "getProject returns detail with snapshot ids"() {
        given:
        def p = new Project(1L, "my-project", "desc")
        projectRepository.findById(1L) >> Optional.of(p)
        projectSnapshotRepository.findByProjectIdOrderBySnapshotId(1L) >> [
            new ProjectSnapshot(1L, 10L),
            new ProjectSnapshot(1L, 20L)
        ]
        when:
        def result = service.getProject(1L)
        then:
        result.isPresent()
        result.get().id() == 1L
        result.get().name() == "my-project"
        result.get().snapshotIds() == [10L, 20L]
    }

    def "linkSnapshotsToProject clears and saves new links"() {
        when:
        service.linkSnapshotsToProject(1L, [10L, 20L])
        then:
        1 * projectSnapshotRepository.deleteByProjectId(1L)
        1 * projectSnapshotRepository.save(new ProjectSnapshot(1L, 10L))
        1 * projectSnapshotRepository.save(new ProjectSnapshot(1L, 20L))
    }

    def "linkSnapshotsToProject with null list clears only"() {
        when:
        service.linkSnapshotsToProject(1L, null)
        then:
        1 * projectSnapshotRepository.deleteByProjectId(1L)
        0 * projectSnapshotRepository.save(_)
    }
}
