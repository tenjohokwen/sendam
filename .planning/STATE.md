# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-12)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 13 — Foundation Extension (v1.2)

## Current Position

Phase: 13 of 18 (Foundation Extension)
Plan: 01 of 6 in phase (13-01 complete)
Status: In progress
Last activity: 2026-03-12 — Completed 13-01-PLAN.md (foundation utilities)

Progress: v1.0 COMPLETE | v1.1 COMPLETE | v1.2 █░░░░░ ~3%

## Accumulated Context

### Decisions

All v1.0 and v1.1 decisions are logged in PROJECT.md Key Decisions table and archived in:
- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.1-ROADMAP.md`

**13-01 decisions:**
- FOUND-06 pattern: all API calls centralized in `api/<domain>/` folders; no direct axios in components/composables
- FOUND-07 pattern: admin pages import `useErrorHandler` exclusively for API error handling; existing reactive implementation (setError/clearError + computed + i18n) preserved over simpler spec
- Long ID guard: always call `longToString()` on backend ID fields before display (Java BIGSERIAL exceeds JS MAX_SAFE_INTEGER)

### Pending Todos

(None — clean slate for v1.2)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — fallback default is documented as unsafe
- Non-blocking tech debt from v1.0/v1.1:
  - Wrong error code for blank-message validation (INVALID_SENDER_ID used instead of message-specific code)
  - Duplicate @RateLimited on SmsResource AND SmsService fires AOP aspect twice per request (wastes rate-limit tokens)
  - TODO comments in AppEndpoints.java and SecurityConfiguration.java
  - WEBHOOK_DELETED enum constant unwired — needs trigger point when delete endpoint added
  - ClientCreditConsumptionResponse in analytics.contract (minor cross-module coupling with spend.service)
  - Boot smoke test for /v1/analytics/** endpoints recommended before production deploy

## Session Continuity

Last session: 2026-03-12T13:25:57Z
Stopped at: Completed 13-01-PLAN.md — foundation utilities created
Resume file: None
