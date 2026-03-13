# Repository Patterns

This file is automatically updated by `.github/workflows/post-merge-learning.yml`
after each `auto-dev` PR is merged into main. The Copilot Coding Agent reads this
file during **Phase 3: PLAN** to apply lessons learned from past tasks.

Keep each entry concise — one or two sentences. The agent will skip this file if it
grows beyond 200 lines (trim-patterns.yml enforces this).

---

<!-- ================================================================
     ENTRIES BELOW ARE APPENDED AUTOMATICALLY. DO NOT EDIT MANUALLY.
     Format:
       ## YYYY-MM-DD <JIRA-KEY>: <one-line lesson>
       Files: <comma-separated list>
       Avoid: <what not to do>
       Prefer: <what worked well>
     ================================================================ -->

## Template Entry (remove after first real entry)
## 2026-01-01 PROJ-0: Example — validation added to signup flow
Files: SignupController.java, SignupRequest.java, SignupServiceSpec.groovy
Avoid: Adding validation logic inside the service layer.
Prefer: Use @Valid on the controller parameter + @ControllerAdvice for error mapping.
