package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReferenceJdbcRepository implements ReferenceRepository {

    private static final RowMapper<Reference> ROW_MAPPER = (rs, rowNum) ->
            new Reference(rs.getLong("id"), rs.getLong("from_symbol_id"), rs.getLong("to_symbol_id"), rs.getString("ref_type"));

    private final JdbcTemplate jdbc;

    public ReferenceJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Reference reference) {
        jdbc.update("INSERT INTO \"references\" (from_symbol_id, to_symbol_id, ref_type) VALUES (?, ?, ?)",
                reference.fromSymbolId(), reference.toSymbolId(), reference.refType() != null ? reference.refType() : "");
    }

    @Override
    public List<Reference> findAll() {
        return jdbc.query("SELECT id, from_symbol_id, to_symbol_id, ref_type FROM \"references\"", ROW_MAPPER);
    }

    @Override
    public List<Reference> findByFromSymbolIdOrToSymbolId(long symbolId) {
        return jdbc.query("SELECT id, from_symbol_id, to_symbol_id, ref_type FROM \"references\" WHERE from_symbol_id = ? OR to_symbol_id = ?", ROW_MAPPER, symbolId, symbolId);
    }
}
