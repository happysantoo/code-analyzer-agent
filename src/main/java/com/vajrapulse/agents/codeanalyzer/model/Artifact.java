package com.vajrapulse.agents.codeanalyzer.model;

import java.util.Objects;

/**
 * Versioned unit of code (e.g. file). Tied to snapshot (repo + commit).
 */
public final class Artifact {

    private final Long id;
    private final long snapshotId;
    private final String filePath;

    public Artifact(Long id, long snapshotId, String filePath) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Long getId() {
        return id;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public String getFilePath() {
        return filePath;
    }
}
