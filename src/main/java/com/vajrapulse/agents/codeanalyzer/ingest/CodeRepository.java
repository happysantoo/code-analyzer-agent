package com.vajrapulse.agents.codeanalyzer.ingest;

/**
 * Resolves a repo ref to a commit SHA and lists files (with optional content) for that commit.
 * Supports clone-from-URL and current-workspace modes.
 */
public interface CodeRepository {

    /**
     * Resolve the given repo location and ref to a snapshot (commit SHA + file list).
     *
     * @param repoUrlOrPath repo URL (for clone) or absolute path to workspace directory
     * @param ref          Git ref (branch/tag/SHA) or null for workspace (use current state)
     * @return snapshot with commit SHA and file entries
     */
    RepoSnapshot resolve(String repoUrlOrPath, String ref);
}
