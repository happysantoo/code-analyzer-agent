package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class SymbolJdbcRepository implements SymbolRepository {

    private static final RowMapper<SymbolRow> ROW_MAPPER = (rs, rowNum) ->
            new SymbolRow(rs.getLong("id"), rs.getLong("artifact_id"), rs.getString("name"), rs.getString("kind"), rs.getString("visibility"));

    private final JdbcTemplate jdbc;

    public SymbolJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SymbolRow save(SymbolRow symbol) {
        if (symbol.id() != null) return symbol;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO symbols (artifact_id, name, kind, visibility) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, symbol.artifactId());
            ps.setString(2, symbol.name());
            ps.setString(3, symbol.kind());
            ps.setString(4, symbol.visibility() != null ? symbol.visibility() : "");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) return symbol;
        return new SymbolRow(key.longValue(), symbol.artifactId(), symbol.name(), symbol.kind(), symbol.visibility());
    }

    @Override
    public Optional<SymbolRow> findById(long id) {
        List<SymbolRow> list = jdbc.query("SELECT id, artifact_id, name, kind, visibility FROM symbols WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<SymbolRow> findByArtifactIdOrderById(long artifactId) {
        return jdbc.query("SELECT id, artifact_id, name, kind, visibility FROM symbols WHERE artifact_id = ? ORDER BY id", ROW_MAPPER, artifactId);
    }
}
