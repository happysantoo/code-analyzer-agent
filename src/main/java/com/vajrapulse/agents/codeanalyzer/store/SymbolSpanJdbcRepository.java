package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SymbolSpanJdbcRepository implements SymbolSpanRepository {

    private static final RowMapper<SymbolSpan> ROW_MAPPER = (rs, rowNum) ->
            new SymbolSpan(rs.getLong("symbol_id"), rs.getString("file_path"), rs.getInt("start_line"), rs.getInt("start_column"), rs.getInt("end_line"), rs.getInt("end_column"));

    private final JdbcTemplate jdbc;

    public SymbolSpanJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(SymbolSpan span) {
        jdbc.update("INSERT INTO symbol_spans (symbol_id, file_path, start_line, start_column, end_line, end_column) VALUES (?, ?, ?, ?, ?, ?)",
                span.symbolId(), span.filePath(), span.startLine(), span.startColumn(), span.endLine(), span.endColumn());
    }

    @Override
    public Optional<SymbolSpan> findById(long symbolId) {
        List<SymbolSpan> list = jdbc.query("SELECT symbol_id, file_path, start_line, start_column, end_line, end_column FROM symbol_spans WHERE symbol_id = ?", ROW_MAPPER, symbolId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
