# SDLC Orchestrator Design: Jira-to-PR via Copilot CLI

This document is the engineering blueprint for expanding the code-analyzer-agent into a
full **SDLC orchestrator**. The orchestrator picks up a Jira story, uses the existing
code-analyzer for codebase understanding, delegates all AI work (planning, code
generation, test fixing, self-review) to **GitHub Copilot CLI**, manages **persistent
context/memory** across tasks, runs tests, and opens a Pull Request -- optionally with
human-in-the-loop checkpoints.

---

## 1. End-to-End Architecture

### 1.1 Component Overview

```mermaid
flowchart TB
    subgraph external [External Systems]
        JiraCloud[Jira Cloud]
        GitHub[GitHub]
        CopilotACP[Copilot CLI<br/>ACP Server]
    end

    subgraph orchestrator [SDLC Orchestrator]
        direction TB
        JiraClient[JiraClient<br/>poll + update]
        StoryParser[StoryParser<br/>extract requirements]
        Scheduler[Task Scheduler<br/>cron / event-driven]

        subgraph codeAnalyzer [Existing Code Analyzer]
            IngestionSvc[IngestionService]
            QuerySvc[QueryService]
            AskQSvc[AskQuestionService]
            EmbeddingPipeline[EmbeddingPipeline]
        end

        ContextBuilder[ContextBuilder<br/>keyword + semantic search]
        PromptAssembler[PromptAssembler<br/>layered prompt + token budget]
        CopilotBridge[CopilotBridge<br/>ACP JSON-RPC client]
        PatchApplier[PatchApplier<br/>apply diffs to working tree]
        TestRunner[TestRunner<br/>execute build + test]
        GitOpsSvc[GitOpsService<br/>branch + commit + push]
        PrCreator[PrCreator<br/>gh pr create]
        TaskOrchestrator[SdlcOrchestrator<br/>state machine]
        TaskCtxSvc[TaskContextService<br/>read/write context]
        CheckpointCtrl[CheckpointController<br/>approve/reject REST]
    end

    subgraph storage [PostgreSQL]
        RelationalDB[(Relational Tables<br/>snapshots, symbols, ...)]
        VectorDB[(pgvector<br/>code_embeddings)]
        SdlcDB[(SDLC Tables<br/>sdlc_task, task_context)]
    end

    JiraCloud <-->|REST API| JiraClient
    JiraClient --> StoryParser
    StoryParser --> TaskOrchestrator
    Scheduler --> TaskOrchestrator

    TaskOrchestrator --> IngestionSvc
    TaskOrchestrator --> ContextBuilder
    ContextBuilder --> QuerySvc
    ContextBuilder --> AskQSvc
    TaskOrchestrator --> PromptAssembler
    PromptAssembler --> TaskCtxSvc
    TaskOrchestrator --> CopilotBridge
    CopilotBridge <-->|JSON-RPC| CopilotACP
    TaskOrchestrator --> PatchApplier
    TaskOrchestrator --> TestRunner
    TaskOrchestrator --> GitOpsSvc
    TaskOrchestrator --> PrCreator
    PrCreator -->|gh CLI| GitHub
    TaskOrchestrator --> CheckpointCtrl

    IngestionSvc --> RelationalDB
    EmbeddingPipeline --> VectorDB
    QuerySvc --> RelationalDB
    AskQSvc --> VectorDB
    TaskCtxSvc --> SdlcDB
    TaskOrchestrator --> SdlcDB
```

### 1.2 Human-in-the-Loop vs Autonomous

The orchestrator supports two autonomy modes, configurable per-task or globally.

```mermaid
flowchart LR
    subgraph humanMode [Human-in-the-Loop]
        direction TB
        H_Intake[Intake] --> H_Understand[Understand]
        H_Understand --> H_Plan[Plan]
        H_Plan --> H_Wait1[AWAIT_PLAN_APPROVAL<br/>human approves]
        H_Wait1 --> H_Implement[Implement]
        H_Implement --> H_Wait2[AWAIT_CHANGE_APPROVAL<br/>human approves]
        H_Wait2 --> H_Test[Test + Fix]
        H_Test --> H_Submit[Submit PR]
    end

    subgraph autoMode [Fully Autonomous]
        direction TB
        A_Intake[Intake] --> A_Understand[Understand]
        A_Understand --> A_Plan[Plan]
        A_Plan --> A_SelfReview1[Self-Review Plan<br/>Copilot validates]
        A_SelfReview1 --> A_Implement[Implement]
        A_Implement --> A_SelfReview2[Self-Review Code<br/>Copilot reviews]
        A_SelfReview2 --> A_Test[Test + Fix]
        A_Test --> A_Submit[Submit PR]
    end
```

In autonomous mode, the two human checkpoints are replaced by **Copilot self-review**
prompts. The self-review asks Copilot to critique its own plan/code and either proceed
or revise. The PR itself still goes through normal human code review on GitHub.

---

## 2. Task Lifecycle State Machine

The `SdlcOrchestrator` drives each task through a formal state machine. Every
transition is persisted to `sdlc_task.status` so the orchestrator can resume after
restarts.

```mermaid
stateDiagram-v2
    [*] --> INTAKE : Jira story picked up

    INTAKE --> UNDERSTANDING : story parsed and stored
    INTAKE --> FAILED : Jira parse error

    UNDERSTANDING --> PLANNING : code context built
    UNDERSTANDING --> FAILED : analysis error

    PLANNING --> AWAITING_PLAN_APPROVAL : plan generated (human mode)
    PLANNING --> IMPLEMENTING : plan generated (auto mode, self-review passed)
    PLANNING --> FAILED : Copilot unreachable

    AWAITING_PLAN_APPROVAL --> IMPLEMENTING : human approves
    AWAITING_PLAN_APPROVAL --> PLANNING : human requests revision
    AWAITING_PLAN_APPROVAL --> CANCELLED : human rejects

    IMPLEMENTING --> AWAITING_CHANGE_APPROVAL : changes applied (human mode)
    IMPLEMENTING --> TESTING : changes applied (auto mode, self-review passed)
    IMPLEMENTING --> FAILED : Copilot unreachable or patch error

    AWAITING_CHANGE_APPROVAL --> TESTING : human approves
    AWAITING_CHANGE_APPROVAL --> IMPLEMENTING : human requests revision
    AWAITING_CHANGE_APPROVAL --> CANCELLED : human rejects

    TESTING --> SUBMITTING : all tests pass
    TESTING --> FIXING : tests fail, retries remaining
    TESTING --> FAILED : tests fail, max retries exceeded

    FIXING --> TESTING : fix applied, re-run tests
    FIXING --> FAILED : Copilot fix failed

    SUBMITTING --> DONE : PR opened, Jira updated
    SUBMITTING --> FAILED : git push or PR creation error

    FAILED --> INTAKE : manual retry
    CANCELLED --> [*]
    DONE --> [*]
```

### State Descriptions

| State | Description | Exit condition |
|-------|-------------|----------------|
| INTAKE | Jira story fetched and parsed | Story context written to `task_context` |
| UNDERSTANDING | Code analyzer ingests/searches the repo | Code context written to `task_context` |
| PLANNING | Copilot CLI generates an implementation plan | Plan stored; checkpoint or auto-proceed |
| AWAITING_PLAN_APPROVAL | Paused; waiting for human approval via REST | Human calls approve/revise/reject endpoint |
| IMPLEMENTING | Copilot CLI generates code changes; PatchApplier applies them | Changes applied; checkpoint or auto-proceed |
| AWAITING_CHANGE_APPROVAL | Paused; waiting for human approval via REST | Human calls approve/revise/reject endpoint |
| TESTING | Test command executed | Tests pass or fail |
| FIXING | Copilot CLI generates a fix for test failures | Fix applied; returns to TESTING |
| SUBMITTING | Branch created, committed, pushed; PR opened; Jira updated | PR URL stored |
| DONE | Terminal success state | -- |
| FAILED | Terminal failure state (retryable) | Manual retry resets to INTAKE |
| CANCELLED | Terminal state; human rejected the task | -- |

### Retry and Escalation

- **Test retries**: configurable `sdlc.test.max-retries` (default 3). Each TESTING->FIXING->TESTING cycle increments a counter stored on `sdlc_task`.
- **Copilot retries**: if Copilot ACP is unreachable, the bridge retries with exponential backoff (1s, 2s, 4s) up to 3 times before marking FAILED.
- **Manual retry**: a FAILED task can be retried via `POST /api/sdlc/tasks/{id}/retry`, which resets status to INTAKE and clears stale context.

---

## 3. Extended Data Model

### 3.1 ER Diagram

The two new tables integrate with the existing schema via foreign keys to `snapshots`
and logically relate to `projects`.

```mermaid
erDiagram
    projects ||--o{ project_snapshots : contains
    snapshots ||--o{ project_snapshots : "linked in"
    snapshots ||--o{ artifacts : has
    snapshots ||--o{ file_contents : has
    snapshots ||--o{ code_embeddings : has

    sdlc_task }o--|| snapshots : "analyzed as"
    sdlc_task ||--o{ task_context : "has context"

    projects {
        bigserial id PK
        varchar name
        varchar description
    }

    snapshots {
        bigserial id PK
        varchar repo_url
        varchar commit_sha
        timestamptz created_at
    }

    sdlc_task {
        bigserial id PK
        varchar jira_key
        varchar repo_url
        varchar branch_name
        bigint snapshot_id FK
        varchar status
        varchar autonomy_mode
        int test_retry_count
        timestamptz created_at
        timestamptz updated_at
    }

    task_context {
        bigserial id PK
        bigint task_id FK
        varchar phase
        varchar context_type
        text content
        int token_estimate
        timestamptz created_at
    }
```

### 3.2 DDL

```sql
-- V4: SDLC orchestrator tables
CREATE TABLE sdlc_task (
    id                BIGSERIAL    PRIMARY KEY,
    jira_key          VARCHAR(32)  NOT NULL,
    repo_url          VARCHAR(512) NOT NULL,
    branch_name       VARCHAR(256),
    snapshot_id       BIGINT       REFERENCES snapshots(id),
    status            VARCHAR(32)  NOT NULL DEFAULT 'INTAKE',
    autonomy_mode     VARCHAR(16)  NOT NULL DEFAULT 'human',
    test_retry_count  INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE task_context (
    id              BIGSERIAL    PRIMARY KEY,
    task_id         BIGINT       NOT NULL REFERENCES sdlc_task(id) ON DELETE CASCADE,
    phase           VARCHAR(32)  NOT NULL,
    context_type    VARCHAR(32)  NOT NULL,
    content         TEXT         NOT NULL,
    token_estimate  INT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sdlc_task_jira       ON sdlc_task(jira_key);
CREATE INDEX idx_sdlc_task_repo       ON sdlc_task(repo_url);
CREATE INDEX idx_sdlc_task_status     ON sdlc_task(status);
CREATE INDEX idx_task_context_task    ON task_context(task_id);
CREATE INDEX idx_task_context_phase   ON task_context(task_id, phase);
```

### 3.3 Enumerations

**Task status** values:

`INTAKE`, `UNDERSTANDING`, `PLANNING`, `AWAITING_PLAN_APPROVAL`, `IMPLEMENTING`,
`AWAITING_CHANGE_APPROVAL`, `TESTING`, `FIXING`, `SUBMITTING`, `DONE`, `FAILED`,
`CANCELLED`

**Phase** values (for `task_context.phase`):

`INTAKE`, `UNDERSTAND`, `PLAN`, `IMPLEMENT`, `TEST`, `FIX`, `REVIEW`, `SUBMIT`

**Context type** values (for `task_context.context_type`):

| Type | Description | Typical producer |
|------|-------------|-----------------|
| STORY | Jira summary, description, acceptance criteria, labels | StoryParser |
| CODE_CONTEXT | Relevant symbols, file snippets, structural info | ContextBuilder |
| HISTORICAL | Condensed context from past tasks on same repo | TaskContextService |
| PLAN | Implementation plan generated by Copilot | Copilot (PLAN phase) |
| DIFF | Generated patches or file edits | Copilot (IMPLEMENT phase) |
| TEST_RESULT | Test pass/fail output | TestRunner |
| FIX_DIFF | Corrective patch for test failure | Copilot (FIX phase) |
| SELF_REVIEW | Copilot's self-review critique (auto mode) | Copilot (auto checkpoints) |
| PR_URL | GitHub PR URL | PrCreator |
| REPO_SUMMARY | Condensed knowledge base for a repo (cross-task) | Condensation job |

---

## 4. Phase-by-Phase Sequence Diagrams

### 4.1 Intake Phase

```mermaid
sequenceDiagram
    participant Scheduler
    participant JiraClient
    participant Jira as Jira Cloud
    participant StoryParser
    participant Orchestrator as SdlcOrchestrator
    participant DB as PostgreSQL

    Scheduler->>JiraClient: poll()
    JiraClient->>Jira: GET /rest/api/3/search<br/>jql=status="Ready for Dev"<br/>AND labels="auto-dev"
    Jira-->>JiraClient: issues JSON
    loop each new issue
        JiraClient->>StoryParser: parse(issueJson)
        StoryParser-->>JiraClient: StoryContext(summary, description, AC, labels, repoUrl)
        JiraClient->>Orchestrator: createTask(jiraKey, repoUrl, storyContext)
        Orchestrator->>DB: INSERT sdlc_task (status=INTAKE)
        Orchestrator->>DB: INSERT task_context (phase=INTAKE, type=STORY)
        Orchestrator->>JiraClient: transitionIssue(jiraKey, "In Progress")
        JiraClient->>Jira: POST /rest/api/3/issue/{key}/transitions
        Orchestrator->>Orchestrator: transition(INTAKE -> UNDERSTANDING)
    end
```

### 4.2 Understand Phase

```mermaid
sequenceDiagram
    participant Orchestrator as SdlcOrchestrator
    participant Ingestion as IngestionService
    participant Query as QueryService
    participant Ask as AskQuestionService
    participant CtxBuilder as ContextBuilder
    participant CtxSvc as TaskContextService
    participant DB as PostgreSQL

    Orchestrator->>CtxSvc: getContext(taskId, INTAKE, STORY)
    CtxSvc-->>Orchestrator: storyContext

    Orchestrator->>Ingestion: analyzeRepository(repoUrl, ref)
    Ingestion-->>Orchestrator: snapshotId
    Orchestrator->>DB: UPDATE sdlc_task SET snapshot_id

    Orchestrator->>CtxBuilder: buildContext(snapshotId, storyContext)
    CtxBuilder->>CtxBuilder: extractKeywords(storyContext)
    CtxBuilder->>Query: searchSymbols(snapshotId, keywords)
    Query-->>CtxBuilder: matching symbols
    CtxBuilder->>Ask: askQuestion(snapshotId, storyContext.summary)
    Ask-->>CtxBuilder: relevant code chunks
    CtxBuilder->>CtxSvc: loadHistorical(repoUrl)
    CtxSvc->>DB: SELECT past PLAN + REVIEW context<br/>WHERE repo_url = repoUrl AND status = DONE
    DB-->>CtxSvc: historical entries
    CtxSvc-->>CtxBuilder: historicalContext

    CtxBuilder-->>Orchestrator: codeContext + historicalContext
    Orchestrator->>CtxSvc: save(taskId, UNDERSTAND, CODE_CONTEXT, codeContext)
    Orchestrator->>CtxSvc: save(taskId, UNDERSTAND, HISTORICAL, historicalContext)
    Orchestrator->>Orchestrator: transition(UNDERSTANDING -> PLANNING)
```

### 4.3 Plan Phase

```mermaid
sequenceDiagram
    participant Orchestrator as SdlcOrchestrator
    participant CtxSvc as TaskContextService
    participant Assembler as PromptAssembler
    participant Bridge as CopilotBridge
    participant Copilot as Copilot ACP

    Orchestrator->>CtxSvc: getAllContext(taskId)
    CtxSvc-->>Orchestrator: [STORY, CODE_CONTEXT, HISTORICAL]
    Orchestrator->>Assembler: assemblePlanPrompt(allContext)
    Assembler-->>Orchestrator: systemPrompt + userPrompt (token-budgeted)

    Orchestrator->>Bridge: prompt(systemPrompt, userPrompt)
    Bridge->>Copilot: JSON-RPC request
    Copilot-->>Bridge: implementation plan (text)
    Bridge-->>Orchestrator: CopilotResponse(plan)

    Orchestrator->>CtxSvc: save(taskId, PLAN, PLAN, plan)

    alt human mode
        Orchestrator->>Orchestrator: transition(PLANNING -> AWAITING_PLAN_APPROVAL)
        Note over Orchestrator: Paused. Waiting for<br/>POST /api/sdlc/tasks/{id}/approve-plan
    else auto mode
        Orchestrator->>Bridge: prompt(selfReviewPrompt, plan)
        Bridge->>Copilot: "Review this plan critically..."
        Copilot-->>Bridge: review feedback
        Bridge-->>Orchestrator: CopilotResponse(review)
        Orchestrator->>CtxSvc: save(taskId, PLAN, SELF_REVIEW, review)
        Orchestrator->>Orchestrator: transition(PLANNING -> IMPLEMENTING)
    end
```

### 4.4 Implement Phase

```mermaid
sequenceDiagram
    participant Orchestrator as SdlcOrchestrator
    participant CtxSvc as TaskContextService
    participant Assembler as PromptAssembler
    participant Bridge as CopilotBridge
    participant Copilot as Copilot ACP
    participant Patcher as PatchApplier
    participant FS as Working Tree

    Orchestrator->>CtxSvc: getContext(taskId, [STORY, CODE_CONTEXT, PLAN])
    CtxSvc-->>Orchestrator: context bundle
    Orchestrator->>Assembler: assembleImplementPrompt(contextBundle)
    Assembler-->>Orchestrator: systemPrompt + userPrompt

    Orchestrator->>Bridge: planAndEdit(systemPrompt, userPrompt)
    Bridge->>Copilot: JSON-RPC request (with shell/file tools allowed)
    Copilot-->>Bridge: diffs / file edits
    Bridge-->>Orchestrator: CopilotResponse(diffs)

    Orchestrator->>CtxSvc: save(taskId, IMPLEMENT, DIFF, diffs)
    Orchestrator->>Patcher: apply(workingTreePath, diffs)
    Patcher->>FS: write modified files
    Patcher-->>Orchestrator: ApplyResult(filesChanged, errors)

    alt human mode
        Orchestrator->>Orchestrator: transition(IMPLEMENTING -> AWAITING_CHANGE_APPROVAL)
    else auto mode
        Orchestrator->>Bridge: prompt(codeReviewPrompt, diffs)
        Copilot-->>Bridge: review feedback
        Orchestrator->>CtxSvc: save(taskId, IMPLEMENT, SELF_REVIEW, review)
        Orchestrator->>Orchestrator: transition(IMPLEMENTING -> TESTING)
    end
```

### 4.5 Test and Fix Loop

```mermaid
sequenceDiagram
    participant Orchestrator as SdlcOrchestrator
    participant Runner as TestRunner
    participant Shell as Process
    participant CtxSvc as TaskContextService
    participant Assembler as PromptAssembler
    participant Bridge as CopilotBridge
    participant Copilot as Copilot ACP
    participant Patcher as PatchApplier

    Orchestrator->>Runner: runTests(workingTreePath, testCommand)
    Runner->>Shell: mvn verify (or configured command)
    Shell-->>Runner: exit code + stdout/stderr
    Runner-->>Orchestrator: TestResult(passed, output)

    Orchestrator->>CtxSvc: save(taskId, TEST, TEST_RESULT, output)

    alt tests pass
        Orchestrator->>Orchestrator: transition(TESTING -> SUBMITTING)
    else tests fail AND retryCount < maxRetries
        Orchestrator->>Orchestrator: transition(TESTING -> FIXING)
        Orchestrator->>CtxSvc: getContext(taskId, [PLAN, DIFF, TEST_RESULT])
        CtxSvc-->>Orchestrator: context bundle
        Orchestrator->>Assembler: assembleFixPrompt(contextBundle)
        Assembler-->>Orchestrator: systemPrompt + userPrompt
        Orchestrator->>Bridge: planAndEdit(systemPrompt, userPrompt)
        Bridge->>Copilot: JSON-RPC request
        Copilot-->>Bridge: fix diffs
        Bridge-->>Orchestrator: CopilotResponse(fixDiffs)
        Orchestrator->>CtxSvc: save(taskId, FIX, FIX_DIFF, fixDiffs)
        Orchestrator->>Patcher: apply(workingTreePath, fixDiffs)
        Orchestrator->>Orchestrator: incrementRetryCount
        Orchestrator->>Orchestrator: transition(FIXING -> TESTING)
    else tests fail AND retryCount >= maxRetries
        Orchestrator->>Orchestrator: transition(TESTING -> FAILED)
        Orchestrator->>JiraClient: postComment(jiraKey, "Tests failed after N retries")
    end
```

### 4.6 Submit Phase

```mermaid
sequenceDiagram
    participant Orchestrator as SdlcOrchestrator
    participant GitOps as GitOpsService
    participant Git as JGit
    participant PrCreator
    participant GH as gh CLI
    participant CtxSvc as TaskContextService
    participant JiraClient
    participant Jira as Jira Cloud

    Orchestrator->>GitOps: createBranchAndCommit(workingTree, branchName, commitMsg)
    GitOps->>Git: checkout -b auto/PROJ-1234
    GitOps->>Git: add -A
    GitOps->>Git: commit -m "PROJ-1234: Add email validation"
    GitOps->>Git: push -u origin auto/PROJ-1234
    GitOps-->>Orchestrator: PushResult(ok)

    Orchestrator->>CtxSvc: getContext(taskId, [STORY, PLAN, TEST_RESULT])
    CtxSvc-->>Orchestrator: context for PR body
    Orchestrator->>PrCreator: createPr(branchName, prBody)
    PrCreator->>GH: gh pr create --title "..." --body "..."
    GH-->>PrCreator: PR URL
    PrCreator-->>Orchestrator: prUrl

    Orchestrator->>CtxSvc: save(taskId, SUBMIT, PR_URL, prUrl)
    Orchestrator->>JiraClient: transitionIssue(jiraKey, "In Review")
    JiraClient->>Jira: POST transitions
    Orchestrator->>JiraClient: postComment(jiraKey, "PR opened: <prUrl>")
    JiraClient->>Jira: POST comment
    Orchestrator->>Orchestrator: transition(SUBMITTING -> DONE)
```

---

## 5. Context Memory Deep Dive

### 5.1 Layered Prompt Construction

Every Copilot CLI invocation receives a prompt assembled from multiple layers.
The `PromptAssembler` fills layers in priority order within a configurable token budget.

```mermaid
flowchart TB
    subgraph budget ["Token Budget (e.g. 100K)"]
        direction TB
        L1["Layer 1: Static Instructions<br/>(.github/copilot-instructions.md)<br/>~2K tokens — always included"]
        L2["Layer 2: Story Context<br/>(Jira summary + AC)<br/>~1-3K tokens — always included"]
        L3["Layer 3: Phase-Specific Instruction<br/>(plan/implement/fix prompt template)<br/>~500 tokens — always included"]
        L4["Layer 4: Code Context<br/>(symbols, chunks, file contents)<br/>~20-60K tokens — truncated to fit"]
        L5["Layer 5: Prior Phase Output<br/>(plan, diffs, test results)<br/>~5-20K tokens — truncated to fit"]
        L6["Layer 6: Historical Context<br/>(past tasks, repo summary)<br/>~2-10K tokens — best-effort"]
    end

    L1 --> L2 --> L3 --> L4 --> L5 --> L6
```

**Token budget algorithm:**

1. Reserve space for layers 1-3 (fixed, small).
2. Allocate remaining budget to layers 4-6 in a 60/25/15 ratio.
3. If a layer's content exceeds its allocation, truncate by relevance ranking
   (most relevant symbols/chunks first; most recent historical entries first).
4. `task_context.token_estimate` is computed on write using a simple heuristic
   (character count / 4) to avoid re-tokenizing on every prompt assembly.

### 5.2 Cross-Task Memory Flow

The historical context mechanism allows the orchestrator to learn from past tasks.

```mermaid
flowchart TB
    subgraph pastTasks [Completed Tasks]
        T1[Task A<br/>DONE]
        T2[Task B<br/>DONE]
        T3[Task C<br/>DONE]
    end

    subgraph condensation [Condensation Job]
        Query[Query past PLAN + REVIEW<br/>entries for repo X]
        Feed[Feed to Copilot CLI:<br/>'Summarize patterns and<br/>conventions from these plans']
        Store[Store as REPO_SUMMARY<br/>in task_context]
    end

    subgraph newTask [New Task D]
        Load[ContextBuilder loads<br/>REPO_SUMMARY + recent PLANs]
        Inject[PromptAssembler injects<br/>into Layer 6]
        Plan[Copilot plans with<br/>historical awareness]
    end

    T1 -->|PLAN + REVIEW| Query
    T2 -->|PLAN + REVIEW| Query
    T3 -->|PLAN + REVIEW| Query
    Query --> Feed
    Feed --> Store

    Store --> Load
    Load --> Inject
    Inject --> Plan
```

### 5.3 Context Condensation Sequence

A scheduled job (e.g. nightly) condenses accumulated context into a compact repo
knowledge base.

```mermaid
sequenceDiagram
    participant Job as CondensationJob
    participant CtxSvc as TaskContextService
    participant DB as PostgreSQL
    participant Assembler as PromptAssembler
    participant Bridge as CopilotBridge
    participant Copilot as Copilot ACP

    Job->>CtxSvc: findReposWithNewCompletedTasks(since=lastRun)
    CtxSvc->>DB: SELECT DISTINCT repo_url FROM sdlc_task<br/>WHERE status='DONE' AND updated_at > lastRun
    DB-->>CtxSvc: [repoA, repoB]

    loop each repo
        Job->>CtxSvc: loadRecentContext(repoUrl, types=[PLAN, REVIEW, SELF_REVIEW], limit=20)
        CtxSvc-->>Job: contextEntries

        Job->>CtxSvc: loadExistingRepoSummary(repoUrl)
        CtxSvc-->>Job: previousSummary (or null)

        Job->>Assembler: assembleCondensationPrompt(contextEntries, previousSummary)
        Assembler-->>Job: prompt

        Job->>Bridge: prompt(systemPrompt, condensationPrompt)
        Bridge->>Copilot: JSON-RPC
        Copilot-->>Bridge: condensed summary
        Bridge-->>Job: CopilotResponse(summary)

        Job->>CtxSvc: upsertRepoSummary(repoUrl, summary)
        CtxSvc->>DB: INSERT/UPDATE task_context<br/>(context_type=REPO_SUMMARY)
    end
```

### 5.4 Context Type Reference

| Type | Producer | Consumer phases | Retention |
|------|----------|----------------|-----------|
| STORY | StoryParser (INTAKE) | UNDERSTAND, PLAN, IMPLEMENT, SUBMIT | Permanent |
| CODE_CONTEXT | ContextBuilder (UNDERSTAND) | PLAN, IMPLEMENT | Permanent |
| HISTORICAL | TaskContextService (UNDERSTAND) | PLAN | Refreshed per task |
| PLAN | Copilot (PLAN) | IMPLEMENT, TEST, FIX, SUBMIT | Permanent |
| DIFF | Copilot (IMPLEMENT) | TEST, FIX, SUBMIT | Permanent |
| TEST_RESULT | TestRunner (TEST) | FIX, SUBMIT | Permanent |
| FIX_DIFF | Copilot (FIX) | TEST (retry) | Permanent |
| SELF_REVIEW | Copilot (auto checkpoints) | Next phase | Permanent |
| PR_URL | PrCreator (SUBMIT) | -- | Permanent |
| REPO_SUMMARY | CondensationJob | UNDERSTAND (next task) | Updated nightly |

---

## 6. Copilot CLI Integration Detail

### 6.1 ACP Server Lifecycle

The orchestrator manages the Copilot CLI process as an ACP server.

```mermaid
stateDiagram-v2
    [*] --> STARTING : orchestrator boot

    STARTING --> HEALTHY : ACP responds to healthcheck
    STARTING --> UNHEALTHY : timeout after 30s

    HEALTHY --> HEALTHY : periodic healthcheck (every 30s)
    HEALTHY --> UNHEALTHY : healthcheck fails

    UNHEALTHY --> RESTARTING : restart attempt
    RESTARTING --> STARTING : new process spawned
    RESTARTING --> DEAD : max restart attempts exceeded

    DEAD --> [*] : tasks marked BLOCKED
```

**Startup command:**

```
copilot --acp --port 3000 \
  --allow-tool='shell(mvn,gradle,npm,git)' \
  --deny-tool='shell(rm,curl,wget)'
```

### 6.2 ACP Request/Response Cycle

```mermaid
sequenceDiagram
    participant Bridge as AcpCopilotBridge
    participant ACP as Copilot ACP Server
    participant Timer as Timeout Timer

    Bridge->>Timer: start(120s)
    Bridge->>ACP: POST /jsonrpc<br/>{"method":"prompt","params":{...}}

    alt success within timeout
        ACP-->>Bridge: {"result":{"text":"...","diffs":[...]}}
        Timer-->>Bridge: cancel
        Bridge->>Bridge: parse response
    else timeout
        Timer-->>Bridge: TIMEOUT
        Bridge->>Bridge: retry (attempt 2/3)
        Bridge->>ACP: POST /jsonrpc (retry)
        alt retry success
            ACP-->>Bridge: response
        else all retries exhausted
            Bridge-->>Bridge: throw CopilotUnavailableException
        end
    else ACP error
        ACP-->>Bridge: {"error":{"code":-1,"message":"..."}}
        Bridge->>Bridge: log error, throw CopilotException
    end
```

### 6.3 Bridge Interface and Implementations

```java
public interface CopilotBridge {

    CopilotResponse prompt(String systemPrompt, String userPrompt,
                           Set<String> allowedTools);

    CopilotResponse planAndEdit(String systemPrompt, String userPrompt);

    boolean isHealthy();
}
```

```java
public class AcpCopilotBridge implements CopilotBridge {
    // Talks to Copilot CLI running as ACP server via JSON-RPC over HTTP.
    // Preferred for production: persistent process, structured I/O.
    private final RestClient restClient;
    private final int port;
    private final Duration timeout;
    // ...
}
```

```java
public class CliCopilotBridge implements CopilotBridge {
    // Shells out to `copilot -p "..." --allow-all-tools`.
    // Simpler, for local dev/testing. New process per invocation.
    // Parses stdout for text and diffs.
    // ...
}
```

### 6.4 Prompt Templates

**Plan phase:**

```
SYSTEM:
You are an expert software engineer. You are given a Jira story and relevant code
from the repository. Create a detailed, step-by-step implementation plan.

Include:
- Which files to modify or create
- What changes to make in each file
- What tests to add or update
- Any migration or configuration changes

Respect the project conventions described below.

<project-conventions>
{staticInstructions}
</project-conventions>

USER:
## Jira Story
{storySummary}

## Acceptance Criteria
{acceptanceCriteria}

## Relevant Code
{codeContext}

## Historical Patterns (from past tasks on this repo)
{historicalContext}

## Task
Create an implementation plan for the story above.
```

**Implement phase:**

```
SYSTEM:
You are an expert software engineer. Implement the following plan by generating
the exact code changes needed. Output each change as a unified diff.

USER:
## Plan
{plan}

## Current Code (files to modify)
{fileContents}

## Task
Generate unified diffs for all files that need to change.
Output ONLY the diffs, no explanation.
```

**Fix phase:**

```
SYSTEM:
You are an expert software engineer. Tests are failing after the recent changes.
Analyze the test output and fix the code.

USER:
## Implementation Plan
{plan}

## Changes Made
{diffs}

## Test Failure Output
{testOutput}

## Task
Fix the failing tests. Output unified diffs for the corrected files.
```

**Self-review (auto mode):**

```
SYSTEM:
You are a senior code reviewer. Critically review the following plan/code.
Flag any issues: missing edge cases, incorrect logic, security concerns,
convention violations, missing tests.

If the plan/code is acceptable, respond with "APPROVED".
If changes are needed, respond with "REVISE:" followed by specific feedback.

USER:
{planOrDiffs}
```

---

## 7. Jira Integration Detail

### 7.1 Polling and Extraction Sequence

```mermaid
sequenceDiagram
    participant Cron as Scheduler
    participant Client as JiraClient
    participant Jira as Jira Cloud API
    participant Parser as StoryParser
    participant Orchestrator as SdlcOrchestrator

    Cron->>Client: poll()
    Client->>Jira: GET /rest/api/3/search?jql=...<br/>fields=summary,description,labels,<br/>components,issuelinks,customfield_repo
    Jira-->>Client: {"issues":[...]}

    loop each issue not already tracked
        Client->>Client: deduplicate(jiraKey)
        Client->>Parser: parse(issueJson)
        Parser->>Parser: extractSummary()
        Parser->>Parser: extractAcceptanceCriteria()
        Parser->>Parser: extractLabels()
        Parser->>Parser: resolveRepoUrl()
        Parser-->>Client: StoryContext

        Client->>Orchestrator: createTask(jiraKey, repoUrl, storyContext)

        Client->>Jira: POST /rest/api/3/issue/{key}/transitions<br/>{"transition":{"id":"inProgressId"}}
        Client->>Jira: POST /rest/api/3/issue/{key}/comment<br/>{"body":"Orchestrator picked up this story."}
    end
```

### 7.2 Jira Field Mapping

| Jira field | Maps to | Notes |
|------------|---------|-------|
| `summary` | `StoryContext.summary` | Short description of the story |
| `description` | `StoryContext.description` | Full body; may contain AC |
| `description` (parsed) | `StoryContext.acceptanceCriteria` | Extract lines after "Acceptance Criteria" heading |
| `labels` | `StoryContext.labels` | Used for routing and context |
| `components` | `StoryContext.components` | Maps to code areas |
| `issuelinks` | `StoryContext.linkedIssues` | Related stories, blockers |
| `customfield_10100` (example) | `StoryContext.repoUrl` | Custom field for repo URL; alternatively mapped from project key in config |

### 7.3 Jira Workflow Mapping

```mermaid
stateDiagram-v2
    direction LR

    ReadyForDev : Ready for Dev
    InProgress : In Progress
    InReview : In Review
    Done : Done

    [*] --> ReadyForDev : story groomed

    ReadyForDev --> InProgress : orchestrator picks up<br/>(INTAKE phase)
    InProgress --> InProgress : understanding, planning,<br/>implementing, testing
    InProgress --> InReview : PR opened<br/>(SUBMIT phase)
    InReview --> Done : PR merged<br/>(external trigger)
    InProgress --> ReadyForDev : task FAILED<br/>(returned to backlog)
```

The orchestrator triggers transitions at two points:
1. **Ready for Dev -> In Progress**: when the task enters INTAKE.
2. **In Progress -> In Review**: when the PR is opened (SUBMITTING -> DONE).

The **In Review -> Done** transition happens outside the orchestrator (when the PR
is merged by a human reviewer). Optionally, a GitHub webhook can trigger this.

---

## 8. Git and PR Workflow

### 8.1 Branch, Commit, Push, PR

```mermaid
sequenceDiagram
    participant Orchestrator
    participant GitOps as GitOpsService
    participant JGit
    participant FS as Working Tree
    participant GH as gh CLI
    participant GitHub

    Orchestrator->>GitOps: prepareAndPush(task)

    GitOps->>JGit: fetch origin main
    GitOps->>JGit: checkout -b auto/PROJ-1234 origin/main
    Note over GitOps: changes already applied<br/>to working tree by PatchApplier

    GitOps->>JGit: add(".")
    GitOps->>JGit: commit("PROJ-1234: Add email validation to signup")
    GitOps->>JGit: push origin auto/PROJ-1234
    GitOps-->>Orchestrator: PushResult(success, commitSha)

    Orchestrator->>GH: gh pr create<br/>--title "PROJ-1234: Add email validation"<br/>--body "<assembled PR body>"<br/>--base main<br/>--head auto/PROJ-1234
    GH->>GitHub: create PR
    GitHub-->>GH: PR URL
    GH-->>Orchestrator: prUrl
```

### 8.2 PR Body Template

The PR body is assembled from context:

```markdown
## PROJ-1234: Add email validation to signup

### Jira Story
[PROJ-1234](https://your-org.atlassian.net/browse/PROJ-1234)

{story.summary}

### Implementation Plan
{plan — condensed to first 500 words}

### Changes
{list of files changed with one-line description each}

### Test Results
All tests passing ({testResult.passCount} passed, 0 failed).
{if retries > 0: "Note: {retryCount} test fix iterations were needed."}

---
*This PR was generated by the SDLC Orchestrator.*
```

### 8.3 Conflict Handling

```mermaid
flowchart TB
    Push[git push] --> PushOK{Push<br/>succeeded?}
    PushOK -->|yes| CreatePR[Create PR]
    PushOK -->|no, rejected| Fetch[git fetch origin main]
    Fetch --> Rebase[git rebase origin/main]
    Rebase --> Conflict{Merge<br/>conflicts?}
    Conflict -->|no| RetryPush[git push]
    RetryPush --> CreatePR
    Conflict -->|yes| FeedCopilot[Feed conflict markers<br/>to Copilot CLI]
    FeedCopilot --> Resolve[Copilot generates<br/>resolution]
    Resolve --> Apply[Apply resolution]
    Apply --> ContinueRebase[git rebase --continue]
    ContinueRebase --> RetryPush2[git push]
    RetryPush2 --> PushOK2{Push<br/>succeeded?}
    PushOK2 -->|yes| CreatePR
    PushOK2 -->|no| Escalate[Mark FAILED<br/>notify human via Jira]
```

---

## 9. Package and Class Map

### 9.1 Class Diagram

```mermaid
classDiagram
    direction TB

    class SdlcOrchestrator {
        -SdlcTaskRepository taskRepo
        -TaskContextService ctxSvc
        -JiraClient jiraClient
        -ContextBuilder contextBuilder
        -PromptAssembler promptAssembler
        -CopilotBridge copilotBridge
        -PatchApplier patchApplier
        -TestRunner testRunner
        -GitOpsService gitOps
        -PrCreator prCreator
        +processTask(taskId) void
        +approveplan(taskId) void
        +approveChanges(taskId) void
        +retryTask(taskId) void
    end

    class TaskContextService {
        -TaskContextRepository repo
        +save(taskId, phase, type, content) void
        +getContext(taskId, phase, type) String
        +getAllContext(taskId) List
        +loadHistorical(repoUrl) List
        +upsertRepoSummary(repoUrl, summary) void
        +estimateTokens(content) int
    end

    class PromptAssembler {
        -TaskContextService ctxSvc
        -int maxPromptTokens
        +assemblePlanPrompt(taskId) PromptPair
        +assembleImplementPrompt(taskId) PromptPair
        +assembleFixPrompt(taskId) PromptPair
        +assembleReviewPrompt(taskId) PromptPair
        +assembleCondensationPrompt(entries) PromptPair
    }

    class CopilotBridge {
        <<interface>>
        +prompt(system, user, tools) CopilotResponse
        +planAndEdit(system, user) CopilotResponse
        +isHealthy() boolean
    }

    class AcpCopilotBridge {
        -RestClient restClient
        -int port
        -Duration timeout
    }

    class CliCopilotBridge {
        -String copilotPath
        -Duration timeout
    }

    class JiraClient {
        -RestClient restClient
        -String baseUrl
        -String apiToken
        +poll() List~StoryContext~
        +transitionIssue(key, status) void
        +postComment(key, body) void
    }

    class StoryParser {
        +parse(issueJson) StoryContext
    }

    class ContextBuilder {
        -QueryService queryService
        -AskQuestionService askService
        -TaskContextService ctxSvc
        +buildContext(snapshotId, story) CodeContext
    }

    class PatchApplier {
        +apply(workingTree, diffs) ApplyResult
    }

    class TestRunner {
        -String testCommand
        +runTests(workingTree) TestResult
    }

    class GitOpsService {
        +createBranchAndCommit(tree, branch, msg) PushResult
    }

    class PrCreator {
        +createPr(branch, body) String
    }

    CopilotBridge <|.. AcpCopilotBridge
    CopilotBridge <|.. CliCopilotBridge

    SdlcOrchestrator --> TaskContextService
    SdlcOrchestrator --> PromptAssembler
    SdlcOrchestrator --> CopilotBridge
    SdlcOrchestrator --> JiraClient
    SdlcOrchestrator --> ContextBuilder
    SdlcOrchestrator --> PatchApplier
    SdlcOrchestrator --> TestRunner
    SdlcOrchestrator --> GitOpsService
    SdlcOrchestrator --> PrCreator

    PromptAssembler --> TaskContextService
    ContextBuilder --> TaskContextService
    JiraClient --> StoryParser
```

### 9.2 Package Layout

```
com.vajrapulse.agents.codeanalyzer.sdlc/
├── SdlcOrchestrator.java          # state machine, drives task through phases
├── SdlcTask.java                   # entity: one SDLC task row
├── TaskContext.java                 # entity: one context entry row
├── SdlcTaskRepository.java         # JDBC repository for sdlc_task
├── TaskContextRepository.java      # JDBC repository for task_context
├── TaskContextService.java         # context CRUD, historical loading, token estimation
├── TaskStatus.java                 # enum: INTAKE, UNDERSTANDING, ..., DONE, FAILED
├── ContextType.java                # enum: STORY, CODE_CONTEXT, PLAN, DIFF, ...
├── Phase.java                      # enum: INTAKE, UNDERSTAND, PLAN, IMPLEMENT, ...
│
├── intake/
│   ├── JiraClient.java             # Jira REST API: poll, transition, comment
│   ├── StoryParser.java            # parse Jira issue JSON into StoryContext
│   └── StoryContext.java           # DTO: summary, description, AC, labels, repoUrl
│
├── copilot/
│   ├── CopilotBridge.java          # interface: prompt, planAndEdit, isHealthy
│   ├── AcpCopilotBridge.java       # impl: JSON-RPC over HTTP to ACP server
│   ├── CliCopilotBridge.java       # impl: shell out to `copilot -p`
│   ├── CopilotResponse.java        # DTO: text, diffs, error
│   ├── PromptAssembler.java        # build layered prompts with token budgeting
│   └── PromptPair.java             # DTO: systemPrompt + userPrompt
│
├── implement/
│   ├── PatchApplier.java           # apply unified diffs to working tree
│   ├── ApplyResult.java            # DTO: filesChanged, errors
│   ├── TestRunner.java             # execute test command, capture output
│   └── TestResult.java             # DTO: passed, exitCode, output
│
└── submit/
    ├── GitOpsService.java          # JGit: branch, commit, push
    ├── PushResult.java             # DTO: success, commitSha, errors
    └── PrCreator.java              # gh pr create, return PR URL
```

Additional files:

```
com.vajrapulse.agents.codeanalyzer.config/
└── SdlcConfig.java                 # @ConfigurationProperties for sdlc.*

com.vajrapulse.agents.codeanalyzer.mcp/
└── SdlcController.java             # REST endpoints: tasks, approve, retry
```

```
src/main/resources/
├── db/migration/
│   └── V4__sdlc_orchestrator_tables.sql
└── application-sdlc.yml            # SDLC-specific profile config
```

---

## 10. Configuration Reference

### 10.1 Full Configuration Block

```yaml
sdlc:
  enabled: false

  autonomy-mode: human              # human | auto

  jira:
    base-url: https://your-org.atlassian.net
    api-token: ${JIRA_API_TOKEN}
    username: ${JIRA_USERNAME}
    project-key: PROJ
    intake-status: "Ready for Dev"
    intake-label: auto-dev
    poll-interval-seconds: 60
    repo-url-field: customfield_10100   # Jira custom field for repo URL
    # Alternatively, static mapping:
    # repo-url-map:
    #   PROJ: https://github.com/org/repo

  copilot:
    mode: acp                        # acp | cli
    acp-port: 3000
    acp-startup-command: >-
      copilot --acp --port 3000
      --allow-tool='shell(mvn,gradle,npm,git)'
      --deny-tool='shell(rm,curl,wget)'
    timeout-seconds: 120
    max-retries: 3
    max-prompt-tokens: 100000

  test:
    command: "mvn verify"
    max-retries: 3
    timeout-seconds: 600

  git:
    branch-prefix: "auto/"
    commit-message-template: "{jiraKey}: {storySummary}"
    base-branch: main
    working-directory: ${java.io.tmpdir}/sdlc-workspaces

  condensation:
    enabled: true
    cron: "0 2 * * *"               # nightly at 2 AM
    max-entries-per-repo: 20
```

### 10.2 Spring Profile Strategy

The SDLC orchestrator is activated via the `sdlc` profile:

```
# Enable the orchestrator
SPRING_PROFILES_ACTIVE=demo-ollama,sdlc

# Or in application.yml:
spring:
  profiles:
    default: ${SPRING_PROFILES_DEFAULT:demo-ollama}
```

When `sdlc.enabled=false` (the default), all SDLC beans are excluded via
`@ConditionalOnProperty(name = "sdlc.enabled", havingValue = "true")`.
This ensures the existing code-analyzer functionality is completely unaffected.

---

## 11. Incremental Implementation Roadmap

### 11.1 Timeline

```mermaid
gantt
    title SDLC Orchestrator — 10-Week Roadmap
    dateFormat  YYYY-MM-DD
    axisFormat  %b %d

    section Foundation
    Inc 0 Data Model + Lifecycle  :inc0, 2026-03-16, 7d

    section Integrations
    Inc 1 Copilot CLI Bridge      :inc1, after inc0, 7d
    Inc 2 Jira Integration        :inc2, after inc0, 7d

    section Core Phases
    Inc 3 Understand Phase        :inc3, after inc1, 7d
    Inc 4 Plan Phase              :inc4, after inc3, 7d
    Inc 5 Implement Phase         :inc5, after inc4, 7d

    section Test and Ship
    Inc 6 Test + Fix Loop         :inc6, after inc5, 7d
    Inc 7 PR Submission           :inc7, after inc6, 7d

    section Memory and Polish
    Inc 8 Cross-Task Memory       :inc8, after inc7, 7d
    Inc 9 Polish + Hardening      :inc9, after inc8, 7d
```

Note: Increments 1 and 2 can run in parallel since they are independent.

### 11.2 Per-Increment Detail

#### Increment 0: Data Model and Task Lifecycle

| Attribute | Detail |
|-----------|--------|
| **Scope** | Flyway migration, entity classes, repositories, state machine skeleton |
| **New files** | `V4__sdlc_orchestrator_tables.sql`, `SdlcTask.java`, `TaskContext.java`, `SdlcTaskRepository.java`, `TaskContextRepository.java`, `TaskContextService.java`, `SdlcOrchestrator.java` (skeleton), `TaskStatus.java`, `ContextType.java`, `Phase.java` |
| **Tests** | `SdlcTaskRepositorySpec.groovy`, `TaskContextRepositorySpec.groovy`, `TaskContextServiceSpec.groovy`, `SdlcOrchestratorSpec.groovy` (state transitions only) |
| **Definition of done** | Can create a task, write/read context entries, transition through all states; all unit tests pass |
| **Depends on** | None |

#### Increment 1: Copilot CLI Bridge

| Attribute | Detail |
|-----------|--------|
| **Scope** | CopilotBridge interface, ACP and CLI implementations, PromptAssembler with token budgeting |
| **New files** | `CopilotBridge.java`, `AcpCopilotBridge.java`, `CliCopilotBridge.java`, `CopilotResponse.java`, `PromptAssembler.java`, `PromptPair.java` |
| **Tests** | `CliCopilotBridgeSpec.groovy` (mock process), `PromptAssemblerSpec.groovy` (token budget, layer truncation) |
| **Definition of done** | Can send a prompt to Copilot CLI (via ACP or shell) and receive a response; PromptAssembler correctly truncates within budget |
| **Depends on** | Increment 0 (for TaskContextService used by PromptAssembler) |

#### Increment 2: Jira Integration

| Attribute | Detail |
|-----------|--------|
| **Scope** | JiraClient, StoryParser, INTAKE phase wiring |
| **New files** | `JiraClient.java`, `StoryParser.java`, `StoryContext.java`, `SdlcConfig.java` (Jira properties) |
| **Tests** | `JiraClientSpec.groovy` (mock HTTP), `StoryParserSpec.groovy` (sample JSON), `SdlcOrchestratorIntakeSpec.groovy` |
| **Definition of done** | Scheduler polls Jira, creates tasks, stores STORY context, transitions Jira status |
| **Depends on** | Increment 0 |

#### Increment 3: Understand Phase

| Attribute | Detail |
|-----------|--------|
| **Scope** | ContextBuilder, historical context loading, UNDERSTAND phase wiring |
| **New files** | `ContextBuilder.java` |
| **Tests** | `ContextBuilderSpec.groovy`, `SdlcOrchestratorUnderstandSpec.groovy` |
| **Definition of done** | Given a task with STORY context, the orchestrator analyzes the repo and produces CODE_CONTEXT + HISTORICAL entries |
| **Depends on** | Increment 0, Increment 2 (for StoryContext) |

#### Increment 4: Plan Phase

| Attribute | Detail |
|-----------|--------|
| **Scope** | PLAN phase wiring, checkpoint REST endpoint, auto-mode self-review |
| **New files** | `SdlcController.java` (approve-plan endpoint) |
| **Tests** | `SdlcOrchestratorPlanSpec.groovy`, `SdlcControllerSpec.groovy` |
| **Definition of done** | Copilot generates a plan; human can approve/revise/reject via REST; auto mode self-reviews and proceeds |
| **Depends on** | Increments 0, 1, 3 |

#### Increment 5: Implement Phase

| Attribute | Detail |
|-----------|--------|
| **Scope** | IMPLEMENT phase wiring, PatchApplier, checkpoint for changes |
| **New files** | `PatchApplier.java`, `ApplyResult.java` |
| **Tests** | `PatchApplierSpec.groovy`, `SdlcOrchestratorImplementSpec.groovy` |
| **Definition of done** | Copilot generates diffs; PatchApplier applies them; human can approve/revise via REST |
| **Depends on** | Increments 0, 1, 4 |

#### Increment 6: Test and Fix Loop

| Attribute | Detail |
|-----------|--------|
| **Scope** | TestRunner, TESTING/FIXING states, retry logic, escalation |
| **New files** | `TestRunner.java`, `TestResult.java` |
| **Tests** | `TestRunnerSpec.groovy`, `SdlcOrchestratorTestFixSpec.groovy` (mock test failures, verify retry loop) |
| **Definition of done** | Orchestrator runs tests; on failure, asks Copilot to fix; retries up to max; escalates on exhaustion |
| **Depends on** | Increments 0, 1, 5 |

#### Increment 7: PR Submission and Jira Closure

| Attribute | Detail |
|-----------|--------|
| **Scope** | GitOpsService, PrCreator, SUBMITTING phase, Jira status update and comment |
| **New files** | `GitOpsService.java`, `PushResult.java`, `PrCreator.java` |
| **Tests** | `GitOpsServiceSpec.groovy`, `PrCreatorSpec.groovy`, `SdlcOrchestratorSubmitSpec.groovy` |
| **Definition of done** | End-to-end: task goes from TESTING (passed) through branch/commit/push/PR/Jira update to DONE |
| **Depends on** | Increments 0, 2, 6 |

#### Increment 8: Cross-Task Memory

| Attribute | Detail |
|-----------|--------|
| **Scope** | Historical context queries, condensation job, REPO_SUMMARY |
| **New files** | `CondensationJob.java` |
| **Tests** | `CondensationJobSpec.groovy`, integration test: two tasks on same repo, second task gets historical context |
| **Definition of done** | Condensation job produces a REPO_SUMMARY; new tasks include it in their PLAN phase prompt |
| **Depends on** | Increments 0, 1, 4 |

#### Increment 9: Polish and Hardening

| Attribute | Detail |
|-----------|--------|
| **Scope** | Full REST API (list/get/cancel/retry tasks), observability, error handling, docs |
| **New files** | Extended `SdlcController.java`, `application-sdlc.yml` |
| **Tests** | `SdlcControllerFullSpec.groovy`, error-scenario tests |
| **Definition of done** | REST API documented; structured logging with task correlation IDs; metrics (task duration, retry count, Copilot latency); graceful error handling for all external system failures |
| **Depends on** | All prior increments |

### 11.3 Dependency Graph

```mermaid
flowchart TB
    Inc0[Inc 0<br/>Data Model]
    Inc1[Inc 1<br/>Copilot Bridge]
    Inc2[Inc 2<br/>Jira]
    Inc3[Inc 3<br/>Understand]
    Inc4[Inc 4<br/>Plan]
    Inc5[Inc 5<br/>Implement]
    Inc6[Inc 6<br/>Test + Fix]
    Inc7[Inc 7<br/>PR Submit]
    Inc8[Inc 8<br/>Memory]
    Inc9[Inc 9<br/>Polish]

    Inc0 --> Inc1
    Inc0 --> Inc2
    Inc1 --> Inc3
    Inc2 --> Inc3
    Inc3 --> Inc4
    Inc4 --> Inc5
    Inc5 --> Inc6
    Inc6 --> Inc7
    Inc2 --> Inc7
    Inc1 --> Inc8
    Inc4 --> Inc8
    Inc7 --> Inc9
    Inc8 --> Inc9
```

---

## 12. Risks, Open Questions, and Decisions

### 12.1 Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Copilot context window too small for large codebases | High | High | PromptAssembler token budgeting; aggressive ContextBuilder ranking; chunk large files |
| Copilot generates code that doesn't compile | Medium | Medium | Test-and-fix loop with retries; escalation to human |
| Copilot ACP server crashes or hangs | Medium | Medium | Healthcheck + auto-restart; timeout per request; circuit breaker |
| Jira API rate limiting | Low | Low | Configurable poll interval; exponential backoff; switch to webhooks |
| Git merge conflicts on push | Medium | Medium | Rebase before push; feed conflicts to Copilot; escalate if unresolved |
| Jira story is too vague for Copilot to plan | Medium | High | Validate AC presence at intake; post Jira comment asking for clarification; mark BLOCKED |
| Copilot subscription cost at scale | Low | Medium | Token budgeting reduces prompt size; batch small stories; cost monitoring |
| Security: secrets in generated code or context | Low | High | Pre-commit scan for secrets; never store credentials in task_context; redact in prompts |
| Copilot CLI version drift / breaking changes | Medium | Medium | Pin CLI version; integration test suite catches regressions; fallback to CliCopilotBridge |
| Historical context becomes stale or misleading | Low | Medium | 28-day retention on REPO_SUMMARY; re-condense periodically; human can clear |

### 12.2 Open Questions

| # | Question | Options | Recommendation |
|---|----------|---------|---------------|
| 1 | Should the orchestrator clone repos into a temp directory or reuse existing clones? | Temp dir (clean each time) vs persistent workspace | Temp dir for isolation; cache `.git` for speed |
| 2 | How to handle Jira stories that span multiple repos? | One task per repo (split) vs one task with multiple repos | Start with one task per repo; consider multi-repo in a future increment |
| 3 | Should the orchestrator support non-Java projects (Python, TypeScript)? | Java-only parser vs multi-language | Current code-analyzer only parses Java; Copilot CLI is language-agnostic; orchestrator is language-neutral; parser coverage is the bottleneck |
| 4 | ACP vs plain CLI invocation as the default? | ACP (persistent, structured) vs CLI (simpler, new process each time) | ACP for production; CLI as fallback and for dev/testing |
| 5 | How to handle stories that require database migrations? | Detect migration need from plan; generate Flyway SQL | Let Copilot generate migration files as part of implementation; TestRunner validates them |
| 6 | Should the self-review in auto mode have a confidence threshold? | Binary (APPROVED/REVISE) vs scored (confidence 0-1) | Start with binary; add scoring later if false-positive rate is too high |
| 7 | Where do working trees live during implementation? | System temp dir vs configurable directory | Configurable via `sdlc.git.working-directory`; defaults to temp |

### 12.3 Decisions Already Made

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Use Copilot CLI (not Copilot Coding Agent) for implementation | CLI runs locally where the orchestrator has full control over context and working tree; Coding Agent runs on GitHub infra with less control |
| D2 | ACP server mode as primary integration | Persistent process avoids startup latency; structured JSON-RPC is more reliable than stdout parsing |
| D3 | Custom context store (PostgreSQL) as primary memory | Copilot's built-in Memory is implicit and not queryable; our store is explicit, structured, permanent, and token-budgetable |
| D4 | Human-in-the-loop as default autonomy mode | Safety first; autonomous mode is opt-in after trust is established |
| D5 | Extend existing code-analyzer (not a new service) | Reuse existing ingest, query, and embedding infrastructure; single deployment |
| D6 | Configurable per-task autonomy | Different stories have different risk profiles; allow overriding globally or per-task |
| D7 | Spring profile (`sdlc`) to isolate the feature | Zero impact on existing code-analyzer users; feature-flagged with `sdlc.enabled` |

---

## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| **ACP** | Agent Client Protocol; a JSON-RPC interface for communicating with Copilot CLI as a server |
| **Autonomy mode** | Whether the orchestrator pauses for human approval (`human`) or proceeds automatically (`auto`) |
| **Checkpoint** | A point in the lifecycle where the task pauses in human mode, awaiting approval via REST API |
| **Condensation** | The process of summarizing past task context into a compact REPO_SUMMARY for future tasks |
| **Context entry** | A row in `task_context`; one piece of context produced by a phase for consumption by later phases |
| **Copilot CLI** | GitHub's standalone AI coding assistant, invoked as `copilot` or via ACP |
| **Phase** | A stage of the SDLC task lifecycle (INTAKE, UNDERSTAND, PLAN, IMPLEMENT, TEST, FIX, SUBMIT) |
| **REPO_SUMMARY** | A condensed knowledge base for a repository, built from past tasks' plans and reviews |
| **Self-review** | In auto mode, a Copilot prompt that critiques its own plan or code before proceeding |
| **Token budget** | The maximum number of tokens the PromptAssembler allocates across all prompt layers |

## Appendix B: Related Documents

| Document | Relation |
|----------|----------|
| [13-sdlc-agent-integration.md](13-sdlc-agent-integration.md) | Predecessor: describes the code-analyzer as a read-only MCP in a larger SDLC agent |
| [14-copilot-cli-orchestration-design.md](14-copilot-cli-orchestration-design.md) | Predecessor: initial design for delegating AI to Copilot CLI; this document expands it into a full SDLC orchestrator |
| [03-architecture.md](03-architecture.md) | Existing architecture of the code-analyzer; the orchestrator builds on top of this |
| [08-database-schema-and-relationships.md](08-database-schema-and-relationships.md) | Existing schema; Section 3 of this document extends it with `sdlc_task` and `task_context` |
