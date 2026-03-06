package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.model.Span;
import com.vajrapulse.agents.codeanalyzer.store.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QueryServiceImpl implements QueryService {

    private final SnapshotRepository snapshotRepository;
    private final ArtifactRepository artifactRepository;
    private final SymbolRepository symbolRepository;
    private final SymbolSpanRepository symbolSpanRepository;
    private final ReferenceRepository referenceRepository;
    private final ContainmentRepository containmentRepository;
    private final FileContentRepository fileContentRepository;

    public QueryServiceImpl(
            SnapshotRepository snapshotRepository,
            ArtifactRepository artifactRepository,
            SymbolRepository symbolRepository,
            SymbolSpanRepository symbolSpanRepository,
            ReferenceRepository referenceRepository,
            ContainmentRepository containmentRepository,
            FileContentRepository fileContentRepository) {
        this.snapshotRepository = snapshotRepository;
        this.artifactRepository = artifactRepository;
        this.symbolRepository = symbolRepository;
        this.symbolSpanRepository = symbolSpanRepository;
        this.referenceRepository = referenceRepository;
        this.containmentRepository = containmentRepository;
        this.fileContentRepository = fileContentRepository;
    }

    @Override
    public Optional<Snapshot> getSnapshot(long snapshotId) {
        return snapshotRepository.findById(snapshotId);
    }

    @Override
    public List<Snapshot> listSnapshots(String repoUrlFilter) {
        if (repoUrlFilter != null && !repoUrlFilter.isBlank()) {
            return snapshotRepository.findAll().stream()
                    .filter(s -> repoUrlFilter.equals(s.repoUrl()))
                    .toList();
        }
        return snapshotRepository.findAll();
    }

    @Override
    public List<SymbolSummary> searchSymbols(long snapshotId, String nameFilter, String kindFilter, String pathFilter, int limit, int offset) {
        List<Artifact> artifacts = artifactRepository.findBySnapshotIdOrderByFilePath(snapshotId);
        if (pathFilter != null && !pathFilter.isBlank()) {
            artifacts = artifacts.stream().filter(a -> a.filePath().contains(pathFilter)).toList();
        }
        List<SymbolSummary> result = new ArrayList<>();
        for (Artifact a : artifacts) {
            List<SymbolRow> symbols = symbolRepository.findByArtifactIdOrderById(a.id());
            for (SymbolRow s : symbols) {
                if (nameFilter != null && !nameFilter.isBlank() && !s.name().contains(nameFilter)) continue;
                if (kindFilter != null && !kindFilter.isBlank() && !kindFilter.equalsIgnoreCase(s.kind())) continue;
                result.add(new SymbolSummary(s.id(), s.artifactId(), s.name(), s.kind(), s.visibility(), a.filePath()));
            }
        }
        int from = Math.min(offset, result.size());
        int to = Math.min(offset + limit, result.size());
        return result.subList(from, to);
    }

    @Override
    public Optional<SymbolDetail> getSymbol(long snapshotId, long symbolId) {
        return symbolRepository.findById(symbolId)
                .filter(s -> artifactRepository.findById(s.artifactId()).map(a -> a.snapshotId() == snapshotId).orElse(false))
                .map(s -> {
                    String path = artifactRepository.findById(s.artifactId()).map(Artifact::filePath).orElse("");
                    Span span = symbolSpanRepository.findById(s.id())
                            .map(sp -> new Span(sp.filePath(), sp.startLine(), sp.startColumn(), sp.endLine(), sp.endColumn()))
                            .orElse(new Span(path, 0, 0, 0, 0));
                    return new SymbolDetail(s.id(), s.artifactId(), s.name(), s.kind(), s.visibility(), path, span);
                });
    }

    @Override
    public List<ReferenceSummary> findReferences(long snapshotId, long symbolId, String direction) {
        List<Reference> refs = referenceRepository.findByFromSymbolIdOrToSymbolId(symbolId);
        refs = refs.stream()
                .filter(r -> {
                    boolean fromMatch = r.fromSymbolId() == symbolId;
                    boolean toMatch = r.toSymbolId() == symbolId;
                    return ("from".equalsIgnoreCase(direction) && fromMatch)
                            || ("to".equalsIgnoreCase(direction) && toMatch)
                            || (!"from".equalsIgnoreCase(direction) && !"to".equalsIgnoreCase(direction) && (fromMatch || toMatch));
                })
                .toList();
        return refs.stream()
                .map(r -> new ReferenceSummary(r.id(), r.fromSymbolId(), r.toSymbolId(), r.refType()))
                .toList();
    }

    @Override
    public List<ContainmentNode> getContainment(long snapshotId, Long artifactId, Long symbolId) {
        if (artifactId != null) {
            List<SymbolRow> symbols = symbolRepository.findByArtifactIdOrderById(artifactId);
            Set<Long> childIds = containmentRepository.findAll().stream().map(Containment::childSymbolId).collect(Collectors.toSet());
            List<SymbolRow> roots = symbols.stream().filter(s -> !childIds.contains(s.id())).toList();
            return roots.stream().map(s -> toNode(s.id())).toList();
        }
        if (symbolId != null) {
            return List.of(toNode(symbolId));
        }
        return List.of();
    }

    private ContainmentNode toNode(long symbolId) {
        SymbolRow s = symbolRepository.findById(symbolId).orElse(null);
        if (s == null) return new ContainmentNode(symbolId, "?", "?", List.of());
        List<Containment> children = containmentRepository.findByParentSymbolId(symbolId);
        List<ContainmentNode> childNodes = children.stream().map(c -> toNode(c.childSymbolId())).toList();
        return new ContainmentNode(s.id(), s.name(), s.kind(), childNodes);
    }

    @Override
    public Optional<String> getFileContent(long snapshotId, String filePath) {
        return fileContentRepository.findBySnapshotIdAndFilePath(snapshotId, filePath).map(FileContent::content);
    }
}
