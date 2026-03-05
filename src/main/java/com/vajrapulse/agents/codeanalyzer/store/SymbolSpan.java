package com.vajrapulse.agents.codeanalyzer.store;

public record SymbolSpan(Long symbolId, String filePath, int startLine, int startColumn, int endLine, int endColumn) {}
