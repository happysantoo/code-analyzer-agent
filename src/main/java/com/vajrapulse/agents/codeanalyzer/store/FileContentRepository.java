package com.vajrapulse.agents.codeanalyzer.store;

import java.util.Optional;

public interface FileContentRepository {

    void save(FileContent fileContent);

    Optional<FileContent> findBySnapshotIdAndFilePath(long snapshotId, String filePath);

    void deleteBySnapshotId(long snapshotId);
}
