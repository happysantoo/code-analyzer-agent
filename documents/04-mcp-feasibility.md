# Code Analyzer Agent — MCP Feasibility

This document explains why the Model Context Protocol (MCP) is a good fit for the Code Analyzer, lists the tools and optional resources to expose, and summarizes how to implement the server using the MCP Java SDK and Spring transport. It also covers integration with Cursor and other MCP clients.

---

## 1. Why MCP Fits

- **Tool-oriented:** MCP is built for exposing discrete capabilities as **tools** with well-defined inputs and outputs. The Code Analyzer’s capabilities (analyze repo, search symbols, find references, get containment, get file content, and **ask natural-language questions** via vector search and RAG) map naturally to tools.
- **No built-in reasoning:** The analyzer does not need to plan or reason; it only needs to execute operations when invoked. MCP does not assume an LLM in the server—the "agent" can live in the client (e.g. Cursor or a Spring AI agent) that calls these tools.
- **Capability server model:** The analyzer acts as a **stateless capability server**: given a tool name and arguments, it performs one operation and returns a result. This matches the MCP tool execution model.
- **Ecosystem:** Cursor and other clients already speak MCP. Exposing the analyzer as an MCP server allows it to be used from those clients without custom protocols or REST-specific integration.

**Conclusion:** Using MCP as the primary surface for the Code Analyzer is feasible and aligned with its design. There are no technical blockers.

---

## 2. Tool Inventory

The following MCP tools are proposed. Input/output are summarized; exact JSON schema can be defined during implementation.

| Tool | Purpose | Inputs (summary) | Output (summary) |
|------|---------|------------------|-------------------|
| `analyze_repository` | Clone, parse, persist to **PostgreSQL** (relational + pgvector with embeddings and linkages) | `repo_url`, `ref` (branch/tag/SHA) | `snapshot_id` (e.g. commit SHA), status |
| `list_snapshots` | List analyzed snapshots | optional filters (e.g. repo_url) | list of snapshot metadata (id, repo, commit, timestamp) |
| `get_snapshot` | Get metadata for one snapshot | `snapshot_id` | snapshot metadata |
| `search_symbols` | Find symbols in a snapshot | `snapshot_id`, optional: `name`, `kind`, `path` filters | list of symbols (id, name, kind, path, etc.) |
| `get_symbol` | Get one symbol’s details and location | `snapshot_id`, `symbol_id` (or qualified name) | symbol details + span (file, line, column range) |
| `find_references` | References to/from a symbol | `snapshot_id`, `symbol_id` or name, direction (to/from/both) | list of references (from symbol, to symbol, ref kind, span) |
| `get_containment` | Containment tree for a file or symbol | `snapshot_id`, `artifact_id` or `symbol_id` | tree: file → types → members (e.g. classes → methods) |
| `get_file_content` | Raw file content | `snapshot_id`, `file_path` | file content or error |
| **`ask_question`** | Answer a natural-language question on the codebase using **PostgreSQL pgvector** (embeddings + linkages) and optional RAG. Supports **linked codebases** when used with a project. | **`snapshot_id` or `project_id`** (or `snapshot_ids`), `question` (string), optional: `top_k`, `include_rag` | answer (if RAG enabled) or ranked relevant chunks with metadata and linkages; chunks include snapshot/repo so multi-repo results are attributable |
| **`create_project`** | Create a named project for linking codebases (e.g. for SDLC agentic development) | `name`, optional: `description` | `project_id` |
| **`link_snapshots_to_project`** | Attach one or more snapshots to a project so that **ask_question(project_id, …)** searches across them | `project_id`, `snapshot_ids` (list) | success / updated project |
| **`list_projects`** | List projects (optionally with their snapshot lists) | optional filters | list of projects |
| **`get_project`** | Get project metadata and linked snapshot_ids | `project_id` | project details + snapshot list |

When **`ask_question`** is called with **`project_id`**, the vector search and RAG run over **all snapshots linked to that project**, so related codebases (e.g. frontend + backend + shared lib) are considered together when answering. This supports SDLC agents working across multiple repos during agentic development.

All tools that take `snapshot_id` or `project_id` are read-only except project management and `analyze_repository`. The only mutating operations are: `analyze_repository` (replaces data in PostgreSQL—relational and pgvector—for that repo+commit), and project create/link (update project store in PostgreSQL).

---

## 3. Optional MCP Resources

Resources allow URI-based access to data. If the MCP client supports resources, the server can expose:

- **List of files in a snapshot:** e.g. `codebase://{snapshot_id}/files` — list of file paths or artifact ids.
- **Symbol by id:** e.g. `codebase://{snapshot_id}/symbols/{symbol_id}` — symbol details and span.
- **Containment for a file:** e.g. `codebase://{snapshot_id}/containment/{artifact_id}` — containment tree.

The exact URI scheme and representation (e.g. JSON or text) can be chosen during implementation. Resources are optional; tools alone are sufficient for v1.

---

## 4. MCP Java SDK and Spring Transport

- **Server API:** The [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) provides `McpSyncServer` and `McpAsyncServer`. The server is configured with a transport, capabilities (tools, resources, prompts, etc.), and registered tool/resource handlers.
- **Tool registration:** Each of the tools above is implemented as a handler that receives the tool name and arguments, calls the appropriate Query or Ingest service, and returns a result in the format expected by MCP (e.g. text or structured content).
- **Spring transport:** The SDK offers Spring-based transports (e.g. `mcp-spring-webmvc`, `mcp-spring-webflux`) so the MCP server runs inside a Spring Boot application and is exposed over HTTP with SSE (Server-Sent Events) for the MCP protocol. This avoids the need for a separate stdio process when deploying as a web service.
- **References:** Official MCP documentation and SDK docs (e.g. [modelcontextprotocol.io/sdk/java](https://modelcontextprotocol.io/sdk/java)) describe the exact APIs for creating the server, registering tools, and wiring the Spring transport.

Implementation steps: (1) Add MCP Java SDK and Spring transport dependencies, (2) create an MCP server instance with Spring transport, (3) implement and register one handler per tool (and optionally per resource), (4) expose the transport endpoint (e.g. `/mcp` or as configured by Spring AI MCP starter).

---

## 5. Integration with Cursor and Other Clients

- **Cursor:** Cursor can connect to MCP servers (e.g. via URL or configuration). Once the Code Analyzer is running as an MCP server (e.g. HTTP SSE), users configure Cursor to use it; the AI in Cursor can then call tools such as `search_symbols`, `find_references`, and **`ask_question`** to get natural-language answers or relevant chunks from the vector database.
- **Other MCP clients:** Any client that implements the MCP protocol can discover and invoke the same tools. No custom client code is required beyond MCP configuration.
- **Spring AI agent:** A separate process or module using Spring AI can be configured to use this server as an MCP tool source; the agent then performs multi-step reasoning and calls the analyzer’s tools as needed. See [06-agent-gap-analysis.md](06-agent-gap-analysis.md).

---

## 6. Feasibility Verdict

MCP is a suitable and feasible way to expose the Code Analyzer, including **ask_question** backed by a vector database with embeddings and linkages. The tool set is well-defined, the Java SDK supports both sync and async servers and Spring-based HTTP SSE transport, and the model (stateless tool execution) matches the analyzer’s design. No technical blockers have been identified. The vector store and embedding pipeline are first-class from the start so that questions can be asked on the codebase immediately after analysis.
