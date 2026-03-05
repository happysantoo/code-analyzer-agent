package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class ArtifactJdbcRepository implements ArtifactRepository {

    private static final RowMapper<Artifact> ROW_MAPPER = (rs, rowNum) ->
            new Artifact(rs.getLong("id"), rs.getLong("snapshot_id"), rs.getString("file_path"));

    private final JdbcTemplate jdbc;

    public ArtifactJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Artifact save(Artifact artifact) {
        if (artifact.id() != null) return artifact;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO artifacts (snapshot_id, file_path) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, artifact.snapshotId());
            ps.setString(2, artifact.filePath());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) return artifact;
        return new Artifact(key.longValue(), artifact.snapshotId(), artifact.filePath());
    }

    @Override
    public Optional<Artifact> findById(long id) {
        List<Artifact> list = jdbc.query("SELECT id, snapshot_id, file_path FROM artifacts WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Artifact> findBySnapshotIdOrderByFilePath(long snapshotId) {
        return jdbc.query("SELECT id, snapshot_id, file_path FROM artifacts WHERE snapshot_id = ? ORDER BY file_path", ROW_MAPPER, snapshotId);
    }
}
