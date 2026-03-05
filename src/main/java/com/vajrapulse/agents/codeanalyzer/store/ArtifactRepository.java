package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository {

    Artifact save(Artifact artifact);

    Optional<Artifact> findById(long id);

    List<Artifact> findBySnapshotIdOrderByFilePath(long snapshotId);
}
