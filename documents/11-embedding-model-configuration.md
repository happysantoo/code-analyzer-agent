# Embedding Model Configuration (Pluggable)

The Code Analyzer Agent uses an **Embedder** abstraction for turning text (and code chunks) into vectors. The implementation is **pluggable**: the app does not hard-code a specific AI provider.

## Default: StubEmbedder

With no embedding provider configured, the app uses **StubEmbedder**, which returns zero vectors of dimension 1536. The app runs without any API key, but **ask_question** semantic search is not meaningful (all vectors are identical). Use this for local dev, tests, or when you only need relational queries and symbol search.

## Using a Real Embedding Model

To get real semantic search (and meaningful **ask_question** results), add a **Spring AI** embedding starter and set the required configuration. The app already depends on **spring-ai-model** (embedding API) and wires a **SpringAiEmbedder** when an `EmbeddingModel` bean is present.

### Dimension requirement: 1536

The database schema and vector store use **1536-dimensional** vectors. The adapter validates that the model’s output dimension is 1536. Use a model that produces 1536-dimensional embeddings (e.g. OpenAI `text-embedding-ada-002`, or another 1536-dim model from your provider). Support for other dimensions may be added later via configuration and schema changes.

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

### Same model for ingest and query

The same embedder is used when storing chunks (ingest) and when embedding the user’s question (**ask_question**). Always use the same model and dimension for both so that similarity search is consistent.

## Summary

- **No config:** StubEmbedder (zero vectors); app runs, semantic search not meaningful.
- **Add a Spring AI embedding starter + config:** Real embeddings; **SpringAiEmbedder** is used; dimension must be 1536.
- **Ask_question** uses the same embedder as ingest; do not mix models or dimensions.
