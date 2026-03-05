package com.vajrapulse.agents.codeanalyzer.model;

import java.util.Objects;

/**
 * Raw file content for a path in a snapshot.
 */
public final class FileContent {

    private final long snapshotId;
    private final String filePath;
    private final String content;

    public FileContent(long snapshotId, String filePath, String content) {
        this.snapshotId = snapshotId;
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.content = content != null ? content : "";
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContent() {
        return content;
    }
}
