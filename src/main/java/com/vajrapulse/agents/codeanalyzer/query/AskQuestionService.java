package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.ingest.Embedder;
import com.vajrapulse.agents.codeanalyzer.store.CodeChunkHit;
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Semantic search over code embeddings: embed question, query pgvector, return ranked chunks.
 * Supports single snapshot or project (linked snapshots). Optional RAG can be added later.
 */
@Service
public class AskQuestionService {

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;

    private final Embedder embedder;
    private final CodeEmbeddingRepository codeEmbeddingRepository;
    private final ProjectService projectService;

    public AskQuestionService(Embedder embedder, CodeEmbeddingRepository codeEmbeddingRepository, ProjectService projectService) {
        this.embedder = embedder;
        this.codeEmbeddingRepository = codeEmbeddingRepository;
        this.projectService = projectService;
    }

    public AskQuestionResult ask(long snapshotId, String question, int topK) {
        return ask(List.of(snapshotId), question, topK);
    }

    /** Ask over all snapshots linked to the project. */
    public AskQuestionResult askByProject(long projectId, String question, int topK) {
        List<Long> snapshotIds = projectService.getSnapshotIdsForProject(projectId);
        if (snapshotIds.isEmpty()) {
            return new AskQuestionResult(List.of(), "Project has no linked snapshots.");
        }
        return ask(snapshotIds, question, topK);
    }

    public AskQuestionResult ask(List<Long> snapshotIds, String question, int topK) {
        if (question == null || question.isBlank()) {
            return new AskQuestionResult(List.of(), "Question may not be empty.");
        }
        int k = Math.min(Math.max(topK > 0 ? topK : DEFAULT_TOP_K, 1), MAX_TOP_K);
        List<float[]> vectors = embedder.embed(List.of(question));
        float[] queryVector = vectors.isEmpty() ? new float[0] : vectors.get(0);
        List<CodeChunkHit> hits = codeEmbeddingRepository.searchBySimilarity(snapshotIds, queryVector, k);
        return new AskQuestionResult(hits, null);
    }
}
