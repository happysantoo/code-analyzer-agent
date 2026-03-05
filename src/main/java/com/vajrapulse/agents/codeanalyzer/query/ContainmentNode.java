package com.vajrapulse.agents.codeanalyzer.query;

import java.util.List;

public record ContainmentNode(long symbolId, String name, String kind, List<ContainmentNode> children) {

    public ContainmentNode(long symbolId, String name, String kind) {
        this(symbolId, name, kind, List.of());
    }
}
