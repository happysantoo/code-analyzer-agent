# Code Analyzer as MCP in an SDLC Agent

This document describes how well this code-analyzer MCP can **answer questions about code** when used inside a larger SDLC agent, and how that agent can **make changes** to the codebase — given that **this MCP does not perform edits itself**.

---

## 1. What This MCP Provides

The code-analyzer agent exposes **read-only** capabilities via MCP (and REST):

| Capability | Purpose |
|------------|---------|
| **analyze_repository** | Clone/parse a repo, persist symbols + embeddings to PostgreSQL (relational + pgvector). |
| **list_snapshots** / **get_snapshot** | Inspect what has been analyzed (repo, commit, snapshot_id). |
| **search_symbols** | Structured search by name, kind, path within a snapshot. |
| **ask_question** | Natural-language question → **top-K semantically similar code chunks** with `content`, `file_path`, `span`, `kind`, `symbolId`, `artifactId`. |
| **Projects** | create_project, link_snapshots_to_project, list_projects, get_project — query across multiple snapshots (e.g. frontend + backend). |

So in an SDLC agent context, this MCP is a **retrieval and navigation** tool: it tells the agent *where* relevant code lives and *what* it looks like, but it does **not** execute edits, run tests, or modify files.

---

## 2. How Good Is It at Answering Questions About Code?

### 2.1 What “Answering” Means Here

- **This MCP does not return prose answers.** It returns **ranked code chunks** (snippets + metadata). The “answer” is the set of relevant locations and their content.
- The **SDLC agent** (orchestrator with an LLM) is responsible for:
  - Interpreting the user’s question.
  - Calling `ask_question` (and optionally `search_symbols`, `get_snapshot`, etc.).
  - **Synthesizing** the chunks into a natural-language answer, or using them as context to plan edits.

So “how good” depends on (1) retrieval quality from this MCP and (2) how the parent agent uses the results.

### 2.2 Retrieval Quality (This MCP)

| Strength | Detail |
|----------|--------|
| **Semantic search** | `ask_question` embeds the question and finds code chunks by **meaning**, not just keywords. Queries like “Where is the main entry point?” or “How is user input validated?” can match methods/classes that don’t contain those exact words. |
| **Precise locations** | Each chunk carries `file_path`, `span` (e.g. line/column), `kind` (class, method, field), and links to `symbol_id` / `artifact_id`, so the SDLC agent can navigate to the exact symbol or file. |
| **Snapshot- and project-scoped** | Search can be limited to one snapshot or to all snapshots linked to a project (e.g. multi-repo). |
| **Structured + semantic** | The parent agent can combine `ask_question` (semantic) with `search_symbols` (name/kind/path) for hybrid workflows. |

| Limitation | Detail |
|------------|--------|
| **Chunk granularity** | Embeddings are per **symbol** (e.g. one chunk per method/class). Very long methods or large files are summarized at that granularity; the agent may need to fetch full file content elsewhere if needed. |
| **No cross-chunk reasoning inside MCP** | The MCP does not chain multiple queries or explain *why* a chunk is relevant. The SDLC agent’s LLM does that. |
| **Staleness** | Results reflect the **last analyzed snapshot** (commit). If the repo has changed since then, the agent may need to re-run `analyze_repository` or be aware of the mismatch. |
| **Embedding model** | Quality depends on the configured embedding model (e.g. StubEmbedder → no real similarity; Ollama/Bedrock → model-dependent). |

**Summary:** For an SDLC agent, this MCP is **very good at “find code that matches this question”** and **exposing precise locations**. It is **not** an end-to-end Q&A engine; it is a **retrieval backend** that the agent uses to gather context before answering or editing.

---

## 3. How Can an SDLC Agent Make Changes? (This MCP Does Not Edit)

The code-analyzer MCP is **explicitly read-only**. It does not:

- Edit files
- Apply patches
- Run linters or formatters
- Create branches or commits
- Execute tests

So **all edits happen outside this MCP**. The high-level design (see [02-high-level-design.md](02-high-level-design.md)) states:

> The analyzer is **read-only**; "making changes" is done by **other agents** that use this API for context and apply edits elsewhere.

### 3.1 How an SDLC Agent Uses This MCP to “Make Changes”

A typical flow for an SDLC agent that *does* make changes looks like this:

1. **User goal:** “Add validation for the email field in the signup form.”
2. **Agent calls this MCP:**
   - `ask_question(project_id=…, question="Where is the signup form and email field validated?")`  
     → Gets back chunks with `file_path`, `span`, `content`, `kind`.
   - Optionally: `search_symbols(snapshot_id, name="signup", kind="method")` or similar.
3. **Agent uses the chunks as context:**
   - The LLM (or planner) reads the returned `content` and `file_path`/`span` and decides **where** to change and **what** to change.
4. **Agent calls *other* tools to apply the edit:**
   - Examples: **file write** (e.g. MCP filesystem or editor tool), **apply_patch**, **run_terminal** (e.g. `sed`, or an IDE API), or a dedicated “edit code” tool.
   - Those tools are **not** part of this code-analyzer MCP; they are provided by the same SDLC agent platform (e.g. Cursor, another MCP server, or a custom orchestrator).

So:

- **This MCP** → “Here are the relevant code locations and snippets.”
- **Other tools** → “Apply this diff / write this content at this path.”

The SDLC agent **orchestrates** both: it uses the code-analyzer to **find** the right places, then uses edit tools to **change** them.

### 3.2 What This MCP Gives the Agent for Editing

For the edit step, the agent gets from `ask_question` (and related tools):

| Field | Use for editing |
|-------|------------------|
| **file_path** | Target file to modify. |
| **span** | Approximate or exact location (e.g. line range) for the change. |
| **content** | Snippet of code at that location (what was embedded). |
| **kind** | Symbol type (e.g. method, class) to guide “add a check here” vs “add a new method.” |
| **symbolId** / **artifactId** | Can be used with other APIs (if exposed) to fetch full file content or more context. |

So the MCP is well-suited to **point the editor to the right file and region**; the actual edit is always done by another capability.

---

## 4. Recommendation in a Bigger SDLC Agent Context

- **Use this MCP for:**  
  - Ingesting and indexing repos (analyze_repository).  
  - Semantic and structured search (ask_question, search_symbols).  
  - Scoping by snapshot or project (list_snapshots, projects).  
  - Giving the SDLC agent **precise, retrieval-based context** so it can answer questions and decide where to edit.

- **Do not rely on it for:**  
  - Applying code changes (use editor / filesystem / patch tools).  
  - Running tests or builds (use runner / shell tools).  
  - Generating natural-language answers (the SDLC agent’s LLM should synthesize from the chunks returned by this MCP).

- **To improve “answer quality” in the larger system:**  
  - Use a real embedding model (e.g. Ollama/Bedrock) and re-analyze after significant code changes.  
  - Have the SDLC agent combine multiple tool calls (e.g. ask_question + search_symbols + get file content) and use a capable LLM to summarize and plan edits.  
  - Optionally add an agent layer (see [06-agent-gap-analysis.md](06-agent-gap-analysis.md)) that consumes this MCP and returns synthesized answers, while still delegating edits to other tools.

---

## 5. Summary

| Question | Answer |
|----------|--------|
| **How good is it at answering questions about code?** | **Retrieval:** very good — semantic search + precise locations. **Prose answers:** the MCP only returns chunks; the SDLC agent’s LLM turns them into an answer. End-to-end quality depends on embedding model, chunking, and how the agent uses the results. |
| **How can it make changes to code?** | **It cannot.** This MCP is read-only. An SDLC agent uses this MCP to **find** relevant code (ask_question, search_symbols), then uses **other** tools (file edit, patch, terminal) to **apply** changes. This MCP is the “find” half; the “change” half lives elsewhere. |
