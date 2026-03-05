package com.vajrapulse.agents.codeanalyzer.store;

public record FileContent(Long snapshotId, String filePath, String content) {}
