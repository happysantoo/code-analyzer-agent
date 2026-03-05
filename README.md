# Code Analyzer Agent

A **language-agnostic** code analyzer that checks out source from Git, parses it into a semantic model, stores it in **PostgreSQL with pgvector**, and exposes query and Q&A capabilities via REST (and optionally MCP) for SDLC agents and tooling.

---

## Badges

| Build & Quality | Runtime & Data | Testing |
|-----------------|----------------|---------|
| [![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/) | [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/) | [![Tests](https://img.shields.io/badge/tests-67%20passing-brightgreen)]() |
| [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot) | [![pgvector](https://img.shields.io/badge/pgvector-extension-336791?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector) | [![Coverage](https://img.shields.io/badge/coverage-88%25-yellow)]() |
| [![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/) | [![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.0-6DB33F)](https://spring.io/projects/spring-ai) | [![Spock](https://img.shields.io/badge/Spock-2.4--M4-8B0000)](https://spockframework.org/) |
| [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE) | [![Flyway](https://img.shields.io/badge/Flyway-migrations-red)](https://flywaydb.org/) | [![JaCoCo](https://img.shields.io/badge/JaCoCo-0.8.14-00C4B4)](https://www.jacoco.org/) |

---

## Technology stack (pills)

<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#ED8B00;color:white;font-size:12px">Java 21</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#6DB33F;color:white;font-size:12px">Spring Boot 4</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#C71A36;color:white;font-size:12px">Maven</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#336791;color:white;font-size:12px">PostgreSQL</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#336791;color:white;font-size:12px">pgvector</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#6DB33F;color:white;font-size:12px">Spring AI</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#E34F26;color:white;font-size:12px">JGit</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#B07219;color:white;font-size:12px">JavaParser</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#4298B8;color:white;font-size:12px">Flyway</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#E81123;color:white;font-size:12px">JDBC</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#8B0000;color:white;font-size:12px">Spock</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#00C4B4;color:white;font-size:12px">JaCoCo</span>
<span style="display:inline-block;margin:2px;padding:4px 10px;border-radius:20px;background:#25A162;color:white;font-size:12px">Groovy 4</span>

---

## Features

- **Ingest:** Clone or point at a Git workspace → parse (Java via JavaParser) → canonical semantic model → relational tables + vector embeddings (snapshot replace).
- **Query:** Snapshot-scoped symbols, references, containment, and file content; **ask_question** over one snapshot or a **project** (linked snapshots) via semantic search on pgvector.
- **Projects:** Create projects and link multiple snapshots for cross-repository Q&A.
- **REST API:** `POST/GET /api/tools/*` (e.g. `analyze_repository`, `ask_question`, `list_snapshots`, `create_project`). Ready for MCP tool wrapping.

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

---

## Run

Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` (or use defaults for local PostgreSQL). Then:

```bash
mvn spring-boot:run
```

- **REST tools API:** `POST/GET /api/tools/*` (e.g. `/api/tools/analyze_repository`, `/api/tools/ask_question`).
- **Health:** `GET /actuator/health`

---

## Project layout

| Path | Description |
|------|-------------|
| `src/main/java/com/vajrapulse/agents/codeanalyzer/` | Main application, ingest, query, store, MCP controller |
| `src/test/groovy/com/vajrapulse/agents/codeanalyzer/` | Spock specifications |
| `src/main/resources/db/migration/` | Flyway SQL (relational + pgvector) |
| `documents/` | Requirements, design, architecture, MCP feasibility, vector DB choice, chunking/linkage |

---

## License

This project is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for the full text.
