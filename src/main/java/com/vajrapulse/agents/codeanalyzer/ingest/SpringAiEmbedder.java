package com.vajrapulse.agents.codeanalyzer.ingest;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts Spring AI's {@link EmbeddingModel} to our {@link Embedder} interface.
 * Accepts vectors of any dimension — the DB column is untyped {@code vector} (no fixed size).
 */
public class SpringAiEmbedder implements Embedder {

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
        for (Embedding emb : data) {
            float[] vector = emb.getOutput();
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException(
                        "Embedding model returned a null or empty vector");
            }
            result.add(vector);
        }
        return result;
    }
}
