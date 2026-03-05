package com.vajrapulse.agents.codeanalyzer.store;

public record Reference(Long id, Long fromSymbolId, Long toSymbolId, String refType) {}
