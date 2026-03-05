# Canonical Semantic Model

Language-agnostic model produced by parsers and stored in PostgreSQL. Aligns with [02-high-level-design.md](02-high-level-design.md) §2.2.

## Snapshot identity

- **Snapshot:** One analysis run for a given `(repo_url, commit_sha)`. All stored data is scoped by snapshot; re-run replaces data for that snapshot.

## Core entities

| Concept | Purpose |
|--------|---------|
| **Artifact** | Versioned unit of code (e.g. file). Has `snapshot_id`, `file_path`. Tied to repo + commit via snapshot. |
| **Symbol** | Named entity: type, method, field, variable, etc. Has `artifact_id`, `name`, `kind`, `visibility`. |
| **Span** | Source location for a symbol: `file_path`, `start_line`, `start_column`, `end_line`, `end_column`. |
| **Reference** | "X references Y" (e.g. method call, type use, inheritance). `from_symbol_id`, `to_symbol_id`, optional `ref_type`. |
| **Containment** | "Symbol A is contained in B" (e.g. method in class, class in file). `parent_symbol_id`, `child_symbol_id`. Symbol–artifact relationship is via `symbol.artifact_id`. |
| **FileContent** (optional) | Raw file content for a path in a snapshot. `snapshot_id`, `file_path`, `content`. |

## Project (linked codebases)

- **Project:** Named grouping of snapshot IDs for cross-repo Q&A.
- **ProjectSnapshots:** Junction: `project_id`, `snapshot_id`.

## Vector store (pgvector)

Chunks stored for semantic search: `id`, `snapshot_id`, `content` (text to embed), `embedding` (vector), plus metadata: `artifact_id`, `symbol_id`, `file_path`, `span` (e.g. JSON or text), `kind`. Index on `snapshot_id`; HNSW index on `embedding`.
