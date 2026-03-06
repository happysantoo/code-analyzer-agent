# Pluggable Real Embedding Model

## Summary
- **Embedder** remains the app’s abstraction; it is now backed by Spring AI’s `EmbeddingModel` when a provider is configured, otherwise by `StubEmbedder`.
- New **SpringAiEmbedder** implements `Embedder`, delegates to Spring AI `EmbeddingModel`, and enforces **1536-dimensional** vectors.
- **Bean wiring**: `SpringAiEmbedder` is registered when an `EmbeddingModel` bean exists; `StubEmbedder` is used when no `Embedder` is provided (`@ConditionalOnMissingBean(Embedder.class)`).
- **Coverage**: JaCoCo line coverage minimum raised from 0.62 to **0.84**; new and existing code covered by Spock specs. Reaching 0.90 would require an EmbeddingModel test double for config tests and JavaSemanticParser edge-case coverage.

## How to enable a real embedding model
1. Add a Spring AI embedding starter (e.g. `spring-ai-starter-model-openai`) to `pom.xml` and align version with existing Spring AI (e.g. 1.1.0).
2. Configure the provider (e.g. `spring.ai.openai.api-key=${OPENAI_API_KEY}`).
3. Use a model that outputs **1536-dimensional** vectors (e.g. OpenAI `text-embedding-ada-002`).

See [documents/11-embedding-model-configuration.md](documents/11-embedding-model-configuration.md) for details and examples.

## Testing
- `mvn clean verify` passes.
- New/updated Spock specs: `SpringAiEmbedderSpec`, `ReferenceByIndexSpec`, `SymbolInfoSpec`, and extended specs for `IngestionService`, `UrlOrPathCodeRepository`, `JavaSemanticParser`, `AskQuestionService`, `RepoSnapshot`, `ChunkingStrategy`, `WorkspaceCodeRepository`, `CloneCodeRepository`.
- Integration specs (e.g. `CodeAnalyzerApplicationSpringBootSpec`, `StoreIntegrationSpec`) unchanged and passing with StubEmbedder.
