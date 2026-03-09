# Embedding Model Configuration (Pluggable)

The Code Analyzer Agent uses an **Embedder** abstraction for turning text (and code chunks) into vectors. The implementation is **pluggable**: the app does not hard-code a specific AI provider.

## Default: StubEmbedder

With no embedding provider configured, the app uses **StubEmbedder**, which returns zero vectors of dimension 1536. The app runs without any API key, but **ask_question** semantic search is not meaningful (all vectors are identical). Use this for local dev, tests, or when you only need relational queries and symbol search.

## Using a Real Embedding Model

To get real semantic search (and meaningful **ask_question** results), add a **Spring AI** embedding starter and set the required configuration. The app already depends on **spring-ai-model** (embedding API) and wires a **SpringAiEmbedder** when an `EmbeddingModel` bean is present.

### Dimensions and schema

The database stores embeddings in a `vector` column without a fixed dimension. Earlier versions used `vector(1536)` and required 1536‑dimensional embeddings; this was relaxed in `V3__flexible_embedding_dimension.sql` so different models (e.g. 768‑dim `nomic-embed-text`, 1536‑dim `text-embedding-ada-002`) can be used.

The critical requirement is **consistency**:

- Use the **same model and dimension** for ingest and for **ask_question**.
- If you switch models or dimensions, you should **re-run `analyze_repository`** so all stored embeddings are regenerated with the new dimension.

### Supported providers (via Spring AI)

Add **one** of the following dependencies (align version with the project’s Spring AI usage, e.g. 1.1.x) and configure the corresponding properties.

| Provider | Starter / dependency | Example config |
|----------|----------------------|----------------|
| **OpenAI** | `spring-ai-starter-model-openai` | `spring.ai.openai.api-key=${OPENAI_API_KEY}` (and optional base URL) |
| **Ollama** | `spring-ai-starter-model-ollama` | `spring.ai.ollama.base-url`, model name for embeddings |
| **Azure OpenAI** | `spring-ai-starter-model-azure-openai` | Azure endpoint, API key, deployment name |
| **Bedrock (Titan / Cohere)** | `spring-ai-starter-model-bedrock` | AWS region, credentials; use Titan or Cohere embedding model |

Do **not** commit API keys. Use environment variables (e.g. `OPENAI_API_KEY`) or a secret manager.

### Example: OpenAI (Maven)

Add to `pom.xml` (version align with existing Spring AI, e.g. 1.1.0):

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <version>1.1.0</version>
</dependency>
```

In `application.yml` or via environment:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      # base-url: optional if not using default
```

Restart the app. On startup, Spring Boot will create an `EmbeddingModel` bean; the app will register **SpringAiEmbedder** and use it for ingest and for **ask_question**.

## Profiles: demo vs real

### Demo profile: local Ollama (`demo-ollama`)

For a local demo, you can use **Ollama** running on the same machine and activate the `demo-ollama` Spring profile:

1. Install and start Ollama locally, then pull an embedding model, for example:

   ```bash
   ollama pull nomic-embed-text
   ```

2. Ensure the `spring-ai-starter-model-ollama` dependency is on the classpath (e.g. in your runtime environment or wrapper app).

3. Run the app with the `demo-ollama` profile:

   ```bash
   SPRING_PROFILES_ACTIVE=demo-ollama mvn spring-boot:run
   ```

4. The profile-specific config lives in `application-demo-ollama.yml`:

   ```yaml
   spring:
     ai:
       model:
         embedding: ollama
       ollama:
         base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
         embedding:
           options:
             model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text}
         init:
           pull-model-strategy: ${OLLAMA_PULL_MODEL_STRATEGY:when_missing}
   ```

With this profile active and the Ollama embedding starter present, Spring AI will auto-configure an `EmbeddingModel` backed by Ollama, and the app will use **SpringAiEmbedder** for ingest and `ask_question`.

### Real profile: AWS Bedrock (`bedrock`)

For production/real environments, you can use **AWS Bedrock** (e.g. Titan embeddings) and activate the `bedrock` profile:

1. Add the `spring-ai-starter-model-bedrock` dependency and enable model access for your account in the Bedrock console.
2. Configure AWS credentials/region via environment (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`) or profile.
3. Run with:

   ```bash
   SPRING_PROFILES_ACTIVE=bedrock mvn spring-boot:run
   ```

4. The profile-specific config lives in `application-bedrock.yml`:

   ```yaml
   spring:
     ai:
       model:
         embedding: bedrock-titan
       bedrock:
         aws:
           region: ${AWS_REGION:us-east-1}
           access-key: ${AWS_ACCESS_KEY_ID:}
           secret-key: ${AWS_SECRET_ACCESS_KEY:}
         titan:
           embedding:
             model: ${BEDROCK_TITAN_EMBEDDING_MODEL:amazon.titan-embed-text-v2:0}
   ```

This keeps the **demo Ollama** and **real Bedrock** configurations isolated behind Spring profiles; both still go through the same `Embedder` abstraction and `SpringAiEmbedder` adapter.

### Same model for ingest and query

The same embedder is used when storing chunks (ingest) and when embedding the user’s question (**ask_question**). Always use the same model and dimension for both so that similarity search is consistent.

## Summary

- **No config:** StubEmbedder (zero vectors of length 1536); app runs, semantic search not meaningful.
- **Add a Spring AI embedding starter + config:** Real embeddings; **SpringAiEmbedder** is used; dimension is determined by the model (e.g. 768, 1536) but must be consistent between ingest and query.
- **Ask_question** uses the same embedder as ingest; do not mix models or dimensions without re‑embedding.
