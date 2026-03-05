package com.vajrapulse.agents.codeanalyzer.query

import com.vajrapulse.agents.codeanalyzer.store.Project
import com.vajrapulse.agents.codeanalyzer.store.ProjectRepository
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshot
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshotRepository
import spock.lang.Specification

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
}
