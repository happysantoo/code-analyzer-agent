package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.model.Span;
import com.vajrapulse.agents.codeanalyzer.model.Symbol;
import com.vajrapulse.agents.codeanalyzer.store.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates: resolve repo → parse → persist relational → chunk → embed and store.
 * Replace semantics: re-run replaces all data for (repo_url, commit_sha).
 */
@Service
public class IngestionService {

    private final CodeRepository codeRepository;
    private final ParserRegistry parserRegistry;
    private final SnapshotRepository snapshotRepository;
    private final ArtifactRepository artifactRepository;
    private final SymbolRepository symbolRepository;
    private final SymbolSpanRepository symbolSpanRepository;
    private final ReferenceRepository referenceRepository;
    private final ContainmentRepository containmentRepository;
    private final FileContentRepository fileContentRepository;
    private final ChunkingStrategy chunkingStrategy;
    private final EmbeddingPipeline embeddingPipeline;

    public IngestionService(
            CodeRepository codeRepository,
            ParserRegistry parserRegistry,
            SnapshotRepository snapshotRepository,
            ArtifactRepository artifactRepository,
            SymbolRepository symbolRepository,
            SymbolSpanRepository symbolSpanRepository,
            ReferenceRepository referenceRepository,
            ContainmentRepository containmentRepository,
            FileContentRepository fileContentRepository,
            ChunkingStrategy chunkingStrategy,
            EmbeddingPipeline embeddingPipeline) {
        this.codeRepository = codeRepository;
        this.parserRegistry = parserRegistry;
        this.snapshotRepository = snapshotRepository;
        this.artifactRepository = artifactRepository;
        this.symbolRepository = symbolRepository;
        this.symbolSpanRepository = symbolSpanRepository;
        this.referenceRepository = referenceRepository;
        this.containmentRepository = containmentRepository;
        this.fileContentRepository = fileContentRepository;
        this.chunkingStrategy = chunkingStrategy;
        this.embeddingPipeline = embeddingPipeline;
    }

    /**
     * Analyze the repo at the given location and ref; persist relational + vector data. Returns snapshot id.
     */
    @Transactional
    public long analyze(String repoUrlOrPath, String ref) {
        RepoSnapshot snapshot = codeRepository.resolve(repoUrlOrPath, ref);
        Snapshot snapshotRow = snapshotRepository.findByRepoUrlAndCommitSha(repoUrlOrPath, snapshot.commitSha())
                .orElseGet(() -> snapshotRepository.save(new Snapshot(null, repoUrlOrPath, snapshot.commitSha(), null)));
        long snapshotId = snapshotRow.id();

        snapshotRepository.deleteArtifactsBySnapshotId(snapshotId);
        fileContentRepository.deleteBySnapshotId(snapshotId);

        List<ChunkDto> allChunks = new ArrayList<>();
        for (FileEntry file : snapshot.files()) {
            Optional<SemanticParser> parserOpt = parserRegistry.getParserFor(file.path());
            if (parserOpt.isEmpty()) continue;

            ParseResult result = parserOpt.get().parse(file.path(), file.content());
            if (result.symbols().isEmpty()) {
                if (file.content() != null && !file.content().isBlank()) {
                    fileContentRepository.save(new FileContent(snapshotId, file.path(), file.content()));
                }
                continue;
            }

            Artifact artifact = artifactRepository.save(new Artifact(null, snapshotId, file.path()));
            long artifactId = artifact.id();

            List<Long> symbolIds = new ArrayList<>();
            for (int i = 0; i < result.symbols().size(); i++) {
                SymbolInfo info = result.symbols().get(i);
                SymbolRow se = symbolRepository.save(new SymbolRow(null, artifactId, info.name(), info.kind(), info.visibility()));
                symbolIds.add(se.id());

                if (i < result.spans().size()) {
                    Span sp = result.spans().get(i);
                    symbolSpanRepository.save(new SymbolSpan(se.id(), sp.getFilePath(), sp.getStartLine(), sp.getStartColumn(), sp.getEndLine(), sp.getEndColumn()));
                }
            }

            for (ReferenceByIndex r : result.references()) {
                if (r.fromIndex() < symbolIds.size() && r.toIndex() < symbolIds.size()) {
                    referenceRepository.save(new Reference(null, symbolIds.get(r.fromIndex()), symbolIds.get(r.toIndex()), r.refType()));
                }
            }
            for (ContainmentByIndex c : result.containments()) {
                if (c.parentIndex() < symbolIds.size() && c.childIndex() < symbolIds.size()) {
                    containmentRepository.save(new Containment(null, symbolIds.get(c.parentIndex()), symbolIds.get(c.childIndex())));
                }
            }

            if (file.content() != null && !file.content().isBlank()) {
                fileContentRepository.save(new FileContent(snapshotId, file.path(), file.content()));
            }

            List<Symbol> symbolsForChunking = new ArrayList<>();
            for (int i = 0; i < result.symbols().size(); i++) {
                SymbolInfo info = result.symbols().get(i);
                Long symId = symbolIds.get(i);
                symbolsForChunking.add(new Symbol(symId, artifactId, info.name(), info.kind(), info.visibility()));
            }
            List<ChunkDto> fileChunks = chunkingStrategy.chunk(snapshotId, file.path(), symbolsForChunking, result.spans());
            allChunks.addAll(fileChunks);
        }

        embeddingPipeline.embedAndStore(snapshotId, allChunks);
        return snapshotId;
    }
}
