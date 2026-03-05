package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProjectSnapshotJdbcRepository implements ProjectSnapshotRepository {

    private static final RowMapper<ProjectSnapshot> ROW_MAPPER = (rs, rowNum) ->
            new ProjectSnapshot(rs.getLong("project_id"), rs.getLong("snapshot_id"));

    private final JdbcTemplate jdbc;

    public ProjectSnapshotJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ProjectSnapshot projectSnapshot) {
        jdbc.update("INSERT INTO project_snapshots (project_id, snapshot_id) VALUES (?, ?) ON CONFLICT (project_id, snapshot_id) DO NOTHING",
                projectSnapshot.projectId(), projectSnapshot.snapshotId());
    }

    @Override
    public void deleteByProjectId(long projectId) {
        jdbc.update("DELETE FROM project_snapshots WHERE project_id = ?", projectId);
    }

    @Override
    public List<ProjectSnapshot> findByProjectIdOrderBySnapshotId(long projectId) {
        return jdbc.query("SELECT project_id, snapshot_id FROM project_snapshots WHERE project_id = ? ORDER BY snapshot_id", ROW_MAPPER, projectId);
    }
}
