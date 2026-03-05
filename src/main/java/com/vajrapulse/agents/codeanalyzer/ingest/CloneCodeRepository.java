package com.vajrapulse.agents.codeanalyzer.ingest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Code repository that clones from a URL and lists files at a given ref.
 */
public class CloneCodeRepository implements CodeRepository {

    private static final Set<String> DEFAULT_JAVA_EXT = Set.of(".java");

    private final Path tempBase;
    private final Set<String> fileExtensions;

    public CloneCodeRepository(Path tempBase) {
        this(tempBase, DEFAULT_JAVA_EXT);
    }

    public CloneCodeRepository(Path tempBase, Set<String> fileExtensions) {
        this.tempBase = tempBase != null ? tempBase : Path.of(System.getProperty("java.io.tmpdir"));
        this.fileExtensions = fileExtensions != null ? Set.copyOf(fileExtensions) : DEFAULT_JAVA_EXT;
    }

    @Override
    public RepoSnapshot resolve(String repoUrlOrPath, String ref) {
        if (repoUrlOrPath == null || repoUrlOrPath.isBlank()) {
            throw new IllegalArgumentException("repoUrlOrPath must be non-blank");
        }
        String refToUse = ref != null && !ref.isBlank() ? ref : "HEAD";
        try {
            Path clonePath = Files.createTempDirectory(tempBase, "code-analyzer-clone-");
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrlOrPath)
                    .setDirectory(clonePath.toFile())
                    .setDepth(1)
                    .call()) {
                ObjectId resolved = git.getRepository().resolve(refToUse);
                if (resolved == null) {
                    throw new IllegalArgumentException("Cannot resolve ref: " + refToUse);
                }
                String commitSha = resolved.getName();
                List<FileEntry> files = listFilesAtCommit(git, resolved);
                return RepoSnapshot.of(commitSha, files);
            } finally {
                deleteRecursively(clonePath);
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("Failed to clone and resolve " + repoUrlOrPath + " @" + refToUse, e);
        }
    }

    private List<FileEntry> listFilesAtCommit(Git git, ObjectId commitId) throws Exception {
        List<FileEntry> result = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(git.getRepository());
             ObjectReader reader = git.getRepository().newObjectReader()) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(reader)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    if (!treeWalk.isSubtree()) {
                        String path = treeWalk.getPathString();
                        if (hasWantedExtension(path)) {
                            byte[] bytes = treeWalk.getObjectReader().open(treeWalk.getObjectId(0)).getBytes();
                            String content = new String(bytes);
                            result.add(FileEntry.of(path, content));
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean hasWantedExtension(String path) {
        return fileExtensions.stream().anyMatch(path::endsWith);
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }
}
