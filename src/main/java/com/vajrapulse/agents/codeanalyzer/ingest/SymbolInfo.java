package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.Objects;

/**
 * Symbol without DB id: name, kind, visibility for parser output.
 */
public record SymbolInfo(String name, String kind, String visibility) {

    public SymbolInfo {
        name = name != null ? name : "";
        kind = kind != null ? kind : "";
        visibility = visibility != null ? visibility : "";
    }
}
