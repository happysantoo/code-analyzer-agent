package com.vajrapulse.agents.codeanalyzer.ingest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Code repository backed by the current workspace (local path).
 * Resolves HEAD to commit SHA and lists files from the working tree.
 */
public class WorkspaceCodeRepository implements CodeRepository {

    private static final Set<String> DEFAULT_JAVA_EXT = Set.of(".java");

    private final Set<String> fileExtensions;

    public WorkspaceCodeRepository() {
        this(DEFAULT_JAVA_EXT);
    }

    public WorkspaceCodeRepository(Set<String> fileExtensions) {
        this.fileExtensions = fileExtensions != null ? Set.copyOf(fileExtensions) : DEFAULT_JAVA_EXT;
    }

    @Override
    public RepoSnapshot resolve(String repoUrlOrPath, String ref) {
        Path base = Path.of(repoUrlOrPath);
        if (!Files.isDirectory(base)) {
            throw new IllegalArgumentException("Not a directory: " + repoUrlOrPath);
        }
        Path gitDir = base.resolve(".git");
        if (!Files.isDirectory(gitDir)) {
            throw new IllegalArgumentException("Not a git repository: " + repoUrlOrPath);
        }
        try (Git git = Git.open(base.toFile())) {
            Repository repo = git.getRepository();
            String commitSha = repo.resolve(ref != null && !ref.isBlank() ? ref : "HEAD").getName();
            List<FileEntry> files = listFilesWithContent(base, base);
            return RepoSnapshot.of(commitSha, files);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve workspace at " + repoUrlOrPath, e);
        }
    }

    private List<FileEntry> listFilesWithContent(Path base, Path dir) {
        List<FileEntry> result = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.collect(Collectors.toList())) {
                if (Files.isDirectory(p)) {
                    if (!p.getFileName().toString().equals(".git")) {
                        result.addAll(listFilesWithContent(base, p));
                    }
                } else if (hasWantedExtension(p)) {
                    String relative = base.relativize(p).toString().replace('\\', '/');
                    String content = Files.readString(p);
                    result.add(FileEntry.of(relative, content));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list files under " + dir, e);
        }
        return result;
    }

    private boolean hasWantedExtension(Path p) {
        String name = p.getFileName().toString();
        return fileExtensions.stream().anyMatch(name::endsWith);
    }
}
