package com.vajrapulse.agents.codeanalyzer.ingest;

/**
 * Delegates to {@link CloneCodeRepository} for URLs (http/https/git@) and to
 * {@link WorkspaceCodeRepository} for local filesystem paths.
 */
public class UrlOrPathCodeRepository implements CodeRepository {

    private final WorkspaceCodeRepository workspaceRepository;
    private final CloneCodeRepository cloneRepository;

    public UrlOrPathCodeRepository(WorkspaceCodeRepository workspaceRepository, CloneCodeRepository cloneRepository) {
        this.workspaceRepository = workspaceRepository;
        this.cloneRepository = cloneRepository;
    }

    @Override
    public RepoSnapshot resolve(String repoUrlOrPath, String ref) {
        if (isUrl(repoUrlOrPath)) {
            return cloneRepository.resolve(repoUrlOrPath, ref);
        }
        return workspaceRepository.resolve(repoUrlOrPath, ref);
    }

    private static boolean isUrl(String repoUrlOrPath) {
        if (repoUrlOrPath == null || repoUrlOrPath.isBlank()) {
            return false;
        }
        String s = repoUrlOrPath.trim();
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("git@");
    }
}
