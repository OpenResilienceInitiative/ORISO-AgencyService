# Identity-only agency Matrix API (#289)

The technical-user-only GET and POST endpoints at
`/internal/agencies/{agencyId}/matrix-service-account` return only:

```json
{"matrixUserId":"@agency:homeserver"}
```

Neither an existing-account response nor a new-provision response returns or
decrypts a stored password. Authentication/authorization and the existing
404/202 outcomes remain unchanged. Provisioning continues to store credentials
encrypted; there is no database migration, account rotation, deletion or new
privilege/configuration in this change.

## Required rollout order

Deploy the companion UserService change for
[AgencyService #289](https://github.com/OpenResilienceInitiative/ORISO-AgencyService/issues/289)
**first**. All six callers must use the existing bounded Synapse admin
impersonation path and accept an identity-only response. Verify room creation,
membership changes, enquiry acceptance/history, message reads/system messages
and team discussion create/archive before deploying this AgencyService change.
Older UserService versions require the removed password field and are incompatible.

## Rollback

Restore the old AgencyService response **before** reverting UserService. That
rollback restores reusable-password transport and should only be a deliberate
recovery step. Retiring/rotating stored secrets or old devices/tokens is a separate
operator decision. Keep issue references as `Refs`, not `Closes`, until both
services and target-environment acceptance are complete.
