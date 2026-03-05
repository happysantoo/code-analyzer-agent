package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ContainmentJdbcRepository implements ContainmentRepository {

    private static final RowMapper<Containment> ROW_MAPPER = (rs, rowNum) ->
            new Containment(rs.getLong("id"), rs.getLong("parent_symbol_id"), rs.getLong("child_symbol_id"));

    private final JdbcTemplate jdbc;

    public ContainmentJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Containment containment) {
        jdbc.update("INSERT INTO containment (parent_symbol_id, child_symbol_id) VALUES (?, ?)",
                containment.parentSymbolId(), containment.childSymbolId());
    }

    @Override
    public List<Containment> findAll() {
        return jdbc.query("SELECT id, parent_symbol_id, child_symbol_id FROM containment", ROW_MAPPER);
    }

    @Override
    public List<Containment> findByParentSymbolId(long parentSymbolId) {
        return jdbc.query("SELECT id, parent_symbol_id, child_symbol_id FROM containment WHERE parent_symbol_id = ?", ROW_MAPPER, parentSymbolId);
    }
}
