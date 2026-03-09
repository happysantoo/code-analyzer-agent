package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.ingest.Embedder;
import com.vajrapulse.agents.codeanalyzer.store.CodeChunkHit;
import com.vajrapulse.agents.codeanalyzer.store.CodeEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AskQuestionService.class);

    private final Embedder embedder;
    private final CodeEmbeddingRepository codeEmbeddingRepository;
    private final ProjectService projectService;

    public AskQuestionService(Embedder embedder, CodeEmbeddingRepository codeEmbeddingRepository, ProjectService projectService) {
        this.embedder = embedder;
        this.codeEmbeddingRepository = codeEmbeddingRepository;
        this.projectService = projectService;
    }

    public AskQuestionResult ask(long snapshotId, String question, int topK) {
        if (snapshotId <= 0) {
            log.warn("ask() called with invalid snapshotId={} (question='{}')", snapshotId, question);
            return new AskQuestionResult(List.of(),
                    "Provide a valid snapshot_id (from list_snapshots or analyze_repository), or use project_id to search over a project.");
        }
        log.info("ask() for single snapshotId={} topK={} question='{}'", snapshotId, topK, question);
        return ask(List.of(snapshotId), question, topK);
    }

    /** Ask over all snapshots linked to the project. */
    public AskQuestionResult askByProject(long projectId, String question, int topK) {
        List<Long> snapshotIds = projectService.getSnapshotIdsForProject(projectId);
        if (snapshotIds.isEmpty()) {
            log.warn("askByProject() called for projectId={} but it has no linked snapshots", projectId);
            return new AskQuestionResult(List.of(), "Project has no linked snapshots.");
        }
        log.info("askByProject() projectId={} snapshots={} topK={} question='{}'", projectId, snapshotIds, topK, question);
        return ask(snapshotIds, question, topK);
    }

    public AskQuestionResult ask(List<Long> snapshotIds, String question, int topK) {
        if (question == null || question.isBlank()) {
            log.warn("ask() called with blank question for snapshots={}", snapshotIds);
            return new AskQuestionResult(List.of(), "Question may not be empty.");
        }
        int k = Math.min(Math.max(topK > 0 ? topK : DEFAULT_TOP_K, 1), MAX_TOP_K);
        log.info("ask() snapshots={} resolvedTopK={} embedder={} question='{}'",
                snapshotIds, k, embedder != null ? embedder.getClass().getSimpleName() : "null", question);

        List<float[]> vectors = embedder != null ? embedder.embed(List.of(question)) : List.of();
        if (vectors == null) {
            log.warn("Embedder returned null vector list for question='{}'", question);
            vectors = List.of();
        }

        float[] queryVector = vectors.isEmpty() ? new float[0] : vectors.get(0);
        log.info("ask() embedded question into vector of length={} for snapshots={}",
                queryVector != null ? queryVector.length : -1, snapshotIds);

        List<CodeChunkHit> hits = codeEmbeddingRepository.searchBySimilarity(snapshotIds, queryVector, k);
        if (hits == null) hits = List.of();
        log.info("ask() searchBySimilarity returned {} hits for snapshots={}", hits.size(), snapshotIds);

        return new AskQuestionResult(hits, null);
    }
}
