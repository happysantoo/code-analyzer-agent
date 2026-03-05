package com.vajrapulse.agents.codeanalyzer.ingest;

/**
 * Parses one file (path + content) into the canonical model: symbols, spans, references, containment.
 */
public interface SemanticParser {

    /**
     * Parse the file and return symbols, spans, references, and containment.
     *
     * @param filePath relative or absolute file path (for span file_path and reporting)
     * @param content  file content
     * @return parse result with symbol list and index-based references/containment
     */
    ParseResult parse(String filePath, String content);
}
