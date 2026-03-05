package com.vajrapulse.agents.codeanalyzer.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FileContentJdbcRepository implements FileContentRepository {

    private static final RowMapper<FileContent> ROW_MAPPER = (rs, rowNum) ->
            new FileContent(rs.getLong("snapshot_id"), rs.getString("file_path"), rs.getString("content"));

    private final JdbcTemplate jdbc;

    public FileContentJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FileContent fileContent) {
        jdbc.update("INSERT INTO file_contents (snapshot_id, file_path, content) VALUES (?, ?, ?) ON CONFLICT (snapshot_id, file_path) DO UPDATE SET content = EXCLUDED.content",
                fileContent.snapshotId(), fileContent.filePath(), fileContent.content() != null ? fileContent.content() : "");
    }

    @Override
    public Optional<FileContent> findBySnapshotIdAndFilePath(long snapshotId, String filePath) {
        List<FileContent> list = jdbc.query("SELECT snapshot_id, file_path, content FROM file_contents WHERE snapshot_id = ? AND file_path = ?", ROW_MAPPER, snapshotId, filePath);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void deleteBySnapshotId(long snapshotId) {
        jdbc.update("DELETE FROM file_contents WHERE snapshot_id = ?", snapshotId);
    }
}
