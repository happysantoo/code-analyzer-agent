package com.vajrapulse.agents.codeanalyzer.ingest;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts Spring AI's {@link EmbeddingModel} to our {@link Embedder} interface.
 * Validates that the model returns 1536-dimensional vectors to match the schema.
 */
public class SpringAiEmbedder implements Embedder {

    private static final int REQUIRED_DIMENSION = 1536;

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbedder(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingRequest request = new EmbeddingRequest(texts, null);
        EmbeddingResponse response = embeddingModel.call(request);
        List<Embedding> data = response.getResults();
        if (data == null || data.size() != texts.size()) {
            throw new IllegalStateException(
                    "Embedding model returned " + (data == null ? "null" : data.size()) + " vectors, expected " + texts.size());
        }
        List<float[]> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            Embedding emb = data.get(i);
            float[] vector = emb.getOutput();
            if (vector == null || vector.length != REQUIRED_DIMENSION) {
                throw new IllegalArgumentException(
                        "Embedding model must return vectors of dimension " + REQUIRED_DIMENSION
                                + " (schema constraint); got " + (vector == null ? "null" : vector.length));
            }
            result.add(vector);
        }
        return result;
    }
}
