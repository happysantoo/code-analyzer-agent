package com.vajrapulse.agents.codeanalyzer.model;

import java.util.Objects;

/**
 * Named entity: type, method, field, variable, etc.
 */
public final class Symbol {

    private final Long id;
    private final long artifactId;
    private final String name;
    private final String kind;
    private final String visibility;

    public Symbol(Long id, long artifactId, String name, String kind, String visibility) {
        this.id = id;
        this.artifactId = artifactId;
        this.name = Objects.requireNonNull(name, "name");
        this.kind = kind != null ? kind : "";
        this.visibility = visibility != null ? visibility : "";
    }

    public Long getId() {
        return id;
    }

    public long getArtifactId() {
        return artifactId;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getVisibility() {
        return visibility;
    }
}
