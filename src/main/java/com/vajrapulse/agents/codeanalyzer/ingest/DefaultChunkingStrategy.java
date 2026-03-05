package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.model.Span;
import com.vajrapulse.agents.codeanalyzer.model.Symbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Default chunking: one chunk per symbol; text = "kind visibility name" (e.g. "CLASS public Foo").
 * Span formatted as "startLine:startCol-endLine:endCol" for metadata.
 */
public class DefaultChunkingStrategy implements ChunkingStrategy {

    @Override
    public List<ChunkDto> chunk(long snapshotId, String filePath, List<Symbol> symbols, List<Span> spans) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<ChunkDto> result = new ArrayList<>(symbols.size());
        for (int i = 0; i < symbols.size(); i++) {
            Symbol s = symbols.get(i);
            Span sp = i < (spans != null ? spans.size() : 0) ? spans.get(i) : null;
            String spanStr = sp != null
                    ? sp.getStartLine() + ":" + sp.getStartColumn() + "-" + sp.getEndLine() + ":" + sp.getEndColumn()
                    : "";
            String text = buildChunkText(s);
            Long artifactId = s.getId() != null ? Long.valueOf(s.getArtifactId()) : null;
            result.add(new ChunkDto(
                    text,
                    snapshotId,
                    artifactId,
                    s.getId(),
                    filePath,
                    spanStr,
                    s.getKind()
            ));
        }
        return result;
    }

    private static String buildChunkText(Symbol s) {
        String vis = s.getVisibility();
        if (vis == null || vis.isBlank()) {
            vis = "package";
        }
        return s.getKind() + " " + vis + " " + s.getName();
    }
}
