package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.model.Span;

public record SymbolDetail(long id, long artifactId, String name, String kind, String visibility, String filePath, Span span) {}
