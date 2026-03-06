# Testing with an Embedded Database

This document explains why the Code Analyzer Agent uses **H2** as the embedded database for integration tests, and why we do not use Testcontainers (Docker) for the test suite.

## Choice: H2

Tests run with **H2** in in-memory mode, using the `test` profile. No Docker or external process is required.

### Why H2?

| Criterion | H2 | Alternatives |
|-----------|----|--------------|
| **Zero external deps** | Runs in-process, no Docker or DB server. | Testcontainers needs Docker; embedded Postgres (e.g. zonky) starts a real Postgres process. |
| **Speed** | In-memory, very fast startup and teardown. | Testcontainers: image pull + container start; embedded Postgres: process spawn. |
| **CI / dev parity** | Same experience everywhere: `mvn test` just works. | Testcontainers can fail in CI if Docker is unavailable or restricted. |
| **PostgreSQL compatibility** | `MODE=PostgreSQL` and `DATABASE_TO_LOWER=TRUE` get close for standard SQL. | Real Postgres (Testcontainers) is 100% compatible. |
| **Schema portability** | We maintain a small H2-specific migration set (`db/migration-h2`) that mirrors the relational part of the main schema. | One schema (Postgres) with Testcontainers. |

### Limitations of H2 for this project

- **pgvector:** H2 has no native vector type or similarity operators (`<=>`). The app’s production store uses PostgreSQL’s `vector(1536)` and `code_embeddings`. So we **do not** run the real vector store in tests.
- **How we handle it:** In the `test` profile we stub `CodeEmbeddingRepository` (see `TestDbConfig` in package `com.vajrapulse.agents.codeanalyzer.integration`). The application context loads against H2 with the **relational** schema only; embedding save/search are no-ops in tests. Relational repositories (snapshots, artifacts, symbols, etc.) are tested against H2 in Spock integration specs under the same package.

### Why not another embedded DB?

- **HSQLDB:** Similar to H2 (in-memory, no Docker). H2 is more widely used with Spring and has better PostgreSQL compatibility mode.
- **SQLite:** Different SQL dialect and type system; would need more migration tweaks and is less common in the Java/Spring ecosystem for “Postgres-like” tests.
- **Embedded PostgreSQL (e.g. zonky, otj-pg-embedded):** Real Postgres and full pgvector support, but requires a native binary or Docker for the Postgres process, which complicates CI and local setup.
- **Testcontainers:** Matches production (real Postgres + pgvector) but requires Docker and is slower. We prefer fast, Docker-free tests by default; you can still run manual or CI tests against a real Postgres (e.g. `docker compose`) when needed.

## Summary

We use **H2** so that:

1. **`mvn test`** runs everywhere without Docker or a running database.
2. **Relational persistence** (snapshots, artifacts, symbols, references, containment, file contents, projects) is tested with real SQL and Flyway migrations.
3. **Vector/embedding behavior** is stubbed in tests; production continues to use PostgreSQL + pgvector.

For full end-to-end tests against real PostgreSQL and pgvector (e.g. ingestion + semantic search), run the application locally with `docker compose` and the runbook in the main [README](../README.md#test-environment-runbook).
