package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(long id);

    List<Project> findAll();
}
