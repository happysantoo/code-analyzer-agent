# Chunking and Linkage Model

## Chunking strategy

- **Default:** One chunk per symbol. Chunk text = `kind visibility name` (e.g. `CLASS public Foo`, `METHOD public run`).
- **Linkage:** Each chunk is stored in `code_embeddings` with metadata columns: `snapshot_id`, `artifact_id`, `symbol_id`, `file_path`, `span`, `kind`. These columns provide entity linkage to the relational schema (symbols, artifacts) so that ask_question results can be joined to references, containment, and file content.
- **Snapshot:** `snapshot_id` is always set; every vector search is filtered by snapshot (or snapshot list for projects).

## Storage

Linkages are stored as columns in the same pgvector table (`code_embeddings`). No separate linkage table. Optional chunk-to-chunk links (e.g. related chunk ids) can be added later as additional columns or a separate table if needed for context expansion.
