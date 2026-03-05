package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.List;

/**
 * Abstracts embedding of text to vectors. Can be backed by Spring AI EmbeddingModel.
 */
public interface Embedder {

    /**
     * Embed texts into vectors. Each vector must have the same dimension (e.g. 1536).
     */
    List<float[]> embed(List<String> texts);
}
