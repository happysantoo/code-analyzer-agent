# Code Analyzer Agent — Java 25 and Spring AI Usage

This document specifies the use of **Java 25** as the runtime, **Spring Boot 4.x** as the application framework, and **Spring AI** for MCP integration, **embeddings**, **vector store**, and **RAG** (question-answering on the codebase). The design starts with a vector database, correct embeddings, and linkages so that users can ask questions on the code from day one. It also provides a dependency sketch for implementation.

---

## 1. Java 25

- **Version:** Java 25 is the target runtime and language level for the Code Analyzer. Java 25 reached General Availability (GA) in September 2025 and is an LTS release.
- **Use in the project:** Set the project’s source and target compatibility to 25. Build and run all services (ingestion, query API, MCP server) on JRE 25.
- **Relevant features (optional):** Virtual threads and structured concurrency can be used for concurrent parsing or I/O if beneficial; pattern matching and other language features may simplify parser or query code. These are not required for the core design.

---

## 2. Spring Boot 4.x

- **Role:** Spring Boot 4.x hosts the entire application: ingestion service, query API, and MCP server. It must be compatible with Java 25 (Spring Boot 4.0+; verify against the chosen minor version).
- **Responsibilities:** Dependency injection, configuration (e.g. DB URL, Git temp dir, MCP endpoint), and serving the MCP transport (HTTP SSE). No separate process is required for the MCP server when using the Spring transport from the MCP Java SDK.

---

## 3. Spring AI Usage

Spring AI is used for MCP integration, **embeddings**, **vector store**, and **RAG** from the start, plus an optional agent layer.

### 3.1 MCP Server Boot Integration

- **Purpose:** Ease wiring of the MCP server into a Spring Boot application (e.g. auto-configuration of the MCP server bean and HTTP SSE endpoint).
- **How:** Use the Spring AI MCP server starter or the equivalent integration that exposes the MCP Java SDK’s Spring transport (e.g. `mcp-spring-webmvc` or `mcp-spring-webflux`) as a Spring-managed endpoint. Consult the Spring AI and MCP Java SDK documentation for the exact artifact and configuration.

### 3.2 Embeddings and Vector Store (Required from Start)

- **Purpose:** Support semantic search and question-answering on the codebase. The design **starts with** a vector database, correct embeddings, and linkages.
- **How:** Use Spring AI’s **embedding model** (e.g. OpenAI, Azure OpenAI, or a local model) to compute embeddings for chunks (e.g. symbol + docstring + signature). The **vector store** is **PostgreSQL with pgvector** (same instance as the relational schema)—see [07-vector-database-comparison.md](07-vector-database-comparison.md). Use Spring AI’s pgvector vector store (`spring-ai-starter-vector-store-pgvector`) to persist embeddings with **metadata** (snapshot_id, artifact_id, symbol_id, path, span, kind) and **linkages** (to relational entities; optionally to related chunk ids). The same embedding model is used at ingest time and at query time for the user’s question. Document format and linkage fields must be defined so that **ask_question** can filter by snapshot and optionally expand context via linkages.

### 3.3 RAG for Ask Question (In Scope)

- **Purpose:** The **ask_question** MCP tool answers natural-language questions using the vector store and, optionally, an LLM to synthesize an answer.
- **How:** (1) Embed the question with the same model. (2) Query the vector store for similar documents filtered by snapshot_id, get top-k chunks with metadata and linkages. (3) Optionally expand context using linkages (e.g. fetch related chunks or relational data). (4) Either return ranked chunks to the client or run **RAG**: build a prompt with retrieved chunks (and optionally file content), call a Spring AI **chat model**, return the generated answer. Configure the vector store and RAG prompt so that linkages are used to include relevant surrounding context (e.g. same file, referenced type).

### 3.4 Optional Agent Layer

- **Purpose:** If the system is to behave as a single "code analyzer agent" that accepts high-level goals and returns synthesized answers, an **agent** component can be added (e.g. Spring AI agent abstractions).
- **How:** The agent’s tools would be the Code Analyzer’s MCP tools (including **ask_question**). See [06-agent-gap-analysis.md](06-agent-gap-analysis.md). This remains optional; the primary Q&A interface is **ask_question** via MCP.

---

## 4. Dependency Sketch

The following is a conceptual list of dependency areas; exact artifact names and versions should be taken from current MCP Java SDK and Spring AI documentation.

| Area | Dependencies (conceptual) |
|------|---------------------------|
| Runtime / build | Java 25; build tool (Maven or Gradle) with Java 25 source/target. |
| Application | Spring Boot 4.x (e.g. 4.0+ or latest compatible with Java 25). |
| MCP server | `io.modelcontextprotocol.sdk:mcp-*` (core); `mcp-spring-webmvc` or `mcp-spring-webflux` for Spring transport. |
| Spring AI | Spring AI BOM: MCP server integration, **embedding model**, **vector store** (pgvector starter), and **chat model** for RAG (ask_question). |
| Java parsing | JavaParser (e.g. `com.github.javaparser:javaparser-symbol-solver-core`) or equivalent. |
| **Database** | **PostgreSQL with pgvector** — single database for relational schema and vector store. PostgreSQL driver; Flyway/Liquibase for migrations; `spring-ai-starter-vector-store-pgvector`. |
| Git | JGit or ProcessBuilder for Git clone/list; abstracted behind `CodeRepository`. |

When implementing, add the correct BOMs and version alignment (e.g. Spring AI and Spring Boot) as per the official documentation.

---

## 5. Summary

- **Java 25:** Single runtime and language level for the project.
- **Spring Boot 4.x:** Hosts ingestion, embedding pipeline, query API, ask_question/RAG, and MCP server in one process.
- **Spring AI:** Used for MCP server boot integration; **embedding model** and **vector store** (required from start); **chat model** for RAG in **ask_question**; optional agent layer.
- **Database:** **PostgreSQL with pgvector** — one database for relational and vector data; embeddings and linkages so that **ask_question** can answer natural-language questions on the codebase.
- **MCP Java SDK:** Provides the MCP server and Spring transport; used in conjunction with Spring AI where the latter provides boot-time wiring and embedding/vector/chat integration.

The design starts with a vector database, embeddings, and linkages so that users and SDLC agents can ask questions on the code immediately after analysis, in addition to structured queries.
