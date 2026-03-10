---
phase: 04-provider-integration
plan: 01
subsystem: infra
tags: [nexah, resilience4j, circuit-breaker, sms-provider, spring-security, rest-template]

# Dependency graph
requires:
  - phase: 03-send-sms
    provides: NexahClient dependency required by DrCallbackResource; ProviderUnavailableException needed by ApiAdvice
provides:
  - NexahClient with sendSms (@CircuitBreaker nexah) and checkAvailability() probe
  - 8 Nexah DTOs with exact @JsonProperty mappings (reponsecode typo, String totalSmsUnit in DR)
  - NexahProperties @ConfigurationProperties(prefix=nexah)
  - resilience4j.circuitbreaker.instances.nexah configured in application.yaml
  - ProviderUnavailableException + ProviderError.PROVIDER_UNAVAILABLE
  - NexahSecurityConfiguration @Order(0) permitting /v1/provider/** without API key
  - DrCallbackResource POST /v1/provider/dr stub (Plan 04-02 wires processing)
  - ApiAdvice handler: ProviderUnavailableException -> HTTP 503 PROVIDER_UNAVAILABLE
affects:
  - 04-02-PLAN (DrCallbackService wires into DrCallbackResource stub)
  - 04-03-PLAN (SmsService calls NexahClient.sendSms)

# Tech tracking
tech-stack:
  added: [resilience4j circuit breaker (spring-cloud-starter-circuitbreaker-resilience4j already present)]
  patterns:
    - Circuit breaker via @CircuitBreaker annotation (not Spring Cloud abstraction) — Resilience4j native
    - NexahClient does NOT extend AbstractClient — credential-in-body auth incompatible with interceptor pattern
    - @Order(0) SecurityFilterChain scoped to /v1/provider/** runs before @Order(1) API key chain

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahProperties.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahSendRequest.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahSendResponse.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahSmsEntry.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahDrEntry.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahDrPayload.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahDrAck.java
    - src/main/java/com/softropic/sendam/client/contract/nexah/NexahDrResponse.java
    - src/main/java/com/softropic/sendam/client/infrastructure/nexah/NexahClient.java
    - src/main/java/com/softropic/sendam/client/contract/exception/ProviderUnavailableException.java
    - src/main/java/com/softropic/sendam/client/contract/exception/ProviderError.java
    - src/main/java/com/softropic/sendam/client/config/NexahSecurityConfiguration.java
    - src/main/java/com/softropic/sendam/client/api/DrCallbackResource.java
  modified:
    - src/main/java/com/softropic/sendam/client/config/ClientConfig.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java
    - src/main/resources/application.yaml

key-decisions:
  - "NexahClient does not extend AbstractClient — AbstractClient requires RestRequestInterceptor for MoMo auth; Nexah uses credential-in-body POST auth, incompatible with interceptor model"
  - "ProviderError enum created alongside ProviderUnavailableException — consistent with SmsError/CancelNotAllowedException pattern (each domain defines its own ErrorCode enum)"
  - "NexahSecurityConfiguration @Order(0) with securityMatcher('/v1/provider/**') — scoped narrowly to avoid shadowing /v1/sms/** and other client paths protected by @Order(1)"
  - "DrCallbackResource is a stub in Plan 01 — returns empty dlrlist; processing wired in Plan 04-02 by DrCallbackService"
  - "checkAvailability() not wrapped by circuit breaker — it IS the probe, not the guarded operation"

patterns-established:
  - "Provider client pattern: own RestTemplate with BufferingClientHttpRequestFactory + MappingJackson2HttpMessageConverter(FAIL_ON_UNKNOWN_PROPERTIES=false), no AbstractClient inheritance"
  - "Nexah typo: 'reponsecode'/'reponsedescription' in DR payloads — preserved in NexahDrEntry and NexahDrAck"
  - "DR totalSmsUnit: String in DR payloads, Integer in send response — distinct types in distinct DTOs"

# Metrics
duration: 6min
completed: 2026-03-10
---

# Phase 4 Plan 1: Nexah Client Infrastructure Summary

**Nexah HTTP client with Resilience4j circuit breaker, 8 DR/send DTOs with spec-exact @JsonProperty mappings, and permit-all security chain for the /v1/provider/dr callback endpoint**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-10T19:42:55Z
- **Completed:** 2026-03-10T19:48:31Z
- **Tasks:** 2
- **Files modified:** 16 (13 created, 3 modified)

## Accomplishments

- NexahClient with `sendSms` wrapped by `@CircuitBreaker(name = "nexah")` and fallback that throws `ProviderUnavailableException`; `checkAvailability()` probe for health checks
- All 8 Nexah DTOs with exact @JsonProperty mappings including the `reponsecode` typo in DR types and `String totalSmsUnit` in `NexahDrEntry` (vs `Integer` in `NexahSmsEntry`)
- `NexahSecurityConfiguration` @Order(0) permitting `/v1/provider/**` without API key auth, plus `DrCallbackResource` stub (Plan 04-02 wires real processing)
- `ProviderUnavailableException` -> HTTP 503 PROVIDER_UNAVAILABLE mapped in ApiAdvice

## Task Commits

Each task was committed atomically:

1. **Task 1: Nexah DTOs and NexahClient** - `cdd467c` (feat)
2. **Task 2: ProviderUnavailableException, NexahSecurityConfiguration, DrCallbackResource skeleton, ApiAdvice handler** - `401b5af` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `client/contract/nexah/NexahProperties.java` - @ConfigurationProperties(prefix="nexah") record
- `client/contract/nexah/NexahSendRequest.java` - Send request DTO (user/password/senderid/sms/mobiles)
- `client/contract/nexah/NexahSendResponse.java` - Send response DTO (responsecode/sms list)
- `client/contract/nexah/NexahSmsEntry.java` - SMS entry in send response (Integer totalSmsUnit)
- `client/contract/nexah/NexahDrEntry.java` - DR callback inbound entry (String totalSmsUnit, reponsecode typo)
- `client/contract/nexah/NexahDrPayload.java` - Inbound DR callback payload (dlrlist)
- `client/contract/nexah/NexahDrAck.java` - DR acknowledgement entry (reponsecode typo, int status)
- `client/contract/nexah/NexahDrResponse.java` - DR acknowledgement response (dlrlist)
- `client/infrastructure/nexah/NexahClient.java` - Outbound HTTP client with circuit breaker
- `client/contract/exception/ProviderUnavailableException.java` - Typed exception for circuit OPEN state
- `client/contract/exception/ProviderError.java` - ErrorCode enum: PROVIDER_UNAVAILABLE
- `client/config/NexahSecurityConfiguration.java` - @Order(0) SecurityFilterChain for /v1/provider/**
- `client/api/DrCallbackResource.java` - POST /v1/provider/dr stub
- `client/config/ClientConfig.java` - Added @EnableConfigurationProperties(NexahProperties.class)
- `security/api/ApiAdvice.java` - Added providerUnavailableHandler -> HTTP 503
- `resources/application.yaml` - Added resilience4j.circuitbreaker.instances.nexah and nexah.* config

## Decisions Made

- **NexahClient does not extend AbstractClient** — AbstractClient requires a `RestRequestInterceptor` designed for MoMo token-header auth; Nexah uses credentials-in-body POST auth incompatible with that interceptor. NexahClient creates its own RestTemplate using the same pattern (BufferingClientHttpRequestFactory + ObjectMapper with FAIL_ON_UNKNOWN_PROPERTIES=false).
- **ProviderError enum** added alongside `ProviderUnavailableException` — consistent with the established pattern where each domain defines its own `ErrorCode` enum (SmsError, ClientError, SecError). Keeps error codes co-located with exceptions.
- **NexahSecurityConfiguration @Order(0) with narrow securityMatcher** — scoped to `/v1/provider/**` only, so it does not shadow the @Order(1) API key chain that protects `/v1/sms/**`, `/v1/credits/**`, etc.
- **DrCallbackResource is a deliberate stub** — returns empty dlrlist in Plan 01 to allow security chain verification independently from message processing. Plan 04-02 replaces the stub body.
- **`checkAvailability()` not wrapped by circuit breaker** — it IS the health probe; wrapping it would defeat its purpose as a mechanism to check if the breaker should close.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None — all files compiled on first attempt. 124 existing tests pass with zero regressions. The `emailService` circuit breaker errors in test output are intentional simulation in pre-existing email resilience tests.

## User Setup Required

**External services require manual configuration.** Three environment variables must be set before the application can call Nexah:

| Variable | Source |
|----------|--------|
| `NEXAH_USER` | Nexah account login (smsvas.com) |
| `NEXAH_PASSWORD` | Nexah account password |
| `NEXAH_SENDERID` | Sender ID registered with Nexah |

Without these, the application context will fail to start (Spring Boot will reject unresolvable `${NEXAH_USER}` etc.).

## Next Phase Readiness

- Plan 04-02 (DrCallbackService): `DrCallbackResource.handleDrCallback()` stub is ready to receive the service; `NexahDrPayload` and `NexahDrResponse` DTOs are correct
- Plan 04-03 (SmsService integration): `NexahClient.sendSms()` and `NexahSendRequest`/`NexahSendResponse` types are complete and ready for wiring
- No blockers for Plans 02 or 03

---
*Phase: 04-provider-integration*
*Completed: 2026-03-10*
