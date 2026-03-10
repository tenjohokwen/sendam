# Codebase Concerns

**Analysis Date:** 2026-03-09

---

## Tech Debt

**In-memory login-attempt tracking is not multi-node safe:**
- Issue: `LoginAttemptsService` uses Guava `LoadingCache` (JVM-local) for tracking failed logins by client, IP, and user. The comment in the file itself flags this: "This cache cannot be used for multi node apps."
- Files: `src/main/java/com/softropic/sendam/security/service/LoginAttemptsService.java`
- Impact: In a horizontally-scaled deployment each node maintains independent counters. An attacker can bypass lockout thresholds by spreading requests across nodes. Brute-force protection is entirely ineffective at scale.
- Fix approach: Replace with a distributed cache (Redis) or a database-backed counter with `SELECT FOR UPDATE` semantics. A Redis Bucket4j integration would work given bucket4j-core is already a dependency.

**Allowed machine-client list is hard-coded in configuration, not the database:**
- Issue: `ClientIdAccessDecisionManager` reads `allowed.clients` from `application.yaml` as a comma-delimited string at startup. Adding or removing clients requires a redeployment.
- Files: `src/main/java/com/softropic/sendam/security/service/ClientIdAccessDecisionManager.java`, `src/main/resources/application.yaml`
- Impact: Operational inflexibility; no audit trail for client allow-list changes.
- Fix approach: Persist allowed clients in a DB table; reload on cache-miss or with a short TTL.

**`blacklistClient()` records but does not enforce:**
- Issue: `LoginAttemptsService.blacklistClient()` increments a Guava cache entry for the client but the actual guard (`clientNotBlacklisted`) only checks if the count is zero. More critically, the method is called but the resulting block is never plumbed back into access decisions for subsequent requests beyond the current login attempt flow. The TODO in the code reads: "not yet making use of this to prevent users. Only recording."
- Files: `src/main/java/com/softropic/sendam/security/service/LoginAttemptsService.java` (line 142)
- Impact: Fraud detection fires warnings but fraudulent clients are not actually blocked.
- Fix approach: After blacklisting, persist to DB and check on every request in `ClientIdAccessDecisionManager`.

**SMS registration strategy is a stub:**
- Issue: `SmsRegistrationStrategy` returns a random UUID for both `notifyNewUser` and `notifyUserExists`. No SMS is sent.
- Files: `src/main/java/com/softropic/sendam/security/api/registration/SmsRegistrationStrategy.java`
- Impact: Phone-based user registration silently succeeds without sending an activation SMS. Users who register via phone never receive activation codes.
- Fix approach: Integrate an SMS gateway (Twilio, AWS SNS); implement the send + delivery tracking.

**`audit/` package not yet migrated to target architecture:**
- Issue: `ARCHITECTURE.md` explicitly marks the `audit/` sub-package as "pending migration to infrastructure". It contains service, listeners, repository, and events that should be split across `infrastructure/audit/`, `repo/`, and `contract/event/`.
- Files: `src/main/java/com/softropic/sendam/security/audit/` (all files)
- Impact: Violates the declared architectural invariant; new audit code added here compounds the debt.
- Fix approach: Move classes per the table in ARCHITECTURE.md. Migrate listener → `infrastructure/audit/`, repository entity → `repo/`, events → `contract/event/`.

**`User.toString()` logs password hash:**
- Issue: `User.toString()` includes `password` (the BCrypt hash) in its output: `", \"password\":\"" + password + "\""`. Any log statement or debug output printing a `User` object will record the hash.
- Files: `src/main/java/com/softropic/sendam/security/repo/User.java` (lines 332–333)
- Impact: Password hashes in logs increase risk if log storage is compromised. This is a low-risk (hash, not plaintext) but still poor hygiene.
- Fix approach: Remove `password` from `toString()` or mask it (`"[PROTECTED]"`).

**Duplicate `@Autowired` field injection in controllers:**
- Issue: `AccountResource` and `ProfileResource` use field injection (`@Autowired` on fields) instead of constructor injection. `ApiAdvice` also uses field injection.
- Files: `src/main/java/com/softropic/sendam/security/api/AccountResource.java`, `src/main/java/com/softropic/sendam/security/api/ProfileResource.java`, `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`
- Impact: Makes classes harder to unit-test without a Spring context; hides dependencies; violates project convention (other classes use constructor injection).
- Fix approach: Replace with constructor injection.

**Duplicate validation utilities:**
- Issue: Two `InputValidatorTest` classes exist in different packages: `src/test/java/com/softropic/sendam/common/validation/InputValidatorTest.java` and `src/test/java/com/softropic/sendam/security/api/util/InputValidatorTest.java`. The TODO in `InputValidator.java` also notes email validation is inconsistent with the `@Email` annotation regex.
- Files: `src/main/java/com/softropic/sendam/common/validation/InputValidator.java`, `src/main/java/com/softropic/sendam/security/infrastructure/jwt/filter/JWTAuthenticationFilter.java` (line 215)
- Impact: Different validation paths may accept/reject different email formats, producing confusing behavior.
- Fix approach: Unify email validation to a single regex or bean-validation annotation; delete the duplicate test class.

**`AbstractClient` uses deprecated `RestTemplate` without SSL configuration:**
- Issue: `AbstractClient` constructs `RestTemplate` with `SimpleClientHttpRequestFactory`, which uses JDK's `HttpURLConnection`. The `TcpConfiguration.checkCertificate` field exists but is never wired into the `RestTemplate`; SSL verification cannot actually be disabled per-client. The config YAML sets `checkCertificate: false` for the MoMo client, suggesting this was intended.
- Files: `src/main/java/com/softropic/sendam/common/client/AbstractClient.java`, `src/main/java/com/softropic/sendam/common/client/TcpConfiguration.java`
- Impact: The `checkCertificate` configuration property is silently ignored. Connections always use JVM default trust store.
- Fix approach: Switch to `HttpComponentsClientHttpRequestFactory` and wire `checkCertificate` into the `SSLContext`.

**No Flyway migration scripts present:**
- Issue: `src/main/resources/db/migration/` directory exists but contains no SQL files. The dev profile uses `hibernate.ddl-auto: create-drop`, meaning schema is managed by Hibernate in development, defeating the purpose of Flyway. Production profile has `ddl-auto: none`.
- Files: `src/main/resources/db/migration/` (empty), `src/main/resources/application-dev.yaml` (line 28), `src/main/resources/application.yaml` (line 28)
- Impact: Production deployments with `ddl-auto: none` and no migration scripts will fail to create schema on a fresh database. Schema changes cannot be versioned.
- Fix approach: Write baseline Flyway migration matching the current Hibernate-generated schema; commit all future changes as versioned migrations.

---

## Security Considerations

**All actuator endpoints exposed in production profile:**
- Risk: `application.yaml` sets `management.endpoints.web.exposure.include: "*"`. This exposes heap dumps, env (with secret values), shutdown, and other sensitive endpoints.
- Files: `src/main/resources/application.yaml` (lines 123–126)
- Current mitigation: `show-details: when-authorized` is set for health/env; ROLE_ADMIN required. However, many other endpoints (beans, conditions, mappings, loggers, threaddump) have no such restriction.
- Recommendations: Restrict to `health,info,metrics,prometheus` in production. Protect `/manage/**` via the security filter chain (it is listed in `SECURED_MAPPINGS` with `ADMIN` role but that depends on `activate.security` being true).

**JMX enabled in production profile:**
- Risk: `spring.jmx.enabled: true` in `application.yaml`. JMX ports expose management operations and can be exploited if left accessible.
- Files: `src/main/resources/application.yaml` (line 25)
- Current mitigation: None apparent; no JMX auth configuration found.
- Recommendations: Set `spring.jmx.enabled: false` unless remote JMX management is explicitly required and properly secured.

**CSRF disabled globally:**
- Risk: `http.csrf(AbstractHttpConfigurer::disable)` in `SecurityConfiguration`. The comment justifies this with "not needed when using token-based authentication", which is correct for pure API clients — but the app also serves an SPA at `/` that could be targeted by CSRF if cookies are used for auth (and they are, via `JWT_COOKIE_NAME`).
- Files: `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` (line 166)
- Current mitigation: JWT stored in `HttpOnly` cookie reduces CSRF risk since JavaScript cannot read it; the double-submit pattern is not implemented.
- Recommendations: Evaluate CSRF risk for each state-changing endpoint; consider enabling CSRF with cookie-based CSRF token for the SPA flow.

**`X-Frame-Options` disabled:**
- Risk: `http.headers(customizer -> customizer.frameOptions(... ::disable))` removes all clickjacking protection.
- Files: `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` (line 186)
- Current mitigation: None.
- Recommendations: Set `SAMEORIGIN` instead of disabling entirely unless embedding in frames is explicitly required.

**Hardcoded placeholder credentials in dev YAML:**
- Risk: `application-dev.yaml` uses fallback values like `dev_mail_password`, `dev_gmx_password`, `dev_gmail_password` as defaults when environment variables are absent. If dev profile is accidentally activated in a shared environment these values are used.
- Files: `src/main/resources/application-dev.yaml` (lines 73, 97, 103, 109, 158)
- Current mitigation: Values are placeholders, not real credentials (unlike the historical hardcoded values flagged in `docs/owasp-violations.md`).
- Recommendations: Ensure dev profile cannot be activated in any internet-facing environment; document profile activation safeguards.

**MoMo API endpoints point to sandbox:**
- Risk: Both `application.yaml` and `application-dev.yaml` point to `sandbox.momodeveloper.mtn.com`. There is no production MoMo configuration.
- Files: `src/main/resources/application.yaml` (line 142), `src/main/resources/application-dev.yaml` (line 153)
- Current mitigation: N/A (sandbox is not real money).
- Recommendations: Create a production profile with real MoMo endpoints and proper key management before going live.

**Session ID not validated in JWT claims:**
- Risk: `RequestMetadataProvider` has a TODO on line 81: "to avoid fraud, ensure the session id is found in the claims". This means JWT theft cannot be detected via session ID mismatch.
- Files: `src/main/java/com/softropic/sendam/security/common/util/RequestMetadataProvider.java` (line 81)
- Current mitigation: JWT theft detection is partially implemented (`JWTTheftException` exists), but session ID binding is incomplete.
- Recommendations: Complete session ID claim validation in the JWT authorization filter.

---

## Performance Bottlenecks

**`addresses` loaded eagerly for every Customer:**
- Problem: `Customer.addresses` is a `@ElementCollection(fetch = FetchType.EAGER)`. Every query that loads a `User` or `Customer` will also load all associated addresses, even when addresses are not needed.
- Files: `src/main/java/com/softropic/sendam/security/repo/Customer.java` (line 76)
- Cause: Eager fetch with no size bound on a collection per entity.
- Improvement path: Change to `FetchType.LAZY` and load addresses only in endpoints that display profile/address data.

**`RestTemplate` (synchronous, blocking) used for external HTTP:**
- Problem: `AbstractClient` uses synchronous `RestTemplate` for MoMo API calls. Spring Boot 3.x ships `WebClient` (reactive/non-blocking) as the recommended alternative.
- Files: `src/main/java/com/softropic/sendam/common/client/AbstractClient.java`
- Cause: Legacy client design from before reactive adoption.
- Improvement path: Migrate to `RestClient` (Spring 6.1+) or `WebClient` for non-blocking external calls; `RestTemplate` is in maintenance mode.

**No timer metrics on REST client calls:**
- Problem: `RestRequestInterceptor` has a TODO: "add timer metrics". External call latency is not tracked.
- Files: `src/main/java/com/softropic/sendam/common/client/RestRequestInterceptor.java` (line 39)
- Cause: Metrics instrumentation was not completed.
- Improvement path: Add Micrometer `Timer` around `execute()` calls; Prometheus exporter is already on the classpath.

---

## Fragile Areas

**`SecurityConfiguration.fraudAwareAuthenticationManager()` is not a `@Bean`:**
- Files: `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` (line 106)
- Why fragile: `fraudAwareAuthenticationManager()` is a plain method annotated with `@SuppressWarnings("PMD")` but not `@Bean`. It creates a new `AuthenticationManagerSimulator` on every call. The `filterChain` calls it inline. If this method is called more than once, multiple instances are created.
- Safe modification: If a second filter or component needs `FraudAwareAuthenticationManager`, it cannot be injected — it must call the same private method, creating a second instance with a separate `AuthenticationManagerSimulator`.
- Test coverage: No test explicitly covers that `FraudAwareAuthenticationManager` is a singleton.

**`DbSchemaChecker` fires in `@PostConstruct`, not on context refresh:**
- Files: `src/main/java/com/softropic/sendam/common/persistence/DbSchemaChecker.java`
- Why fragile: The TODO on line 32 notes the author is uncertain if `@PostConstruct` is the right hook. If Flyway has not yet run by the time this bean initializes, it will always find pending migrations and abort startup. Bean initialization order in Spring is non-deterministic without explicit dependencies.
- Safe modification: Add `@DependsOn("flyway")` or refactor to an `ApplicationReadyEvent` listener.
- Test coverage: Not covered in the test suite.

**`activate.security=false` disables all authorization:**
- Files: `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` (lines 113–114, 216–223)
- Why fragile: Setting `activate.security=false` (e.g., in a misconfigured CI environment) permits all requests to secured endpoints without authentication. The property is read from the environment at startup.
- Safe modification: Never set `activate.security=false` in any non-isolated test environment. Add a startup assertion that the property is `true` when `spring.profiles.active` does not include `test`.
- Test coverage: `ApplicationNoSecurity` test config exists; ensure it is `@ActiveProfiles("test")` scoped only.

---

## Missing Critical Features

**No Flyway migration scripts:**
- Problem: The database schema has no migration history. Cannot deploy to a fresh environment or track incremental schema changes.
- Blocks: Production deployment on a new database; schema evolution without downtime.

**SMS delivery not implemented:**
- Problem: Phone-based registration silently drops the notification.
- Blocks: Any use case that requires phone-number-based registration or OTP delivery via SMS.

**Client blacklisting not enforced:**
- Problem: Blacklisted clients are stored in-memory but the block is not applied on subsequent requests.
- Blocks: Fraud mitigation for persistent bad actors.

**No duplicate registration deduplication window:**
- Problem: `AccountManagementFacade.registerAccount()` has a TODO noting that the messaging module should avoid sending duplicate notifications within 5 minutes. Multiple rapid registrations for the same email can send multiple emails.
- Files: `src/main/java/com/softropic/sendam/security/api/AccountManagementFacade.java` (line 120)
- Blocks: Preventing email spam to existing users from repeated registration attempts.

---

## Test Coverage Gaps

**No tests for `AccountResource` endpoints:**
- What's not tested: Registration, activation, password-reset-init, password-reset-finish as HTTP-level integration tests with request/response contracts.
- Files: `src/main/java/com/softropic/sendam/security/api/AccountResource.java`
- Risk: Breaking API changes (status codes, field names) go undetected.
- Priority: High

**No tests for `ProfileResource`:**
- What's not tested: Email change, phone change, address update, 2FA toggle — all HTTP-level.
- Files: `src/main/java/com/softropic/sendam/security/api/ProfileResource.java`
- Risk: Profile mutation endpoints can silently break.
- Priority: High

**SMS registration path has no test:**
- What's not tested: `SmsRegistrationStrategy` invocation path; phone-based registration flow.
- Files: `src/main/java/com/softropic/sendam/security/api/registration/SmsRegistrationStrategy.java`
- Risk: When SMS is implemented, there is no test baseline to validate the new behavior against the existing API contract.
- Priority: Medium

**`ClientIdAccessDecisionManager` has no test:**
- What's not tested: Machine client allow-list enforcement; behavior when `requestMetadata.isMachineClient()` returns true/false.
- Files: `src/main/java/com/softropic/sendam/security/service/ClientIdAccessDecisionManager.java`
- Risk: Allow-list bypass goes undetected.
- Priority: Medium

**`ApiAdvice` exception handler coverage is partial:**
- What's not tested: Many exception handler branches in `ApiAdvice` (409 lines) may lack corresponding tests. The `SecurityFilterChainIT` covers some paths but not all 20+ handler methods.
- Files: `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`
- Risk: Incorrect HTTP status codes or error body shapes returned for certain exception types.
- Priority: Medium

---

## Dependencies at Risk

**`jasypt` version 1.9.3 is unmaintained:**
- Risk: Jasypt 1.9.x has not had a release since 2014. If a vulnerability is found there is no upstream fix.
- Files: `pom.xml` (line 163)
- Impact: Encryption of sensitive config properties at rest.
- Migration plan: Evaluate `spring-boot-starter-vault` (HashiCorp Vault) or `jasypt-spring-boot` (a maintained fork) if Jasypt is actively used.

**`jjwt` at version 0.13.0 (pre-release naming convention):**
- Risk: The current stable jjwt series is 0.12.x. `0.13.0` is a non-standard version string; the `owasp-violations.md` document was written when the project used `0.12.6`. Verify the exact artifact in the lock files.
- Files: `pom.xml` (lines 147–161)
- Impact: JWT token creation and validation.
- Migration plan: Confirm the exact version resolves correctly and check for any breaking changes in `0.13.0`.

**`com.vladmihalcea:hibernate-types-60` alongside `hypersistence-utils`:**
- Risk: Both `hibernate-types-60` (version 2.21.1, the older library) and `hypersistence-utils-hibernate-63` (version 3.9.10, the newer successor) are on the classpath. These overlap in functionality.
- Files: `pom.xml` (lines 121–129)
- Impact: Duplicate type mappers could cause conflicts if both register handlers for the same Hibernate type.
- Migration plan: Remove `hibernate-types-60` and consolidate exclusively on `hypersistence-utils-hibernate-63`.

---

## Scaling Limits

**Guava in-memory caches for rate limiting and login attempts:**
- Current capacity: Single JVM, bounded by JVM heap.
- Limit: Any multi-node deployment makes rate limits and login attempt counts per-node, not global.
- Scaling path: Bucket4j with Redis backend (`bucket4j-redis`) for `RateLimitingService`; distributed cache (Redis) for `LoginAttemptsService`.

**HikariCP pool size capped at 25 per node (PostgreSQL limit of 100 total):**
- Current capacity: 25 connections per node × 4 nodes = 100 (PostgreSQL default).
- Limit: Adding a fifth node will exceed PostgreSQL's default `max_connections`. PgBouncer or connection pooling middleware is needed before scaling beyond 4 nodes.
- Files: `src/main/resources/application.yaml` (lines 51–52)
- Scaling path: Deploy PgBouncer or increase PostgreSQL `max_connections` (with memory consideration).

---

*Concerns audit: 2026-03-09*
