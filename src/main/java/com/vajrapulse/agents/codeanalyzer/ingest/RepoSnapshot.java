package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.List;

/**
 * Result of resolving a repo ref: commit SHA and list of files (paths and optional content).
 */
public record RepoSnapshot(String commitSha, List<FileEntry> files) {

    public RepoSnapshot {
        if (commitSha == null || commitSha.isBlank()) {
            throw new IllegalArgumentException("commitSha must be non-blank");
        }
        files = files != null ? List.copyOf(files) : List.of();
    }

    public static RepoSnapshot of(String commitSha, List<FileEntry> files) {
        return new RepoSnapshot(commitSha, files);
    }
}
