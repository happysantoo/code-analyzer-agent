# Code Analyzer Agent — Requirements

## Stakeholders

- **SDLC automation systems:** Pipelines and tools that need to reason about code structure, dependencies, and impact.
- **Other agents:** LLM-driven or rule-based agents that answer questions about the codebase or plan changes and need a queryable representation of the code.
- **Developers:** Users who interact with the system indirectly (e.g. via Cursor or other IDEs that use the analyzer as an MCP tool server).

## Functional Requirements

### Ingest and Analysis

- **FR-1** The system SHALL support checking out source code from a Git repository given a URL and a ref (branch, tag, or commit SHA).
- **FR-2** The system SHALL parse source files into a language-agnostic semantic model (symbols, references, containment, spans).
- **FR-3** The system SHALL support Java as the first language; the design SHALL allow adding other languages via pluggable parsers.
- **FR-4** Each analysis run SHALL be tied to a single commit (repo URL + ref); all persisted data SHALL be scoped by snapshot (e.g. commit SHA).
- **FR-5** The system SHALL support both "clone to a temporary directory" and "analyze current workspace" modes via a repository abstraction.

### Storage

- **FR-6** The system SHALL persist the semantic model to a relational database. The **database of choice is PostgreSQL** (symbols, artifacts, references, containment, spans).
- **FR-7** The system SHALL persist code representations in a **vector database** with **embeddings** and **linkages** to support semantic search and question-answering. The vector store SHALL be **pgvector** (PostgreSQL extension) on the same PostgreSQL instance, so that relational and vector data reside in one database. Embeddings SHALL be computed for queryable units (e.g. symbols with docstrings, method signatures, or code chunks) and stored with metadata (snapshot_id, artifact_id, symbol_id, path, span). Linkages SHALL connect vector entries to relational entities and optionally to related chunks (e.g. same file, same type, reference targets) for context expansion when answering questions.
- **FR-8** The system SHALL support replacing all data for a given (repo URL, commit SHA) in a single transactional or coordinated update (full re-run), including both relational and vector stores.
- **FR-9** The system SHALL store raw file contents keyed by (repo, commit, path) for retrieval and for inclusion in RAG context when answering questions.

### Query and Exposure

- **FR-10** The system SHALL expose capabilities via MCP (Model Context Protocol) as tools (and optionally resources).
- **FR-11** The system SHALL support snapshot-scoped queries: list/get snapshots, search symbols (by name, kind, path), get symbol details and span, find references to/from a symbol, get containment tree, get file content.
- **FR-12** The system SHALL support **asking questions** on the codebase: given a natural-language question and a snapshot_id (or a **project** / set of snapshot_ids), the system SHALL use the vector store (semantic search over embeddings) and linkages to retrieve relevant chunks, optionally combine with relational data (e.g. references, containment), and return an answer (e.g. via RAG: retrieve → augment prompt → generate with an LLM, or return ranked chunks for the client to interpret).
- **FR-13** The system SHALL support **linking related codebases** for Q&A during the SDLC agentic development phase. A **project** (or equivalent) SHALL group one or more snapshots (each snapshot = one repo at one commit). When answering a question, the system SHALL allow the scope to be either a single snapshot or a **project** (union of linked snapshots), so that semantic search and RAG consider chunks from all linked codebases. Optionally, **cross-codebase linkages** (e.g. dependency relationships between repos) MAY be used to expand context when retrieving chunks from one repo to include related chunks from another.
- **FR-14** Query operations SHALL be read-only; the analyzer SHALL NOT apply edits to source code.

### Tool Inventory (MCP)

- **FR-15** The system SHALL provide at least the following MCP tools: `analyze_repository`, `list_snapshots` / `get_snapshot`, `search_symbols`, `get_symbol`, `find_references`, `get_containment`, `get_file_content`, and **`ask_question`** (or `query_codebase`) that accepts a natural-language question and either a single `snapshot_id` or a **`project_id`** (or `snapshot_ids`) so that questions can be answered over one or **multiple linked codebases**. The system SHALL also provide a way to create and manage **projects** (e.g. `create_project`, `link_snapshots_to_project`, `list_projects`, `get_project`) so that SDLC agents can define and use linked codebases when asking questions.

## Non-Functional Requirements

- **NFR-1 Performance:** Ingest time and query latency SHALL be documented and acceptable for target repo sizes. The design SHALL support **very large codebases** (e.g. on the order of **a million lines of code**) via parallel or batched processing, streaming where appropriate, and bounded memory use.
- **NFR-2 Scalability:** The design SHALL scale to repositories with millions of lines of code (tens of thousands of files, hundreds of thousands of symbols/chunks). This implies: (1) **ingest** — parallel or batched parsing and batched embedding with backpressure/rate limiting; (2) **relational store** — indexing and optional partitioning by snapshot; (3) **vector store** — use of approximate nearest-neighbor (ANN) indexes and a store capable of millions of vectors per snapshot; (4) **queries** — pagination and bounded result sets (e.g. top-k) so that response size and latency remain acceptable. See [03-architecture.md](03-architecture.md) §6.
- **NFR-3 Extensibility:** Adding a new language SHALL require only implementing a parser adapter that produces the canonical semantic model and registering it; no change to storage schema or MCP tool contracts.
- **NFR-4 Technology:** The implementation SHALL use Java 25 as the runtime and **Spring Boot 4.x** as the application framework; MCP exposure SHALL use the official MCP Java SDK with Spring transport. The **database SHALL be PostgreSQL with the pgvector extension** (single instance for relational and vector data).

## User Stories

- **As an SDLC agent**, I want to **analyze a repository** at a given URL and ref so that I have a snapshot id to use in subsequent queries.
- **As an SDLC agent**, I want to **search for symbols** by name, kind, or path within a snapshot so that I can find classes, methods, or other entities.
- **As an SDLC agent**, I want to **find references** to or from a symbol so that I can reason about call graphs and dependencies.
- **As an SDLC agent**, I want to **get the containment tree** (e.g. file → classes → methods) so that I can understand structure and scope.
- **As an SDLC agent**, I want to **get file content** for a path in a snapshot so that I can show or reason about source code.
- **As an SDLC agent**, I want to **ask a natural-language question** about the codebase (e.g. "Where is authentication handled?" or "What calls this method?") and get an answer or relevant code chunks using semantic search and linkages.
- **As an SDLC agent**, I want to **link related codebases** (e.g. frontend repo + backend repo + shared library) into a **project** so that when I ask a question during agentic development, the answer is based on **all linked repos** and I can see how code fits together across repositories.
- **As a developer**, I want the analyzer to be **available as an MCP server** so that I can use it from Cursor or other MCP clients without custom integration.

## Out of Scope (v1)

- **Applying edits:** The analyzer does not modify source code; other agents or tools use its output to decide what to change and apply edits via their own mechanisms.
- **Incremental parsing:** v1 uses full re-run and replace per (repo, commit); true incremental (only changed files) may be added later.
- **Cross-repo dependency analysis at ingest:** Automatic discovery of repo-to-repo dependencies (e.g. from build files) for cross-codebase linkages is optional; linking is explicit via projects and snapshot lists. Cross-codebase linkage metadata (e.g. "repo A depends on repo B") can be added later to improve context expansion.
- **Authentication and authorization:** v1 does not define auth for Git clone or for MCP; assume environment or network-level handling.
