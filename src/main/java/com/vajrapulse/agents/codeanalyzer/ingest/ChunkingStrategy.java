package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.model.Span;
import com.vajrapulse.agents.codeanalyzer.model.Symbol;

import java.util.List;

/**
 * Produces embeddable chunks from the canonical model (per snapshot).
 * Chunk text = symbol name + kind (and optionally signature/docstring when available).
 * Linkage metadata (snapshot_id, artifact_id, symbol_id, path, span, kind) is stored in the vector table.
 */
@FunctionalInterface
public interface ChunkingStrategy {

    /**
     * Build chunks for the given symbols and their spans. artifactId/symbolId may be null
     * if not yet persisted; ingestion sets them when writing to pgvector.
     *
     * @param snapshotId snapshot id
     * @param filePath   file path for all symbols in this call
     * @param symbols    symbols (with ids if already persisted)
     * @param spans      spans aligned with symbols by index
     * @return list of chunk DTOs (text to embed + metadata)
     */
    List<ChunkDto> chunk(long snapshotId, String filePath, List<Symbol> symbols, List<Span> spans);
}
