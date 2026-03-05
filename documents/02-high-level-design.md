# Code Analyzer Agent — High-Level Design

## Summary

The Code Analyzer ingests source code from Git, parses it into a **language-agnostic semantic model**, persists that model in a **relational database** and a **vector database** (with **embeddings** and **linkages**) so that users and SDLC agents can both run structured queries and **ask natural-language questions** on the codebase. It exposes **read-only query capabilities** and **question-answering** via **MCP (Model Context Protocol)**. The design is **Java-first** but **language-agnostic** at the model and API level. See [01-requirements.md](01-requirements.md) for requirements and [03-architecture.md](03-architecture.md) for component and data-flow detail.

---

## 1. Problem Statement

**Goal:** Build a system that ingests source code from Git, understands its structure and semantics, and persists a representation that other SDLC agents can use to:

- **Understand** the codebase (navigate, reason about dependencies, find patterns).
- **Answer questions** (e.g. "Where is X used?", "What calls this method?", "What is the entry point?").
- **Support making changes** given a requirement (by exposing enough context so that other agents can decide where and what to change; the analyzer itself does not apply edits).

**Constraints:**

- Start with **Java** as the primary language.
- Design for **language-agnostic** analysis so additional languages can be added without reworking core storage or MCP tool contracts.
- Storage must support **efficient queries** (by symbol, relationship, and optionally text/semantic search) and **replace-by-snapshot** updates when the repo changes.

---

## 2. Key Design Decisions

### 2.1 Three Pillars: Ingest, Storage, Consumption

The system is split into:

1. **Ingest:** Git clone/update → language-specific parser → **canonical semantic model** → **embedding pipeline** (chunk, embed, store with linkages).
2. **Storage:** **Relational DB** (symbols, artifacts, references, containment, spans, file contents) and **vector database** (embeddings + metadata + linkages) from the start. Both are populated during ingestion and support snapshot-scoped replacement.
3. **Consumption:** MCP server exposing tools (and optionally resources) for structured queries and for **asking questions** (semantic search + RAG over the vector store and linkages).

This keeps parsing and storage independent of how clients call the system; the vector store is a first-class store for Q&A.

### 2.2 Language-Agnostic Canonical Model

All language-specific parsers produce the same **canonical model**:

| Concept       | Purpose                                                                 |
|---------------|-------------------------------------------------------------------------|
| **Artifact**  | Versioned unit of code (e.g. file). Tied to repo + commit.              |
| **Symbol**    | Named entity: type, method, field, variable, etc.; kind and visibility.  |
| **Span**      | Source location (file path, start/end line/column).                     |
| **Reference** | "X references Y" (e.g. method call, type use, inheritance).             |
| **Containment** | "Symbol A is contained in B" (e.g. method in class, class in file).    |

Optional extensions: documentation per symbol, annotations, signatures. SDLC agents and MCP tools work only against this model; adding a language means implementing a parser that fills the same structures.

### 2.3 Git and Versioning

- **One commit per analysis:** Each run is tied to one Git ref (branch + commit SHA). All stored data is keyed by `(repo_url, commit_sha)` (snapshot).
- **Repository abstraction:** Support both "clone to temp dir" and "analyze current workspace" via a single interface.
- **Updates:** Full re-run replaces all data for a given (repo, commit) in one transaction; incremental parsing is out of scope for v1.

### 2.4 Storage: PostgreSQL with pgvector (Single Database)

The **database of choice is PostgreSQL with the pgvector extension**. One PostgreSQL instance hosts both relational tables and vector data (embeddings), giving a single operational footprint for backup, deployment, and querying.

- **Relational store (PostgreSQL):** Repo/Commit, Artifacts, Symbols, SymbolSpans, References, Containment, and **file contents** keyed by (repo, commit, path). Normalized schema and indexes for symbol name, path, (repo, commit), and reference endpoints.
- **Vector store (pgvector, same PostgreSQL):** Store **embeddings** for queryable units of code. Each unit (chunk) is a meaningful piece for Q&A—e.g. a symbol with its docstring and signature, a method body, or a file-level summary. Each vector document includes:
  - **Embedding:** Vector from an embedding model (e.g. via Spring AI).
  - **Metadata:** `snapshot_id`, `artifact_id`, `symbol_id`, `file_path`, `span` (line/column), `kind` (e.g. class, method).
  - **Linkages:** (1) **Entity linkage:** Foreign keys to relational entities (symbol_id, artifact_id) so results can be joined to references, containment, and file content. (2) **Chunk linkages (optional):** References to related chunk ids (e.g. same file, same type, or "calls" / "references") so that when answering a question the system can expand context by following links to related chunks.
- **Chunking strategy:** Define what to embed (e.g. per-symbol: name + signature + docstring + optional snippet; or per-method body). Use a consistent strategy so that "ask question" retrieves the right granularity. Linkages are written during ingestion when the canonical model (references, containment) is available.
- **Snapshot replacement:** On full re-run, replace both relational and vector data for the given (repo, commit) in PostgreSQL so that Q&A and structured queries stay in sync. See [07-vector-database-comparison.md](07-vector-database-comparison.md) for the chosen database rationale.

### 2.5 Parser Strategy

- **Interface:** One parser contract: given file path and content (or AST), return symbols, spans, references, and containment for the canonical model.
- **Java first:** Use a single Java parser (e.g. JavaParser); walk the AST and map to the canonical model. A **parser registry** selects by file extension or language id so new languages can be added without changing core flow.

### 2.6 Exposure: MCP First

- **Primary surface:** MCP tools (and optionally resources). Cursor and other MCP clients use the analyzer as a **tool server**; no built-in reasoning or planning in the analyzer.
- **Optional:** REST for non-MCP consumers. The analyzer is **read-only**; "making changes" is done by other agents that use this API for context and apply edits elsewhere.

### 2.7 MCP Server vs Separate Agent

- **Recommendation:** Implement the code analyzer as an **MCP server**, not a standalone reasoning agent. The analyzer exposes capabilities (clone, parse, store, query); an LLM or other agent elsewhere does planning and multi-step reasoning and **calls this server as a tool**.
- **Optional agent layer:** If desired, a separate component (e.g. Spring AI agent) can consume this MCP server, accept high-level goals (e.g. "Where is X used?"), and return synthesized answers. See [06-agent-gap-analysis.md](06-agent-gap-analysis.md).

### 2.8 Component Boundaries

| Component               | Responsibility                                                                 |
|-------------------------|---------------------------------------------------------------------------------|
| Repo provider           | Clone or open repo; resolve ref to commit; list files.                          |
| Parser (per language)   | Parse files → canonical model (symbols, refs, containment, spans).              |
| Ingestion service       | Orchestrate: clone → parse → write to **PostgreSQL** (relational tables) → **chunk, embed, write to pgvector** (same PostgreSQL) with linkages. |
| **PostgreSQL (relational + pgvector)** | Single database: relational tables (semantic model, file contents, projects) and pgvector tables (embeddings + metadata + linkages); replace per snapshot. |
| Embedding pipeline      | Chunk canonical model (e.g. per symbol or method), compute embeddings (Spring AI), store in **pgvector** (PostgreSQL) with linkages to symbols/artifacts and optional chunk-to-chunk links. |
| Query API               | Snapshot-scoped read-only queries (relational) and **ask_question** (vector search + optional RAG with linkages). |
| MCP server              | Expose Query API and ask_question as MCP tools (and optionally resources).     |

---

## 3. MCP Tools (Initial Set)

- `analyze_repository` — repo URL + ref → clone/parse/persist to **PostgreSQL** (relational tables + pgvector with embeddings and linkages); returns snapshot_id.
- `get_snapshot` / `list_snapshots` — metadata for analyzed snapshots.
- `search_symbols` — snapshot_id + filters (name, kind, path) → list of symbols.
- `get_symbol` — symbol id → details + span.
- `find_references` — symbol id or name → references to/from that symbol.
- `get_containment` — file or symbol id → containment tree.
- `get_file_content` — snapshot_id + path → raw file content.
- **`ask_question`** (or `query_codebase`) — **snapshot_id or project_id** (or snapshot_ids) + natural-language question → answer or ranked relevant chunks. When project_id is used, search and RAG run over **all snapshots linked in the project** so that related codebases are considered together. Uses vector search, metadata filters, and linkages (including optional cross-codebase linkages) and, if configured, RAG.
- **Project management (optional but recommended):** `create_project`, `link_snapshots_to_project`, `list_projects`, `get_project` so that SDLC agents can define and use linked codebases when asking questions.

Optional MCP resources: e.g. `codebase://{snapshot_id}/files`, `codebase://{snapshot_id}/symbols/{id}`.

---

## 3.1 Linking Related Codebases (Project-Scoped Q&A)

To support **SDLC agentic development** where an agent works across multiple repos (e.g. frontend, backend, shared library), the system supports **linking related codebases** so that questions can be answered over a **project** (a set of snapshots).

- **Project:** A named grouping of one or more **snapshot_ids**. Each snapshot remains a single (repo_url, commit). A project is the union of those snapshots for the purpose of Q&A.
- **Ask question over a project:** When the client calls **`ask_question`** with a **project_id** (or a list of snapshot_ids) instead of a single snapshot_id, the system:
  - Queries the **vector store** for chunks that belong to **any** of the snapshots in the project (metadata filter: snapshot_id IN project’s snapshot list).
  - Returns ranked chunks or runs RAG over that union, so the answer can draw from all linked codebases.
  - Optionally uses **cross-codebase linkages** (if available): e.g. when a chunk from repo A references an API defined in repo B, context expansion can include the related chunk from repo B in the RAG prompt.
- **Project management:** Clients can **create_project** (name, optional description), **link_snapshots_to_project** (project_id, snapshot_ids), **list_projects**, **get_project** (snapshot list). Projects are stored in the relational store (e.g. project table, project_snapshots junction).
- **Use during agentic development:** An SDLC agent can, at the start of a task, create or select a project that links the relevant repos (e.g. main app + libs), then call **ask_question(project_id, question)** so that answers consider the full context (e.g. "How does the API client call the auth service?" across client repo and service repo).

---

## 4. Implementation Order (Summary)

1. Define canonical semantic model and document it.
2. Design and create **PostgreSQL** schema: relational tables (indexes) and **pgvector** tables (embedding dimensions, metadata fields, linkage fields).
3. Implement repo abstraction (clone + list files).
4. Implement Java parser adapter (e.g. JavaParser → canonical model).
5. **Define chunking strategy** (what to embed: e.g. symbol + docstring + signature) and **linkage model** (entity ids, optional related-chunk ids).
6. Ingestion pipeline: run parser per file → bulk-insert into **PostgreSQL** (relational) → **chunk → compute embeddings → store in pgvector** (same PostgreSQL) with linkages for (repo, commit).
7. Query API (get symbol, find references, containment, file content) and **ask_question** (vector search + optional RAG using linkages for context).
8. MCP server: register all tools including **ask_question**; run with Spring Boot + SSE transport.
9. Iterate: more reference types, resolution, chunk shapes, and RAG prompt tuning.

Technology choices (Java 25, Spring Boot 4.x, MCP Java SDK, Spring AI, **PostgreSQL with pgvector**, embedding model) are detailed in [05-java25-spring-ai.md](05-java25-spring-ai.md) and [07-vector-database-comparison.md](07-vector-database-comparison.md). MCP feasibility and tool/resource design are in [04-mcp-feasibility.md](04-mcp-feasibility.md). **Scalability** for very large codebases (e.g. a million lines of code) is addressed in [03-architecture.md](03-architecture.md) §6 (parallel/batched ingest, ANN vector search, indexing, pagination).
