# Agency permission-policy authority

Agency permission reads and changed-override validation use fresh public tenant policies.
An inherited requirement is enforced even when an older agency row has no tenant ID.

- An explicit positive stored tenant ID retains the existing public projection and legacy
  boolean fallback contract. A present mismatched projection ID is refused; an absent ID remains
  compatible with older providers. Policy operations do not reuse cached tenant responses.
- A stored `NULL` tenant ID is supported only with `multitenancy.enabled=false`. Each policy
  operation fetches the configured single tenant afresh. Single-domain mode uses the configured
  main subdomain, because the single-tenant endpoint can be ambiguous there. This path requires
  a positive returned tenant ID, settings, and a nonempty, complete canonical policy map.
- Zero and negative stored IDs are not legacy absence. Missing tenant context in multitenant
  mode is denied. A missing/malformed single-tenant authority or provider outage returns the
  fixed settings-unavailable response; it never permits raw agency overrides.
- Topic registration is not a cascaded permission. Only its Boolean value for an explicit
  tenant uses the existing tenant cache, under `topics-registration:<id>`. The cache configuration
  and its expiry are unchanged; single-tenant and main-subdomain topic routing are unchanged.
- Policy lookup diagnostics record only the exception class and HTTP status (when present).
  The public 503 has no upstream cause; neither provider body nor exception message is logged.

`TenantPolicySingleModeHttpTest` exercises the actual generated tenant HTTP client and Spring
cache proxy. `AgencyServiceTest.policyLookupFailureShouldLogOnlySafeClassAndStatusThroughTheHttpHandler`
checks both the diagnostic and the real exception handler for disclosure.
