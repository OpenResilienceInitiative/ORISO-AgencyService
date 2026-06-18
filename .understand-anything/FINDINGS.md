# Findings: ORISO-AgencyService

## Generation Summary

- Generated from latest dev commit: `972078ea7d50225e02e2aea8a0743b98aaa31a04`
- Files analyzed: 328
- Category breakdown: {"config":23,"pipeline":3,"code":226,"docs":3,"infra":1,"script":5,"data":67}
- Graph nodes: 1266
- Graph edges: 2578

## Notable Current-Code Changes Reflected

- Agency admin controls are represented through new service, facade, converter, settings, entity, and repository classes.
- Agency settings persistence is represented through `AgencySettingsService`, `Agency.settings`, and the `0020_agency_settings` Liquibase changeset.
- The admin API contract reflects expanded agency admin behavior in `api/agencyadminservice.yaml`.
- Security and tenant resolution updates are represented in `SecurityConfig`, `Authority`, `AuthenticatedUserConfig`, and tenant resolver classes.

## Review Notes

- The graph was regenerated from the current repository only.
- The local macOS checkout has a tracked `README.md`/`readme.md` casing collision; the generator read tracked content through Git so the graph still represents tracked files.
- Validate with `python3 -m json.tool .understand-anything/knowledge-graph.json > /dev/null`.
