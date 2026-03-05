package com.vajrapulse.agents.codeanalyzer.ingest;

/**
 * Reference between two symbols by index in the parse result symbol list.
 */
public record ReferenceByIndex(int fromIndex, int toIndex, String refType) {

    public ReferenceByIndex {
        refType = refType != null ? refType : "";
    }
}
