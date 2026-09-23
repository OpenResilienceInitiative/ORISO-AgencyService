# Agency Matrix password backfill (#200)

Issue: https://github.com/OpenResilienceInitiative/ORISO-AgencyService/issues/200

## Decision

Retain the existing `enc:` format for this legacy-data migration. The current cipher is
AES-128/ECB/PKCS5Padding, with the first 16 bytes of
`SHA-1("agency-matrix-password" + applicationKey)` as the key. The issue's SHA-256 description
was inaccurate. This change deliberately preserves the derivation and valid ciphertext;
old AgencyService binaries can still read migrated values during a rolling update or rollback.
An independent OpenSSL-produced synthetic fixture tests this compatibility.

ECB is deterministic and unauthenticated and is not an appropriate choice for a new credential
store. AES-GCM with a random per-value 96-bit nonce, an authentication tag, and a versioned
prefix was evaluated. Enabling new-format writes immediately would break old readers during
rolling deployment and binary rollback. Safe adoption needs a reader-first rollout followed
by explicit writer activation and a rollback/key-retention plan. That is a separate change.
The follow-up [#289](https://github.com/OpenResilienceInitiative/ORISO-AgencyService/issues/289)
assesses removing the stored password and plaintext transport entirely; if it must remain,
that assessment should specify the staged GCM transition. No transport/API behavior is changed here.

The cipher now encrypts empty/whitespace non-null values too. Otherwise the backfill could claim
success while leaving rows selected by the acceptance query in plaintext. An `enc:` input is
validated before being returned unchanged: malformed or unsupported values fail instead of being
silently accepted or double encrypted. The `enc:` prefix remains reserved for stored ciphertext;
a plaintext password beginning with it was already unsupported. ECB padding validation cannot
authenticate a value or reliably establish that an application key is correct.

## Execution and failure boundaries

The startup runner is **disabled by default**. Deploying this code alone does not migrate data.
When explicitly enabled, it runs after application initialization, before Spring Boot announces
readiness. It requires a nonblank existing `SERVICE_ENCRYPTION_APPKEY` **before any database
access**. It never invents or rotates a key.

One transaction locks all non-null password rows in ID order, including other tenants and
soft-deleted agencies. Existing ciphertext is validated and preserved; plaintext is encrypted
through `AgencyMatrixPasswordCipher` and only `matrix_password` is updated. Row locks serialize current database writes and overlapping runners. They **cannot** prevent an
agency entity loaded before migration from being saved afterwards with its stale plaintext password:
current agency entities have no optimistic version guard and JPA saves may update the whole row.
Therefore **all other AgencyService writers/replicas must be stopped or fully drained first**.
Run a single isolated maintenance instance with application traffic blocked through readback.
A regression test demonstrates the stale-save boundary and the need for exclusive maintenance. A pre-commit failure rolls back earlier row updates and fails
startup; a lost commit acknowledgement can leave the outcome uncertain, so any failure requires
aggregate readback before retry. The error never claims rollback was confirmed; success logs only the committed row count. A second run logs zero changes. Database
exception causes are deliberately omitted because drivers may include bound credentials.

This is a small legacy-data migration, not a batched online job. It holds locks for the full
transaction and reads the non-null values into memory. Schedule the opt-in restart in a maintenance
window with exclusive application access. The existing `VARCHAR(255)` column is unchanged; ciphertext expansion may make unusually
long historical values fail, in which case the entire transaction rolls back. The runner checks
the encoded length before writing, protecting against silent truncation even in non-strict SQL mode. Verify the existing
key against the deployment's secret provenance before running: nonblank does not prove correct,
and a database containing only plaintext cannot detect the wrong key automatically. Keep the
same key for later reads and rollbacks.

## Operator/reviewer runbook

1. On a disposable database first, run:

   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B \
     -Dtest=AgencyMatrixPasswordCipherTest,AgencyMatrixPasswordBackfillIT,AgencyMatrixPasswordBackfillJpaTest,AgencyServiceMatrixCredentialsTest test
   ```

   On Linux set `JAVA_HOME` to the installed Java 21 runtime. Docker is required; the integration
   test creates and removes its own MariaDB 10.11 container, with synthetic credentials only.
   `AgencyMatrixPasswordBackfillIT` is also in the required CI integration allowlist.

2. For an authorized environment, retain its current image/configuration and take the normal
   encrypted backup. Stop all AgencyService replicas and other processes that can write agency rows;
   drain in-flight requests and close their persistence contexts. Block ingress and internal/direct application traffic (readiness alone is insufficient) and start
   **one isolated maintenance instance**, not a normal rolling deployment. Keep all other writers
   stopped until readback succeeds; otherwise a stale JPA save can restore plaintext after commit. Confirm the existing application key through the secret manager without
   printing it. Enable `SERVICE_ENCRYPTION_AGENCY_MATRIX_BACKFILL_ENABLED=true` for this
   isolated AgencyService startup with the new image. Also set `AGENCY_DELETEWORKFLOW_CRON=-`
   on the maintenance instance: Spring's disabled-cron marker prevents its own scheduled agency
   deletion workflow from running during migration/readback. The backfill default is `false`;
   the existing key stays unchanged.
   A missing key must fail startup visibly before any row changes. A migration error must also
   fail startup. Pre-commit errors roll back; a commit-time connection loss may have an uncertain
   outcome. Inspect aggregate state before retry while keeping writers stopped. Restore the previous image/configuration if
   needed; do not export or restore plaintext values as a rollback technique.

3. Read only the aggregate count (never dump password values):

   ```sql
   SELECT COUNT(*) AS remaining_plaintext
   FROM agency
   WHERE matrix_password IS NOT NULL AND matrix_password NOT LIKE 'enc:%';
   ```

   Expect `0` and a successful committed-row-count log. Restart with opt-in still enabled once;
   expect `0 row(s) encrypted`, while traffic and all other writers remain stopped. Then stop the
   maintenance instance, set the opt-in back to `false`, restore the previous deletion cron,
   retain the same key, and start fresh normal
   replicas. Only restore traffic after readiness and the final aggregate readback.
   The count checks prefix coverage; the runner additionally validates existing ciphertext.
   Confirm the internal service-account workflow still authenticates normally on Dev.

4. Record image, environment, aggregate counts and restart/readback evidence on #200. PreDev
   requires Frank's explicit per-task instruction; no live database was touched for this change.
   The historical 19 plaintext rows are unverified today. Local tests do not satisfy the issue's
   PreDev count acceptance or the post-merge Dev verification gate.

## Local evidence

- Red command: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B
  -Dtest=AgencyMatrixPasswordCipherTest,AgencyMatrixPasswordBackfillIT test` (one shell line).
  The original cipher plus a no-op runner ran 9 tests: 5 assertion failures, no errors/skips.
  The database still had 3 plaintext rows; missing-key and database-failure tests did not throw;
  blank-value encryption and malformed-prefix rejection failed.
- Final targeted command above: **20 tests, 0 failures, 0 errors, 0 skipped**. This includes
  9 MariaDB migration tests, 2 auto-configured JPA transaction tests against H2, 7 cipher tests,
  and 2 existing service credential tests. The MariaDB test uses a minimal real table matching
  the migration's columns, with non-strict SQL mode to exercise truncation safety; the JPA test
  uses the actual mapped agency schema and Spring's `JpaTransactionManager`/`JdbcTemplate` wiring.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B test`: **701 tests, 0 failures, 0 errors,
  0 skipped**, build successful. Surefire's `default-test` execution is skipped by the existing
  POM; its `unit-tests` execution actually ran the suite and produced the result above.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) scripts/ci/run-required-integration-tests.sh`:
  **29 tests, 0 failures, 0 errors, 0 skipped**, build successful and **8 complete reports** verified
  by `scripts/ci/verify-test-reports.py`. This is the final reviewed version, including all 9
  MariaDB backfill cases; the required CI allowlist now includes this migration test.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B package -DskipTests`: **build successful**.
  The existing POM sets `skipTests=false` on its `unit-tests` execution, so this command also
  reran **701 tests, 0 failures, 0 errors, 0 skipped** despite the command-line skip flag.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B checkstyle:check@validate`:
  **0 violations, build successful**, using the repository's `google_checks_light.xml` rules.
  The same configured execution passed during targeted, unit, required-integration and package runs.
- The router's standalone `./mvnw -B checkstyle:check` command uses `default-cli`, which does not
  inherit configuration nested under execution `validate`: **3899 violations** on this branch.
  An untouched `git archive HEAD` snapshot at `e558954b5adc2a147dc75c971baecbeada713022`, checked with
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B -f target/baseline-checkstyle/pom.xml
  checkstyle:check` (one shell line), already fails with **3867 violations** under those defaults.
  This is command/configuration drift, not a claim that every working-tree violation pre-existed.
  The intended project-configured gate passes. No POM/dependency/style sweep was added to this ticket.
- `git diff --check`: passed.
- The separate Astra review found and resolved two operational correctness issues: stale JPA
  saves require exclusive maintenance, and commit acknowledgement failures require readback.
  Final rereview reported no remaining actionable code findings.
- All evidence is local, on Java 21.0.11, using synthetic fixtures. No deployment image or live
  database was modified; Dev and authorized PreDev acceptance remain separate delivery gates.
