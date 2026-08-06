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

- Integration branch: `pre-dev` when used for ORISO feature work.
- Skim `.understand-anything/` before non-trivial changes.
- Keep agency contracts aligned with Admin agency screens and UserService references — do not invent parallel agency models.
- Secrets: use `config.env.example`; never commit real env files.

## Done

Touched tests pass; package succeeds for PR-bound work; checkstyle clean when you touch Java sources. Task notes: `docs/agent-tasks/YYYY-MM-DD_short-name/` if needed.
