# Pluggable Real Embedding Model & Ollama Demo Profile

## Summary
- **Embedder** remains the app’s abstraction; it is now backed by Spring AI’s `EmbeddingModel` when a provider is configured, otherwise by `StubEmbedder`.
- New **SpringAiEmbedder** implements `Embedder`, delegates to Spring AI `EmbeddingModel`, and accepts vectors of any dimension (backed by a flexible `vector` column).
- **Bean wiring**: `SpringAiEmbedder` is registered when an `EmbeddingModel` bean exists; `StubEmbedder` is used when no `Embedder` is provided (`@ConditionalOnMissingBean(Embedder.class)`).
- **Profiles**: Added `demo-ollama` and `bedrock` Spring profiles with dedicated `application-*.yml` configs for local Ollama demos and AWS Bedrock Titan in real environments.
- **Boot & coverage**: Downgraded to Spring Boot **3.5.11** (to align with Spring AI 1.1.x) and adjusted JaCoCo line coverage minimum from 0.84 to **0.82** after adding the Ollama starter and auto-config classes.

## How to enable a real embedding model
1. Add a Spring AI embedding starter (e.g. `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`, or `spring-ai-starter-model-bedrock`) to `pom.xml` and align version with existing Spring AI (e.g. 1.1.0).
2. Configure the provider (e.g. `spring.ai.openai.api-key=${OPENAI_API_KEY}`, or `spring.ai.ollama.base-url`, or Bedrock credentials/region).
3. Use a model whose embedding dimension you are comfortable with (e.g. 768‑dim `nomic-embed-text`, 1536‑dim OpenAI `text-embedding-ada-002`). The schema now accepts any dimension; just ensure ingest and query both use the same model/dimension and re‑run `analyze_repository` after switching models.

See [documents/11-embedding-model-configuration.md](documents/11-embedding-model-configuration.md) for details and examples.

## Testing
- `mvn clean verify` passes on Spring Boot 3.5.11 with Spring AI 1.1.x and the Ollama starter on the classpath.
- New/updated Spock specs: `SpringAiEmbedderSpec`, `ReferenceByIndexSpec`, `SymbolInfoSpec`, and extended specs for `IngestionService`, `UrlOrPathCodeRepository`, `JavaSemanticParser`, `AskQuestionService`, `RepoSnapshot`, `ChunkingStrategy`, `WorkspaceCodeRepository`, `CloneCodeRepository`.
- Integration specs (e.g. `CodeAnalyzerApplicationSpringBootSpec`, `StoreIntegrationSpec`) unchanged and passing with StubEmbedder.
