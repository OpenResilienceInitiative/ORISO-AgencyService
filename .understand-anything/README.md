# Understand-Anything Graph: ORISO-AgencyService

This directory contains the Understand-Anything graph and developer notes for `ORISO-AgencyService`.

## Graph

- Graph file: `.understand-anything/knowledge-graph.json`
- Generated at: `2026-06-12T03:32:00.339Z`
- Source commit: `972078ea7d50225e02e2aea8a0743b98aaa31a04`
- Files analyzed: 328
- Nodes: 1266
- Edges: 2578

## Repository Purpose

AgencyService provides agency core data, postcode ranges, agency settings, and agency admin APIs for the ORISO Online-Beratung platform.

## Current Refresh Notes

The graph was regenerated from latest `dev`. The latest code includes agency admin control persistence, agency settings support, new Liquibase changesets, updated admin API contracts, and security/tenant resolver updates.

## Dashboard

From this repository root:

```sh
PLUGIN_ROOT="$HOME/.understand-anything-plugin"
test -d "$PLUGIN_ROOT/packages/dashboard" || PLUGIN_ROOT="$HOME/.understand-anything/repo/understand-anything-plugin"
cd "$PLUGIN_ROOT/packages/dashboard"
GRAPH_DIR="$(pwd)" npx vite --host 127.0.0.1
```

Use the tokenized URL printed by Vite. The dashboard reads `.understand-anything/knowledge-graph.json`.

## Main Files Scanned

- `.github/actions/docker-build-push/action.yml` - config, yaml
- `.github/actions/maven-build/action.yml` - config, yaml
- `.github/workflows/ci-feature-branch.yml` - pipeline, yaml
- `.github/workflows/ci-main.yml` - pipeline, yaml
- `.github/workflows/ci-pull-request.yml` - pipeline, yaml
- `.gitignore` - code, unknown
- `.mvn/wrapper/maven-wrapper.properties` - config, properties
- `.swagger-codegen-ignore` - code, unknown
- `CHANGELOG.md` - docs, markdown
- `Dockerfile` - infra, dockerfile
- `LICENSE` - code, unknown
- `README.md` - docs, markdown
- `api/agencyadminservice.yaml` - config, yaml
- `api/agencyservice.yaml` - config, yaml
- `check-version.sh` - script, shell
- `commitlint.config.js` - code, javascript
- `deploy-development.sh` - script, shell
- `docker-build.cmd` - script, batch
- `documentation/AgencyService-Architektur.graphml` - data, xml
- `google_checks_light.xml` - config, xml
