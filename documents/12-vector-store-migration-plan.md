# Migration Plan: Custom pgvector Repository → Spring AI PgVectorStore

This document captures the options, trade-offs, and a concrete migration plan for
moving from the current custom `JdbcCodeEmbeddingRepository` / `code_embeddings`
schema to Spring AI’s `PgVectorStore` (vector-store) abstraction.

It is **purely design** at this point; you can decide later whether to adopt it.

---

## 1. Current State

### 1.1 Schema: `code_embeddings`

Defined in `V2__pgvector_extension_and_vector_table.sql` and modified by
`V3__flexible_embedding_dimension.sql`:

- **Table**: `code_embeddings`
- **Columns**:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `snapshot_id BIGINT NOT NULL`
  - `content TEXT NOT NULL`
  - `embedding vector`  
    - Originally `vector(1536)`, relaxed to plain `vector` in V3 so any
      dimension is allowed.
  - `artifact_id BIGINT`
  - `symbol_id BIGINT`
  - `file_path VARCHAR(4096)`
  - `span TEXT`
  - `kind VARCHAR(64)`
- **Indexes**:
  - `idx_code_embeddings_snapshot_id` on `snapshot_id`
  - Historically an HNSW index on `embedding vector_cosine_ops`
    (`idx_code_embeddings_hnsw`), dropped in V3 and expected to be recreated
    once a model/dimension is chosen.

### 1.2 Repository: `JdbcCodeEmbeddingRepository`

`JdbcCodeEmbeddingRepository` is the only implementation of
`CodeEmbeddingRepository` and does all pgvector work directly:

- `saveAll(long snapshotId, List<ChunkDto> chunks, List<float[]> embeddings)`:
  - Validates sizes.
  - Formats each `float[]` as a pgvector literal like `[0.1,0.2,...]`.
  - Inserts into `code_embeddings` using an `INSERT ... ?::vector` statement.
- `searchBySimilarity(List<Long> snapshotIds, float[] queryEmbedding, int topK)`:
  - Builds SQL:

    ```sql
    SELECT content, snapshot_id, artifact_id, symbol_id, file_path, span, kind
    FROM code_embeddings
    WHERE snapshot_id IN (?,?,...)
    ORDER BY embedding <=> ?::vector
    LIMIT ?
    ```

  - Maps rows to the strongly typed `CodeChunkHit` type.

This is wired via `IngestionConfig` as the default `CodeEmbeddingRepository`
using Spring beans and autowiring (`JdbcTemplate`).

### 1.3 Query Flow: `AskQuestionService`

- Uses an `Embedder` (either `SpringAiEmbedder` or `StubEmbedder`) to embed the
  **question** into a `float[]`.
- Passes that vector to `CodeEmbeddingRepository.searchBySimilarity(...)` along
  with `snapshotIds` and `topK`.
- Wraps the results (`List<CodeChunkHit>`) into an `AskQuestionResult`.

So the current abstraction layers are:

- `EmbeddingModel` (Spring AI) → `SpringAiEmbedder` (`Embedder`)  
  → `DefaultEmbeddingPipeline` → `CodeEmbeddingRepository` → `code_embeddings`.

---

## 2. Spring AI PgVectorStore Data Model

Spring AI’s `PgVectorStore` uses a generic **document + metadata** model.
By default, it creates a table like:

```sql
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1536)
);
```

- `id`: UUID primary key.
- `content`: the document content (string).
- `metadata`: JSON payload for arbitrary key/value metadata.
- `embedding`: vector column (default dimension 1536; configurable).

An HNSW index is typically created:

```sql
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);
```

The high-level API is the `VectorStore` interface:

- `add(List<Document> documents)`
- `similaritySearch(String query, int k)`
- `similaritySearch(SearchRequest request)`

Where each `Document` has:

- `content: String`
- `metadata: Map<String, Object>`

**Key idea:** all typed metadata (like `snapshot_id`, `file_path`, etc.) is
stored in the `metadata` JSON column instead of as first-class columns.

---

## 3. Mapping Current Model → Spring AI Model

### 3.1 From `ChunkDto` to `Document`

For each code chunk:

- `Document.content` = `ChunkDto.textToEmbed()`
- `Document.metadata` might include:

  ```json
  {
    "snapshot_id": <long>,
    "artifact_id": <Long or null>,
    "symbol_id": <Long or null>,
    "file_path": "<String or null>",
    "span": "<String or null>",
    "kind": "<String or null>"
  }
  ```

`PgVectorStore` then:

- Either calls an `EmbeddingModel` to compute embeddings and stores them into
  `embedding`, or
- Accepts pre-embedded vectors (depending on which constructor / API is used).

### 3.2 From `Document` back to `CodeChunkHit`

For query results:

- `content` ← `document.getContent()`
- `snapshotId` ← `Long.parseLong(metadata.get("snapshot_id").toString())`
- `artifactId`, `symbolId`, `filePath`, `span`, `kind` ← taken from metadata.

So the same semantics as `CodeChunkHit` can be preserved, but the backing
storage is now Spring AI’s `vector_store` table with JSON metadata instead of
your bespoke `code_embeddings` table.

---

## 4. What You Gain vs Lose by Moving to PgVectorStore

### 4.1 Gains

- **Less custom plumbing**:
  - No need to own `JdbcCodeEmbeddingRepository` and its SQL.
  - No manual float[] → vector literal formatting.
- **Alignment with Spring AI**:
  - Uses the same `VectorStore` abstraction as the rest of Spring AI’s RAG
    features (retrievers, chains, etc.).
  - Easier to adopt higher-level Spring AI building blocks in the future.
- **Unified embedding + storage**:
  - `VectorStore` can handle both embedding (via an `EmbeddingModel`) and
    persisting vectors, so you don’t have to separate `Embedder` + repository
    unless you want to.

### 4.2 Losses

- **Loss of direct DB-centric control**:
  - You no longer own the table schema; Spring AI creates and manages
    `vector_store` (unless you override defaults).
  - Tuning indexes or doing advanced SQL over embeddings requires awareness of
    Spring AI’s schema, not just your own.
- **Metadata becomes JSON, not columns**:
  - `snapshot_id`, `artifact_id`, `symbol_id`, `file_path`, `span`, `kind` move
    into `metadata json`.
  - Ad-hoc debugging SQL (e.g. `SELECT * FROM code_embeddings WHERE snapshot_id = 2`)
    becomes `SELECT ... FROM vector_store WHERE metadata->>'snapshot_id' = '2'`.
- **Coupling to Spring AI**:
  - Storage becomes tightly coupled to Spring AI’s `VectorStore`/`Document`
    model instead of a neutral pgvector schema.
  - If you ever wanted to drop Spring AI, you would need a backwards mapping
    from `vector_store` to a different abstraction.

### 4.3 Functionality Check

For your current use case (semantic search over code chunks per snapshot/project),
you **do not lose any core functionality** by switching:

- You can still:
  - ingest code chunks into a vector store,
  - associate them with snapshots, symbols, files,
  - perform top‑K similarity search,
  - scope searches by snapshot or project,
  - map hits back to code locations.

The trade-off is **control vs convenience**, not capabilities.

---

## 5. Migration Plan (High-Level)

The plan assumes:

- Spring Boot 3.5.11.
- Spring AI 1.1.x.
- Ollama / Bedrock embedding configured via `EmbeddingModel` as today.

Two styles are possible:

1. **Coexistence**: run `code_embeddings` and `vector_store` side-by-side, then
   flip to `vector_store` once validated.
2. **Hard switch**: switch logic to `VectorStore` in one go, re-run
   `analyze_repository` to regenerate embeddings, and treat `code_embeddings`
   as deprecated.

Because this is still a greenfield-ish app and you control re-ingest, the
**hard switch** is realistic.

The phases below are written for a mostly one-way migration (hard switch) but
can be adapted to coexistence by leaving the old code in place behind flags.

---

## 6. Phase 1 – Enable PgVectorStore

1. **Remove the explicit auto-config exclusion** (once ready to switch):

   In `CodeAnalyzerApplication`:

   - Remove:

     ```java
     @SpringBootApplication(excludeName = {
         "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
     })
     ```

   - Or narrow the exclusion to tests/profiles if you want coexistence.

2. **Configure pgvector store properties**:

   In `application.yml` or a profile:

   ```yaml
   spring:
     ai:
       vectorstore:
         pgvector:
           initialize-schema: true
           # table-name: optional override (default: vector_store)
           # distance-type: cosine | euclidean | inner-product
           # index-type: hnsw | ivfflat | none
   ```

3. **Database side**:

   - Existing Flyway migrations already set up `vector` extension and
     `code_embeddings`. Spring AI will create `vector_store` separately.
   - There is no conflict in having both tables; the app code decides which one
     it uses.

4. **Tests / H2 considerations**:

   - H2 does not support `vector`, so Spring AI’s pgvector auto-config will not
     work against the test DB.
   - Options:
     - Disable `PgVectorStoreAutoConfiguration` in tests via profile, and
       **mock the `VectorStore`** in units that depend on it.
     - Or avoid `VectorStore` usage entirely in integration tests (similar to
       how vector behavior is stubbed today).

---

## 7. Phase 2 – Switch Ingest to PgVectorStore

### 7.1 Decide where to call `VectorStore`

Two options:

- **A. Through `EmbeddingPipeline`**:
  - Change `EmbeddingPipeline` to depend on a `VectorStore` bean instead of
    `CodeEmbeddingRepository`.
  - `DefaultEmbeddingPipeline`:
    - builds `List<Document>` from chunks + snapshotId,
    - calls `vectorStore.add(documents)`.
- **B. Directly in `IngestionService`**:
  - Inline the pipeline:
    - `IngestionService` builds `List<Document>`,
    - calls `vectorStore.add(documents)` directly.

If simplification is the goal, **B** is attractive. If you want minimal churn
initially, **A** is safer.

### 7.2 Define a mapper from `ChunkDto` to `Document`

Create a mapper (utility or method) that:

- Takes `(snapshotId: long, chunk: ChunkDto)`.
- Produces a `Document`:

  ```java
  Document doc = new Document(
      chunk.textToEmbed(),
      Map.of(
          "snapshot_id", snapshotId,
          "artifact_id", chunk.artifactId(),
          "symbol_id", chunk.symbolId(),
          "file_path", chunk.filePath(),
          "span", chunk.span(),
          "kind", chunk.kind()
      )
  );
  ```

Use that in ingest:

- Build `List<Document>` from all `ChunkDto`s for a snapshot.
- Call `vectorStore.add(documents)`.

### 7.3 Decommission `CodeEmbeddingRepository.saveAll`

After ingest uses `vectorStore.add(...)` exclusively:

- `CodeEmbeddingRepository.saveAll` becomes unused.
- Mark it for deletion in Phase 4.

---

## 8. Phase 3 – Switch `ask_question` to PgVectorStore

### 8.1 Current pattern

- `AskQuestionService`:
  - uses `Embedder` (`SpringAiEmbedder` or `StubEmbedder`) to get `float[]`.
  - calls `CodeEmbeddingRepository.searchBySimilarity(snapshotIds, vector, k)`.
  - wraps `CodeChunkHit` into `AskQuestionResult`.

### 8.2 New pattern – let `VectorStore` handle embedding + search

Preferred pattern with Spring AI:

- Inject `VectorStore vectorStore` into `AskQuestionService`.
- For snapshot-scoped search:

  1. Build a metadata filter such as `snapshot_id == X`.
  2. Call:

     ```java
     List<Document> docs = vectorStore.similaritySearch(
         /* query= */ question,
         /* topK= */ k
         // plus filter on snapshot_id if supported by API
     );
     ```

  3. Convert `Document` list into `CodeChunkHit` list by reading `metadata`.

- For project-scoped search:

  - Obtain snapshot ids for the project (as today).
  - Filter:
    - Either: use `IN` semantics in metadata filter (if API supports it).
    - Or: overfetch without filter, then filter in memory by `snapshot_id`
      (less efficient but simple for small data sets).

### 8.3 What happens to `Embedder`?

With `VectorStore` in charge of embedding:

- You can:
  - Keep `Embedder` only for ingest tests or specialized flows, or
  - Remove it entirely and rely on Spring AI’s `EmbeddingModel` integration
    inside `PgVectorStore`.

The pluggability then lives in **which EmbeddingModel** Spring AI is using
(Ollama vs Bedrock) rather than in a separate `Embedder` abstraction.

---

## 9. Phase 4 – Cleanup and Schema Decommissioning

Once ingest and query both use `VectorStore`:

1. **Code cleanup**:
   - Delete:
     - `CodeEmbeddingRepository` interface.
     - `JdbcCodeEmbeddingRepository`.
     - Any specs that only test the old repository behavior.
   - Remove the `CodeEmbeddingRepository` bean from `IngestionConfig`.
   - Optionally simplify or remove `EmbeddingPipeline` if it only wrapped
     `VectorStore.add`.

2. **Schema cleanup** (optional but recommended eventually):
   - Add a Flyway migration to drop `code_embeddings` and its indexes:

     ```sql
     DROP INDEX IF EXISTS idx_code_embeddings_snapshot_id;
     DROP TABLE IF EXISTS code_embeddings;
     ```

   - Update DB docs to reflect that:
     - vectors now live in `vector_store` (Spring AI),
     - relational metadata (projects, snapshots, symbols) is still yours.

3. **Documentation updates**:
   - `07-vector-database-comparison.md`:
     - Mention that the implementation now relies on Spring AI’s
       `PgVectorStore`.
   - `08-database-schema-and-relationships.md`:
     - Show `vector_store` instead of `code_embeddings`, or at least explain
       both if you keep the old table around temporarily.
   - `10-testing-embedded-database.md`:
     - Explicitly describe how tests stub or mock `VectorStore` given H2’s lack
       of vector support.
   - `11-embedding-model-configuration.md`:
     - Note that the configured `EmbeddingModel` is used by Spring AI’s
       `VectorStore` for both ingest and query.

---

## 10. Decision Checklist

When you’re ready to decide whether to move to Spring AI’s `PgVectorStore`, ask:

1. **Am I comfortable coupling the vector store layer to Spring AI?**
   - If yes → `PgVectorStore` is a good simplification.
2. **Do I rely heavily on ad-hoc SQL over embeddings?**
   - If yes → consider whether JSON metadata + `vector_store` is acceptable.
3. **Do I need to run without Spring AI at all?**
   - You’ve indicated **no**, which removes a major reason for the current
     abstraction.
4. **Am I OK re-running `analyze_repository` to regenerate embeddings?**
   - If yes → a hard switch is fine.

If the answers are mostly “yes”, then:

- Keeping the current `CodeEmbeddingRepository` abstraction is **optional**.
- Leaning directly on Spring AI’s `VectorStore` is reasonable and will simplify
  the code at the cost of tighter framework coupling.

