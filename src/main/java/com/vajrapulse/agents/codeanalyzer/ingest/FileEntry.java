package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.Objects;

/**
 * A single file in a repo snapshot: path and optional content.
 */
public record FileEntry(String path, String content) {

    public FileEntry {
        Objects.requireNonNull(path, "path");
    }

    public static FileEntry of(String path) {
        return new FileEntry(path, null);
    }

    public static FileEntry of(String path, String content) {
        return new FileEntry(path, content);
    }
}
