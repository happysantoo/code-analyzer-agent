package com.vajrapulse.agents.codeanalyzer.query;

public record ReferenceSummary(long id, long fromSymbolId, long toSymbolId, String refType) {}
