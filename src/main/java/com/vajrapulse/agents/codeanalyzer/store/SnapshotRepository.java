package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository {

    Optional<Snapshot> findByRepoUrlAndCommitSha(String repoUrl, String commitSha);

    Snapshot save(Snapshot snapshot);

    Optional<Snapshot> findById(long id);

    List<Snapshot> findAll();

    void deleteArtifactsBySnapshotId(long snapshotId);
}
