# Code Analyzer Agent — Documentation Index

This directory contains the design and feasibility documentation for the Code Analyzer Agent: a system that checks out source code from Git, understands it via a language-agnostic semantic model, stores it in **PostgreSQL with pgvector** (one database for relational schema and vector embeddings/linkages), and exposes capabilities via MCP so other SDLC agents can understand the code, **ask natural-language questions** on it, and (with separate tooling) change the code. The design supports **linking related codebases** (e.g. frontend + backend + shared lib) into **projects** so that questions can be answered across multiple repos during the SDLC agentic development phase.

## Document Map

| Document | Purpose |
|----------|---------|
| [01-requirements.md](01-requirements.md) | Functional and non-functional requirements, stakeholders, user stories, and out-of-scope items. |
| [02-high-level-design.md](02-high-level-design.md) | Problem statement, goals, key design decisions, canonical semantic model, **storage (PostgreSQL with pgvector)**, and MCP vs agent positioning. |
| [03-architecture.md](03-architecture.md) | Component diagram, data flow (including **embedding pipeline** and **ask question / RAG**), deployment view, and technology mapping. |
| [04-mcp-feasibility.md](04-mcp-feasibility.md) | Why MCP fits, tool and resource inventory, Java SDK and Spring transport, and integration with Cursor/other clients. |
| [05-java25-spring-ai.md](05-java25-spring-ai.md) | Java 25 as runtime, Spring Boot 4.x, Spring AI usage (MCP wiring, optional agent/RAG), and dependency sketch. |
| [06-agent-gap-analysis.md](06-agent-gap-analysis.md) | What makes an "agent," what the current design is (MCP server), what is missing to become an agent, and options to close the gap. |
| [07-vector-database-comparison.md](07-vector-database-comparison.md) | **Chosen database: PostgreSQL with pgvector.** Comparison of vector databases and rationale for the choice. |
| [08-database-schema-and-relationships.md](08-database-schema-and-relationships.md) | **Database schema and ER.** Tables, relationships, sample data, and SQL to inspect snapshots, artifacts, symbols, and code_embeddings. |
| [09-embeddings-and-why-ai.md](09-embeddings-and-why-ai.md) | **Embeddings and why we use AI.** What embeddings are, how they enable semantic search, and why an embedding model is needed to make sense of code and questions. |
| [10-testing-embedded-database.md](10-testing-embedded-database.md) | **Testing with H2.** Why integration tests use embedded H2 instead of Testcontainers; trade-offs and how the vector store is stubbed. |
| [11-embedding-model-configuration.md](11-embedding-model-configuration.md) | **Pluggable embedding model.** How to enable a real embedding model via Spring AI starters (OpenAI, Ollama, etc.), dimension considerations, and example configuration. |
| [12-vector-store-migration-plan.md](12-vector-store-migration-plan.md) | **Migration plan.** Options and steps to move from the custom pgvector repository to Spring AI’s PgVectorStore; data model comparison and trade-offs. |
| [13-sdlc-agent-integration.md](13-sdlc-agent-integration.md) | **SDLC agent integration.** How good this MCP is at answering questions about code, that it does not perform edits, and how a larger SDLC agent combines it with edit tools to make changes. |
| [14-copilot-cli-orchestration-design.md](14-copilot-cli-orchestration-design.md) | **Alternative design.** Architecture where this app drops embedded AI and delegates all intelligence to Copilot CLI; flowcharts, trade-offs, and a hybrid option. |
| [15-sdlc-orchestrator-design.md](15-sdlc-orchestrator-design.md) | **SDLC orchestrator.** Full Jira-to-PR automation blueprint: task lifecycle state machine, Copilot CLI (ACP) integration, persistent context/memory across tasks, test-and-fix loop, incremental 10-week roadmap. |
| [16-copilot-native-sdlc-agent.md](16-copilot-native-sdlc-agent.md) | **Copilot-native SDLC agent.** Zero-custom-server design using only Copilot Enterprise artifacts (AGENTS.md, copilot-instructions.md, GitHub Actions). Jira label triggers full Jira-to-PR flow via the Copilot Coding Agent with CONTEXT.md for task memory and patterns.md for cross-task learning. |

## How the Documents Relate

- **Requirements** (01) define what the system must do; **High-Level Design** (02) and **Architecture** (03) describe how it is structured and how it behaves.
- **MCP Feasibility** (04) and **Java 25 / Spring AI** (05) justify and specify the chosen technology and exposure model.
- **Agent Gap Analysis** (06) clarifies the difference between the current "tool server" design and a full "agent," and how to close that gap if desired.
- **Vector Database Comparison** (07) compares vector stores and recommends pgvector for storage and Q&A; use it when choosing or tuning the vector store.
- **Database Schema** (08) documents the PostgreSQL schema, ER diagrams, and how to query the data.
- **Embeddings and Why AI** (09) explains embedding models and semantic search for education and onboarding.
- **Testing with Embedded Database** (10) explains the use of H2 for tests and why we do not rely on Testcontainers by default.
- **Embedding Model Configuration** (11) describes how to configure a real, pluggable embedding model and how embedding dimensions interact with the schema.
- **Vector Store Migration Plan** (12) is a design doc for optionally adopting Spring AI’s PgVectorStore instead of the custom repository.
- **SDLC Agent Integration** (13) explains how this MCP fits into a bigger SDLC agent: Q&A quality, read-only nature, and how to combine it with edit tools.
- **Copilot CLI Orchestration Design** (14) is an alternative architecture where AI is fully delegated to Copilot CLI, with trade-offs and a hybrid option.
- **SDLC Orchestrator Design** (15) expands (14) into a full Jira-to-PR orchestrator with state machine, context memory, Copilot ACP integration, test-and-fix loop, and a 10-increment delivery roadmap. This is the engineering blueprint for the next major evolution of the project.
- **Copilot-Native SDLC Agent** (16) is an alternative to (15) that requires zero custom servers. It uses only GitHub Copilot Enterprise artifacts (AGENTS.md, GitHub Actions workflows) to achieve the same Jira-to-PR goal, with file-based context memory and a 5-week rollout plan.

Read in numerical order for a full narrative; use the index above to jump to a specific topic.
