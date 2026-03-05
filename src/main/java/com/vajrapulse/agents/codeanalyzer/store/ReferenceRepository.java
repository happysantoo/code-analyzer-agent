package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;

public interface ReferenceRepository {

    void save(Reference reference);

    List<Reference> findAll();

    List<Reference> findByFromSymbolIdOrToSymbolId(long symbolId);
}
