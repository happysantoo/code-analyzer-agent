package com.vajrapulse.agents.codeanalyzer.store;

import java.util.Optional;

public interface SymbolSpanRepository {

    void save(SymbolSpan span);

    Optional<SymbolSpan> findById(long symbolId);
}
