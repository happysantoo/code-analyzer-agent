package com.vajrapulse.agents.codeanalyzer.ingest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of semantic parsers by file extension. Ingestion selects parser by extension.
 */
public class ParserRegistry {

    private final Map<String, SemanticParser> byExtension = new HashMap<>();

    public ParserRegistry() {
        register(".java", new JavaSemanticParser());
    }

    public void register(String extension, SemanticParser parser) {
        if (extension != null && parser != null) {
            byExtension.put(extension.startsWith(".") ? extension : "." + extension, parser);
        }
    }

    public Optional<SemanticParser> getParserFor(String filePath) {
        if (filePath == null) {
            return Optional.empty();
        }
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = filePath.substring(dot);
        return Optional.ofNullable(byExtension.get(ext));
    }
}
