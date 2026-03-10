# Architecture

**Analysis Date:** 2026-03-09

## Pattern Overview

**Overall:** Layered Package Architecture within a Monolith with integrated SPA frontend

**Key Characteristics:**
- The backend is a Spring Boot 3 monolith serving a Vue/Quasar SPA as static assets
- Package location is the specification: every class's allowed imports are determined by its package alone
- Dependency flow is strictly unidirectional — no circular references allowed (`spring.main.allow-circular-references: false`)
- The application is split into vertical slices (`security`, `email`) each with identical internal layer structure
- The frontend communicates with the backend exclusively through REST APIs using cookie-based JWT authentication

## Layers

**api (Entry Points):**
- Purpose: REST controllers and facades that translate HTTP to domain logic
- Location: `src/main/java/com/softropic/sendam/security/api/`
- Contains: `@RestController` classes, facade `@Service` classes, API-only DTOs, registration strategies, `@RestControllerAdvice`
- Depends on: `service`, `contract`, `repo` (for direct entity access in limited cases)
- Used by: HTTP clients (browser SPA, external callers)
- Key files: `AccountResource.java`, `ProfileResource.java`, `AccountManagementFacade.java`, `ApiAdvice.java`

**contract (Shared Language):**
- Purpose: Passive types (DTOs, enums, events, exceptions, value objects) with no Spring bean lifecycle
- Location: `src/main/java/com/softropic/sendam/security/contract/`
- Contains: DTOs, enums, Spring application events, all exceptions, `@ConfigurationProperties` classes, projection interfaces, value objects crossing layers
- Depends on: nothing within the module (only standard Java and third-party libraries)
- Used by: all other layers — importable from everywhere without creating cycles
- Key files: `Principal.java`, `UserDto.java`, `LoginIdType.java`, `SecurityProperties.java`, `contract/event/AccountChangeEvent.java`, `contract/exception/AuthorizationException.java`

**service (Domain Logic):**
- Purpose: Business use cases; orchestrates reads from repo, applies rules, publishes events, returns contract types
- Location: `src/main/java/com/softropic/sendam/security/service/`
- Contains: `@Service` classes, domain interfaces implemented by infrastructure
- Depends on: `repo`, `contract`, `common`
- Prohibited from importing: `infrastructure`, `api`, `config`
- Key files: `UserService.java`, `UserRegistrationService.java`, `LoginAttemptsService.java`, `TwoFactorLoginService.java`, `PasswordResetService.java`, `LoginTokenManager.java` (interface implemented by `infrastructure/jwt/JwtManagerImpl.java`)

**infrastructure (Technical Implementations):**
- Purpose: Fulfills service interfaces using technical details — JWT, JPA auditing, filters, event listeners
- Location: `src/main/java/com/softropic/sendam/security/infrastructure/`
- Contains: JWT management, security filters, Spring event listeners, authentication handlers, AOP aspects
- Depends on: `service`, `repo`, `contract`, `common`
- Prohibited from importing: `api`
- Sub-packages:
  - `infrastructure/jwt/` — JWT creation, validation, extraction (`JwtManagerImpl`, `TokenCreatorImpl`, `TokenValidatorImpl`)
  - `infrastructure/jwt/filter/` — `JWTAuthenticationFilter`, `JWTAuthorizationFilter`
  - `infrastructure/filter/` — `SecondFactorLoginFilter`, `SecurityAdviceFilter`, `SessionRefreshFilter`
  - `infrastructure/listener/` — `AuthenticationFailureListener`, `AuthenticationSuccessListener`, `SendMailListener`
  - `infrastructure/audit/` — `SpringSecurityAuditorAware`

**repo (Persistence Leaf):**
- Purpose: JPA entities, Spring Data repositories, entity listeners; nothing above may be imported here
- Location: `src/main/java/com/softropic/sendam/security/repo/`
- Contains: `@Entity` classes, Spring Data `Repository` interfaces, `@EntityListeners`
- Depends on: `contract` only
- Key files: `User.java`, `LoginInfo.java`, `SecKey.java`, `UserRepository.java`, `LoginInfoRepository.java`, `SecKeyEntityListener.java`

**common (Internal Cross-Cutting):**
- Purpose: Internal utilities too tightly coupled to the module to live in the global `common` package
- Location: `src/main/java/com/softropic/sendam/security/common/`
- Contains: internal Spring events, cookie utilities, security constants, request metadata providers
- Depends on: `contract` only
- Prohibited from importing: `service`, `infrastructure`, `repo`, `api`
- Key files: `common/event/AuthEvent.java`, `common/event/FraudEvent.java`, `common/util/CookieUtil.java`, `common/util/SecurityConstants.java`, `common/util/RequestMetadataProvider.java`

**config (Composition Root):**
- Purpose: Spring wiring. The only layer permitted to import from all other layers simultaneously
- Location: `src/main/java/com/softropic/sendam/security/config/` and `src/main/java/com/softropic/sendam/config/`
- Contains: `@Configuration` classes, `@EnableConfigurationProperties` registrations, CORS, MVC, security filter chain wiring
- Note: Exempt from the unidirectional import rule — it is the composition root
- Key files: `SecurityConfiguration.java`, `DataSourceConfig.java`

**Global common (Cross-Module Utilities):**
- Purpose: Shared types, base entities, and utilities used across all vertical slices
- Location: `src/main/java/com/softropic/sendam/common/`
- Contains: `AbstractAuditingEntity`, `BaseEntity`, persistence helpers, message response types (`Response`, `Success`, `Failure`, `ErrorDto`), validation annotations, HTTP client abstractions, payment/refund enums, thread pool MDC wrappers
- Key files: `common/persistence/AbstractAuditingEntity.java`, `common/message/Response.java`, `common/message/Success.java`, `common/client/AbstractClient.java`

## Data Flow

**HTTP Request → Response:**
1. Request enters Spring Security filter chain (`JWTAuthorizationFilter` validates JWT cookie, sets `SecurityContext`)
2. `JWTAuthenticationFilter` handles `/authenticate` — reads credentials from body, calls `AuthenticationManager`, issues JWT cookie on success
3. Controller method (`@RestController` in `api/`) receives request, calls `service` classes
4. `service` reads/writes via `repo` (Spring Data repositories), applies business logic, publishes Spring application events
5. Controller returns `Success` or `ErrorDto` record; `ApiAdvice` (`@RestControllerAdvice`) catches any exception and translates to `ErrorDto`

**Authentication Flow:**
1. `POST /authenticate` → `JWTAuthenticationFilter` → `FraudAwareAuthenticationManager` → `DaoAuthProvider` → `LoadUserByUserNameService`
2. Success: JWT tokens created by `JwtManagerImpl`, written to `HttpOnly` cookies
3. Optional 2FA: `TwoFactorLoginService` generates OTP; `POST /otp` completes with `SecondFactorLoginFilter`
4. Subsequent requests: `JWTAuthorizationFilter` extracts and validates JWT from cookie, sets Spring `SecurityContext`

**Email Notification Flow:**
1. `AccountManagementFacade` (in `security/api/`) publishes an `Envelope` Spring application event
2. `AccountChangeEmailListener` (in `email/infrastructure/listener/`) receives the event
3. `MailManager` (in `email/service/`) persists the `Envelope` to DB, then calls `MailService`
4. `MailService` renders Thymeleaf template and sends via `JavaMailSender` (provider rotation via `SenderProvider`)
5. `EmailRetryScheduler` retries failed envelopes via `SELECT FOR UPDATE SKIP LOCKED`

**State Management (Frontend):**
- Pinia store (`src/frontend/src/stores/`) for reactive state
- Cookie-based auth detection: presence of `user=` cookie signals authenticated state
- `useSession` composable (`src/frontend/src/composables/useSession.js`) wraps session monitoring and logout logic
- Axios instance in `src/frontend/src/boot/axios.js` with interceptors for loading state, locale header, and 401 redirect

## Key Abstractions

**Response Envelope (`Success` / `Failure` / `ErrorDto`):**
- Purpose: Standardized API response wrapper with `helpCode` (support ID) for every response
- Examples: `src/main/java/com/softropic/sendam/common/message/Success.java`, `common/message/ErrorDto.java`
- Pattern: `Success` is a Java record: `record Success(String helpCode, String msgKey, String msg, Map<String, Object> payload)`

**`LoginTokenManager` Interface:**
- Purpose: Separates the JWT capability contract (defined in `service/`) from its JWT implementation (in `infrastructure/`)
- Examples: `src/main/java/com/softropic/sendam/security/service/LoginTokenManager.java` (interface), `src/main/java/com/softropic/sendam/security/infrastructure/jwt/JwtManagerImpl.java` (impl)
- Pattern: Interface in `service/`, implementation in `infrastructure/`

**`AbstractAuditingEntity`:**
- Purpose: Base entity providing `createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate`, `requestId`, `sessionId`, `status` to all JPA entities
- Examples: `src/main/java/com/softropic/sendam/common/persistence/AbstractAuditingEntity.java`
- Pattern: `@MappedSuperclass` with `@Audited` (Hibernate Envers), `@EntityListeners` for request/session ID stamping and Spring Data auditing

**`AccountManagementFacade`:**
- Purpose: Orchestration facade in `api/` that coordinates multiple services and strategies for account lifecycle operations
- Examples: `src/main/java/com/softropic/sendam/security/api/AccountManagementFacade.java`
- Pattern: `@Service` in `api/` package; applies `@RateLimited` AOP aspect to all public methods

**`@RateLimited` Annotation + `RateLimitingAspect`:**
- Purpose: Declarative per-endpoint rate limiting using Bucket4j
- Examples: `src/main/java/com/softropic/sendam/security/contract/util/RateLimited.java` (annotation), `src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java` (aspect)

**`RegistrationNotificationStrategy` (Strategy Pattern):**
- Purpose: Pluggable email vs SMS notification for user registration
- Examples: `src/main/java/com/softropic/sendam/security/api/registration/EmailRegistrationStrategy.java`, `SmsRegistrationStrategy.java`

## Entry Points

**`AppTemplateApplication`:**
- Location: `src/main/java/com/softropic/sendam/AppTemplateApplication.java`
- Triggers: `java -jar` / Maven Spring Boot plugin
- Responsibilities: Spring Boot bootstrap, enables `@EnableRetry`

**`AccountResource` (REST):**
- Location: `src/main/java/com/softropic/sendam/security/api/AccountResource.java`
- Triggers: HTTP requests to `/v1/account/**`
- Responsibilities: Registration, activation, authentication check, password reset, profile retrieval

**`ProfileResource` (REST):**
- Location: `src/main/java/com/softropic/sendam/security/api/ProfileResource.java`
- Triggers: HTTP requests to profile endpoints
- Responsibilities: Profile updates (email, phone, password, 2FA toggle, address)

**`JWTAuthenticationFilter`:**
- Location: `src/main/java/com/softropic/sendam/security/infrastructure/jwt/filter/JWTAuthenticationFilter.java`
- Triggers: `POST /authenticate`
- Responsibilities: Credential extraction, authentication, JWT issuance, 2FA OTP gate

**`JWTAuthorizationFilter`:**
- Location: `src/main/java/com/softropic/sendam/security/infrastructure/jwt/filter/JWTAuthorizationFilter.java`
- Triggers: All HTTP requests requiring authentication
- Responsibilities: JWT cookie extraction, validation, `SecurityContext` population

**Frontend App:**
- Location: `src/frontend/src/App.vue`
- Triggers: Browser loads SPA from Spring Boot static assets
- Responsibilities: Vue application root; router-view rendering

## Error Handling

**Strategy:** Centralized exception translation at the API boundary via `@RestControllerAdvice`

**Patterns:**
- All exceptions are caught in `ApiAdvice` (`src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`) and translated to `ErrorDto` with a unique `helpCode` (Sqids-encoded UUID)
- Security exceptions (`AuthorizationException`, `AuthenticationException`, JWT exceptions) also publish `SecurityAlertEvent` for audit logging
- `Failure` events (fraud, bad credentials) publish `FraudEvent` or `BadCredentialsEvent` for listener-driven side effects
- All exceptions in the module are defined in `contract/exception/` to be importable from any layer
- `ApplicationException` base class carries a `supportId` (help code) and `logContext` map for structured logging

## Cross-Cutting Concerns

**Logging:** SLF4J + Logstash Logback encoder for structured JSON logs; MDC propagation across thread pools via `MdcDecorator`/`MdcWrapper`; Tomcat access log writes to `/usr/local/var/ledger/sendam_access.ledger`

**Validation:** Bean Validation (Jakarta) annotations on DTOs; custom annotations in `src/main/java/com/softropic/sendam/common/validation/` (phone numbers, language codes, name format, date windows); `ApiAdvice` translates `MethodArgumentNotValidException` and `ConstraintViolationException` to field-level `ErrorDto`

**Authentication:** Cookie-based JWT (`HttpOnly`); `fcookie` browser fingerprint cookie for fraud detection; `user=` cookie signals authenticated state to SPA router guard; Bucket4j rate limiting via `@RateLimited` AOP aspect

**Auditing:** Hibernate Envers (`@Audited` on all entities) + Spring Data auditing + `RequestIdAuditEntityListener` (stamps `request_id`) + `SessionIdAuditEntityListener` (stamps `session_id`); separate `audit/` sub-module records security audit trail (`AuditLog` entity via `TrailService`)

---

*Architecture analysis: 2026-03-09*
