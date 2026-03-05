package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;

public interface ProjectSnapshotRepository {

    void save(ProjectSnapshot projectSnapshot);

    void deleteByProjectId(long projectId);

    List<ProjectSnapshot> findByProjectIdOrderBySnapshotId(long projectId);
}
