# Code Analyzer Agent

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot) [![PostgreSQL + pgvector](https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A **language-agnostic** code analyzer that checks out source from Git, parses it into a semantic model, stores it in **PostgreSQL with pgvector**, and exposes query and Q&A capabilities via REST (and optionally MCP) for SDLC agents and tooling.

**GitHub repo summary (About):**  
*Language-agnostic code analyzer: ingest Git repos into a semantic model with pgvector, expose REST/MCP APIs for symbols, search, and Q&A for SDLC agents.*

---

## Features

- **Ingest:** **Git URL or local path:** pass a GitHub (or any Git) URL or a local repo path to `analyze_repository`; the app clones URLs into a temp dir (configurable) or reads the workspace. Then parse (Java via JavaParser) → canonical semantic model → relational tables + vector embeddings (snapshot replace).
- **Query:** Snapshot-scoped symbols, references, containment, and file content; **ask_question** over one snapshot or a **project** (linked snapshots) via semantic search on pgvector.
- **Projects:** Create projects and link multiple snapshots for cross-repository Q&A.
- **REST API:** `POST/GET /api/tools/*` (e.g. `analyze_repository`, `ask_question`, `list_snapshots`, `create_project`). Ready for MCP tool wrapping.
- **Embedding model (pluggable):** The app uses an `Embedder` abstraction. By default, **StubEmbedder** (zero vectors) is used so the app runs without an API key. For real semantic search, add a Spring AI embedding starter (e.g. OpenAI, Ollama, Bedrock) and set the required properties; the app will use that model for ingest and for **ask_question**. See [Embedding model configuration](documents/11-embedding-model-configuration.md).

---

## Requirements

- **Java 21**
- **Spring Boot 4.x**
- **PostgreSQL** with the **pgvector** extension

---

## Build

```bash
mvn clean verify
```

- Unit tests use **Spock** (Groovy); coverage is enforced via **JaCoCo** (e.g. line coverage gate).
- Reports: `mvn test` runs tests; `target/site/jacoco/index.html` for coverage after `mvn verify`.
- **Integration tests** (Spock specs in package `com.vajrapulse.agents.codeanalyzer.integration`) use an **embedded H2** database (no Docker). The `test` profile applies an H2-compatible relational schema via Flyway; the vector store is stubbed so the full app context loads. See [documents/10-testing-embedded-database.md](documents/10-testing-embedded-database.md) for the choice of H2.

---

## Run

Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` (or use defaults for local PostgreSQL). Then:

```bash
mvn spring-boot:run
```

- **REST tools API:** `POST/GET /api/tools/*` (e.g. `/api/tools/analyze_repository`, `/api/tools/ask_question`).
- **Health:** `GET /actuator/health`
- **Logs:** The app logs to the console (stdout). For `analyze_repository` you’ll see when it starts, the resolved commit and file count, and when it finishes with `snapshot_id` and chunk count. Adjust `logging.level.com.vajrapulse.agents.codeanalyzer` in `application.yml` to `DEBUG` for more detail.

---

## Project layout

| Path | Description |
|------|-------------|
| `src/main/java/com/vajrapulse/agents/codeanalyzer/` | Main application, ingest, query, store, MCP controller |
| `src/test/groovy/com/vajrapulse/agents/codeanalyzer/` | Spock specifications |
| `src/main/resources/db/migration/` | Flyway SQL (relational + pgvector) |
| `documents/` | Requirements, design, architecture, MCP feasibility, vector DB choice, chunking/linkage |

---

## Test environment (runbook)

To test the agent against a real codebase on your laptop:

1. **Start PostgreSQL + pgvector** (from project root):
   ```bash
   docker compose up -d
   ```
   Defaults: database `codeanalyzer`, user `postgres`, password `postgres`, port `5432`. The `docker/init-pgvector.sql` script enables the pgvector extension on first start.

2. **Env vars (optional):** Override with `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` if needed; otherwise the app uses the defaults above.

3. **Run the app:** `mvn spring-boot:run`, then check `GET http://localhost:8080/actuator/health`. On first run, **Flyway** applies migrations and creates the schema (e.g. `snapshots`, `artifacts`, `code_embeddings`, etc.) in the `codeanalyzer` database.

4. **Ingest a real repo:** Use either a **Git URL** or a **local path** to a Git repository. Optional: set `codeanalyzer.clone.temp-dir` (or env `CODEANALYZER_CLONE_TEMP_DIR`) to control where URL clones are checked out; default is the JVM temp directory.
   ```bash
   # Option A: Ingest by GitHub URL (app clones into temp dir, then analyzes)
   curl -s -X POST http://localhost:8080/api/tools/analyze_repository \
     -H "Content-Type: application/json" \
     -d '{"repo_url": "https://github.com/spring-projects/spring-petclinic.git", "ref": "HEAD"}'

   # Option B: Ingest by local path (e.g. after git clone)
   # git clone https://github.com/spring-projects/spring-petclinic.git /tmp/petclinic
   curl -s -X POST http://localhost:8080/api/tools/analyze_repository \
     -H "Content-Type: application/json" \
     -d '{"repo_url": "/tmp/petclinic", "ref": "HEAD"}'
   ```
   Response includes `snapshot_id`; use it in the next calls.

5. **Search symbols:**  
   `curl -s "http://localhost:8080/api/tools/search_symbols?snapshot_id=1&limit=10"`

6. **Ask a question (semantic search):**  
   `curl -s -X POST http://localhost:8080/api/tools/ask_question -H "Content-Type: application/json" -d '{"question": "Where is the main entry point?", "snapshot_id": 1, "top_k": 5}'`  
   You must pass **`snapshot_id`** (from `analyze_repository` or `list_snapshots`) or **`project_id`**. If you omit both, the API returns an error message. With the default **StubEmbedder** (no real embedding model), chunks are stored with zero vectors, so ranking is not meaningful; for real semantic search, configure an embedding model (e.g. OpenAI) in `application.yml`.

To tear down: `docker compose down` (add `-v` to remove the database volume).

---

## License

This project is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for the full text.
