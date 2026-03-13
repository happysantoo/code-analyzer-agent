# One-Time Setup Guide

This guide walks through everything needed to activate the Copilot-native SDLC agent
in a Java Spring Boot GitHub repository. Complete these steps once; the agent then
runs automatically every time a Jira story is labeled `auto-dev`.

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| GitHub Copilot Enterprise | Required for the Copilot Coding Agent feature |
| Jira Cloud (or Server with webhook support) | Story source |
| SonarQube (Cloud or self-hosted) | Static analysis in Phase 5 |
| Jenkins (public URL) | CI validation in Phase 6 |
| GitHub CLI (`gh`) | Pre-installed on GitHub Actions runners |

---

## Step 1: Copy Files Into Your Repository

Copy the entire contents of this `sdlc-agent-template/` folder into the **root** of
your target Java Spring Boot repository:

```
your-repo/
├── AGENTS.md                               ← copy here
├── CONTEXT.md.template                     ← copy here (reference only)
├── .github/
│   ├── copilot-instructions.md             ← copy here
│   ├── mcp.json                            ← copy here
│   ├── pr-template-auto.md                 ← copy here
│   ├── copilot/
│   │   └── patterns.md                     ← copy here
│   ├── scripts/
│   │   └── check-jenkins.js               ← copy here
│   └── workflows/
│       ├── jira-intake.yml                 ← copy here
│       ├── post-merge-learning.yml         ← copy here
│       └── trim-patterns.yml              ← copy here
```

Then edit the files as described in the steps below before committing.

---

## Step 2: Enable Copilot Coding Agent

1. Go to your repository on GitHub.
2. Navigate to **Settings → Copilot → Coding agent**.
3. Enable the Coding Agent for this repository.
4. Under **MCP servers**, verify that `.github/mcp.json` is detected.

---

## Step 3: Configure GitHub Repository Secrets and Variables

Go to **Settings → Secrets and variables → Actions**.

### Secrets (sensitive — never commit these)

| Secret name | Value |
|-------------|-------|
| `JIRA_API_TOKEN` | Jira API token (from [id.atlassian.com/manage-profile/security/api-tokens](https://id.atlassian.com/manage-profile/security/api-tokens)) |
| `SONAR_TOKEN` | SonarQube user token with Execute Analysis permission |
| `JENKINS_USER` | Jenkins username |
| `JENKINS_TOKEN` | Jenkins API token (User → Configure → API Token) |

### Variables (non-sensitive configuration)

| Variable name | Example value |
|---------------|---------------|
| `JIRA_BASE_URL` | `https://your-org.atlassian.net` |
| `JIRA_MCP_URL` | `https://your-jira-mcp-server.example.com` (your hosted Jira MCP endpoint) |
| `SONAR_URL` | `https://sonarcloud.io` or your self-hosted URL |
| `JENKINS_URL` | `https://jenkins.your-org.com` |
| `JENKINS_JOB_NAME` | `my-service` or `my-service/main` (folder/job) |

---

## Step 4: Configure the Jira MCP Server

The `.github/mcp.json` file references `${JIRA_MCP_URL}` and `${JIRA_API_TOKEN}`.
These are injected from GitHub secrets/variables at runtime by the Copilot Coding Agent.

You need a Jira MCP server accessible from GitHub's network. Options:
- **Hosted Jira MCP**: Use an existing public Jira MCP provider, or
- **Self-hosted**: Deploy [mcp-atlassian](https://github.com/sooperset/mcp-atlassian)
  (or equivalent) on a publicly accessible server, then set `JIRA_MCP_URL` to its URL.

---

## Step 5: Configure the Jira Automation Webhook

In Jira, create an **Automation rule**:

1. **Trigger:** Label added → value: `auto-dev`
2. **Condition:** Issue type is Story (optional, to limit scope)
3. **Action:** Send web request
   - **URL:** `https://api.github.com/repos/{owner}/{repo}/dispatches`
   - **HTTP method:** POST
   - **Headers:**
     ```
     Content-Type: application/json
     Authorization: Bearer <GitHub PAT with repo scope>
     Accept: application/vnd.github+json
     X-GitHub-Api-Version: 2022-11-28
     ```
   - **Body:**
     ```json
     {
       "event_type": "jira-story-ready",
       "client_payload": {
         "jira_key": "{{issue.key}}",
         "summary": "{{issue.summary}}",
         "description": "{{issue.description}}",
         "ac": "{{issue.customfield_acceptance_criteria}}",
         "labels": "{{#join}}{{issue.labels}}{{/join}}",
         "components": "{{#join}}{{issue.components}}{{/join}}"
       }
     }
     ```
   - Replace `customfield_acceptance_criteria` with your actual Jira field ID if you
     store AC in a custom field; otherwise parse it from the description.

4. **Save and publish** the rule.

---

## Step 6: Customise `copilot-instructions.md`

Open `.github/copilot-instructions.md` and adjust for your project:
- Update package naming conventions.
- Add any project-specific patterns (e.g. event sourcing, specific annotations).
- Add the SonarQube quality gate threshold if different from the default.

---

## Step 7: Seed `patterns.md`

Open `.github/copilot/patterns.md` and:
- Delete the example Template Entry.
- Add any known project patterns you want the agent to follow from day one.

---

## Step 8: Test the Full Flow

1. Create a test Jira story with a clear, simple acceptance criterion.
2. Add the label `auto-dev` to the story.
3. Watch GitHub Actions: the `jira-intake.yml` workflow should run within 1-2 minutes.
4. A GitHub Issue should appear, assigned to Copilot.
5. The Copilot Coding Agent should start working on the issue (visible in the issue timeline).
6. Monitor the issue comments for phase updates.

---

## Step 9: Optional — Human Review Mode

To require human approval before the agent implements changes:
1. In Jira, add **both** `auto-dev` and `auto-dev-human-review` labels to the story.
2. The Jira Automation rule sends both labels in the `client_payload`.
3. The `jira-intake.yml` workflow adds `auto-dev-human-review` to the GitHub Issue.
4. The agent posts the plan and waits for a comment containing "APPROVED".

To approve:
- Open the GitHub Issue and add a comment: `APPROVED`

To request changes:
- Comment: `REVISE: <your feedback here>`

---

## Troubleshooting

| Problem | Check |
|---------|-------|
| Jira webhook not firing | Confirm Jira Automation rule is published and the PAT has `repo` scope |
| GitHub Issue not created | Check Actions tab → `jira-intake.yml` → view logs |
| Copilot not starting | Confirm Coding Agent is enabled in repo Settings → Copilot |
| Copilot cannot reach Jira MCP | Verify `JIRA_MCP_URL` variable and `JIRA_API_TOKEN` secret are set |
| `mvn verify` failing in CI | Check that the GitHub Actions runner has Java 17+ and Maven available |
| Jenkins check failing | Verify `JENKINS_URL`, `JENKINS_USER`, `JENKINS_TOKEN` secrets; test `curl` manually |
| SonarQube failing | Check `SONAR_URL` and `SONAR_TOKEN`; ensure project key matches |
| `patterns.md` too large | Trigger `trim-patterns.yml` manually via Actions → workflow_dispatch |
