package com.vajrapulse.agents.codeanalyzer.model;

/**
 * "Symbol A is contained in B" (e.g. method in class, class in file).
 */
public final class Containment {

    private final long parentSymbolId;
    private final long childSymbolId;

    public Containment(long parentSymbolId, long childSymbolId) {
        this.parentSymbolId = parentSymbolId;
        this.childSymbolId = childSymbolId;
    }

    public long getParentSymbolId() {
        return parentSymbolId;
    }

    public long getChildSymbolId() {
        return childSymbolId;
    }
}
