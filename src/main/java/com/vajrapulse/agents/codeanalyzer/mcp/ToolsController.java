package com.vajrapulse.agents.codeanalyzer.mcp;

import com.vajrapulse.agents.codeanalyzer.ingest.IngestionService;
import com.vajrapulse.agents.codeanalyzer.query.*;
import com.vajrapulse.agents.codeanalyzer.store.Snapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API that mirrors MCP tool operations. MCP tool handlers can delegate to these endpoints or to the services directly.
 */
@RestController
@RequestMapping("/api/tools")
public class ToolsController {

    private final IngestionService ingestionService;
    private final QueryService queryService;
    private final AskQuestionService askQuestionService;
    private final ProjectService projectService;

    public ToolsController(
            IngestionService ingestionService,
            QueryService queryService,
            AskQuestionService askQuestionService,
            ProjectService projectService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.askQuestionService = askQuestionService;
        this.projectService = projectService;
    }

    @PostMapping("/analyze_repository")
    public ResponseEntity<Map<String, Object>> analyzeRepository(@RequestBody Map<String, String> params) {
        String repoUrlOrPath = params.get("repo_url");
        String ref = params.get("ref");
        long snapshotId = ingestionService.analyze(repoUrlOrPath, ref);
        return ResponseEntity.ok(Map.of("snapshot_id", snapshotId, "status", "ok"));
    }

    @GetMapping("/list_snapshots")
    public List<Snapshot> listSnapshots(@RequestParam(required = false) String repo_url) {
        return queryService.listSnapshots(repo_url);
    }

    @GetMapping("/get_snapshot/{id}")
    public ResponseEntity<Snapshot> getSnapshot(@PathVariable long id) {
        return queryService.getSnapshot(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search_symbols")
    public List<SymbolSummary> searchSymbols(
            @RequestParam long snapshot_id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return queryService.searchSymbols(snapshot_id, name, kind, path, limit, offset);
    }

    @PostMapping("/ask_question")
    public AskQuestionResult askQuestion(@RequestBody Map<String, Object> params) {
        String question = (String) params.get("question");
        int topK = params.containsKey("top_k") ? ((Number) params.get("top_k")).intValue() : 10;
        if (params.containsKey("project_id")) {
            long projectId = ((Number) params.get("project_id")).longValue();
            return askQuestionService.askByProject(projectId, question, topK);
        }
        long snapshotId = params.containsKey("snapshot_id") ? ((Number) params.get("snapshot_id")).longValue() : 0L;
        return askQuestionService.ask(snapshotId, question, topK);
    }

    @PostMapping("/create_project")
    public ResponseEntity<Map<String, Object>> createProject(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        String description = params.getOrDefault("description", "");
        long projectId = projectService.createProject(name, description);
        return ResponseEntity.ok(Map.of("project_id", projectId));
    }

    @PostMapping("/link_snapshots_to_project")
    public ResponseEntity<Map<String, String>> linkSnapshots(@RequestBody Map<String, Object> params) {
        long projectId = ((Number) params.get("project_id")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) params.get("snapshot_ids");
        List<Long> snapshotIds = ids != null ? ids.stream().map(Number::longValue).toList() : List.of();
        projectService.linkSnapshotsToProject(projectId, snapshotIds);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/list_projects")
    public List<ProjectSummary> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/get_project/{id}")
    public ResponseEntity<ProjectDetail> getProject(@PathVariable long id) {
        return projectService.getProject(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
