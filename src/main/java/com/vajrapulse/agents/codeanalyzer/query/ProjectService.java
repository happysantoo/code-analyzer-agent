package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.store.Project;
import com.vajrapulse.agents.codeanalyzer.store.ProjectRepository;
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshot;
import com.vajrapulse.agents.codeanalyzer.store.ProjectSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectSnapshotRepository projectSnapshotRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectSnapshotRepository projectSnapshotRepository) {
        this.projectRepository = projectRepository;
        this.projectSnapshotRepository = projectSnapshotRepository;
    }

    @Transactional
    public long createProject(String name, String description) {
        Project p = projectRepository.save(new Project(null, name != null ? name : "", description));
        return p.id();
    }

    @Transactional
    public void linkSnapshotsToProject(long projectId, List<Long> snapshotIds) {
        projectSnapshotRepository.deleteByProjectId(projectId);
        if (snapshotIds != null) {
            for (Long sid : snapshotIds) {
                projectSnapshotRepository.save(new ProjectSnapshot(projectId, sid));
            }
        }
    }

    public List<ProjectSummary> listProjects() {
        return projectRepository.findAll().stream()
                .map(p -> new ProjectSummary(p.id(), p.name(), p.description()))
                .toList();
    }

    public Optional<ProjectDetail> getProject(long projectId) {
        return projectRepository.findById(projectId)
                .map(p -> {
                    List<Long> snapshotIds = projectSnapshotRepository.findByProjectIdOrderBySnapshotId(p.id()).stream()
                            .map(ProjectSnapshot::snapshotId)
                            .toList();
                    return new ProjectDetail(p.id(), p.name(), p.description(), snapshotIds);
                });
    }

    public List<Long> getSnapshotIdsForProject(long projectId) {
        return projectSnapshotRepository.findByProjectIdOrderBySnapshotId(projectId).stream()
                .map(ProjectSnapshot::snapshotId)
                .toList();
    }
}
