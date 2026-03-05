package com.vajrapulse.agents.codeanalyzer.store;

import java.util.List;

public interface ContainmentRepository {

    void save(Containment containment);

    List<Containment> findAll();

    List<Containment> findByParentSymbolId(long parentSymbolId);
}
