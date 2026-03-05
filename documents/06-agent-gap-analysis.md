# Code Analyzer Agent — Agent Gap Analysis

This document defines what is meant by an **agent** in this context, describes what the current design is (an MCP **tool server**), identifies **what is missing** to turn it into a full agent, and outlines **options to close the gap**.

---

## 1. Definition of "Agent"

For this project, an **agent** is understood as a system that exhibits:

- **Goal- or task-driven behavior:** It accepts a high-level objective (e.g. in natural language or as a structured task) such as "Analyze repo X and tell me where method Y is used."
- **Orchestration loop:** It decides **which** actions to take and **in what order** (e.g. first analyze repository, then search for a symbol, then find references), rather than executing a single fixed operation per request.
- **Tool use:** It can invoke tools (e.g. MCP tools) to gather information or perform actions. The agent chooses when and how to use them.
- **Context and memory:** It maintains per-session or per-task context so that the results of earlier tool calls can inform the next steps and the final answer.
- **Synthesis:** It produces a final response that may combine multiple tool results (e.g. an answer to "Where is X used?" that aggregates references and locations).

Often an **LLM (or equivalent)** is in the loop to interpret the goal, choose tools, and synthesize answers; alternatively, a rule-based or template-based planner could drive the loop with a fixed strategy.

---

## 2. What the Current Design Is

The current design is **not** an agent in the above sense. It is a **tool server** (exposed via MCP):

- **Stateless per request:** Each MCP tool call is independent. The server does not maintain a session or task context across calls.
- **No goals:** The server does not accept a high-level goal; it accepts a specific tool name and arguments (e.g. `search_symbols(snapshot_id, name="Foo")`).
- **No orchestration:** The server does not decide which tools to call or in what order. The **client** (e.g. Cursor, or another process) is responsible for that.
- **No LLM in the server:** The server has no built-in model for reasoning or natural-language understanding. It only executes the requested tool and returns the result.

So the Code Analyzer, as designed, **enables** agents by providing capabilities (analyze, search, find references, **ask natural-language questions** via PostgreSQL pgvector and RAG, etc.) but is itself a **capability server**, not an agent. The **ask_question** tool uses **PostgreSQL with pgvector** (embeddings and linkages) to answer questions on the codebase; that Q&A capability is first-class from the start.

---

## 3. What Is Missing to Become an Agent

To turn the system into an agent (as defined above), the following would need to be added:

| Missing piece | Description |
|---------------|-------------|
| **Goal / task input** | An interface that accepts a high-level request (e.g. "Where is X used?" or "What calls this method?") instead of only discrete tool calls. |
| **Orchestration loop** | Logic that plans a sequence of tool calls (e.g. analyze_repository → search_symbols → find_references), executes them, and decides when to stop or ask for clarification. |
| **LLM or planner** | A component that interprets the goal, selects tools, and optionally synthesizes a natural-language answer from tool results. This could be an LLM (e.g. via Spring AI) or a rule-based planner. |
| **Context / memory** | Per-task or per-session state that accumulates tool results and allows the loop to reason about what has been done and what to do next. |

With these in place, the system could accept a single high-level request and return a single synthesized answer, behaving as an "agent" from the user’s perspective.

---

## 4. Options to Close the Gap

### Option A — Keep as MCP Server Only

- **Description:** The Code Analyzer remains a tool server. The "agent" is the **client**: e.g. Cursor (which has its own LLM and tool-calling loop) or a separate Spring AI agent that uses this server as one of its MCP tool sources.
- **Pros:** Simple; single responsibility (analyzer only does ingest + store + query); no LLM or orchestration logic in this codebase.
- **Cons:** There is no single deployable "code analyzer agent"; agent behavior depends on how the client is configured and used.

### Option B — Add an Agent Layer in This Project

- **Description:** In the same repository and deployable, add an **agent** component (e.g. using Spring AI’s agent abstractions) that (1) accepts goals (e.g. via an API or a dedicated MCP prompt/tool), (2) has the Code Analyzer’s MCP tools as its tool set (either in-process calls to Query/Ingest services or out-of-process to the same app’s MCP server), (3) runs the orchestration loop (optionally with an LLM), and (4) returns synthesized answers.
- **Pros:** One deployable that is both "code analyzer service" and "code analyzer agent"; users can call either raw tools or high-level goals.
- **Cons:** More code and configuration; need to host and configure an LLM (or a planner) and manage agent-specific concerns (prompts, context, timeouts).

### Option C — Separate Agent Service

- **Description:** A **second** service (e.g. another Spring Boot app or module) implements the agent (goals, loop, LLM, context). It uses the Code Analyzer **only** via its MCP server (over HTTP SSE). Clear separation: "analyzer service" vs "analyzer agent."
- **Pros:** Separation of concerns; analyzer stays minimal; agent can be scaled or replaced independently.
- **Cons:** Two deployables and two codebases (or two modules); operational complexity.

---

## 5. Recommendation

- **Current design:** Treat the Code Analyzer as an **intentional capability server (MCP)**. The "agent" is the client (Cursor or a dedicated Spring AI agent). No change is required for the analyzer to be valuable; it already enables agent-like behavior when used from an MCP client that has orchestration and, if needed, an LLM.
- **If a single deployable "code analyzer agent" is desired:** Add the agent layer **inside this project** (Option B): same repo, same process or same deployable, with Spring AI agent abstractions, goals, and tools (backed by the existing Query/Ingest services). Document the agent in [03-architecture.md](03-architecture.md) and [05-java25-spring-ai.md](05-java25-spring-ai.md) and keep the MCP server as the low-level tool surface that the agent (or external clients) can use.

---

## 6. Summary

- **Agent** = goal-driven, orchestration loop, tool use, context, and synthesis (often with an LLM).
- **Current system** = MCP tool server: stateless, no goals, no loop, no LLM in the server.
- **Missing for an agent:** Goal/task interface, orchestration loop, LLM or planner, and context/memory.
- **Ways to get an agent:** Keep the client as the agent (Option A), add an agent layer in this project (Option B), or build a separate agent service that calls this MCP server (Option C). Option B is recommended if a single "code analyzer agent" deployable is required.
