package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.store.Snapshot;

import java.util.List;
import java.util.Optional;

/**
 * Read-only snapshot-scoped queries: snapshots, symbols, references, containment, file content.
 */
public interface QueryService {

    Optional<Snapshot> getSnapshot(long snapshotId);

    List<Snapshot> listSnapshots(String repoUrlFilter);

    List<SymbolSummary> searchSymbols(long snapshotId, String nameFilter, String kindFilter, String pathFilter, int limit, int offset);

    Optional<SymbolDetail> getSymbol(long snapshotId, long symbolId);

    List<ReferenceSummary> findReferences(long snapshotId, long symbolId, String direction);

    List<ContainmentNode> getContainment(long snapshotId, Long artifactId, Long symbolId);

    Optional<String> getFileContent(long snapshotId, String filePath);
}
