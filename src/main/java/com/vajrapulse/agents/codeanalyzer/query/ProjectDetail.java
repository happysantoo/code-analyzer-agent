package com.vajrapulse.agents.codeanalyzer.query;

import java.util.List;

public record ProjectDetail(long id, String name, String description, List<Long> snapshotIds) {}
