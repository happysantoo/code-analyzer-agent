package com.vajrapulse.agents.codeanalyzer.store;

import java.time.Instant;

public record Snapshot(Long id, String repoUrl, String commitSha, Instant createdAt) {}
