package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.Objects;

/**
 * One unit to embed: text + metadata for vector store and linkages.
 * artifactId and symbolId may be null until set by ingestion after persist.
 */
public record ChunkDto(
    String textToEmbed,
    long snapshotId,
    Long artifactId,
    Long symbolId,
    String filePath,
    String span,
    String kind
) {
    public ChunkDto {
        Objects.requireNonNull(textToEmbed, "textToEmbed");
        Objects.requireNonNull(filePath, "filePath");
        span = span != null ? span : "";
        kind = kind != null ? kind : "";
    }
}
