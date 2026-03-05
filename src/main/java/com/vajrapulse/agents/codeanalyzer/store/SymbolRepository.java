package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;
import java.util.Optional;

public interface SymbolRepository {

    SymbolRow save(SymbolRow symbol);

    Optional<SymbolRow> findById(long id);

    List<SymbolRow> findByArtifactIdOrderById(long artifactId);
}
