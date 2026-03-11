# Phase 6: Fix API Key Security Chain - Research

**Researched:** 2026-03-11
**Domain:** Spring Security dual SecurityFilterChain configuration; path matcher scoping
**Confidence:** HIGH

---

## Summary

Phase 6 is a surgical configuration fix. The three API key self-service endpoints (`/v1/api/keys`) exist and work correctly in isolation — the controller (`ClientApiKeyResource`), service (`ApiKeyService`), and filter (`ApiKeyAuthenticationFilter`) are all fully implemented. The only problem is that `ClientSecurityConfiguration.clientApiSecurityFilterChain()` does not include `/v1/api/**` in its `securityMatcher(...)` call, so those requests fall through to the `@Order(2)` JWT chain which has no concept of API key clients.

The fix requires two coordinated changes in two files:
1. Add `CLIENT_API_KEYS = "/v1/api/**"` constant to `AppEndpoints`.
2. Add `AppEndpoints.CLIENT_API_KEYS` to the `securityMatcher(...)` varargs in `ClientSecurityConfiguration`.
3. Remove the dead `TOPUPS_API = "/v1/topups/**"` constant from `AppEndpoints` and from the same `securityMatcher(...)` call.

No service layer, filter, controller, repository, or test changes are required. The existing unit tests for `ApiKeyService` and `AdminApiKeyResource` are unaffected. An integration test for the fixed routing should be added.

**Primary recommendation:** Add `CLIENT_API_KEYS` constant to `AppEndpoints`; include it and drop `TOPUPS_API` from `ClientSecurityConfiguration.securityMatcher(...)` in a single atomic change.

---

## Standard Stack

This phase touches only Spring Security configuration — no new dependencies.

### Core
| File | Role | Change Required |
|------|------|-----------------|
| `AppEndpoints.java` | Central path constant registry | Add `CLIENT_API_KEYS`; remove `TOPUPS_API` |
| `ClientSecurityConfiguration.java` | `@Order(1)` API key filter chain | Add `CLIENT_API_KEYS` to `securityMatcher`; drop `TOPUPS_API` |

### No New Dependencies
No `pom.xml` changes. Spring Security is already on the classpath at the version used by Spring Boot 3.5.11.

---

## Architecture Patterns

### Dual SecurityFilterChain Ordering (existing pattern — do not change)

```java
// NexahSecurityConfiguration — @Order(0)
http.securityMatcher("/v1/provider/**")  // Nexah DR callbacks

// ClientSecurityConfiguration — @Order(1)
http.securityMatcher(SMS_API, CREDITS_API, TOPUPS_API, WEBHOOKS_API)
// MUST become:
http.securityMatcher(SMS_API, CREDITS_API, CLIENT_API_KEYS, WEBHOOKS_API)

// SecurityConfiguration — @Order(2)
// JWT chain — catches everything not claimed by Orders 0 and 1
```

Spring Security evaluates filter chains in `@Order` sequence. The first chain whose `securityMatcher` matches the request wins; subsequent chains are skipped entirely. `@Order(1)` must claim `/v1/api/**` before `@Order(2)` sees it.

### AppEndpoints Constant Pattern (existing convention)

All path strings are centralized in `AppEndpoints` as `public static final String` constants. `ClientSecurityConfiguration` references constants by name — never by string literal. This is the established pattern; the new constant follows it exactly.

Existing precedent:
```java
// AppEndpoints.java (lines 19-22)
public static final String SMS_API      = "/v1/sms/**";
public static final String CREDITS_API  = "/v1/credits/**";
public static final String TOPUPS_API   = "/v1/topups/**";   // REMOVE
public static final String WEBHOOKS_API = "/v1/webhooks/**";
// ADD:
public static final String CLIENT_API_KEYS = "/v1/api/**";
```

### SECURED_MAPPINGS Does Not Need Updating

`AppEndpoints.SECURED_MAPPINGS` is used only by the `@Order(2)` JWT chain (`SecurityConfiguration`). It maps path patterns to required roles for JWT-authenticated users. The API key chain (`@Order(1)`) does not consult `SECURED_MAPPINGS` — it calls `auth.anyRequest().authenticated()` and delegates all authority decisions to `ApiKeyAuthenticationFilter`. Therefore `SECURED_MAPPINGS` does not need a `/v1/api/**` entry.

### Anti-Patterns to Avoid

- **Do not add `@Component` to `ApiKeyAuthenticationFilter`**: The prior decision explicitly prohibits this. It would cause Spring Boot to register it as a global servlet filter applied to every request (including the JWT chain), not just the `@Order(1)` chain. The filter is already instantiated manually inside `clientApiSecurityFilterChain()`.
- **Do not widen the matcher to `/v1/**`**: `AppEndpoints.CLIENT_API` was previously updated to `/v1/**` (see STATE.md decision log), but then scoped back down. Using `/v1/**` as the `@Order(1)` matcher would absorb all `/v1/` paths including those currently guarded by the JWT chain — unintended scope expansion.
- **Do not add `/v1/api/**` to `SECURED_MAPPINGS`**: That map is for the JWT chain. API key clients never have `ROLE_USER/ADMIN/LTD_ADMIN`, so adding them there would have no effect and would mislead future readers.
- **Do not remove `TOPUPS_API` from `AppEndpoints` without also removing it from `ClientSecurityConfiguration`**: Dead references in `securityMatcher(...)` silently guard a path no controller owns; they should both be removed together.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Path matching in securityMatcher | Custom request matchers | Spring Security's built-in Ant pattern matching via `securityMatcher(String...)` | Already in use everywhere |
| Filter chain ordering | Manual comparators | `@Order(int)` annotation | Established pattern in this codebase; lower number = higher priority |

---

## Common Pitfalls

### Pitfall 1: Adding the path only to `securityMatcher` and forgetting `AppEndpoints`

**What goes wrong:** Hard-coded string in `ClientSecurityConfiguration` instead of the constant. Future readers see inconsistency; `AppEndpoints` remains incomplete.

**How to avoid:** Always define the constant in `AppEndpoints` first, then reference it. Never use a string literal in `securityMatcher(...)`.

### Pitfall 2: Removing `TOPUPS_API` from `AppEndpoints` but not from `ClientSecurityConfiguration` (or vice versa)

**What goes wrong:** Compile error (if removed from `AppEndpoints` but still referenced in `ClientSecurityConfiguration`), or residual dead path guard (opposite order).

**How to avoid:** Make both changes atomically in the same commit. The compiler will catch the reference error if you forget one side.

### Pitfall 3: Assuming the fix needs integration test changes

**What goes wrong:** Rewriting existing tests or adding too much scope to this phase.

**How to avoid:** The existing `SecurityFilterChainIT` tests the JWT chain, not the API key chain. A new narrow test (`ClientApiKeySecurityIT` or similar) should verify that `GET /v1/api/keys` with a valid Bearer API key returns 200, and that the same request with no token returns 401. This is an additive test, not a rewrite.

### Pitfall 4: Confusing `TOPUPS_API` removal with a functional regression

**What goes wrong:** Worrying that removing `/v1/topups/**` from the matcher breaks topup endpoints.

**How to avoid:** The actual topup controller (`TopupResource`) lives at `/v1/credits/topups`, which is fully covered by `CREDITS_API = "/v1/credits/**"`. `TOPUPS_API` matches no real controller. Removing it is safe.

---

## Code Examples

### After-state for AppEndpoints (the two lines that change)

```java
// Source: existing pattern in AppEndpoints.java — verified by reading file
// REMOVE this line:
// public static final String TOPUPS_API = "/v1/topups/**";
// ADD this line:
public static final String CLIENT_API_KEYS = "/v1/api/**";
```

### After-state for ClientSecurityConfiguration.securityMatcher

```java
// Source: ClientSecurityConfiguration.java line 43 — verified by reading file
// BEFORE:
//   .securityMatcher(AppEndpoints.SMS_API, AppEndpoints.CREDITS_API, AppEndpoints.TOPUPS_API, AppEndpoints.WEBHOOKS_API)
// AFTER:
http
    .securityMatcher(AppEndpoints.SMS_API, AppEndpoints.CREDITS_API, AppEndpoints.CLIENT_API_KEYS, AppEndpoints.WEBHOOKS_API)
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `TOPUPS_API` in securityMatcher | `CLIENT_API_KEYS` in securityMatcher | Correct path; APIKEY-01/02/03 become reachable |
| `/v1/api/keys` falls through to JWT chain → 403 | `/v1/api/keys` handled by API key chain → 401 (no token) or 200 (valid token) | CRITICAL-1 closed |

---

## Open Questions

None. The root cause is fully diagnosed in the milestone audit (`CRITICAL-1`), the affected lines are identified precisely, and the fix is unambiguous. No unknowns remain.

---

## Sources

### Primary (HIGH confidence)

- Source code read directly:
  - `AppEndpoints.java` — confirmed `TOPUPS_API` exists at line 21, `CLIENT_API_KEYS` absent
  - `ClientSecurityConfiguration.java` — confirmed `securityMatcher(...)` at line 43 references `TOPUPS_API`, missing `CLIENT_API_KEYS`
  - `ClientApiKeyResource.java` — confirmed controller maps to `/v1/api/keys`, is fully implemented
  - `ApiKeyAuthenticationFilter.java` — confirmed not `@Component`, instantiated manually
  - `SecurityConfiguration.java` — confirmed `@Order(2)` JWT chain, `SECURED_MAPPINGS` usage
  - `.planning/v1.0-MILESTONE-AUDIT.md` — confirmed CRITICAL-1 and CRITICAL-2 root cause analysis

### Secondary (MEDIUM confidence)

- Spring Security dual filter chain ordering behavior: consistent with the `@Order` annotations already present in the codebase (`NexahSecurityConfiguration @Order(0)`, `ClientSecurityConfiguration @Order(1)`, `SecurityConfiguration @Order(2)`)

---

## Metadata

**Confidence breakdown:**
- Root cause: HIGH — audit document + direct code inspection confirm exactly which lines to change
- Fix approach: HIGH — pattern is already used in this codebase for other path constants
- Safety of TOPUPS_API removal: HIGH — `TopupResource` uses `/v1/credits/topups`, covered by `CREDITS_API`; no controller at `/v1/topups/**`
- Test strategy: HIGH — additive integration test; existing tests unaffected

**Research date:** 2026-03-11
**Valid until:** N/A — this phase targets specific lines; the findings don't become stale
