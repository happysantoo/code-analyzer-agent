package com.vajrapulse.agents.codeanalyzer.query;

import com.vajrapulse.agents.codeanalyzer.store.CodeChunkHit;

import java.util.List;

public record AskQuestionResult(List<CodeChunkHit> chunks, String errorMessage) {

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
