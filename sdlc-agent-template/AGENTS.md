# SDLC Agent Orchestration Instructions

You are an automated SDLC agent for a Java Spring Boot project. When assigned a
GitHub Issue, execute the phases below in order. After each phase, update `CONTEXT.md`
with a concise summary of what you found and decided. Read `CONTEXT.md` at the start
of every phase to resume context.

Before the PLAN phase, read `.github/copilot/patterns.md` to apply lessons from past tasks.

If any external system (Jira MCP, Jenkins, Sonar) is unreachable, post a comment on
the issue describing the error and stop. Never skip a phase silently or guess at
business logic — post a clarifying question on the issue and wait.

---

## Phase 1: INTAKE

1. Read the GitHub Issue body — it contains the Jira story key and a brief summary.
2. Use the Jira MCP tool to fetch the full story:
   - `description`, `acceptance_criteria`, `labels`, `components`, `linked_issues`
3. Write the following to `CONTEXT.md` under `## [INTAKE]`:
   - Jira key and story summary (one sentence)
   - Acceptance criteria as a bullet list
   - Labels and components (used in Phase 2 to scope the search)
4. Post a comment on the GitHub Issue: "✅ Intake complete. Starting codebase analysis."

---

## Phase 2: UNDERSTAND

1. Read `CONTEXT.md` — focus on labels, components, and acceptance criteria.
2. Search the codebase for:
   - Controllers, services, and repositories related to the story topic
   - Existing tests covering the affected area
   - Any relevant Flyway migrations or configuration files
3. Write to `CONTEXT.md` under `## [UNDERSTAND]`:
   - List of files to modify, each with a one-line reason
   - Any new files to create
   - Required Flyway migration? (yes/no, and why)
   - Any pom.xml changes needed? (yes/no — check first before adding dependencies)

---

## Phase 3: PLAN

1. Read `CONTEXT.md` and `.github/copilot/patterns.md`.
2. Write a concise, ordered implementation plan to `CONTEXT.md` under `## [PLAN]`:
   - For each file: what to change and why (one line each)
   - Test changes: which spec files to add or update
   - Migration: SQL filename and what it does (if needed)
3. Post the plan as a comment on the GitHub Issue.
4. **If the issue has label `auto-dev-human-review`:**
   - Post: "📋 Plan ready. Reply **APPROVED** to proceed or **REVISE: <feedback>** to request changes."
   - Poll issue comments every 60 seconds.
   - On "APPROVED": proceed to Phase 4.
   - On "REVISE: ...": update the plan, re-post, and wait again.
   - On no response after 4 hours: post a timeout notice and stop.

---

## Phase 4: IMPLEMENT

1. Read `CONTEXT.md` — follow the plan exactly.
2. Apply coding standards from `.github/copilot-instructions.md`.
3. Make all code changes: modify existing files, create new files, add migration SQL.
4. Run: `mvn compile -q`
   - If compilation fails: fix errors and re-run. Do not proceed until it passes.
5. Write to `CONTEXT.md` under `## [IMPLEMENT]`:
   - List of all files changed or created

---

## Phase 5: VALIDATE

1. Run: `mvn verify -q`
   - On failure: read the test output, identify the root cause, fix the code or tests.
   - Re-run `mvn verify`. Retry up to **3 times**.
   - After 3 failures: post comment "❌ Tests failing after 3 retries. Human intervention needed."
     Include the last failure summary. Then stop.
2. Run: `mvn sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.token=$SONAR_TOKEN -q`
   - If SonarQube reports new **Critical** or **Blocker** issues: fix them and re-run once.
   - If they persist: post comment listing the issues and stop.
3. Write to `CONTEXT.md` under `## [VALIDATE]`:
   - Tests: X passed, Y failed (should be 0 failed)
   - SonarQube: clean / N issues fixed

---

## Phase 6: JENKINS VALIDATION

1. Try the REST API first:
   ```
   node .github/scripts/check-jenkins.js
   ```
   This script reads `$JENKINS_URL`, `$JENKINS_JOB_NAME`, `$JENKINS_USER`,
   `$JENKINS_TOKEN` from the environment and exits 0 on SUCCESS, 1 on failure.

2. If the build is still running, the script polls every 30 seconds for up to 10 minutes.
3. If the build FAILS: read the Jenkins console output (via API), attempt to fix the
   root cause, re-push, and wait for a new build. Retry once.
4. Write to `CONTEXT.md` under `## [JENKINS]`:
   - Build number and result

---

## Phase 7: SUBMIT

1. Stage and commit all changes:
   ```
   git add -A
   git commit -m "<jira-key>: <story-summary-slug>"
   ```
2. Push the branch:
   ```
   git push -u origin HEAD
   ```
3. Create the pull request:
   ```
   gh pr create \
     --title "<jira-key>: <story-summary>" \
     --body "$(cat .github/pr-template-auto.md)" \
     --base main \
     --label auto-dev
   ```
4. Use the Jira MCP tool to:
   - Transition the story status to **"In Review"**
   - Post a comment on the Jira story: "PR opened: <pr-url>"
5. Post a comment on the GitHub Issue:
   "🚀 PR opened: <pr-url>. Story transitioned to In Review in Jira."
