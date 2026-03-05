package com.vajrapulse.agents.codeanalyzer.query;

public record SymbolSummary(long id, long artifactId, String name, String kind, String visibility, String filePath) {}
