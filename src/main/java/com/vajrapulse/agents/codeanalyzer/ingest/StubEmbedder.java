package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Returns zero vectors of fixed dimension. Use when no real embedding model is configured (e.g. dev/test).
 */
public class StubEmbedder implements Embedder {

    private static final int DIMENSION = 1536;

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream()
                .map(t -> new float[DIMENSION])
                .collect(Collectors.toList());
    }
}
