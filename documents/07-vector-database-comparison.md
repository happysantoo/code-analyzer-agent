# Code Analyzer Agent — Vector Database Comparison and Chosen Stack

The **database of choice for the Code Analyzer Agent is PostgreSQL with the pgvector extension.** One PostgreSQL instance hosts both the relational schema (symbols, artifacts, references, projects, file contents) and the vector store (embeddings and linkages). This document summarizes why that choice was made and compares it to other vector databases for context. For storage and Q&A design see [02-high-level-design.md](02-high-level-design.md) and [03-architecture.md](03-architecture.md).

---

## 1. Role of the Vector Database in This Project

The vector store is used to:

- Store **embeddings** of code chunks (e.g. symbol + docstring + signature) with **metadata** (snapshot_id, artifact_id, symbol_id, path, span, kind) and **linkages** (to relational entities; optionally to related chunks).
- Support **semantic search** for **ask_question**: embed the user’s question, run approximate nearest-neighbor (ANN) search filtered by **snapshot_id** or by **snapshot_id IN project’s snapshot list** (linked codebases), return top-k chunks for RAG or direct response.
- Scale to **very large codebases** (e.g. a million lines of code → hundreds of thousands of chunks per snapshot; see [03-architecture.md](03-architecture.md) §6).

**Critical requirements for the vector DB:**

| Requirement | Why it matters |
|-------------|----------------|
| **Metadata filtering** | Every query is scoped by snapshot_id (single snapshot or list for projects). Filtering must be efficient (e.g. index on snapshot_id). |
| **ANN search** | Exact k-NN does not scale to millions of vectors. HNSW, IVFFlat, or equivalent for sub-second search. |
| **Spring AI integration** | The stack uses Spring AI’s `VectorStore` and `EmbeddingModel`; a first-class Spring AI vector store reduces custom glue and keeps one abstraction for ingest and query. |
| **Scale** | Hundreds of thousands of vectors per snapshot; multiple snapshots/projects. Store must handle millions of vectors and batch delete/insert for snapshot replacement. |
| **Metadata + payload** | Chunk metadata (snapshot_id, path, symbol_id, etc.) and optional linkage data must be stored and returned with search results. |

---

## 2. Vector Databases Considered

### 2.1 pgvector (PostgreSQL extension)

- **Description:** Extension for PostgreSQL that adds vector type and operators (e.g. cosine distance, L2). Supports IVFFlat and HNSW indexes for ANN. Metadata and linkages live as normal columns; filtering uses standard SQL (e.g. `WHERE snapshot_id = $1` or `WHERE snapshot_id = ANY($1)`).
- **Deployment:** Self-hosted; same PostgreSQL instance can host the **relational** schema (symbols, artifacts, references, projects) and the vector data. Single database to operate and backup.
- **Spring AI:** Officially supported via `spring-ai-starter-vector-store-pgvector`. Uses `VectorStore` interface; supports metadata filtering and schema init.
- **Pros:** One database for relational + vector; no extra infrastructure; strong metadata filtering (SQL); ACID; good for “replace by snapshot” (delete by snapshot_id + bulk insert); Spring AI native. **Cons:** Scale and performance depend on PostgreSQL tuning and hardware; at very large scale (tens of millions of vectors) a dedicated vector DB may outperform.

### 2.2 Qdrant

- **Description:** Open-source vector database (Rust), focused on high-performance ANN (HNSW). Rich metadata filtering (e.g. by scalar fields); supports payload and filtering in queries. Can be self-hosted or used as a managed service (Qdrant Cloud).
- **Deployment:** Self-hosted (single binary or Docker) or cloud. Separate from the relational DB.
- **Spring AI:** Supported via `spring-ai-starter-vector-store-qdrant`. Filtering by metadata is a core feature.
- **Pros:** Designed for vectors and metadata filtering; excellent ANN performance; good scale; Spring AI support. **Cons:** Additional service to run and monitor; data lives in two systems (relational + Qdrant).

### 2.3 Pinecone

- **Description:** Fully managed vector database (cloud). Serverless or pod-based. Strong scalability and low ops; filtering by metadata supported.
- **Deployment:** Cloud only; no self-hosted option.
- **Spring AI:** Supported via `spring-ai-starter-vector-store-pinecone` (or equivalent). Integrates with Spring AI’s `VectorStore`.
- **Pros:** No infrastructure to run; scales well; good for teams that prefer managed services. **Cons:** Vendor lock-in; cost at scale; data leaves your environment; not suitable if deployment must be on-prem or single-tenant self-hosted.

### 2.4 Weaviate

- **Description:** Open-source vector database with GraphQL and REST APIs. Supports vector + scalar search and hybrid search. Metadata (properties) and filtering are first-class. Can be self-hosted or Weaviate Cloud.
- **Deployment:** Self-hosted (e.g. Docker/Kubernetes) or managed cloud.
- **Spring AI:** Supported (e.g. `spring-ai-starter-vector-store-weaviate` or similar). Check current Spring AI docs for exact module name.
- **Pros:** Flexible data model; good filtering; hybrid search option. **Cons:** Another service to operate; learning curve if using advanced features.

### 2.5 Chroma

- **Description:** Open-source embedding store; often used for prototypes and smaller deployments. Supports persistence and metadata; can run in-process (embedded) or as a server.
- **Deployment:** Embedded (e.g. in-process) or server; lighter weight than Qdrant/Weaviate.
- **Spring AI:** Supported (e.g. Chroma vector store integration). Good for dev and small-scale.
- **Pros:** Simple; Spring AI support; easy local/dev setup. **Cons:** Not primarily designed for millions of vectors or heavy concurrent load; fewer production-scale stories than pgvector or Qdrant.

### 2.6 Redis Stack (RediSearch with vectors)

- **Description:** Redis with RediSearch module; supports vector fields and similarity search (e.g. HNSW). Metadata can be stored as document fields; filtering via RediSearch query syntax.
- **Deployment:** Self-hosted Redis Stack or managed (e.g. Redis Cloud).
- **Spring AI:** Redis vector store support exists in Spring AI; confirm current starter/artifact for your version.
- **Pros:** Very fast; many teams already use Redis; can reuse for caching/sessions. **Cons:** Vector support and scaling story are less documented than dedicated vector DBs; operational model differs from PostgreSQL.

### 2.7 Others (brief)

- **Milvus / Zilliz:** Very scalable, aimed at large-scale ML; typically more operational complexity; Spring AI support may be community or partial.
- **Elasticsearch (dense_vector):** Can do vector search with metadata filtering; good if you already use Elasticsearch for search; Spring AI has an Elasticsearch vector store option.
- **Azure AI Search, MongoDB Atlas Vector Search, Cassandra:** Viable if you are already on those platforms; choice then depends on existing stack and Spring AI support.

---

## 3. Comparison Summary

| Vector DB | Spring AI | Metadata filter | ANN (HNSW/etc.) | Scale (order) | Self-hosted | Same DB as relational |
|-----------|-----------|------------------|------------------|---------------|-------------|------------------------|
| **pgvector** | Yes | SQL (excellent) | Yes (IVFFlat, HNSW) | Millions (tuned PG) | Yes | **Yes (PostgreSQL)** |
| **Qdrant** | Yes | Yes | Yes (HNSW) | Millions+ | Yes / Cloud | No |
| **Pinecone** | Yes | Yes | Yes | Millions+ | No (cloud only) | No |
| **Weaviate** | Yes | Yes | Yes | Large | Yes / Cloud | No |
| **Chroma** | Yes | Yes | Yes | Small–medium | Yes | No |
| **Redis Stack** | Yes | Yes | Yes | Medium–large | Yes | No |

---

## 4. Chosen Database: PostgreSQL with pgvector

**PostgreSQL with pgvector** is the chosen database for the Code Analyzer Agent.

**Reasons:**

1. **Single operational footprint**  
   The design already uses a relational database for snapshots, artifacts, symbols, references, containment, file contents, and projects. Using **PostgreSQL with pgvector** keeps both relational and vector data in one place: one backup, one deployment, one connection pool, and simpler security and networking.

2. **Strong metadata filtering**  
   Query pattern is always “filter by snapshot_id (or snapshot_id IN list), then ANN search.” In pgvector this is standard SQL (e.g. `WHERE snapshot_id = $1` or `= ANY($1)`) with an index on `snapshot_id`. No need to learn a second query language or API for filters.

3. **Snapshot replacement**  
   Replacing all data for a snapshot is a well-understood pattern: delete by `snapshot_id`, then bulk insert. PostgreSQL handles this with transactions and indexes; no extra “vector DB” bulk API to coordinate.

4. **Spring AI support**  
   Spring AI’s pgvector vector store is a first-class integration (`VectorStore` + metadata). You keep one abstraction for embedding + store across ingest and **ask_question**, and can still use the same PostgreSQL for projects and snapshot lists.

5. **Scale adequacy**  
   For “hundreds of thousands of vectors per snapshot” and “millions of lines of code,” PostgreSQL with pgvector and an HNSW (or IVFFlat) index is sufficient with proper indexing (e.g. on `snapshot_id`) and hardware. If you later outgrow it, the same schema and Spring AI `VectorStore` abstraction can be swapped for another backend (e.g. Qdrant) with limited code change.

6. **Deployment flexibility**  
   Works on-prem, in your own cloud tenant, or in managed PostgreSQL (e.g. AWS RDS, Azure Database for PostgreSQL, Google Cloud SQL) with the pgvector extension enabled. No dependency on a specific vector-DB SaaS.

**If a different store is needed later** (e.g. scale or deployment constraints), alternatives include: **Qdrant** for maximum vector throughput; **Pinecone** for fully managed cloud; **Chroma** for minimal local/dev. The Spring AI `VectorStore` abstraction limits the impact of switching to configuration and store-specific code.

---

## 5. Suggested Implementation Notes for pgvector

- **Schema:** One table (or partitioned table) for vector documents: e.g. `id`, `snapshot_id`, `content` (or chunk text), `embedding` (vector type), plus columns for `artifact_id`, `symbol_id`, `file_path`, `span`, `kind`, and any linkage fields. Index on `snapshot_id` (and optionally `(snapshot_id, kind)` if you filter by kind).
- **ANN index:** Create an HNSW index on the embedding column (with optional `WHERE snapshot_id IS NOT NULL` if you use partial indexes). Tune `m` and `ef_construction` for your dimension and workload.
- **Project-scoped queries:** Use `WHERE snapshot_id = ANY($1::uuid[])` (or equivalent) with the project’s snapshot list; the ANN index and metadata filter together keep latency bounded (see [03-architecture.md](03-architecture.md) §6.5).
- **Spring AI:** Use `spring-ai-starter-vector-store-pgvector`; configure `VectorStore` and schema (e.g. init script or Flyway migration). Map chunk metadata and linkages to the table columns so that **ask_question** receives snapshot_id and other fields for filtering and context expansion.

---

## 6. Summary

| Aspect | Choice |
|--------|--------|
| **Database** | **PostgreSQL with pgvector** |
| **Rationale** | Single database for relational + vector data, strong metadata filtering for snapshot/project scope, first-class Spring AI support, and sufficient scale for the target workload (up to millions of vectors). |
| **Reflected in** | [01-requirements.md](01-requirements.md) (FR-6, FR-7), [02-high-level-design.md](02-high-level-design.md) (§2.4), [03-architecture.md](03-architecture.md), [05-java25-spring-ai.md](05-java25-spring-ai.md). |

All documentation for the Code Analyzer Agent assumes PostgreSQL with pgvector as the database of choice. The Spring AI `VectorStore` abstraction allows a different store to be used later if requirements change.
