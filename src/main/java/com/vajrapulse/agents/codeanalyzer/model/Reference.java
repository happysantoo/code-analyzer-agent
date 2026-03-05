package com.vajrapulse.agents.codeanalyzer.model;

import java.util.Objects;

/**
 * "X references Y" (e.g. method call, type use, inheritance).
 */
public final class Reference {

    private final Long id;
    private final long fromSymbolId;
    private final long toSymbolId;
    private final String refType;

    public Reference(Long id, long fromSymbolId, long toSymbolId, String refType) {
        this.id = id;
        this.fromSymbolId = fromSymbolId;
        this.toSymbolId = toSymbolId;
        this.refType = refType != null ? refType : "";
    }

    public Long getId() {
        return id;
    }

    public long getFromSymbolId() {
        return fromSymbolId;
    }

    public long getToSymbolId() {
        return toSymbolId;
    }

    public String getRefType() {
        return refType;
    }
}
