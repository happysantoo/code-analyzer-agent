package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.model.Span;

import java.util.List;

/**
 * Result of parsing one file: symbols with spans, and reference/containment by symbol index.
 * Ingestion resolves indices to DB ids after insert.
 */
public record ParseResult(
    List<SymbolInfo> symbols,
    List<Span> spans,
    List<ReferenceByIndex> references,
    List<ContainmentByIndex> containments
) {
    public ParseResult {
        symbols = symbols != null ? List.copyOf(symbols) : List.of();
        spans = spans != null ? List.copyOf(spans) : List.of();
        references = references != null ? List.copyOf(references) : List.of();
        containments = containments != null ? List.copyOf(containments) : List.of();
    }
}
