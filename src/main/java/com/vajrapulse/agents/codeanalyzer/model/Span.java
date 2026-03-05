package com.vajrapulse.agents.codeanalyzer.model;

import java.util.Objects;

/**
 * Source location: file path and line/column range.
 */
public final class Span {

    private final String filePath;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public Span(String filePath, int startLine, int startColumn, int endLine, int endColumn) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }
}
