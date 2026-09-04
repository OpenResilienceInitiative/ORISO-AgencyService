# AGENTS.md — ORISO-AgencyService

Load workspace parent `../AGENTS.md` first (`PROJECT_ORISO_ROOT` = parent of this repo).

## Stack

Java **21**, Spring Boot **4.0.1**, Maven Wrapper **3.9.15**. Owns agencies and `/service/agencies` admin APIs.

## Commands

```bash
./mvnw -B test
./mvnw -B package -DskipTests
./mvnw -B checkstyle:check   # bound to validate in pom
```

From workspace: `REPO=ORISO-AgencyService ../scripts/harness/verify-fast.sh` (or `verify-full.sh`).

CI: `./mvnw -B test` then `./mvnw -B package -DskipTests` on Java 21.

## Context

- Integration branch: `dev` when used for ORISO feature work.
- Skim `.understand-anything/` before non-trivial changes.
- Keep agency contracts aligned with Admin agency screens and UserService references — do not invent parallel agency models.
- Secrets: use `config.env.example`; never commit real env files.

## Done

Touched tests pass; package succeeds for PR-bound work; checkstyle clean when you touch Java sources. Task notes: `docs/agent-tasks/YYYY-MM-DD_short-name/` if needed.

## AI agent delivery rules

Binding for every AI coding agent working in this repository. Canonical text and
rationale: `ORISO-Docs/oriso-platform/coding-standards.mdx` (section "AI agent
delivery rules"). Summary:

- **An agent never merges its own pull request.** Not on green CI, not on "finish
  it", not for chores or test-only changes. Delivery ends at: verified → PR open
  with evidence and a reviewer test plan → reviewers requested → issue
  `In review`. Merge only on an explicit, per-PR instruction naming that PR.
- **Request reviewers in the same step that opens the PR.** A PR without
  requested reviewers is not open for review.
- **"Pre-Dev is free" means the server, not the branch.** Deploying images,
  mutating config or data and running E2E on the Pre-Dev server needs no
  approval; the `dev` *branch* is review-gated like any shared branch.
- **Restore what you borrowed.** Record image reference *and* `imagePullPolicy`
  before swapping anything on Pre-Dev, put both back before reporting done, and
  say so in the report.
- **State where it was verified** in every PR body — environment and image, or
  plainly "local only".
