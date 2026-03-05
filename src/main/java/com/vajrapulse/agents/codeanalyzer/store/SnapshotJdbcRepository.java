package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SnapshotJdbcRepository implements SnapshotRepository {

    private static final RowMapper<Snapshot> ROW_MAPPER = (rs, rowNum) -> new Snapshot(
            rs.getLong("id"),
            rs.getString("repo_url"),
            rs.getString("commit_sha"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
    );

    private final JdbcTemplate jdbc;

    public SnapshotJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Snapshot> findByRepoUrlAndCommitSha(String repoUrl, String commitSha) {
        String sql = "SELECT id, repo_url, commit_sha, created_at FROM snapshots WHERE repo_url = ? AND commit_sha = ?";
        List<Snapshot> list = jdbc.query(sql, ROW_MAPPER, repoUrl, commitSha);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Snapshot save(Snapshot snapshot) {
        if (snapshot.id() != null) {
            return snapshot;
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO snapshots (repo_url, commit_sha, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, snapshot.repoUrl());
            ps.setString(2, snapshot.commitSha());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) return snapshot;
        return new Snapshot(key.longValue(), snapshot.repoUrl(), snapshot.commitSha(), Instant.now());
    }

    @Override
    public Optional<Snapshot> findById(long id) {
        List<Snapshot> list = jdbc.query("SELECT id, repo_url, commit_sha, created_at FROM snapshots WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Snapshot> findAll() {
        return jdbc.query("SELECT id, repo_url, commit_sha, created_at FROM snapshots ORDER BY id", ROW_MAPPER);
    }

    @Override
    public void deleteArtifactsBySnapshotId(long snapshotId) {
        jdbc.update("DELETE FROM artifacts WHERE snapshot_id = ?", snapshotId);
    }
}
