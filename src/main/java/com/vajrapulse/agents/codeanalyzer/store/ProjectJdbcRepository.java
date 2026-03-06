package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectJdbcRepository implements ProjectRepository {

    private static final RowMapper<Project> ROW_MAPPER = (rs, rowNum) ->
            new Project(rs.getLong("id"), rs.getString("name"), rs.getString("description"));

    private final JdbcTemplate jdbc;

    public ProjectJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Project save(Project project) {
        if (project.id() != null) return project;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO projects (name, description) VALUES (?, ?)", new String[]{"id"});
            ps.setString(1, project.name());
            ps.setString(2, project.description());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) return project;
        return new Project(key.longValue(), project.name(), project.description());
    }

    @Override
    public Optional<Project> findById(long id) {
        List<Project> list = jdbc.query("SELECT id, name, description FROM projects WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Project> findAll() {
        return jdbc.query("SELECT id, name, description FROM projects ORDER BY id", ROW_MAPPER);
    }
}
