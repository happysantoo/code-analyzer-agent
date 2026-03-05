package com.vajrapulse.agents.codeanalyzer.store;

/**
 * One hit from vector similarity search: content and metadata for ask_question.
 */
public record CodeChunkHit(String content, long snapshotId, Long artifactId, Long symbolId, String filePath, String span, String kind) {}
